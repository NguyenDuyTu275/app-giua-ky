package com.appgiuaky.gpaplanner;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Giữ dữ liệu bảng điểm (lưu file JSON trong máy), API key và trạng thái các tác vụ AI.
 * Dùng chung cho mọi màn hình (Fragment lấy qua ViewModelProvider của Activity).
 */
public class AppViewModel extends AndroidViewModel {

    private static final String KEY_API = "gemini_api_key";
    /** Keeps each advisor request small; older turns matter little once the transcript is resent. */
    private static final int MAX_HISTORY_MESSAGES = 20;

    private final DataFile file;
    private final SharedPreferences prefs;
    /** Separate from the network thread so a slow Gemini call never delays saving. */
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    /** Luồng nền cho các lệnh gọi mạng tới Gemini. */
    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();

    private final MutableLiveData<AcademicData> data = new MutableLiveData<>();
    /** Storage problem to show the user once; cleared after it is shown. */
    private final MutableLiveData<String> storageNotice = new MutableLiveData<>();
    private final MutableLiveData<String> apiKey = new MutableLiveData<>();

    private final List<ChatMessage> chatMessages = new ArrayList<>();
    private final MutableLiveData<List<ChatMessage>> chat = new MutableLiveData<>(Collections.<ChatMessage>emptyList());
    private final MutableLiveData<Boolean> chatLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> chatError = new MutableLiveData<>();
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

        // Chuẩn bị dữ liệu trên luồng chính, chỉ gọi mạng ở luồng nền
        final String key = apiKey.getValue();
        final String prompt = GeminiService.advisorPrompt(current());
        final List<ChatMessage> history = GeminiService.recentHistory(chatMessages, MAX_HISTORY_MESSAGES);
        networkExecutor.execute(() -> {
            try {
                String reply = GeminiService.generate(key, prompt, history);
                synchronized (chatMessages) {
                    chatMessages.add(new ChatMessage(false, reply));
                    chat.postValue(new ArrayList<>(chatMessages));
                }
            } catch (Exception e) {
                synchronized (chatMessages) {
                    chatMessages.remove(chatMessages.size() - 1);
                    chat.postValue(new ArrayList<>(chatMessages));
                }
                failedInput = text;
                chatError.postValue(e.getMessage() != null ? e.getMessage() : "Không gọi được Gemini");
            } finally {
                chatLoading.postValue(false);
            }
        });
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
        studyPlanLoading.setValue(true);
        final String key = apiKey.getValue();
        final String request = GeminiService.studyPlanRequest(exams, hoursPerDay, todayUtcMillis());
        networkExecutor.execute(() -> {
            try {
                studyPlan.postValue(GeminiService.generate(key, GeminiService.STUDY_PLANNER_PROMPT,
                        Collections.singletonList(new ChatMessage(true, request))));
            } catch (Exception e) {
                studyPlanError.postValue(e.getMessage() != null ? e.getMessage() : "Không gọi được Gemini");
            } finally {
                studyPlanLoading.postValue(false);
            }
        });
    }

    @Override
    protected void onCleared() {
        networkExecutor.shutdownNow();
        // Let a pending write finish so the last edit is not lost
        ioExecutor.shutdown();
    }
}
