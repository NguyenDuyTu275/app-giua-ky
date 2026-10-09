package com.appgiuaky.gpaplanner.types;

/** Một điểm thành phần của môn học, ví dụ "Giữa kỳ" chiếm 30%. score = null nghĩa là chưa có điểm. */
public final class GradeComponent {

    public final String name;
    /** Trọng số theo phần trăm (tổng các thành phần của một môn = 100). */
    public final double weight;
    public final Double score;

    public GradeComponent(String name, double weight, Double score) {
        this.name = name;
        this.weight = weight;
        this.score = score;
    }
}
