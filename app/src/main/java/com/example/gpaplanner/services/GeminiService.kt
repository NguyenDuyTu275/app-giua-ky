package com.example.gpaplanner.services

import com.example.gpaplanner.types.AcademicData
import com.example.gpaplanner.types.Course
import com.example.gpaplanner.utils.GpaCalculator
import com.example.gpaplanner.utils.fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Gọi Gemini API (REST generateContent) và chứa các câu prompt cho Cố vấn học vụ & Lập lịch ôn thi. */
object GeminiService {

    const val MODEL = "gemini-3.8-flash"
    private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"

    private val DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    data class ChatMessage(val fromUser: Boolean, val text: String)

    /** Gửi system prompt + lịch sử hội thoại, trả về câu trả lời dạng văn bản. */
    suspend fun generate(apiKey: String, systemPrompt: String, history: List<ChatMessage>): String =
        withContext(Dispatchers.IO) {
            val contents = JSONArray()
            history.forEach { m ->
                contents.put(
                    JSONObject()
                        .put("role", if (m.fromUser) "user" else "model")
                        .put("parts", JSONArray().put(JSONObject().put("text", m.text)))
                )
            }
            val body = JSONObject()
                .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))
                .put("contents", contents)

            val conn = URL(ENDPOINT).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 15_000
                conn.readTimeout = 90_000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.setRequestProperty("x-goog-api-key", apiKey)
                conn.outputStream.use { it.write(body.toString().toByteArray()) }

                val ok = conn.responseCode in 200..299
                val response = (if (ok) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (!ok) {
                    val message = runCatching { JSONObject(response).getJSONObject("error").getString("message") }.getOrNull()
                    throw IOException(message ?: "Lỗi HTTP ${conn.responseCode}")
                }
                parseText(response)
            } finally {
                conn.disconnect()
            }
        }

    private fun parseText(response: String): String {
        val parts = JSONObject(response).optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts")
            ?: throw IOException("Gemini không trả về nội dung")
        val text = (0 until parts.length())
            .map { parts.getJSONObject(it) }
            .filterNot { it.optBoolean("thought") }
            .joinToString("") { it.optString("text") }
        // Bỏ ký hiệu markdown phổ biến vì app hiển thị văn bản thuần
        return text.replace("**", "").replace(Regex("(?m)^#+\\s*"), "").trim()
    }

    // ---------------------------------------------------------------- Prompts

    /** Mô tả bảng điểm bằng văn bản để đưa vào prompt. */
    fun describeTranscript(data: AcademicData): String = buildString {
        val total = GpaCalculator.cumulative(data.semesters)
        appendLine("Thang điểm: " + GpaCalculator.SCALE.joinToString(", ") { "${it.letter} (từ ${it.minScore}) = ${it.point}" })
        if (total.credits > 0) {
            appendLine(
                "GPA tích lũy: ${total.gpa4.fmt()}/4 (${GpaCalculator.classify(total.gpa4).label}), " +
                    "hệ 10: ${total.gpa10.fmt()}, tín chỉ tích lũy: ${total.credits}/${data.totalProgramCredits}"
            )
        } else {
            appendLine("Chưa có môn nào hoàn thành. Chương trình có ${data.totalProgramCredits} tín chỉ.")
        }
        data.semesters.forEach { s ->
            val sum = GpaCalculator.semesterSummary(s)
            appendLine()
            appendLine(if (sum.credits > 0) "${s.name} (GPA kỳ ${sum.gpa4.fmt()}):" else "${s.name}:")
            s.courses.forEach { appendLine("- " + describeCourse(it)) }
        }
    }

    private fun describeCourse(c: Course): String {
        val head = "${c.code} ${c.name}, ${c.credits} TC" + if (c.countInGpa) "" else " (không tính GPA)"
        val score = GpaCalculator.courseScore(c)
        if (score != null) return "$head: ${score.fmt(1)} (${GpaCalculator.letterOf(score).letter})"
        val done = c.components.filter { it.score != null }
            .joinToString(", ") { "${it.name} ${it.score!!.fmt(1)} (${it.weight.fmt(0)}%)" }
        val left = c.components.filter { it.score == null }.joinToString(", ") { "${it.name} (${it.weight.fmt(0)}%)" }
        return "$head: ${GpaCalculator.status(c).label}" +
            (if (done.isNotEmpty()) "; đã có $done" else "") + "; còn thiếu $left"
    }

    fun advisorPrompt(data: AcademicData): String = """
        Bạn là cố vấn học vụ của một sinh viên đại học Việt Nam. Hãy trả lời bằng tiếng Việt, ngắn gọn,
        cụ thể, dựa trên số liệu thật trong bảng điểm bên dưới (nêu rõ con số khi cần). Đưa ra lời khuyên
        thực tế: nên ưu tiên môn nào, cần bao nhiêu điểm, có nên học cải thiện không.
        Viết văn bản thuần, dùng gạch đầu dòng "-" khi liệt kê, không dùng markdown như ** hay #.

        Quy chế tính điểm: điểm học phần = tổng điểm thành phần × trọng số, làm tròn 1 chữ số thập phân;
        GPA tích lũy chỉ tính các môn đạt (D trở lên), môn học lại lấy lần điểm cao nhất.
        Xếp loại theo GPA hệ 4: Xuất sắc ≥ 3.6, Giỏi ≥ 3.2, Khá ≥ 2.5, Trung bình ≥ 2.0.

        BẢNG ĐIỂM CỦA SINH VIÊN:
    """.trimIndent() + "\n" + describeTranscript(data)

    val STUDY_PLANNER_PROMPT = """
        Bạn là trợ lý lập kế hoạch ôn thi cho sinh viên đại học Việt Nam.
        Hãy lập lịch ôn thi theo từng ngày, bằng tiếng Việt, văn bản thuần (không dùng markdown như ** hay #).
        Mỗi dòng có dạng "dd/MM (thứ): môn - nội dung ôn - số giờ". Ưu tiên môn thi sớm, môn nhiều tín chỉ
        và môn cần điểm cuối kỳ cao. Ngày sát kỳ thi dành cho luyện đề và ôn tổng hợp. Không xếp vượt số giờ
        mỗi ngày cho phép. Cuối cùng thêm 3-5 lời khuyên ngắn.
    """.trimIndent()

    /** Nội dung yêu cầu lập lịch: các môn (chưa có đủ điểm) kèm ngày thi. */
    fun studyPlanRequest(exams: List<Pair<Course, LocalDate>>, hoursPerDay: Int, today: LocalDate): String = buildString {
        appendLine("Hôm nay là ${today.format(DATE)}. Mỗi ngày tôi có thể ôn tối đa $hoursPerDay giờ.")
        appendLine("Các môn cần thi:")
        exams.sortedBy { it.second }.forEach { (course, date) ->
            val days = ChronoUnit.DAYS.between(today, date)
            append("- ${course.name} (${course.credits} TC), thi ngày ${date.format(DATE)} (còn $days ngày)")
            val known = course.components.filter { it.score != null }
            if (known.isNotEmpty()) {
                append("; điểm đã có: " + known.joinToString(", ") { "${it.name} ${it.score!!.fmt(1)}" })
            }
            val needs = GpaCalculator.rescue(course).joinToString(", ") { (grade, need) ->
                "${grade.letter}: " + (need?.fmt(1) ?: "không thể")
            }
            appendLine("; điểm trung bình cần ở các phần còn lại để đạt từng mức: $needs")
        }
    }
}
