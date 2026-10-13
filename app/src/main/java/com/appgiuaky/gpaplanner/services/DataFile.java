package com.appgiuaky.gpaplanner.services;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * A UTF-8 text file that is replaced atomically on write, so a crash mid-write never leaves a
 * truncated or half-written file behind.
 */
public final class DataFile {

    private final File file;

    public DataFile(File file) {
        this.file = file;
    }

    /** Returns the file content, or null if the file does not exist. */
    public String read() throws IOException {
        if (!file.exists()) return null;
        try (InputStream in = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) file.length()];
            int read = 0;
            while (read < bytes.length) {
                int n = in.read(bytes, read, bytes.length - read);
                if (n < 0) break;
                read += n;
            }
            return new String(bytes, 0, read, StandardCharsets.UTF_8);
        }
    }

    public void write(String content) throws IOException {
        File tmp = new File(file.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.getFD().sync();
        }
        if (tmp.renameTo(file)) return;
        // rename does not replace an existing file on every platform (e.g. Windows JVMs used for tests)
        if (file.delete() && tmp.renameTo(file)) return;
        tmp.delete();
        throw new IOException("Không ghi được file " + file.getName());
    }

    /** Moves an unreadable file aside so it is kept for recovery instead of being overwritten. */
    public File quarantine() throws IOException {
        File target = new File(file.getPath() + ".corrupt-" + System.currentTimeMillis());
        if (!file.renameTo(target)) throw new IOException("Không sao lưu được file " + file.getName());
        return target;
    }
}
