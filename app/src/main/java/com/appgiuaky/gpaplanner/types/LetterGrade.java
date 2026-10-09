package com.appgiuaky.gpaplanner.types;

/** Một bậc trong thang điểm: điểm hệ 10 từ minScore trở lên được điểm chữ letter, quy ra point (hệ 4). */
public final class LetterGrade {

    public final String letter;
    public final double minScore;
    public final double point;

    public LetterGrade(String letter, double minScore, double point) {
        this.letter = letter;
        this.minScore = minScore;
        this.point = point;
    }
}
