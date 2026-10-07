package com.example.gpaplanner.utils

import com.example.gpaplanner.types.AcademicData
import com.example.gpaplanner.types.Course
import com.example.gpaplanner.types.GradeComponent
import com.example.gpaplanner.types.Semester

/** Dữ liệu mẫu 4 học kỳ (21 môn) để demo ngay: 3 kỳ đã xong, kỳ 4 đang học, có 1 môn F đã học lại. */
object MockData {

    /** Cơ cấu điểm mặc định cho môn mới: Chuyên cần 10% - Giữa kỳ 30% - Cuối kỳ 60%. */
    fun defaultComponents(): List<GradeComponent> = listOf(
        GradeComponent("Chuyên cần", 10.0),
        GradeComponent("Giữa kỳ", 30.0),
        GradeComponent("Cuối kỳ", 60.0),
    )

    private fun course(
        code: String,
        name: String,
        credits: Int,
        attendance: Double?,
        midterm: Double?,
        final: Double?,
        countInGpa: Boolean = true,
    ) = Course(
        code = code,
        name = name,
        credits = credits,
        components = listOf(
            GradeComponent("Chuyên cần", 10.0, attendance),
            GradeComponent("Giữa kỳ", 30.0, midterm),
            GradeComponent("Cuối kỳ", 60.0, final),
        ),
        countInGpa = countInGpa,
    )

    fun create(): AcademicData = AcademicData(
        totalProgramCredits = 130,
        semesters = listOf(
            Semester(
                name = "Học kỳ 1",
                courses = listOf(
                    course("MATH101", "Giải tích 1", 3, 9.0, 7.5, 8.0),
                    course("PHY101", "Vật lý đại cương", 3, 8.0, 6.0, 6.5),
                    course("IT101", "Nhập môn lập trình", 3, 10.0, 8.5, 9.0),
                    course("ENG101", "Tiếng Anh 1", 2, 9.0, 8.0, 7.0),
                    course("PE101", "Giáo dục thể chất 1", 1, 10.0, 8.0, 8.0, countInGpa = false),
                ),
            ),
            Semester(
                name = "Học kỳ 2",
                courses = listOf(
                    course("MATH102", "Giải tích 2", 3, 7.0, 3.0, 3.0),
                    course("MATH103", "Đại số tuyến tính", 3, 8.0, 7.0, 7.5),
                    course("IT102", "Kỹ thuật lập trình", 3, 9.0, 8.0, 8.5),
                    course("POL101", "Triết học Mác - Lênin", 3, 9.0, 7.0, 6.0),
                    course("ENG102", "Tiếng Anh 2", 2, 10.0, 8.0, 8.0),
                ),
            ),
            Semester(
                name = "Học kỳ 3",
                courses = listOf(
                    course("MATH102", "Giải tích 2 (học lại)", 3, 9.0, 7.0, 7.0),
                    course("IT201", "Cấu trúc dữ liệu và giải thuật", 4, 9.0, 8.0, 8.0),
                    course("IT202", "Cơ sở dữ liệu", 3, 10.0, 9.0, 9.0),
                    course("IT203", "Kiến trúc máy tính", 3, 8.0, 6.0, 5.0),
                    course("STAT201", "Xác suất thống kê", 3, 8.0, 7.0, 6.5),
                    course("POL102", "Kinh tế chính trị Mác - Lênin", 2, 9.0, 8.0, 7.0),
                ),
            ),
            Semester(
                name = "Học kỳ 4",
                courses = listOf(
                    course("IT204", "Lập trình hướng đối tượng", 3, 9.0, 8.0, null),
                    course("IT205", "Mạng máy tính", 3, 8.0, 5.0, null),
                    course("IT206", "Hệ điều hành", 3, 7.0, 4.5, null),
                    course("IT207", "Phát triển ứng dụng di động", 3, 10.0, 9.0, null),
                    course("POL103", "Chủ nghĩa xã hội khoa học", 2, 9.0, null, null),
                ),
            ),
        ),
    )
}
