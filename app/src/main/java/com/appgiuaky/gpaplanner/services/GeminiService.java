package com.appgiuaky.gpaplanner.services;

import com.appgiuaky.gpaplanner.types.AcademicData;
import com.appgiuaky.gpaplanner.types.Course;
import com.appgiuaky.gpaplanner.types.GradeComponent;
import com.appgiuaky.gpaplanner.types.Semester;
import com.appgiuaky.gpaplanner.utils.Format;
import com.appgiuaky.gpaplanner.utils.GpaCalculator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.function.Consumer;

/**
 * Gọi Gemini API (REST generateContent) và chứa các câu prompt cho Cố vấn học vụ & Lập lịch ôn thi.
 * Hàm generate chạy đồng bộ (chặn luồng), nên phải gọi từ luồng nền.
 */
public final class GeminiService {

    private GeminiService() {
    }

    /** gemini-2.5-flash không còn cấp cho người dùng mới (key tạo sau này bị từ chối), nên dùng bản Flash ổn định mới nhất. */
    public static final String MODEL = "gemini-3.8-flash";
    /**
     * Thử lần lượt khi model trước đang quá tải hoặc hết lượt miễn phí (mỗi model có hạn mức riêng).
     * Đều là bản ổn định và đều nhận thinkingLevel "low".
     */
    private static final String[] MODELS = {MODEL, "gemini-3.6-flash", "gemini-3.5-flash-lite"};
    private static final String ENDPOINT_PREFIX = "https://generativelanguage.googleapis.com/v1beta/models/";
    /** alt=sse: câu trả lời về dần theo từng gói (Server-Sent Events) thay vì đợi viết xong cả bài. */
    private static final String ENDPOINT_SUFFIX = ":streamGenerateContent?alt=sse";
    private static final String SSE_DATA = "data:";
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    /**
     * Thời gian chờ tối đa mỗi lần đọc. Cũng là thời gian chờ chữ đầu tiên của mỗi model trước khi chuyển sang
     * model dự phòng, nên đặt ngắn: các gói stream bình thường về cách nhau chỉ vài giây.
     */
    private static final int READ_TIMEOUT_MS = 12_000;
    /** Thời gian chờ chữ đầu tiên đủ để thử hết các model, cộng thêm chút cho kết nối và gửi request. */
    public static final long FIRST_TEXT_TIMEOUT_MS = (long) MODELS.length * READ_TIMEOUT_MS + 4_000;
    /**
     * Mức "suy nghĩ" trước khi trả lời (Gemini 3: low / medium / high, mặc định medium). "low" bắt đầu trả lời
     * sớm hơn mà vẫn đủ cho các phép tính điểm trong app.
     */
    private static final String THINKING_LEVEL = "low";
    private static final long DAY_MILLIS = 24L * 60 * 60 * 1000;

    public static final class ChatMessage {
        public final boolean fromUser;
        public final String text;

        public ChatMessage(boolean fromUser, String text) {
            this.fromUser = fromUser;
            this.text = text;
        }
    }

    /** Một môn cần thi; date là mốc 0h UTC của ngày thi (đúng như MaterialDatePicker trả về). */
    public static final class Exam {
        public final Course course;
        public final long dateUtcMillis;

        public Exam(Course course, long dateUtcMillis) {
            this.course = course;
            this.dateUtcMillis = dateUtcMillis;
        }
    }

    /** Định dạng ngày dd/MM/yyyy cho mốc 0h UTC. */
    public static String formatDate(long utcMillis) {
        SimpleDateFormat f = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(utcMillis));
    }

    /**
     * The last [max] messages, with leading advisor messages dropped because Gemini expects a
     * conversation to start with a user turn.
     */
    public static List<ChatMessage> recentHistory(List<ChatMessage> messages, int max) {
        int start = Math.max(0, messages.size() - max);
        while (start < messages.size() && !messages.get(start).fromUser) start++;
        return new ArrayList<>(messages.subList(start, messages.size()));
    }

    /**
     * Gửi system prompt + lịch sử hội thoại và nhận câu trả lời theo kiểu streaming: mỗi khi có thêm chữ,
     * [onPartial] nhận toàn bộ phần đã có (đã bỏ markdown). Trả về câu trả lời hoàn chỉnh.
     * Chạy đồng bộ (chặn luồng); [onPartial] được gọi trên chính luồng đó.
     */
    public static String generate(String apiKey, String systemPrompt, List<ChatMessage> history,
                                  Consumer<String> onPartial) throws IOException {
        String body = requestBody(systemPrompt, history);
        ModelBusyException busy = null;
        for (String model : MODELS) {
            try {
                return stream(model, apiKey, body, onPartial);
            } catch (ModelBusyException e) {
                // Chưa có chữ nào được gửi về, nên thử model kế tiếp vẫn an toàn
                busy = e;
            }
        }
        throw busy;
    }

    /** Lỗi do model đang quá tải hoặc hết lượt miễn phí: model khác có hàng đợi và hạn mức riêng. */
    static boolean shouldTryNextModel(int httpCode) {
        return httpCode == 429 || httpCode == 500 || httpCode == 503 || httpCode == 504;
    }

    /** Lỗi trước khi có chữ nào, mà một model khác có thể không gặp (xem shouldTryNextModel). */
    private static final class ModelBusyException extends IOException {
        ModelBusyException(String message) {
            super(message);
        }
    }

    private static String requestBody(String systemPrompt, List<ChatMessage> history) throws IOException {
        try {
            JSONArray contents = new JSONArray();
            for (ChatMessage m : history) {
                contents.put(new JSONObject()
                        .put("role", m.fromUser ? "user" : "model")
                        .put("parts", new JSONArray().put(new JSONObject().put("text", m.text))));
            }
            return new JSONObject()
                    .put("systemInstruction",
                            new JSONObject().put("parts", new JSONArray().put(new JSONObject().put("text", systemPrompt))))
                    .put("contents", contents)
                    .put("generationConfig", new JSONObject()
                            .put("thinkingConfig", new JSONObject().put("thinkingLevel", THINKING_LEVEL)))
                    .toString();
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    private static String stream(String model, String apiKey, String body, Consumer<String> onPartial)
            throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(ENDPOINT_PREFIX + model + ENDPOINT_SUFFIX).openConnection();
        StringBuilder raw = new StringBuilder();
        try {
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("x-goog-api-key", apiKey);
            try (OutputStream out = conn.getOutputStream()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            if (code < 200 || code > 299) {
                String message = errorMessage(conn.getErrorStream());
                if (message == null) message = "Lỗi HTTP " + code;
                if (shouldTryNextModel(code)) throw new ModelBusyException(message);
                throw new IOException(message);
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String data = sseData(line);
                    if (data == null) continue;
                    String delta = chunkText(data);
                    if (delta.isEmpty()) continue;
                    raw.append(delta);
                    onPartial.accept(clean(raw.toString()));
                }
            }
            String text = clean(raw.toString());
            if (text.isEmpty()) throw new IOException("Gemini không trả về nội dung");
            return text;
        } catch (SocketTimeoutException e) {
            // Model đang quá tải có thể giữ request rất lâu mà không trả lời; chưa có chữ nào thì thử model khác
            if (raw.length() == 0) throw new ModelBusyException("Gemini đang quá tải, không phản hồi kịp");
            throw e;
        } finally {
            conn.disconnect();
        }
    }

    /** Phần dữ liệu của một dòng SSE ("data: {...}"); null với dòng trống hoặc dòng không phải dữ liệu. */
    static String sseData(String line) {
        if (!line.startsWith(SSE_DATA)) return null;
        String data = line.substring(SSE_DATA.length()).trim();
        return data.isEmpty() ? null : data;
    }

    /** Bỏ ký hiệu markdown phổ biến vì app hiển thị văn bản thuần. */
    static String clean(String text) {
        return text.replace("**", "").replaceAll("(?m)^#+\\s*", "").trim();
    }

    /**
     * Câu báo lỗi trong phản hồi lỗi của Gemini, hoặc null nếu không đọc được.
     * Với endpoint stream, lỗi cũng được gửi dạng SSE ("data: {...}") và kết nối có thể không tự đóng,
     * nên chỉ đọc tới khi gặp khối JSON đầu tiên thay vì đọc tới hết (sẽ treo tới khi hết thời gian chờ).
     */
    private static String errorMessage(InputStream error) {
        if (error == null) return null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(error, StandardCharsets.UTF_8))) {
            StringBuilder json = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                String data = sseData(line);
                json.append(data != null ? data : line);
                String message = errorMessageOf(json.toString());
                if (message != null) return message;
            }
        } catch (IOException ignored) {
            // Không đọc được phần báo lỗi: nơi gọi báo mã HTTP thay thế
        }
        return null;
    }

    /**
     * Trường error.message của một khối JSON đầy đủ ({...} hoặc mảng [{...}] mà endpoint stream có thể trả);
     * null nếu chưa đủ khối hoặc không có trường đó.
     */
    private static String errorMessageOf(String json) {
        try {
            String trimmed = json.trim();
            JSONObject body = trimmed.startsWith("[")
                    ? new JSONArray(trimmed).getJSONObject(0)
                    : new JSONObject(trimmed);
            return body.getJSONObject("error").getString("message");
        } catch (JSONException e) {
            return null;
        }
    }

    /** Phần chữ mới trong một gói stream, bỏ qua phần "suy nghĩ"; gói không có chữ (ví dụ gói kết thúc) trả về "". */
    private static String chunkText(String json) throws IOException {
        try {
            JSONArray candidates = new JSONObject(json).optJSONArray("candidates");
            JSONObject content = candidates == null ? null : candidates.getJSONObject(0).optJSONObject("content");
            JSONArray parts = content == null ? null : content.optJSONArray("parts");
            if (parts == null) return "";
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < parts.length(); i++) {
                JSONObject part = parts.getJSONObject(i);
                if (!part.optBoolean("thought")) text.append(part.optString("text"));
            }
            return text.toString();
        } catch (JSONException e) {
            throw new IOException("Phản hồi của Gemini không hợp lệ", e);
        }
    }

    // ---------------------------------------------------------------- Prompts

    /** Mô tả bảng điểm bằng văn bản để đưa vào prompt. */
    public static String describeTranscript(AcademicData data) {
        StringBuilder sb = new StringBuilder();
        GpaCalculator.Summary total = GpaCalculator.cumulative(data.semesters);
        List<String> scale = new ArrayList<>();
        for (com.appgiuaky.gpaplanner.types.LetterGrade g : GpaCalculator.SCALE) {
            scale.add(g.letter + " (từ " + g.minScore + ") = " + g.point);
        }
        sb.append("Thang điểm: ").append(Format.join(", ", scale)).append('\n');
        if (total.credits > 0) {
            sb.append("GPA tích lũy: ").append(Format.fmt(total.gpa4)).append("/4 (")
                    .append(GpaCalculator.classify(total.gpa4).label).append("), hệ 10: ").append(Format.fmt(total.gpa10))
                    .append(", tín chỉ tích lũy: ").append(total.credits).append('/').append(data.totalProgramCredits).append('\n');
        } else {
            sb.append("Chưa có môn nào hoàn thành. Chương trình có ").append(data.totalProgramCredits).append(" tín chỉ.\n");
        }
        for (Semester s : data.semesters) {
            GpaCalculator.Summary sum = GpaCalculator.semesterSummary(s);
            sb.append('\n').append(s.name);
            if (sum.credits > 0) sb.append(" (GPA kỳ ").append(Format.fmt(sum.gpa4)).append(')');
            sb.append(":\n");
            for (Course c : s.courses) sb.append("- ").append(describeCourse(c)).append('\n');
        }
        return sb.toString();
    }

    private static String describeCourse(Course c) {
        String head = c.code + " " + c.name + ", " + c.credits + " TC" + (c.countInGpa ? "" : " (không tính GPA)");
        Double score = GpaCalculator.courseScore(c);
        if (score != null) {
            return head + ": " + Format.fmt(score, 1) + " (" + GpaCalculator.letterOf(score).letter + ")";
        }
        List<String> done = new ArrayList<>();
        List<String> left = new ArrayList<>();
        for (GradeComponent g : c.components) {
            if (g.score != null) done.add(g.name + " " + Format.fmt(g.score, 1) + " (" + Format.fmt(g.weight, 0) + "%)");
            else left.add(g.name + " (" + Format.fmt(g.weight, 0) + "%)");
        }
        return head + ": " + GpaCalculator.status(c).label
                + (done.isEmpty() ? "" : "; đã có " + Format.join(", ", done))
                + "; còn thiếu " + Format.join(", ", left);
    }

    public static String advisorPrompt(AcademicData data) {
        return "Bạn là cố vấn học vụ của một sinh viên đại học Việt Nam. Hãy trả lời bằng tiếng Việt, ngắn gọn,\n"
                + "cụ thể, dựa trên số liệu thật trong bảng điểm bên dưới (nêu rõ con số khi cần). Đưa ra lời khuyên\n"
                + "thực tế: nên ưu tiên môn nào, cần bao nhiêu điểm, có nên học cải thiện không.\n"
                + "Viết văn bản thuần, dùng gạch đầu dòng \"-\" khi liệt kê, không dùng markdown như ** hay #.\n"
                + "\n"
                + "Quy chế tính điểm: điểm học phần = tổng điểm thành phần × trọng số, làm tròn 1 chữ số thập phân;\n"
                + "GPA tích lũy chỉ tính các môn đạt (D trở lên), môn học lại lấy lần điểm cao nhất.\n"
                + "Xếp loại theo GPA hệ 4: Xuất sắc ≥ 3.6, Giỏi ≥ 3.2, Khá ≥ 2.5, Trung bình ≥ 2.0.\n"
                + "\n"
                + "BẢNG ĐIỂM CỦA SINH VIÊN:\n"
                + describeTranscript(data);
    }

    public static final String STUDY_PLANNER_PROMPT =
            "Bạn là trợ lý lập kế hoạch ôn thi cho sinh viên đại học Việt Nam.\n"
                    + "Hãy lập lịch ôn thi theo từng ngày, bằng tiếng Việt, văn bản thuần (không dùng markdown như ** hay #).\n"
                    + "Mỗi dòng có dạng \"dd/MM (thứ): môn - nội dung ôn - số giờ\". Ưu tiên môn thi sớm, môn nhiều tín chỉ\n"
                    + "và môn cần điểm cuối kỳ cao. Ngày sát kỳ thi dành cho luyện đề và ôn tổng hợp. Không xếp vượt số giờ\n"
                    + "mỗi ngày cho phép. Cuối cùng thêm 3-5 lời khuyên ngắn.";

    /** Nội dung yêu cầu lập lịch: các môn (chưa có đủ điểm) kèm ngày thi. todayUtcMillis là mốc 0h UTC của hôm nay. */
    public static String studyPlanRequest(List<Exam> exams, int hoursPerDay, long todayUtcMillis) {
        List<Exam> sorted = new ArrayList<>(exams);
        Collections.sort(sorted, (a, b) -> Long.compare(a.dateUtcMillis, b.dateUtcMillis));
        StringBuilder sb = new StringBuilder();
        sb.append("Hôm nay là ").append(formatDate(todayUtcMillis)).append(". Mỗi ngày tôi có thể ôn tối đa ")
                .append(hoursPerDay).append(" giờ.\n");
        sb.append("Các môn cần thi:\n");
        for (Exam exam : sorted) {
            Course course = exam.course;
            long days = (exam.dateUtcMillis - todayUtcMillis) / DAY_MILLIS;
            sb.append("- ").append(course.name).append(" (").append(course.credits).append(" TC), thi ngày ")
                    .append(formatDate(exam.dateUtcMillis)).append(" (còn ").append(days).append(" ngày)");
            List<String> known = new ArrayList<>();
            for (GradeComponent g : course.components) {
                if (g.score != null) known.add(g.name + " " + Format.fmt(g.score, 1));
            }
            if (!known.isEmpty()) sb.append("; điểm đã có: ").append(Format.join(", ", known));
            List<String> needs = new ArrayList<>();
            for (GpaCalculator.RescueRow row : GpaCalculator.rescue(course)) {
                needs.add(row.grade.letter + ": " + (row.need == null ? "không thể" : Format.fmt(row.need, 1)));
            }
            sb.append("; điểm trung bình cần ở các phần còn lại để đạt từng mức: ").append(Format.join(", ", needs)).append('\n');
        }
        return sb.toString();
    }
}
