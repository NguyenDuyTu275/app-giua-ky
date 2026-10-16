package com.appgiuaky.gpaplanner.components.account;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.ClearCredentialException;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.NoCredentialException;
import androidx.fragment.app.Fragment;

import com.appgiuaky.gpaplanner.AccountViewModel;
import com.appgiuaky.gpaplanner.BuildConfig;
import com.appgiuaky.gpaplanner.R;
import com.appgiuaky.gpaplanner.components.Ui;
import com.appgiuaky.gpaplanner.types.UserProfile;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Locale;

/** Đăng nhập Google (Credential Manager), hộp thoại tài khoản và ảnh đại diện tròn. */
public final class AccountUi {

    private static final int DIALOG_AVATAR_DP = 72;
    /** Chữ cái đầu chiếm chừng này phần đường kính ảnh đại diện. */
    private static final float INITIAL_SIZE_RATIO = 0.45f;

    private AccountUi() {
    }

    public static void showDialog(Fragment fragment, AccountViewModel vm) {
        Context c = fragment.requireContext();
        UserProfile profile = vm.getAccount().getValue();
        View view = LayoutInflater.from(c).inflate(R.layout.dialog_account, null);
        ((ImageView) view.findViewById(R.id.account_avatar))
                .setImageDrawable(avatar(c, vm.getAvatar().getValue(), profile, DIALOG_AVATAR_DP, false));
        TextView name = view.findViewById(R.id.account_name);
        TextView email = view.findViewById(R.id.account_email);
        if (profile == null) {
            name.setText(R.string.account_signed_out);
            email.setVisibility(View.GONE);
        } else {
            name.setText(profile.displayName());
            email.setText(profile.email);
        }

        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(c)
                .setTitle(R.string.account_title)
                .setView(view)
                .setNegativeButton(R.string.close, null);
        if (profile == null) {
            dialog.setPositiveButton(R.string.sign_in_google, (d, w) -> signIn(fragment.requireActivity(), vm));
        } else {
            dialog.setPositiveButton(R.string.sign_out, (d, w) -> signOut(c, vm));
        }
        dialog.show();
    }

    /** Hiện bảng chọn tài khoản Google của máy; chọn xong thì lưu tên, email, ảnh vào [vm]. */
    public static void signIn(Activity activity, AccountViewModel vm) {
        String clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID;
        if (clientId.isEmpty()) {
            toast(activity, activity.getString(R.string.sign_in_not_configured));
            return;
        }
        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(new GetSignInWithGoogleOption.Builder(clientId).build())
                .build();
        CredentialManager.create(activity).getCredentialAsync(activity, request, null,
                ContextCompat.getMainExecutor(activity),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse response) {
                        onCredential(activity, vm, response.getCredential());
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        // Người dùng tự đóng bảng chọn tài khoản: không phải lỗi
                        if (e instanceof GetCredentialCancellationException) return;
                        toast(activity, e instanceof NoCredentialException
                                ? activity.getString(R.string.sign_in_no_account)
                                : activity.getString(R.string.sign_in_failed, e.getMessage()));
                    }
                });
    }

    private static void onCredential(Activity activity, AccountViewModel vm, Credential credential) {
        if (!(credential instanceof CustomCredential)
                || !GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
            toast(activity, activity.getString(R.string.sign_in_failed, credential.getType()));
            return;
        }
        GoogleIdTokenCredential google;
        try {
            google = GoogleIdTokenCredential.createFrom(credential.getData());
        } catch (Exception e) {
            // createFrom báo lỗi khi dữ liệu Google trả về không đúng định dạng
            toast(activity, activity.getString(R.string.sign_in_failed, e.getMessage()));
            return;
        }
        String email = google.getEmail();
        if (email == null) {
            toast(activity, activity.getString(R.string.sign_in_failed, "Google không trả về email"));
            return;
        }
        UserProfile profile = new UserProfile(google.getDisplayName(), email);
        Uri photo = google.getProfilePictureUri();
        vm.signedIn(profile, photo == null ? null : photo.toString());
        toast(activity, activity.getString(R.string.signed_in_as, profile.displayName()));
    }

    private static void signOut(Context c, AccountViewModel vm) {
        vm.signOut();
        // Quên lựa chọn tài khoản đã nhớ, để lần đăng nhập sau Google hỏi lại chọn tài khoản nào
        CredentialManager.create(c).clearCredentialStateAsync(new ClearCredentialStateRequest(), null,
                ContextCompat.getMainExecutor(c), new CredentialManagerCallback<Void, ClearCredentialException>() {
                    @Override
                    public void onResult(Void result) {
                    }

                    @Override
                    public void onError(@NonNull ClearCredentialException e) {
                        // Đã đăng xuất trong app; lỗi này chỉ làm lần sau Google tự chọn lại tài khoản cũ
                    }
                });
        toast(c, c.getString(R.string.signed_out));
    }

    /**
     * Ảnh đại diện tròn cỡ [sizeDp]: ảnh Google nếu có, chữ cái đầu của tên nếu chưa có ảnh, icon người nếu
     * chưa đăng nhập. [onHeader]: nằm trên thanh tiêu đề đỏ, nên vòng tròn chữ cái đầu dùng nền trắng chữ đỏ.
     */
    public static Drawable avatar(Context c, Bitmap photo, UserProfile profile, int sizeDp, boolean onHeader) {
        int size = Ui.dp(c, sizeDp);
        if (photo != null) {
            RoundedBitmapDrawable round = RoundedBitmapDrawableFactory.create(c.getResources(),
                    Bitmap.createScaledBitmap(photo, size, size, true));
            round.setCircular(true);
            return round;
        }
        if (profile == null) return AppCompatResources.getDrawable(c, R.drawable.ic_account_circle);

        int primary = Ui.color(c, androidx.appcompat.R.attr.colorPrimary);
        int onPrimary = Ui.color(c, com.google.android.material.R.attr.colorOnPrimary);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint circle = new Paint(Paint.ANTI_ALIAS_FLAG);
        circle.setColor(onHeader ? Color.WHITE : primary);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, circle);
        Paint letter = new Paint(Paint.ANTI_ALIAS_FLAG);
        letter.setColor(onHeader ? primary : onPrimary);
        letter.setTextSize(size * INITIAL_SIZE_RATIO);
        letter.setTypeface(Typeface.create(ResourcesCompat.getFont(c, R.font.be_vietnam_pro), Typeface.BOLD));
        letter.setTextAlign(Paint.Align.CENTER);
        String name = profile.displayName();
        String initial = name.substring(0, name.offsetByCodePoints(0, 1)).toUpperCase(Locale.ROOT);
        Paint.FontMetrics metrics = letter.getFontMetrics();
        canvas.drawText(initial, size / 2f, size / 2f - (metrics.ascent + metrics.descent) / 2, letter);
        return new BitmapDrawable(c.getResources(), bitmap);
    }

    private static void toast(Context c, String message) {
        Toast.makeText(c, message, Toast.LENGTH_LONG).show();
    }
}
