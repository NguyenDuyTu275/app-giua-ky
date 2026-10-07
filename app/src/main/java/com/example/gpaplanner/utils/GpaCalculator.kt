package com.example.gpaplanner.utils

import com.example.gpaplanner.types.Classification
import com.example.gpaplanner.types.Course
import com.example.gpaplanner.types.CourseStatus
import com.example.gpaplanner.types.LetterGrade
import com.example.gpaplanner.types.Semester
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.ceil

/**
 * Toàn bộ công thức quy đổi điểm: Hệ 10 -> Chữ -> Hệ 4, theo quy chế đào tạo tín chỉ phổ biến
 * ở các trường đại học Việt Nam:
 *  - Điểm học phần = Σ(điểm thành phần × trọng số), làm tròn đến 1 chữ số thập phân.
 *  - GPA học kỳ: trung bình theo tín chỉ của mọi môn đã có điểm trong kỳ (kể cả môn F).
 *  - GPA tích lũy: chỉ tính các môn đã đạt (D trở lên); môn học lại/cải thiện lấy lần có điểm cao nhất.
 *  - Môn có countInGpa = false (GDTC, GDQP...) không tính vào GPA và tín chỉ tích lũy.
 */
object GpaCalculator {

    /** Thang điểm chữ. Trường dùng thang khác (ví dụ có A+) chỉ cần sửa bảng này. */
    val SCALE: List<LetterGrade> = listOf(
        LetterGrade("A", 8.5, 4.0),
        LetterGrade("B+", 8.0, 3.5),
        LetterGrade("B", 7.0, 3.0),
        LetterGrade("C+", 6.5, 2.5),
        LetterGrade("C", 5.5, 2.0),
        LetterGrade("D+", 5.0, 1.5),
        LetterGrade("D", 4.0, 1.0),
        LetterGrade("F", 0.0, 0.0),
    )

    /** Các mức điểm đạt (A..D), từ cao xuống thấp. */
    val PASSING: List<LetterGrade> = SCALE.filter { it.point > 0 }

    data class Summary(val gpa4: Double, val gpa10: Double, val credits: Int)

    private class Entry(val credits: Int, val score10: Double, val point: Double)

    fun round1(x: Double): Double = round(x, 1)

    fun round2(x: Double): Double = round(x, 2)

    // Làm tròn 6 chữ số trước để khử sai số dấu phẩy động (8.2499999 -> 8.25 -> 8.3).
    private fun round(x: Double, digits: Int): Double =
        BigDecimal(x).setScale(6, RoundingMode.HALF_UP).setScale(digits, RoundingMode.HALF_UP).toDouble()

    fun letterOf(score10: Double): LetterGrade = SCALE.first { score10 >= it.minScore }

    fun classify(gpa4: Double): Classification = Classification.entries.first { round2(gpa4) >= it.minGpa }

    fun status(course: Course): CourseStatus = when {
        course.components.isNotEmpty() && course.components.all { it.score != null } -> CourseStatus.COMPLETED
        course.components.any { it.score != null } -> CourseStatus.IN_PROGRESS
        else -> CourseStatus.PLANNED
    }

    /** Điểm học phần hệ 10, null nếu chưa đủ điểm thành phần. */
    fun courseScore(course: Course): Double? {
        if (status(course) != CourseStatus.COMPLETED) return null
        return round1(course.components.sumOf { it.weight * it.score!! } / 100)
    }

    fun courseGrade(course: Course): LetterGrade? = courseScore(course)?.let(::letterOf)

    private fun summarize(entries: List<Entry>): Summary {
        val credits = entries.sumOf { it.credits }
        if (credits == 0) return Summary(0.0, 0.0, 0)
        return Summary(
            gpa4 = entries.sumOf { it.point * it.credits } / credits,
            gpa10 = entries.sumOf { it.score10 * it.credits } / credits,
            credits = credits,
        )
    }

    fun semesterSummary(semester: Semester): Summary = summarize(
        semester.courses.filter { it.countInGpa }.mapNotNull { c ->
            courseScore(c)?.let { Entry(c.credits, it, letterOf(it).point) }
        }
    )

    /** Lần học có điểm cao nhất của mỗi môn (gộp theo mã môn), trong các môn đã có điểm và tính GPA. */
    fun bestCourses(semesters: List<Semester>): List<Course> =
        semesters.flatMap { it.courses }
            .filter { it.countInGpa && courseScore(it) != null }
            .groupBy { it.code.trim().uppercase() }
            .map { (_, attempts) -> attempts.maxBy { courseScore(it)!! } }

    /**
     * GPA tích lũy. [improvements] (id môn -> điểm chữ giả định) dùng cho giả lập học cải thiện:
     * môn được thay bằng điểm mới nếu điểm mới cao hơn.
     */
    fun cumulative(semesters: List<Semester>, improvements: Map<String, LetterGrade> = emptyMap()): Summary =
        summarize(
            bestCourses(semesters).map { c ->
                val score = courseScore(c)!!
                val current = letterOf(score)
                val improved = improvements[c.id]
                if (improved != null && improved.point > current.point) {
                    Entry(c.credits, improved.minScore, improved.point)
                } else {
                    Entry(c.credits, score, current.point)
                }
            }.filter { it.point > 0 }
        )

    /**
     * "Cứu môn": với mỗi mức điểm đạt, điểm trung bình tối thiểu (bước 0.1) cần ở các thành phần
     * chưa có điểm. 0.0 = chắc chắn đạt; null = không thể đạt kể cả được 10.
     */
    fun rescue(course: Course): List<Pair<LetterGrade, Double?>> {
        val known = course.components.filter { it.score != null }.sumOf { it.weight * it.score!! } / 100
        val remainingWeight = course.components.filter { it.score == null }.sumOf { it.weight } / 100
        return PASSING.map { grade ->
            grade to (0..100).map { it / 10.0 }
                .firstOrNull { x -> round1(known + remainingWeight * x) >= grade.minScore }
        }
    }

    /** Phương án kết hợp: highCredits tín chỉ đạt [high], phần còn lại đạt [low]. */
    data class Mix(val high: LetterGrade, val highCredits: Int, val low: LetterGrade, val lowCredits: Int)

    data class TargetPlan(
        /** GPA hệ 4 trung bình cần đạt trên số tín chỉ sắp học. */
        val requiredAvg: Double,
        /** GPA tích lũy cao nhất có thể (nếu mọi môn sắp học đều đạt A). */
        val maxReachable: Double,
        /** Mức điểm chữ tối thiểu nếu mọi môn sắp học cùng một mức; null nếu không thể đạt mục tiêu. */
        val uniformGrade: LetterGrade?,
        val mix: Mix?,
    )

    /** Lập kế hoạch: cần học [plannedCredits] tín chỉ tới với điểm thế nào để GPA tích lũy đạt [target]. */
    fun planTarget(current: Summary, target: Double, plannedCredits: Int): TargetPlan {
        val totalCredits = current.credits + plannedCredits
        val required = (target * totalCredits - current.gpa4 * current.credits) / plannedCredits
        val maxReachable = (current.gpa4 * current.credits + 4.0 * plannedCredits) / totalCredits
        val uniform = PASSING.lastOrNull { it.point >= required - 1e-9 }
        val low = uniform?.let { u -> PASSING.firstOrNull { it.point < u.point } }
        val mix = if (uniform != null && low != null && required > low.point) {
            val share = (required - low.point) / (uniform.point - low.point)
            val highCredits = ceil(share * plannedCredits - 1e-9).toInt()
            if (highCredits < plannedCredits) Mix(uniform, highCredits, low, plannedCredits - highCredits) else null
        } else {
            null
        }
        return TargetPlan(required, maxReachable, uniform, mix)
    }
}

fun Double.fmt(digits: Int = 2): String = String.format(Locale.US, "%.${digits}f", this)

/** Số không kèm ".0" thừa: 10.0 -> "10", 8.5 -> "8.5". */
fun Double.plain(): String = if (this % 1.0 == 0.0) toLong().toString() else toString()

/** Đọc số người dùng nhập, chấp nhận cả dấu phẩy thập phân ("8,5"). */
fun parseNumber(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()
