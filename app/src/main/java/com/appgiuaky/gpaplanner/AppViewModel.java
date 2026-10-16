package com.appgiuaky.gpaplanner;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.appgiuaky.gpaplanner.services.DataFile;
import com.appgiuaky.gpaplanner.services.GeminiService;
import com.appgiuaky.gpaplanner.services.GeminiService.ChatMessage;
import com.appgiuaky.gpaplanner.types.AcademicData;
import com.appgiuaky.gpaplanner.types.Course;
import com.appgiuaky.gpaplanner.types.LetterGrade;
import com.appgiuaky.gpaplanner.types.Semester;
import com.appgiuaky.gpaplanner.utils.MockData;

import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Giữ dữ liệu bảng điểm (lưu file JSON trong máy), API key và trạng thái các tác vụ AI.
 * Dùng chung cho mọi màn hình (Fragment lấy qua ViewModelProvider của Activity).
 */
public class AppViewModel extends AndroidViewModel {

    private static final String KEY_API = "gemini_api_key";
    /** Keeps each advisor request small; older turns matter little once the transcript is resent. */
    private static final int MAX_HISTORY_MESSAGES = 20;
    private static final long FIRST_TEXT_TIMEOUT_MS = GeminiService.FIRST_TEXT_TIMEOUT_MS;
    private static final String NO_RESPONSE_MESSAGE = "Gemini chưa phản hồi sau " + FIRST_TEXT_TIMEOUT_MS / 1000
            + " giây. Có thể máy chủ đang quá tải hoặc mạng yếu, thử lại sau ít phút.";
    private static final String NETWORK_MESSAGE = "Không kết nối được tới Gemini. Kiểm tra kết nối mạng rồi thử lại.";

    private final DataFile file;
    private final SharedPreferences prefs;
    /** Separate from the network thread so a slow Gemini call never delays saving. */
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    /**
     * Luồng nền cho các lệnh gọi mạng tới Gemini. Mỗi lần gọi một luồng riêng: lần gọi bị treo vì mạng hỏng
     * không làm lần gửi sau phải xếp hàng chờ nó.
     */
    private final ExecutorService networkExecutor = Executors.newCachedThreadPool();
    private final Handler main = new Handler(Looper.getMainLooper());
    /** Số thứ tự lần gọi Gemini đang chờ của hỏi đáp / lịch ôn thi (xem callGemini). */
    private final AtomicInteger chatCall = new AtomicInteger();
    private final AtomicInteger planCall = new AtomicInteger();

    private final MutableLiveData<AcademicData> data = new MutableLiveData<>();
    /** Storage problem to show the user once; cleared after it is shown. */
    private final MutableLiveData<String> storageNotice = new MutableLiveData<>();
    private final MutableLiveData<String> apiKey = new MutableLiveData<>();

    private final List<ChatMessage> chatMessages = new ArrayList<>();
    private final MutableLiveData<List<ChatMessage>> chat = new MutableLiveData<>(Collections.<ChatMessage>emptyList());
    private final MutableLiveData<Boolean> chatLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> chatError = new MutableLiveData<>();
    /** Phần câu trả lời đang về (streaming); null khi không có câu trả lời nào đang viết dở. */
    private final MutableLiveData<String> chatPartial = new MutableLiveData<>();
    /** Câu hỏi gửi lỗi, được trả lại ô nhập để người dùng gửi lại. */
    private volatile String failedInput;

    /** Ngày thi người dùng chọn cho từng môn (id môn -> mốc 0h UTC của ngày thi). */
    public final Map<String, Long> examDates = new HashMap<>();

    // Planner tab state lives here because the Fragment is recreated on rotation / theme change
    /** Điểm dự kiến khi học cải thiện: id môn -> điểm chữ. */
    public final Map<String, LetterGrade> improvements = new HashMap<>();
    public String rescueCourseId;
    public boolean plannerInputsInitialized;
    public int plannerSection;

    private final MutableLiveData<String> studyPlan = new MutableLiveData<>();
    private final MutableLiveData<Boolean> studyPlanLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> studyPlanError = new MutableLiveData<>();

    public AppViewModel(@NonNull Application app) {
        super(app);
        file = new DataFile(new File(app.getFilesDir(), "academic.json"));
        prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE);
        data.setValue(load());
        apiKey.setValue(prefs.getString(KEY_API, ""));
    }

    // ------------------------------------------------------------ Bảng điểm

    public LiveData<AcademicData> getData() {
        return data;
    }

    public AcademicData current() {
        return data.getValue();
    }

    public LiveData<String> getStorageNotice() {
        return storageNotice;
    }

    public void clearStorageNotice() {
        storageNotice.setValue(null);
    }

    private AcademicData load() {
        try {
            String json = file.read();
            return json == null ? MockData.create() : AcademicData.fromJson(json);
        } catch (IOException | JSONException e) {
            // Keep the broken file for recovery instead of overwriting it with sample data on the next save
            try {
                file.quarantine();
                storageNotice.setValue("File dữ liệu bị lỗi nên đã được sao lưu, app dùng dữ liệu mẫu");
            } catch (IOException moveFailed) {
                storageNotice.setValue("File dữ liệu bị lỗi và không sao lưu được: " + moveFailed.getMessage());
            }
            return MockData.create();
        }
    }

    /** Ghi dữ liệu hiện tại ra file và báo cho các màn hình vẽ lại. */
    private void save() {
        AcademicData d = current();
        // Serialize on the main thread: the background thread must never read lists the UI is mutating
        String json;
        try {
            json = d.toJson();
        } catch (JSONException e) {
            throw new IllegalStateException("Không chuyển được bảng điểm sang JSON", e);
        }
        data.setValue(d);
        ioExecutor.execute(() -> {
            try {
                file.write(json);
            } catch (IOException e) {
                storageNotice.postValue("Không lưu được dữ liệu: " + e.getMessage());
            }
        });
    }

    private Semester semester(String id) {
        for (Semester s : current().semesters) if (s.id.equals(id)) return s;
        throw new IllegalArgumentException("Không có học kỳ " + id);
    }

    public void addSemester(String name) {
        current().semesters.add(new Semester(name, Collections.<Course>emptyList()));
        save();
    }

    public void renameSemester(String id, String name) {
        semester(id).name = name;
        save();
    }

    public void deleteSemester(String id) {
        current().semesters.remove(semester(id));
        save();
    }

    /** Thêm môn mới, hoặc thay môn có cùng id. */
    public void saveCourse(String semesterId, Course course) {
        List<Course> courses = semester(semesterId).courses;
        for (int i = 0; i < courses.size(); i++) {
            if (courses.get(i).id.equals(course.id)) {
                courses.set(i, course);
                save();
                return;
            }
        }
        courses.add(course);
        save();
    }

    public void deleteCourse(String semesterId, String courseId) {
        List<Course> courses = semester(semesterId).courses;
        for (int i = 0; i < courses.size(); i++) {
            if (courses.get(i).id.equals(courseId)) {
                courses.remove(i);
                break;
            }
        }
        save();
    }

    /**
     * Đưa môn vừa xóa trở lại vị trí cũ (nút Hoàn tác).
     * Returns false if its semester no longer exists, so there is nowhere to restore it.
     */
    public boolean restoreCourse(String semesterId, int index, Course course) {
        for (Semester s : current().semesters) {
            if (s.id.equals(semesterId)) {
                s.courses.add(Math.min(Math.max(index, 0), s.courses.size()), course);
                save();
                return true;
            }
        }
        return false;
    }

    public void setProgramCredits(int credits) {
        current().totalProgramCredits = credits;
        save();
    }

    public void replaceSemesters(List<Semester> semesters) {
        current().semesters.clear();
        current().semesters.addAll(semesters);
        save();
    }

    // ------------------------------------------------------------ Gemini API key

    public LiveData<String> getApiKey() {
        return apiKey;
    }

    public void saveApiKey(String key) {
        String trimmed = key.trim();
        prefs.edit().putString(KEY_API, trimmed).apply();
        apiKey.setValue(trimmed);
    }

    // ------------------------------------------------------------ Cố vấn AI (hỏi đáp)

    public LiveData<List<ChatMessage>> getChat() {
        return chat;
    }

    public LiveData<Boolean> getChatLoading() {
        return chatLoading;
    }

    public LiveData<String> getChatError() {
        return chatError;
    }

    public LiveData<String> getChatPartial() {
        return chatPartial;
    }

    /** Lấy (một lần) câu hỏi vừa gửi lỗi để đưa lại vào ô nhập. */
    public String takeFailedInput() {
        String s = failedInput;
        failedInput = null;
        return s;
    }

    public void sendChat(String input) {
        final String text = input.trim();
        if (text.isEmpty() || Boolean.TRUE.equals(chatLoading.getValue())) return;
        chatError.setValue(null);
        chatMessages.add(new ChatMessage(true, text));
        chat.setValue(new ArrayList<>(chatMessages));
        chatLoading.setValue(true);

        callGemini(chatCall, GeminiService.advisorPrompt(current()),
                GeminiService.recentHistory(chatMessages, MAX_HISTORY_MESSAGES),
                chatPartial::setValue,
                reply -> {
                    chatMessages.add(new ChatMessage(false, reply));
                    chat.setValue(new ArrayList<>(chatMessages));
                    chatPartial.setValue(null);
                    chatLoading.setValue(false);
                },
                error -> {
                    chatMessages.remove(chatMessages.size() - 1);
                    chat.setValue(new ArrayList<>(chatMessages));
                    failedInput = text;
                    chatError.setValue(error);
                    chatPartial.setValue(null);
                    chatLoading.setValue(false);
                });
    }

    /**
     * Gọi Gemini trên luồng nền; [onPartial], [onDone], [onError] luôn chạy trên luồng chính.
     * Quá FIRST_TEXT_TIMEOUT_MS mà chưa có chữ nào thì báo lỗi, vì khi mạng hỏng, bước tìm địa chỉ máy chủ (DNS)
     * có thể treo rất lâu mà thời gian chờ của HttpURLConnection không tính tới.
     * Mỗi lần gọi có một số thứ tự trong [current]: lần gọi đã kết thúc hoặc hết giờ thì kết quả về muộn bị bỏ qua.
     */
    private void callGemini(AtomicInteger current, String systemPrompt, List<ChatMessage> history,
                            Consumer<String> onPartial, Consumer<String> onDone, Consumer<String> onError) {
        final String key = apiKey.getValue();
        final int id = current.incrementAndGet();
        final Runnable timeout = () -> finish(current, id, null, () -> onError.accept(NO_RESPONSE_MESSAGE));
        main.postDelayed(timeout, FIRST_TEXT_TIMEOUT_MS);
        networkExecutor.execute(() -> {
            try {
                String result = GeminiService.generate(key, systemPrompt, history, partial -> {
                    // Lần gọi đã hết giờ: ngừng đọc tiếp thay vì tải nốt câu trả lời không còn ai chờ
                    if (current.get() != id) throw new CancellationException();
                    main.post(() -> {
                        if (current.get() != id) return;
                        main.removeCallbacks(timeout);
                        onPartial.accept(partial);
                    });
                });
                main.post(() -> finish(current, id, timeout, () -> onDone.accept(result)));
            } catch (Exception e) {
                String message = errorMessage(e);
                main.post(() -> finish(current, id, timeout, () -> onError.accept(message)));
            }
        });
    }

    /** Kết thúc lần gọi [id] nếu nó vẫn là lần gọi đang chờ (chạy trên luồng chính). */
    private void finish(AtomicInteger current, int id, Runnable timeout, Runnable action) {
        if (current.get() != id) return;
        current.incrementAndGet();
        if (timeout != null) main.removeCallbacks(timeout);
        action.run();
    }

    private static String errorMessage(Exception e) {
        if (e instanceof UnknownHostException || e instanceof ConnectException || e instanceof SocketTimeoutException) {
            return NETWORK_MESSAGE;
        }
        return e.getMessage() != null ? e.getMessage() : "Không gọi được Gemini";
    }

    // ------------------------------------------------------------ Lịch ôn thi

    public LiveData<String> getStudyPlan() {
        return studyPlan;
    }

    public LiveData<Boolean> getStudyPlanLoading() {
        return studyPlanLoading;
    }

    public LiveData<String> getStudyPlanError() {
        return studyPlanError;
    }

    /** Mốc 0h UTC của ngày hôm nay theo giờ máy (cùng quy ước với MaterialDatePicker). */
    public static long todayUtcMillis() {
        Calendar local = Calendar.getInstance();
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.clear();
        utc.set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH));
        return utc.getTimeInMillis();
    }

    public void generateStudyPlan(List<GeminiService.Exam> exams, int hoursPerDay) {
        studyPlanError.setValue(null);
        // Xóa lịch cũ: lịch mới hiện dần ngay khi Gemini viết tới đâu
        studyPlan.setValue(null);
        studyPlanLoading.setValue(true);
        String request = GeminiService.studyPlanRequest(exams, hoursPerDay, todayUtcMillis());
        callGemini(planCall, GeminiService.STUDY_PLANNER_PROMPT,
                Collections.singletonList(new ChatMessage(true, request)),
                studyPlan::setValue,
                plan -> {
                    studyPlan.setValue(plan);
                    studyPlanLoading.setValue(false);
                },
                error -> {
                    // Không để lại nửa lịch trông như đã xong
                    studyPlan.setValue(null);
                    studyPlanError.setValue(error);
                    studyPlanLoading.setValue(false);
                });
    }

    @Override
    protected void onCleared() {
        main.removeCallbacksAndMessages(null);
        networkExecutor.shutdownNow();
        // Let a pending write finish so the last edit is not lost
        ioExecutor.shutdown();
    }
}
