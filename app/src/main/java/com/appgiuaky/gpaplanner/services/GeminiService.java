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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Gọi Gemini API (REST generateContent) và chứa các câu prompt cho Cố vấn học vụ & Lập lịch ôn thi.
 * Hàm generate chạy đồng bộ (chặn luồng), nên phải gọi từ luồng nền.
 */
public final class GeminiService {

    private GeminiService() {
    }

    public static final String MODEL = "gemini-3.8-flash";
    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL + ":generateContent";
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

    /** Gửi system prompt + lịch sử hội thoại, trả về câu trả lời dạng văn bản. */
    public static String generate(String apiKey, String systemPrompt, List<ChatMessage> history) throws IOException {
        String body;
        try {
            JSONArray contents = new JSONArray();
            for (ChatMessage m : history) {
                contents.put(new JSONObject()
                        .put("role", m.fromUser ? "user" : "model")
                        .put("parts", new JSONArray().put(new JSONObject().put("text", m.text))));
            }
            body = new JSONObject()
                    .put("systemInstruction",
                            new JSONObject().put("parts", new JSONArray().put(new JSONObject().put("text", systemPrompt))))
                    .put("contents", contents)
                    .toString();
        } catch (JSONException e) {
            throw new IOException(e);
        }

        HttpURLConnection conn = (HttpURLConnection) new URL(ENDPOINT).openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(90_000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("x-goog-api-key", apiKey);
            try (OutputStream out = conn.getOutputStream()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            boolean ok = code >= 200 && code <= 299;
            InputStream stream = ok ? conn.getInputStream() : conn.getErrorStream();
            String response = stream == null ? "" : readAll(stream);
            if (!ok) {
                String message = null;
                try {
                    message = new JSONObject(response).getJSONObject("error").getString("message");
                } catch (JSONException ignored) {
                    // Phản hồi lỗi không phải JSON: báo mã HTTP
                }
                throw new IOException(message != null ? message : "Lỗi HTTP " + code);
            }
            return parseText(response);
        } finally {
            conn.disconnect();
        }
    }

    private static String readAll(InputStream in) throws IOException {
        try (InputStream input = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = input.read(buffer)) != -1) out.write(buffer, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String parseText(String response) throws IOException {
        try {
            JSONArray candidates = new JSONObject(response).optJSONArray("candidates");
            JSONObject content = candidates == null ? null : candidates.getJSONObject(0).optJSONObject("content");
            JSONArray parts = content == null ? null : content.optJSONArray("parts");
            if (parts == null) throw new IOException("Gemini không trả về nội dung");
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < parts.length(); i++) {
                JSONObject part = parts.getJSONObject(i);
                if (!part.optBoolean("thought")) text.append(part.optString("text"));
            }
            // Bỏ ký hiệu markdown phổ biến vì app hiển thị văn bản thuần
            return text.toString().replace("**", "").replaceAll("(?m)^#+\\s*", "").trim();
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
