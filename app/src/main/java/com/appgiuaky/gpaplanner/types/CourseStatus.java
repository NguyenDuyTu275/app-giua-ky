package com.appgiuaky.gpaplanner.types;

public enum CourseStatus {
    COMPLETED("Đã học"),
    IN_PROGRESS("Đang học"),
    PLANNED("Chưa học");

    public final String label;

    CourseStatus(String label) {
        this.label = label;
    }
}
