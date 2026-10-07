package com.example.gpaplanner.types

import kotlinx.serialization.Serializable
import java.util.UUID

/** Một điểm thành phần của môn học, ví dụ "Giữa kỳ" chiếm 30%. score = null nghĩa là chưa có điểm. */
@Serializable
data class GradeComponent(
    val name: String,
    val weight: Double,
    val score: Double? = null,
)

@Serializable
data class Course(
    val id: String = UUID.randomUUID().toString(),
    val code: String,
    val name: String,
    val credits: Int,
    val components: List<GradeComponent>,
    /** Các môn như Giáo dục thể chất, Quốc phòng thường không tính vào GPA. */
    val countInGpa: Boolean = true,
)

@Serializable
data class Semester(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val courses: List<Course> = emptyList(),
)

@Serializable
data class AcademicData(
    val semesters: List<Semester>,
    /** Tổng số tín chỉ của chương trình đào tạo, dùng để tính số tín chỉ còn lại. */
    val totalProgramCredits: Int = 130,
)

enum class CourseStatus(val label: String) {
    COMPLETED("Đã học"),
    IN_PROGRESS("Đang học"),
    PLANNED("Chưa học"),
}

/** Một bậc trong thang điểm: điểm hệ 10 từ minScore trở lên được điểm chữ letter, quy ra point (hệ 4). */
data class LetterGrade(
    val letter: String,
    val minScore: Double,
    val point: Double,
)

/** Xếp loại học lực theo GPA tích lũy hệ 4. */
enum class Classification(val label: String, val minGpa: Double) {
    EXCELLENT("Xuất sắc", 3.6),
    VERY_GOOD("Giỏi", 3.2),
    GOOD("Khá", 2.5),
    AVERAGE("Trung bình", 2.0),
    WEAK("Yếu", 1.0),
    POOR("Kém", 0.0),
}
