package com.appgiuaky.gpaplanner.types;

/** Tài khoản Google đang đăng nhập: chỉ dùng để hiện tên và ảnh trong app, không gửi đi đâu. */
public final class UserProfile {
    /** Tên hiển thị; null nếu tài khoản không có tên (khi đó dùng email). */
    public final String name;
    public final String email;

    public UserProfile(String name, String email) {
        this.name = name;
        this.email = email;
    }

    /** Tên để chào và hiển thị: tên tài khoản, hoặc email nếu không có tên. */
    public String displayName() {
        return name != null && !name.trim().isEmpty() ? name.trim() : email;
    }
}
