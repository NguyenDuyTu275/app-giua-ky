package com.appgiuaky.gpaplanner.types;

import java.util.List;
import java.util.UUID;

/** Một môn học (một lần học). Sửa môn = tạo đối tượng mới giữ nguyên id. */
public final class Course {

    public final String id;
    public final String code;
    public final String name;
    public final int credits;
    public final List<GradeComponent> components;
    /** Các môn như Giáo dục thể chất, Quốc phòng thường không tính vào GPA. */
    public final boolean countInGpa;

    public Course(String id, String code, String name, int credits, List<GradeComponent> components, boolean countInGpa) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.credits = credits;
        this.components = components;
        this.countInGpa = countInGpa;
    }

    /** Môn mới với id ngẫu nhiên. */
    public Course(String code, String name, int credits, List<GradeComponent> components, boolean countInGpa) {
        this(UUID.randomUUID().toString(), code, name, credits, components, countInGpa);
    }
}
