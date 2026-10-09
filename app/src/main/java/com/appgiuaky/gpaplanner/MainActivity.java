package com.appgiuaky.gpaplanner;

import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.appgiuaky.gpaplanner.components.aiadvisor.AIAdvisorFragment;
import com.appgiuaky.gpaplanner.components.curriculum.CurriculumFragment;
import com.appgiuaky.gpaplanner.components.dashboard.DashboardFragment;
import com.appgiuaky.gpaplanner.components.gradebook.GradebookFragment;
import com.appgiuaky.gpaplanner.components.simulator.SimulatorFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.color.DynamicColors;

/** Layout chính: thanh điều hướng dưới cùng chuyển giữa 5 màn hình (Fragment). */
public class MainActivity extends AppCompatActivity {

    private static final int[] TABS = {
            R.id.nav_dashboard, R.id.nav_gradebook, R.id.nav_simulator, R.id.nav_curriculum, R.id.nav_advisor,
    };

    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        View root = findViewById(R.id.root);
        bottomNav = findViewById(R.id.bottom_nav);
        // Chừa chỗ cho thanh trạng thái; khi bàn phím mở thì ẩn thanh điều hướng để ô nhập nằm ngay trên bàn phím
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            boolean imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
            int imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            v.setPadding(bars.left, bars.top, bars.right, imeVisible ? imeBottom : 0);
            bottomNav.setVisibility(imeVisible ? View.GONE : View.VISIBLE);
            return insets;
        });

        bottomNav.setOnItemSelectedListener(item -> {
            showTab(item.getItemId());
            return true;
        });
        showTab(bottomNav.getSelectedItemId());
    }

    /** Hiện màn hình của tab được chọn, ẩn các màn hình khác (giữ nguyên trạng thái khi quay lại). */
    private void showTab(int id) {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction tx = fm.beginTransaction();
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
