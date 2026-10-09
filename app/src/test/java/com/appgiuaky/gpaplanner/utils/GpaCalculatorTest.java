package com.appgiuaky.gpaplanner.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.appgiuaky.gpaplanner.types.Classification;
import com.appgiuaky.gpaplanner.types.Course;
import com.appgiuaky.gpaplanner.types.CourseStatus;
import com.appgiuaky.gpaplanner.types.GradeComponent;
import com.appgiuaky.gpaplanner.types.LetterGrade;
import com.appgiuaky.gpaplanner.types.Semester;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GpaCalculatorTest {

    private static Course course(String code, int credits, Double attendance, Double midterm, Double fin) {
        return course(code, credits, attendance, midterm, fin, true);
    }

    private static Course course(String code, int credits, Double attendance, Double midterm, Double fin, boolean countInGpa) {
        return new Course(code, code, credits, Arrays.asList(
                new GradeComponent("Chuyên cần", 10, attendance),
                new GradeComponent("Giữa kỳ", 30, midterm),
                new GradeComponent("Cuối kỳ", 60, fin)), countInGpa);
    }

    private static LetterGrade letter(String l) {
        for (LetterGrade g : GpaCalculator.SCALE) if (g.letter.equals(l)) return g;
        throw new IllegalArgumentException(l);
    }

    private static Double need(List<GpaCalculator.RescueRow> rows, String l) {
        for (GpaCalculator.RescueRow r : rows) if (r.grade.letter.equals(l)) return r.need;
        throw new IllegalArgumentException(l);
    }

    @Test
    public void letterBoundaries() {
        assertEquals("A", GpaCalculator.letterOf(8.5).letter);
        assertEquals("B+", GpaCalculator.letterOf(8.4).letter);
        assertEquals("B", GpaCalculator.letterOf(7.0).letter);
        assertEquals("C+", GpaCalculator.letterOf(6.9).letter);
        assertEquals("D", GpaCalculator.letterOf(4.0).letter);
        assertEquals("F", GpaCalculator.letterOf(3.9).letter);
    }

    @Test
    public void courseScoreRoundsToOneDecimal() {
        // 0.9 + 2.25 + 4.8 = 7.95 -> 8.0 (B+)
        Course c = course("X", 3, 9.0, 7.5, 8.0);
        assertEquals(8.0, GpaCalculator.courseScore(c), 1e-9);
        assertEquals("B+", GpaCalculator.courseGrade(c).letter);
        // 0.85 + 2.4 + 5.0 = 8.25 -> 8.3 (không bị sai số thành 8.2)
        assertEquals(8.3, GpaCalculator.courseScore(course("Y", 3, 8.5, 8.0, 25.0 / 3)), 1e-9);
    }

    @Test
    public void statusFollowsEnteredComponents() {
        assertEquals(CourseStatus.COMPLETED, GpaCalculator.status(course("X", 3, 1.0, 2.0, 3.0)));
        assertEquals(CourseStatus.IN_PROGRESS, GpaCalculator.status(course("X", 3, 1.0, null, null)));
        assertEquals(CourseStatus.PLANNED, GpaCalculator.status(course("X", 3, null, null, null)));
        assertNull(GpaCalculator.courseScore(course("X", 3, 1.0, null, null)));
    }

    @Test
    public void semesterGpaCountsFButCumulativeOnlyBestPassedAttempt() {
        Semester s1 = new Semester("HK1", Arrays.asList(
                course("A1", 3, 10.0, 10.0, 10.0),              // A 4.0
                course("F1", 2, 0.0, 0.0, 0.0),                 // F
                course("PE", 1, 0.0, 0.0, 0.0, false)));        // không tính
        assertEquals(3 * 4.0 / 5, GpaCalculator.semesterSummary(s1).gpa4, 1e-9);
        assertEquals(5, GpaCalculator.semesterSummary(s1).credits);

        GpaCalculator.Summary before = GpaCalculator.cumulative(Collections.singletonList(s1));
        assertEquals(4.0, before.gpa4, 1e-9);
        assertEquals(3, before.credits);

        // Học lại F1 được 7.0 (B)
        Semester s2 = new Semester("HK2", Collections.singletonList(course("f1", 2, 7.0, 7.0, 7.0)));
        GpaCalculator.Summary after = GpaCalculator.cumulative(Arrays.asList(s1, s2));
        assertEquals((3 * 4.0 + 2 * 3.0) / 5, after.gpa4, 1e-9);
        assertEquals(5, after.credits);
    }

    @Test
    public void improvementOnlyCountsWhenHigher() {
        Course b = course("B", 2, 7.0, 7.0, 7.0); // B
        Course a = course("A", 2, 10.0, 10.0, 10.0);
        List<Semester> semesters = Collections.singletonList(new Semester("HK", Arrays.asList(a, b)));
        Map<String, LetterGrade> improved = new HashMap<>();
        improved.put(b.id, letter("A"));
        assertEquals(4.0, GpaCalculator.cumulative(semesters, improved).gpa4, 1e-9);
        Map<String, LetterGrade> worse = new HashMap<>();
        worse.put(b.id, letter("C"));
        assertEquals(3.5, GpaCalculator.cumulative(semesters, worse).gpa4, 1e-9);
    }

    @Test
    public void rescueFindsMinimumFinalScore() {
        // Đã có 0.8 + 1.5 = 2.3 điểm, cuối kỳ chiếm 60%
        List<GpaCalculator.RescueRow> rows = GpaCalculator.rescue(course("X", 3, 8.0, 5.0, null));
        // D: 2.3 + 0.6x làm tròn >= 4.0 -> x = 2.8 (2.3 + 1.68 = 3.98 -> 4.0)
        assertEquals(2.8, need(rows, "D"), 1e-9);
        // A: cần 2.3 + 0.6x >= 8.45 -> x = 10.3 > 10 -> không thể
        assertNull(need(rows, "A"));
        // B+: 2.3 + 0.6x >= 7.95 -> x = 9.5 (2.3 + 5.7 = 8.0)
        assertEquals(9.5, need(rows, "B+"), 1e-9);
    }

    @Test
    public void rescueReportsZeroWhenAlreadySafe() {
        List<GpaCalculator.RescueRow> rows = GpaCalculator.rescue(course("X", 3, 10.0, 10.0, null));
        assertEquals(0.0, need(rows, "D"), 1e-9);
    }

    @Test
    public void planTargetWithMixOfAdjacentGrades() {
        // Dữ liệu mẫu: 40 TC tích lũy, tổng 124.5 điểm -> GPA 3.1125
        GpaCalculator.Summary current = GpaCalculator.cumulative(MockData.create().semesters);
        assertEquals(40, current.credits);
        assertEquals(3.1125, current.gpa4, 1e-9);
        assertEquals(Classification.GOOD, GpaCalculator.classify(current.gpa4));

        // Muốn đạt Giỏi (3.2) sau 90 TC còn lại: cần TB (416 - 124.5) / 90 = 3.2389
        GpaCalculator.TargetPlan plan = GpaCalculator.planTarget(current, 3.2, 90);
        assertEquals(291.5 / 90, plan.requiredAvg, 1e-9);
        assertEquals("B+", plan.uniformGrade.letter);
        GpaCalculator.Mix mix = plan.mix;
        assertNotNull(mix);
        assertEquals("B", mix.low.letter);
        assertEquals(43, mix.highCredits);
        assertEquals(47, mix.lowCredits);
        assertEquals(291.5, mix.highCredits * mix.high.point + mix.lowCredits * mix.low.point, 1e-9);
    }

    @Test
    public void planTargetImpossible() {
        GpaCalculator.Summary current = new GpaCalculator.Summary(2.0, 6.0, 100);
        GpaCalculator.TargetPlan plan = GpaCalculator.planTarget(current, 3.6, 30);
        assertNull(plan.uniformGrade);
        assertNull(plan.mix);
        assertEquals((200.0 + 120) / 130, plan.maxReachable, 1e-9);
    }

    @Test
    public void classifyUsesRoundedGpa() {
        assertEquals(Classification.VERY_GOOD, GpaCalculator.classify(3.196));
        assertEquals(Classification.GOOD, GpaCalculator.classify(3.194));
    }
}
