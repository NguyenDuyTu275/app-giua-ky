package com.example.gpaplanner.utils

import com.example.gpaplanner.types.Classification
import com.example.gpaplanner.types.Course
import com.example.gpaplanner.types.CourseStatus
import com.example.gpaplanner.types.GradeComponent
import com.example.gpaplanner.types.Semester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GpaCalculatorTest {

    private fun course(code: String, credits: Int, vararg scores: Double?, countInGpa: Boolean = true) = Course(
        code = code,
        name = code,
        credits = credits,
        components = listOf(
            GradeComponent("Chuyên cần", 10.0, scores[0]),
            GradeComponent("Giữa kỳ", 30.0, scores[1]),
            GradeComponent("Cuối kỳ", 60.0, scores[2]),
        ),
        countInGpa = countInGpa,
    )

    private fun letter(l: String) = GpaCalculator.SCALE.first { it.letter == l }

    @Test
    fun letterBoundaries() {
        assertEquals("A", GpaCalculator.letterOf(8.5).letter)
        assertEquals("B+", GpaCalculator.letterOf(8.4).letter)
        assertEquals("B", GpaCalculator.letterOf(7.0).letter)
        assertEquals("C+", GpaCalculator.letterOf(6.9).letter)
        assertEquals("D", GpaCalculator.letterOf(4.0).letter)
        assertEquals("F", GpaCalculator.letterOf(3.9).letter)
    }

    @Test
    fun courseScoreRoundsToOneDecimal() {
        // 0.9 + 2.25 + 4.8 = 7.95 -> 8.0 (B+)
        val c = course("X", 3, 9.0, 7.5, 8.0)
        assertEquals(8.0, GpaCalculator.courseScore(c)!!, 1e-9)
        assertEquals("B+", GpaCalculator.courseGrade(c)!!.letter)
        // 0.85 + 2.4 + 5.0 = 8.25 -> 8.3 (không bị sai số thành 8.2)
        assertEquals(8.3, GpaCalculator.courseScore(course("Y", 3, 8.5, 8.0, 25.0 / 3))!!, 1e-9)
    }

    @Test
    fun statusFollowsEnteredComponents() {
        assertEquals(CourseStatus.COMPLETED, GpaCalculator.status(course("X", 3, 1.0, 2.0, 3.0)))
        assertEquals(CourseStatus.IN_PROGRESS, GpaCalculator.status(course("X", 3, 1.0, null, null)))
        assertEquals(CourseStatus.PLANNED, GpaCalculator.status(course("X", 3, null, null, null)))
        assertNull(GpaCalculator.courseScore(course("X", 3, 1.0, null, null)))
    }

    @Test
    fun semesterGpaCountsFButCumulativeOnlyBestPassedAttempt() {
        val s1 = Semester(
            name = "HK1",
            courses = listOf(
                course("A1", 3, 10.0, 10.0, 10.0),                 // A 4.0
                course("F1", 2, 0.0, 0.0, 0.0),                    // F
                course("PE", 1, 0.0, 0.0, 0.0, countInGpa = false), // không tính
            ),
        )
        assertEquals(3 * 4.0 / 5, GpaCalculator.semesterSummary(s1).gpa4, 1e-9)
        assertEquals(5, GpaCalculator.semesterSummary(s1).credits)

        val before = GpaCalculator.cumulative(listOf(s1))
        assertEquals(4.0, before.gpa4, 1e-9)
        assertEquals(3, before.credits)

        // Học lại F1 được 7.0 (B)
        val s2 = Semester(name = "HK2", courses = listOf(course("f1", 2, 7.0, 7.0, 7.0)))
        val after = GpaCalculator.cumulative(listOf(s1, s2))
        assertEquals((3 * 4.0 + 2 * 3.0) / 5, after.gpa4, 1e-9)
        assertEquals(5, after.credits)
    }

    @Test
    fun improvementOnlyCountsWhenHigher() {
        val b = course("B", 2, 7.0, 7.0, 7.0)  // B
        val a = course("A", 2, 10.0, 10.0, 10.0)
        val semesters = listOf(Semester(name = "HK", courses = listOf(a, b)))
        val improved = GpaCalculator.cumulative(semesters, mapOf(b.id to letter("A")))
        assertEquals(4.0, improved.gpa4, 1e-9)
        val worse = GpaCalculator.cumulative(semesters, mapOf(b.id to letter("C")))
        assertEquals(3.5, worse.gpa4, 1e-9)
    }

    @Test
    fun rescueFindsMinimumFinalScore() {
        // Đã có 0.8 + 1.5 = 2.3 điểm, cuối kỳ chiếm 60%
        val rows = GpaCalculator.rescue(course("X", 3, 8.0, 5.0, null)).toMap()
        // D: 2.3 + 0.6x làm tròn >= 4.0 -> x = 2.8 (2.3 + 1.68 = 3.98 -> 4.0)
        assertEquals(2.8, rows.getValue(letter("D"))!!, 1e-9)
        // A: cần 2.3 + 0.6x >= 8.45 -> x = 10.3 > 10 -> không thể
        assertNull(rows.getValue(letter("A")))
        // B+: 2.3 + 0.6x >= 7.95 -> x = 9.5 (2.3 + 5.7 = 8.0)
        assertEquals(9.5, rows.getValue(letter("B+"))!!, 1e-9)
    }

    @Test
    fun rescueReportsZeroWhenAlreadySafe() {
        val rows = GpaCalculator.rescue(course("X", 3, 10.0, 10.0, null)).toMap()
        assertEquals(0.0, rows.getValue(letter("D"))!!, 1e-9)
    }

    @Test
    fun planTargetWithMixOfAdjacentGrades() {
        // Dữ liệu mẫu: 40 TC tích lũy, tổng 124.5 điểm -> GPA 3.1125
        val current = GpaCalculator.cumulative(MockData.create().semesters)
        assertEquals(40, current.credits)
        assertEquals(3.1125, current.gpa4, 1e-9)
        assertEquals(Classification.GOOD, GpaCalculator.classify(current.gpa4))

        // Muốn đạt Giỏi (3.2) sau 90 TC còn lại: cần TB (416 - 124.5) / 90 = 3.2389
        val plan = GpaCalculator.planTarget(current, 3.2, 90)
        assertEquals(291.5 / 90, plan.requiredAvg, 1e-9)
        assertEquals("B+", plan.uniformGrade!!.letter)
        val mix = plan.mix!!
        assertEquals("B", mix.low.letter)
        assertEquals(43, mix.highCredits)
        assertEquals(47, mix.lowCredits)
        assertEquals(291.5, mix.highCredits * mix.high.point + mix.lowCredits * mix.low.point, 1e-9)
    }

    @Test
    fun planTargetImpossible() {
        val current = GpaCalculator.Summary(gpa4 = 2.0, gpa10 = 6.0, credits = 100)
        val plan = GpaCalculator.planTarget(current, 3.6, 30)
        assertNull(plan.uniformGrade)
        assertNull(plan.mix)
        assertEquals((200.0 + 120) / 130, plan.maxReachable, 1e-9)
    }

    @Test
    fun classifyUsesRoundedGpa() {
        assertEquals(Classification.VERY_GOOD, GpaCalculator.classify(3.196))
        assertEquals(Classification.GOOD, GpaCalculator.classify(3.194))
    }
}
