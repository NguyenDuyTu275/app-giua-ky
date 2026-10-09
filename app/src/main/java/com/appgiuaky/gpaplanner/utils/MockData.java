package com.appgiuaky.gpaplanner.utils;

import com.appgiuaky.gpaplanner.types.AcademicData;
import com.appgiuaky.gpaplanner.types.Course;
import com.appgiuaky.gpaplanner.types.GradeComponent;
import com.appgiuaky.gpaplanner.types.Semester;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Dữ liệu mẫu 4 học kỳ (21 môn) để demo ngay: 3 kỳ đã xong, kỳ 4 đang học, có 1 môn F đã học lại. */
public final class MockData {

    private MockData() {
    }

    /** Cơ cấu điểm mặc định cho môn mới: Chuyên cần 10% - Giữa kỳ 30% - Cuối kỳ 60%. */
    public static List<GradeComponent> defaultComponents() {
        return new ArrayList<>(Arrays.asList(
                new GradeComponent("Chuyên cần", 10, null),
                new GradeComponent("Giữa kỳ", 30, null),
                new GradeComponent("Cuối kỳ", 60, null)));
    }

    private static Course course(String code, String name, int credits, Double attendance, Double midterm, Double fin) {
        return course(code, name, credits, attendance, midterm, fin, true);
    }

    private static Course course(String code, String name, int credits, Double attendance, Double midterm, Double fin,
                                 boolean countInGpa) {
        return new Course(code, name, credits, Arrays.asList(
                new GradeComponent("Chuyên cần", 10, attendance),
                new GradeComponent("Giữa kỳ", 30, midterm),
                new GradeComponent("Cuối kỳ", 60, fin)), countInGpa);
    }

    public static AcademicData create() {
        return new AcademicData(Arrays.asList(
                new Semester("Học kỳ 1", Arrays.asList(
                        course("MATH101", "Giải tích 1", 3, 9.0, 7.5, 8.0),
                        course("PHY101", "Vật lý đại cương", 3, 8.0, 6.0, 6.5),
                        course("IT101", "Nhập môn lập trình", 3, 10.0, 8.5, 9.0),
                        course("ENG101", "Tiếng Anh 1", 2, 9.0, 8.0, 7.0),
                        course("PE101", "Giáo dục thể chất 1", 1, 10.0, 8.0, 8.0, false))),
                new Semester("Học kỳ 2", Arrays.asList(
                        course("MATH102", "Giải tích 2", 3, 7.0, 3.0, 3.0),
                        course("MATH103", "Đại số tuyến tính", 3, 8.0, 7.0, 7.5),
                        course("IT102", "Kỹ thuật lập trình", 3, 9.0, 8.0, 8.5),
                        course("POL101", "Triết học Mác - Lênin", 3, 9.0, 7.0, 6.0),
                        course("ENG102", "Tiếng Anh 2", 2, 10.0, 8.0, 8.0))),
                new Semester("Học kỳ 3", Arrays.asList(
                        course("MATH102", "Giải tích 2 (học lại)", 3, 9.0, 7.0, 7.0),
                        course("IT201", "Cấu trúc dữ liệu và giải thuật", 4, 9.0, 8.0, 8.0),
                        course("IT202", "Cơ sở dữ liệu", 3, 10.0, 9.0, 9.0),
                        course("IT203", "Kiến trúc máy tính", 3, 8.0, 6.0, 5.0),
                        course("STAT201", "Xác suất thống kê", 3, 8.0, 7.0, 6.5),
                        course("POL102", "Kinh tế chính trị Mác - Lênin", 2, 9.0, 8.0, 7.0))),
                new Semester("Học kỳ 4", Arrays.asList(
                        course("IT204", "Lập trình hướng đối tượng", 3, 9.0, 8.0, null),
                        course("IT205", "Mạng máy tính", 3, 8.0, 5.0, null),
                        course("IT206", "Hệ điều hành", 3, 7.0, 4.5, null),
                        course("IT207", "Phát triển ứng dụng di động", 3, 10.0, 9.0, null),
                        course("POL103", "Chủ nghĩa xã hội khoa học", 2, 9.0, null, null)))),
                AcademicData.DEFAULT_PROGRAM_CREDITS);
    }
}
