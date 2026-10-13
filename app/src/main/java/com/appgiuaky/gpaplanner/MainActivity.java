package com.appgiuaky.gpaplanner;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import com.appgiuaky.gpaplanner.components.aiadvisor.AIAdvisorFragment;
import com.appgiuaky.gpaplanner.components.curriculum.CurriculumFragment;
import com.appgiuaky.gpaplanner.components.dashboard.DashboardFragment;
import com.appgiuaky.gpaplanner.components.gradebook.GradebookFragment;
import com.appgiuaky.gpaplanner.components.simulator.SimulatorFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.shape.CornerFamily;
import com.google.android.material.shape.MaterialShapeDrawable;

/** Layout chính: thanh điều hướng dưới cùng chuyển giữa 5 màn hình (Fragment). */
public class MainActivity extends AppCompatActivity {

    private static final int[] TABS = {
            R.id.nav_dashboard, R.id.nav_gradebook, R.id.nav_simulator, R.id.nav_curriculum, R.id.nav_advisor,
    };

    private static final float BOUNCE_FROM = 0.85f;
    private static final long BOUNCE_MS = 320;
    private static final float BOUNCE_TENSION = 3f;

    private BottomNavigationView bottomNav;
    /** Vị trí trong TABS của tab đang hiện, để biết màn hình mới trượt vào từ bên nào. */
    private int currentTab = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeSettings.apply(this);
        super.onCreate(savedInstanceState);
        // Thanh trạng thái luôn nằm trên nền đỏ nên icon luôn màu trắng, kể cả ở chế độ sáng
        EdgeToEdge.enable(this, SystemBarStyle.dark(Color.TRANSPARENT));
        setContentView(R.layout.activity_main);

        View root = findViewById(R.id.root);
        View statusBarBackground = findViewById(R.id.status_bar_background);
        bottomNav = findViewById(R.id.bottom_nav);
        // Dải nền đỏ cao bằng thanh trạng thái; khi bàn phím mở thì ẩn thanh điều hướng để ô nhập nằm ngay trên bàn phím
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            boolean imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
            int imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            statusBarBackground.getLayoutParams().height = bars.top;
            statusBarBackground.requestLayout();
            v.setPadding(bars.left, 0, bars.right, imeVisible ? imeBottom : 0);
            bottomNav.setVisibility(imeVisible ? View.GONE : View.VISIBLE);
            return insets;
        });

        roundTopCorners(bottomNav);
        bottomNav.setOnItemSelectedListener(item -> {
            showTab(item.getItemId(), true);
            bounce(bottomNav.findViewById(item.getItemId()));
            return true;
        });
        showTab(bottomNav.getSelectedItemId(), false);

        AppViewModel vm = new ViewModelProvider(this).get(AppViewModel.class);
        vm.getStorageNotice().observe(this, notice -> {
            if (notice == null) return;
            Toast.makeText(this, notice, Toast.LENGTH_LONG).show();
            vm.clearStorageNotice();
        });
    }

    /**
     * Hiện màn hình của tab được chọn, ẩn các màn hình khác (giữ nguyên trạng thái khi quay lại).
     * Khi [animate], màn hình mới trượt vào từ phía của tab được chọn so với tab cũ.
     */
    private void showTab(int id, boolean animate) {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction tx = fm.beginTransaction();
        int index = indexOf(id);
        if (animate && currentTab >= 0 && index != currentTab) {
            tx.setCustomAnimations(index > currentTab ? R.anim.tab_enter_from_end : R.anim.tab_enter_from_start,
                    R.anim.tab_exit);
        }
        currentTab = index;
        for (int tab : TABS) {
            Fragment f = fm.findFragmentByTag(tag(tab));
            if (tab == id) {
                if (f == null) tx.add(R.id.container, create(tab), tag(tab));
                else tx.show(f);
            } else if (f != null) {
                tx.hide(f);
            }
        }
        tx.commit();
    }

    private static int indexOf(int tab) {
        for (int i = 0; i < TABS.length; i++) if (TABS[i] == tab) return i;
        return 0;
    }

    /** Thanh dưới bo tròn hai góc trên, nhìn như một tấm nổi lên trên nội dung. */
    private void roundTopCorners(View nav) {
        if (!(nav.getBackground() instanceof MaterialShapeDrawable)) return;
        MaterialShapeDrawable background = (MaterialShapeDrawable) nav.getBackground();
        float radius = getResources().getDimension(R.dimen.bottom_nav_corner);
        background.setShapeAppearanceModel(background.getShapeAppearanceModel().toBuilder()
                .setTopLeftCorner(CornerFamily.ROUNDED, radius)
                .setTopRightCorner(CornerFamily.ROUNDED, radius)
                .build());
    }

    /** Mục vừa chọn ở thanh dưới nảy nhẹ. */
    private static void bounce(View item) {
        if (item == null) return;
        item.setScaleX(BOUNCE_FROM);
        item.setScaleY(BOUNCE_FROM);
        item.animate().scaleX(1f).scaleY(1f).setDuration(BOUNCE_MS)
                .setInterpolator(new OvershootInterpolator(BOUNCE_TENSION)).start();
    }

    private static String tag(int tab) {
        return "tab_" + tab;
    }

    private static Fragment create(int tab) {
        if (tab == R.id.nav_gradebook) return new GradebookFragment();
        if (tab == R.id.nav_simulator) return new SimulatorFragment();
        if (tab == R.id.nav_curriculum) return new CurriculumFragment();
        if (tab == R.id.nav_advisor) return new AIAdvisorFragment();
        return new DashboardFragment();
    }
}
