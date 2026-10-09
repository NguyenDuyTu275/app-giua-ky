package com.appgiuaky.gpaplanner.utils;

import com.appgiuaky.gpaplanner.types.Classification;
import com.appgiuaky.gpaplanner.types.Course;
import com.appgiuaky.gpaplanner.types.CourseStatus;
import com.appgiuaky.gpaplanner.types.GradeComponent;
import com.appgiuaky.gpaplanner.types.LetterGrade;
import com.appgiuaky.gpaplanner.types.Semester;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Toàn bộ công thức quy đổi điểm: Hệ 10 -> Chữ -> Hệ 4, theo quy chế đào tạo tín chỉ phổ biến
 * ở các trường đại học Việt Nam:
 *  - Điểm học phần = Σ(điểm thành phần × trọng số), làm tròn đến 1 chữ số thập phân.
 *  - GPA học kỳ: trung bình theo tín chỉ của mọi môn đã có điểm trong kỳ (kể cả môn F).
 *  - GPA tích lũy: chỉ tính các môn đã đạt (D trở lên); môn học lại/cải thiện lấy lần có điểm cao nhất.
 *  - Môn có countInGpa = false (GDTC, GDQP...) không tính vào GPA và tín chỉ tích lũy.
 */
public final class GpaCalculator {

    private GpaCalculator() {
    }

    /** Thang điểm chữ, từ cao xuống thấp. Trường dùng thang khác (ví dụ có A+) chỉ cần sửa bảng này. */
    public static final List<LetterGrade> SCALE = Collections.unmodifiableList(Arrays.asList(
            new LetterGrade("A", 8.5, 4.0),
            new LetterGrade("B+", 8.0, 3.5),
            new LetterGrade("B", 7.0, 3.0),
            new LetterGrade("C+", 6.5, 2.5),
            new LetterGrade("C", 5.5, 2.0),
            new LetterGrade("D+", 5.0, 1.5),
            new LetterGrade("D", 4.0, 1.0),
            new LetterGrade("F", 0.0, 0.0)));

    /** Các mức điểm đạt (A..D), từ cao xuống thấp. */
    public static final List<LetterGrade> PASSING;

    static {
        List<LetterGrade> passing = new ArrayList<>();
        for (LetterGrade g : SCALE) if (g.point > 0) passing.add(g);
        PASSING = Collections.unmodifiableList(passing);
    }

    /** Kết quả tính GPA: hệ 4, hệ 10 và số tín chỉ được tính. */
    public static final class Summary {
        public final double gpa4;
        public final double gpa10;
        public final int credits;

        public Summary(double gpa4, double gpa10, int credits) {
            this.gpa4 = gpa4;
            this.gpa10 = gpa10;
            this.credits = credits;
        }
    }

    private static final class Entry {
        final int credits;
        final double score10;
        final double point;

        Entry(int credits, double score10, double point) {
            this.credits = credits;
            this.score10 = score10;
            this.point = point;
        }
    }

    public static double round1(double x) {
        return round(x, 1);
    }

    public static double round2(double x) {
        return round(x, 2);
    }

    // Làm tròn 6 chữ số trước để khử sai số dấu phẩy động (8.2499999 -> 8.25 -> 8.3).
    private static double round(double x, int digits) {
        return new BigDecimal(x).setScale(6, RoundingMode.HALF_UP).setScale(digits, RoundingMode.HALF_UP).doubleValue();
    }

    public static LetterGrade letterOf(double score10) {
        for (LetterGrade g : SCALE) if (score10 >= g.minScore) return g;
        return SCALE.get(SCALE.size() - 1);
    }

    public static Classification classify(double gpa4) {
        double rounded = round2(gpa4);
        for (Classification c : Classification.values()) if (rounded >= c.minGpa) return c;
        return Classification.POOR;
    }

    public static CourseStatus status(Course course) {
        boolean any = false;
        boolean all = !course.components.isEmpty();
        for (GradeComponent g : course.components) {
            if (g.score != null) any = true;
            else all = false;
        }
        if (all) return CourseStatus.COMPLETED;
        return any ? CourseStatus.IN_PROGRESS : CourseStatus.PLANNED;
    }

    /** Điểm học phần hệ 10, null nếu chưa đủ điểm thành phần. */
    public static Double courseScore(Course course) {
        if (status(course) != CourseStatus.COMPLETED) return null;
        double sum = 0;
        for (GradeComponent g : course.components) sum += g.weight * g.score;
        return round1(sum / 100);
    }

    /** Điểm chữ của môn, null nếu chưa có điểm học phần. */
    public static LetterGrade courseGrade(Course course) {
        Double score = courseScore(course);
        return score == null ? null : letterOf(score);
    }

    private static Summary summarize(List<Entry> entries) {
        int credits = 0;
        double points = 0;
        double scores = 0;
        for (Entry e : entries) {
            credits += e.credits;
            points += e.point * e.credits;
            scores += e.score10 * e.credits;
        }
        if (credits == 0) return new Summary(0, 0, 0);
        return new Summary(points / credits, scores / credits, credits);
    }

    public static Summary semesterSummary(Semester semester) {
        List<Entry> entries = new ArrayList<>();
        for (Course c : semester.courses) {
            Double score = courseScore(c);
            if (c.countInGpa && score != null) entries.add(new Entry(c.credits, score, letterOf(score).point));
        }
        return summarize(entries);
    }

    /** Lần học có điểm cao nhất của mỗi môn (gộp theo mã môn), trong các môn đã có điểm và tính GPA. */
    public static List<Course> bestCourses(List<Semester> semesters) {
        Map<String, Course> best = new LinkedHashMap<>();
        for (Semester s : semesters) {
            for (Course c : s.courses) {
                Double score = courseScore(c);
                if (!c.countInGpa || score == null) continue;
                String key = c.code.trim().toUpperCase(Locale.ROOT);
                Course current = best.get(key);
                if (current == null || score > courseScore(current)) best.put(key, c);
            }
        }
        return new ArrayList<>(best.values());
    }

    public static Summary cumulative(List<Semester> semesters) {
        return cumulative(semesters, Collections.<String, LetterGrade>emptyMap());
    }

    /**
     * GPA tích lũy. [improvements] (id môn -> điểm chữ giả định) dùng cho giả lập học cải thiện:
     * môn được thay bằng điểm mới nếu điểm mới cao hơn.
     */
    public static Summary cumulative(List<Semester> semesters, Map<String, LetterGrade> improvements) {
        List<Entry> entries = new ArrayList<>();
        for (Course c : bestCourses(semesters)) {
            double score = courseScore(c);
            LetterGrade current = letterOf(score);
            LetterGrade improved = improvements.get(c.id);
            Entry e = improved != null && improved.point > current.point
                    ? new Entry(c.credits, improved.minScore, improved.point)
                    : new Entry(c.credits, score, current.point);
            if (e.point > 0) entries.add(e);
        }
        return summarize(entries);
    }

    /** Một dòng của bảng "Cứu môn": điểm trung bình cần ở các thành phần còn lại để đạt [grade]. */
    public static final class RescueRow {
        public final LetterGrade grade;
        /** 0.0 = chắc chắn đạt; null = không thể đạt kể cả được 10. */
        public final Double need;

        RescueRow(LetterGrade grade, Double need) {
            this.grade = grade;
            this.need = need;
        }
    }

    /** "Cứu môn": với mỗi mức điểm đạt, điểm trung bình tối thiểu (bước 0.1) cần ở các thành phần chưa có điểm. */
    public static List<RescueRow> rescue(Course course) {
        double known = 0;
        double remainingWeight = 0;
        for (GradeComponent g : course.components) {
            if (g.score != null) known += g.weight * g.score / 100;
            else remainingWeight += g.weight / 100;
        }
        List<RescueRow> rows = new ArrayList<>();
        for (LetterGrade grade : PASSING) {
            Double need = null;
            for (int i = 0; i <= 100; i++) {
                double x = i / 10.0;
                if (round1(known + remainingWeight * x) >= grade.minScore) {
                    need = x;
                    break;
                }
            }
            rows.add(new RescueRow(grade, need));
        }
        return rows;
    }

    /** Phương án kết hợp: highCredits tín chỉ đạt [high], phần còn lại đạt [low]. */
    public static final class Mix {
        public final LetterGrade high;
        public final int highCredits;
        public final LetterGrade low;
        public final int lowCredits;

        Mix(LetterGrade high, int highCredits, LetterGrade low, int lowCredits) {
            this.high = high;
            this.highCredits = highCredits;
            this.low = low;
            this.lowCredits = lowCredits;
        }
    }

    public static final class TargetPlan {
        /** GPA hệ 4 trung bình cần đạt trên số tín chỉ sắp học. */
        public final double requiredAvg;
        /** GPA tích lũy cao nhất có thể (nếu mọi môn sắp học đều đạt A). */
        public final double maxReachable;
        /** Mức điểm chữ tối thiểu nếu mọi môn sắp học cùng một mức; null nếu không thể đạt mục tiêu. */
        public final LetterGrade uniformGrade;
        public final Mix mix;

        TargetPlan(double requiredAvg, double maxReachable, LetterGrade uniformGrade, Mix mix) {
            this.requiredAvg = requiredAvg;
            this.maxReachable = maxReachable;
            this.uniformGrade = uniformGrade;
            this.mix = mix;
        }
    }

    /** Lập kế hoạch: cần học [plannedCredits] tín chỉ tới với điểm thế nào để GPA tích lũy đạt [target]. */
    public static TargetPlan planTarget(Summary current, double target, int plannedCredits) {
        int totalCredits = current.credits + plannedCredits;
        double required = (target * totalCredits - current.gpa4 * current.credits) / plannedCredits;
        double maxReachable = (current.gpa4 * current.credits + 4.0 * plannedCredits) / totalCredits;

        // Mức thấp nhất vẫn đủ điểm, và mức ngay dưới nó
        LetterGrade uniform = null;
        for (LetterGrade g : PASSING) if (g.point >= required - 1e-9) uniform = g;
        LetterGrade low = null;
        if (uniform != null) {
            for (LetterGrade g : PASSING) {
                if (g.point < uniform.point) {
                    low = g;
                    break;
                }
            }
        }

        Mix mix = null;
        if (uniform != null && low != null && required > low.point) {
            double share = (required - low.point) / (uniform.point - low.point);
            int highCredits = (int) Math.ceil(share * plannedCredits - 1e-9);
            if (highCredits < plannedCredits) mix = new Mix(uniform, highCredits, low, plannedCredits - highCredits);
        }
        return new TargetPlan(required, maxReachable, uniform, mix);
    }
}
