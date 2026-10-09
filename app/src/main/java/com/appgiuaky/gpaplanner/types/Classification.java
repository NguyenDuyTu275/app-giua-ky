package com.appgiuaky.gpaplanner.types;

/** Xếp loại học lực theo GPA tích lũy hệ 4 (thứ tự từ cao xuống thấp). */
public enum Classification {
    EXCELLENT("Xuất sắc", 3.6),
    VERY_GOOD("Giỏi", 3.2),
    GOOD("Khá", 2.5),
    AVERAGE("Trung bình", 2.0),
    WEAK("Yếu", 1.0),
    POOR("Kém", 0.0);

    public final String label;
    public final double minGpa;

    Classification(String label, double minGpa) {
        this.label = label;
        this.minGpa = minGpa;
    }
}
