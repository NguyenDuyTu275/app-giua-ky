package com.appgiuaky.gpaplanner;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.appgiuaky.gpaplanner.services.AvatarStore;
import com.appgiuaky.gpaplanner.types.UserProfile;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tài khoản Google đang đăng nhập (tên, email, ảnh). Chỉ lưu trong máy để hiển thị; bảng điểm không gắn với
 * tài khoản và vẫn lưu riêng trong máy như cũ.
 */
public class AccountViewModel extends AndroidViewModel {

    private static final String PREFS = "settings";
    private static final String KEY_NAME = "account_name";
    private static final String KEY_EMAIL = "account_email";

    private final SharedPreferences prefs;
    private final AvatarStore avatarStore;
    /** Đọc file / tải ảnh; một luồng để thứ tự lưu và xóa ảnh không bị đảo. */
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private final MutableLiveData<UserProfile> account = new MutableLiveData<>();
    /** Ảnh đại diện; null khi chưa đăng nhập, chưa tải xong hoặc tài khoản không có ảnh. */
    private final MutableLiveData<Bitmap> avatar = new MutableLiveData<>();

    public AccountViewModel(@NonNull Application app) {
        super(app);
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        avatarStore = new AvatarStore(new File(app.getFilesDir(), "avatar.png"));
        String email = prefs.getString(KEY_EMAIL, null);
        if (email != null) {
            account.setValue(new UserProfile(prefs.getString(KEY_NAME, null), email));
            executor.execute(() -> avatar.postValue(avatarStore.load()));
        }
    }

    public LiveData<UserProfile> getAccount() {
        return account;
    }

    public LiveData<Bitmap> getAvatar() {
        return avatar;
    }

    /** Lưu tài khoản vừa đăng nhập; [photoUrl] (có thể null) được tải về ở luồng nền. */
    public void signedIn(UserProfile profile, String photoUrl) {
        prefs.edit().putString(KEY_NAME, profile.name).putString(KEY_EMAIL, profile.email).apply();
        account.setValue(profile);
        avatar.setValue(null);
        executor.execute(() -> {
            avatarStore.delete();
            if (photoUrl == null) return;
            try {
                Bitmap bitmap = avatarStore.download(photoUrl);
                // Bỏ ảnh nếu trong lúc tải người dùng đã đăng xuất hoặc đổi tài khoản
                main.post(() -> {
                    UserProfile current = account.getValue();
                    if (current != null && current.email.equals(profile.email)) avatar.setValue(bitmap);
                });
            } catch (IOException e) {
                // Không có ảnh thì giao diện hiện chữ cái đầu của tên; lần đăng nhập sau sẽ thử tải lại
            }
        });
    }

    public void signOut() {
        prefs.edit().remove(KEY_NAME).remove(KEY_EMAIL).apply();
        account.setValue(null);
        avatar.setValue(null);
        executor.execute(avatarStore::delete);
    }

    @Override
    protected void onCleared() {
        main.removeCallbacksAndMessages(null);
        executor.shutdown();
    }
}
