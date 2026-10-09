package com.appgiuaky.gpaplanner;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.appgiuaky.gpaplanner.services.GeminiService;
import com.appgiuaky.gpaplanner.services.GeminiService.ChatMessage;
import com.appgiuaky.gpaplanner.types.AcademicData;
import com.appgiuaky.gpaplanner.types.Course;
import com.appgiuaky.gpaplanner.types.Semester;
import com.appgiuaky.gpaplanner.utils.MockData;

import org.json.JSONException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
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

    private final File file;
    private final SharedPreferences prefs;
    /** Luồng nền cho các lệnh gọi mạng tới Gemini. */
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final MutableLiveData<AcademicData> data = new MutableLiveData<>();
    private final MutableLiveData<String> apiKey = new MutableLiveData<>();

    private final List<ChatMessage> chatMessages = new ArrayList<>();
    private final MutableLiveData<List<ChatMessage>> chat = new MutableLiveData<>(Collections.<ChatMessage>emptyList());
    private final MutableLiveData<Boolean> chatLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> chatError = new MutableLiveData<>();
    /** Câu hỏi gửi lỗi, được trả lại ô nhập để người dùng gửi lại. */
    private volatile String failedInput;

    /** Ngày thi người dùng chọn cho từng môn (id môn -> mốc 0h UTC của ngày thi). */
    public final Map<String, Long> examDates = new HashMap<>();
    private final MutableLiveData<String> studyPlan = new MutableLiveData<>();
    private final MutableLiveData<Boolean> studyPlanLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> studyPlanError = new MutableLiveData<>();

    public AppViewModel(@NonNull Application app) {
        super(app);
        file = new File(app.getFilesDir(), "academic.json");
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

    private AcademicData load() {
        if (!file.exists()) return MockData.create();
        try (InputStream in = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) file.length()];
            int read = 0;
            while (read < bytes.length) {
                int n = in.read(bytes, read, bytes.length - read);
                if (n < 0) break;
                read += n;
            }
            return AcademicData.fromJson(new String(bytes, 0, read, StandardCharsets.UTF_8));
        } catch (IOException | JSONException e) {
            // File hỏng: bắt đầu lại với dữ liệu mẫu thay vì làm app không mở được
            return MockData.create();
        }
    }

    /** Ghi dữ liệu hiện tại ra file và báo cho các màn hình vẽ lại. */
    private void save() {
        AcademicData d = current();
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(d.toJson().getBytes(StandardCharsets.UTF_8));
        } catch (IOException | JSONException e) {
            throw new IllegalStateException("Không lưu được bảng điểm", e);
        }
        data.setValue(d);
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
        final List<ChatMessage> history = new ArrayList<>(chatMessages);
        executor.execute(() -> {
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
        executor.execute(() -> {
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
        executor.shutdownNow();
    }
}
