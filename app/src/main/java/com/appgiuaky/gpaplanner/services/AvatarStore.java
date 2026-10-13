package com.appgiuaky.gpaplanner.services;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Ảnh đại diện Google lưu thành file trong máy, để mở app không cần mạng vẫn hiện được ảnh.
 * Các hàm đều chặn luồng (đọc file / tải mạng), phải gọi từ luồng nền.
 */
public final class AvatarStore {

    private static final int TIMEOUT_MS = 15_000;
    /** Ảnh đại diện chỉ vài chục KB; giới hạn để một URL lạ không làm đầy bộ nhớ. */
    private static final int MAX_BYTES = 2 * 1024 * 1024;

    private final File file;

    public AvatarStore(File file) {
        this.file = file;
    }

    /** Ảnh đã lưu, hoặc null nếu chưa có / file hỏng. */
    public Bitmap load() {
        return file.exists() ? BitmapFactory.decodeFile(file.getPath()) : null;
    }

    /** Tải ảnh từ [url], lưu đè file cũ và trả về ảnh đã giải mã. */
    public Bitmap download(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            int code = conn.getResponseCode();
            if (code < 200 || code > 299) throw new IOException("Tải ảnh đại diện lỗi HTTP " + code);
            File temp = new File(file.getPath() + ".tmp");
            try (InputStream in = conn.getInputStream(); OutputStream out = new FileOutputStream(temp)) {
                byte[] buffer = new byte[8192];
                int total = 0;
                int n;
                while ((n = in.read(buffer)) != -1) {
                    total += n;
                    if (total > MAX_BYTES) throw new IOException("Ảnh đại diện quá lớn");
                    out.write(buffer, 0, n);
                }
            }
            Bitmap bitmap = BitmapFactory.decodeFile(temp.getPath());
            if (bitmap == null || !temp.renameTo(file)) {
                temp.delete();
                throw new IOException("Không đọc được ảnh đại diện");
            }
            return bitmap;
        } finally {
            conn.disconnect();
        }
    }

    public void delete() {
        file.delete();
    }
}
