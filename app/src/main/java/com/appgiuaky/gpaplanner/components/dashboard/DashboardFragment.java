package com.appgiuaky.gpaplanner.components.dashboard;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.appgiuaky.gpaplanner.AccountViewModel;
import com.appgiuaky.gpaplanner.AppViewModel;
import com.appgiuaky.gpaplanner.R;
import com.appgiuaky.gpaplanner.ThemeSettings;
import com.appgiuaky.gpaplanner.components.TabFragment;
import com.appgiuaky.gpaplanner.components.Ui;
import com.appgiuaky.gpaplanner.components.account.AccountUi;
import com.appgiuaky.gpaplanner.types.AcademicData;
import com.appgiuaky.gpaplanner.types.Course;
import com.appgiuaky.gpaplanner.types.CourseStatus;
import com.appgiuaky.gpaplanner.types.LetterGrade;
import com.appgiuaky.gpaplanner.types.Semester;
import com.appgiuaky.gpaplanner.types.UserProfile;
import com.appgiuaky.gpaplanner.utils.Format;
import com.appgiuaky.gpaplanner.utils.GpaCalculator;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.List;

/** Chức năng 4: thẻ thống kê, biểu đồ đường GPA theo kỳ và biểu đồ tròn phân bố điểm chữ. */
public class DashboardFragment extends TabFragment {

    /** Cỡ ảnh đại diện trên thanh tiêu đề, vừa với vùng chạm 48dp của nút. */
    private static final int TOOLBAR_AVATAR_DP = 28;

    private View view;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        this.view = view;
        MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.inflateMenu(R.menu.menu_dashboard);
        AccountViewModel accountVm = new ViewModelProvider(requireActivity()).get(AccountViewModel.class);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_account) AccountUi.showDialog(this, accountVm);
            else showThemeDialog();
            return true;
        });
        accountVm.getAccount().observe(getViewLifecycleOwner(), profile -> showAccount(toolbar, accountVm));
        accountVm.getAvatar().observe(getViewLifecycleOwner(), avatar -> showAccount(toolbar, accountVm));
        highlight(R.id.stat_gpa4);

        LineChartView lineChart = view.findViewById(R.id.line_chart);
        LinearLayout legend = view.findViewById(R.id.line_legend);
        legend.addView(Ui.legend(requireContext(), lineChart.getSemesterColor(), getString(R.string.semester_gpa)));
        View cumulative = Ui.legend(requireContext(), lineChart.getCumulativeColor(), getString(R.string.cumulative_gpa));
        cumulative.setPadding(Ui.dp(requireContext(), 16), 0, 0, 0);
        legend.addView(cumulative);

        AppViewModel vm = new ViewModelProvider(requireActivity()).get(AppViewModel.class);
        observeWhenVisible(vm.getData(), this::render);
    }

    /** Đã đăng nhập: lời chào dưới tiêu đề và ảnh đại diện thay cho icon tài khoản. */
    private void showAccount(MaterialToolbar toolbar, AccountViewModel accountVm) {
        UserProfile profile = accountVm.getAccount().getValue();
        toolbar.setSubtitle(profile == null ? null : getString(R.string.greeting, profile.displayName()));
        // Context của thanh tiêu đề để icon mặc định được tô trắng như các icon khác trên nền đỏ
        toolbar.getMenu().findItem(R.id.action_account).setIcon(AccountUi.avatar(toolbar.getContext(),
                accountVm.getAvatar().getValue(), profile, TOOLBAR_AVATAR_DP, true));
    }

    private void showThemeDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.theme_title)
                .setSingleChoiceItems(R.array.theme_options, ThemeSettings.selectedIndex(requireContext()), (d, which) -> {
                    d.dismiss();
                    ThemeSettings.select(requireContext(), which);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** GPA hệ 4 là con số quan trọng nhất nên thẻ của nó có nền đỏ chuyển màu, chữ trắng. */
    private void highlight(int statId) {
        MaterialCardView card = view.findViewById(statId);
        card.setStrokeColor(Color.TRANSPARENT);
        // MaterialCardView cắt nội dung theo góc bo, nên đặt nền chuyển màu cho khối nội dung bên trong
        card.getChildAt(0).setBackgroundResource(R.drawable.bg_hero);
        ((TextView) card.findViewById(R.id.stat_title)).setTextColor(Color.WHITE);
        ((TextView) card.findViewById(R.id.stat_value)).setTextColor(Color.WHITE);
        ((TextView) card.findViewById(R.id.stat_note)).setTextColor(Color.WHITE);
    }

    private void render(AcademicData data) {
        GpaCalculator.Summary total = GpaCalculator.cumulative(data.semesters);
        List<Course> best = GpaCalculator.bestCourses(data.semesters);
        int failed = 0;
        for (Course c : best) if (GpaCalculator.courseGrade(c).point == 0.0) failed++;
        int inProgress = 0;
        for (Semester s : data.semesters) {
            for (Course c : s.courses) if (GpaCalculator.status(c) == CourseStatus.IN_PROGRESS) inProgress++;
        }
        boolean hasGpa = total.credits > 0;

        stat(R.id.stat_gpa4, getString(R.string.stat_gpa4_title), hasGpa ? Format.fmt(total.gpa4) : getString(R.string.no_value),
                hasGpa ? getString(R.string.classification, GpaCalculator.classify(total.gpa4).label)
                        : getString(R.string.no_score_yet), -1);
        stat(R.id.stat_gpa10, getString(R.string.stat_gpa10_title), hasGpa ? Format.fmt(total.gpa10) : getString(R.string.no_value),
                getString(R.string.stat_gpa10_note), -1);
        int progress = GpaCalculator.progressPercent(total.credits, data.totalProgramCredits);
        stat(R.id.stat_credits, getString(R.string.stat_credits_title),
                getString(R.string.credits_fraction, total.credits, data.totalProgramCredits),
                getString(R.string.stat_credits_note, progress), progress);
        String debt = failed > 0 ? getString(R.string.failed_count, failed) : getString(R.string.no_failed);
        stat(R.id.stat_passed, getString(R.string.stat_passed_title), String.valueOf(best.size() - failed),
                getString(R.string.stat_passed_note, debt, inProgress), -1);

        // Biểu đồ đường: các kỳ đã có điểm
        List<String> labels = new ArrayList<>();
        List<Double> semesterGpa = new ArrayList<>();
        List<Double> cumulativeGpa = new ArrayList<>();
        List<String> points = new ArrayList<>();
        for (int i = 0; i < data.semesters.size(); i++) {
            GpaCalculator.Summary sem = GpaCalculator.semesterSummary(data.semesters.get(i));
            if (sem.credits == 0) continue;
            String label = data.semesters.get(i).name
                    .replace(getString(R.string.semester_word), getString(R.string.semester_short));
            labels.add(label);
            semesterGpa.add(sem.gpa4);
            cumulativeGpa.add(GpaCalculator.cumulative(data.semesters.subList(0, i + 1)).gpa4);
            points.add(getString(R.string.chart_line_item, label, Format.fmt(sem.gpa4)));
        }
        boolean noLine = labels.isEmpty();
        view.findViewById(R.id.line_empty).setVisibility(noLine ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.line_chart).setVisibility(noLine ? View.GONE : View.VISIBLE);
        view.findViewById(R.id.line_legend).setVisibility(noLine ? View.GONE : View.VISIBLE);
        LineChartView lineChart = view.findViewById(R.id.line_chart);
        lineChart.setData(labels, toArray(semesterGpa), toArray(cumulativeGpa));
        // Screen readers cannot read a Canvas drawing, so describe the data instead
        lineChart.setContentDescription(noLine ? null : getString(R.string.chart_line_description,
                Format.join(", ", points), Format.fmt(cumulativeGpa.get(cumulativeGpa.size() - 1))));

        // Biểu đồ tròn: số môn theo điểm chữ (lần học tốt nhất của mỗi môn)
        List<String> letters = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        int sum = 0;
        for (LetterGrade grade : GpaCalculator.SCALE) {
            int n = 0;
            for (Course c : best) if (GpaCalculator.courseGrade(c).letter.equals(grade.letter)) n++;
            if (n == 0) continue;
            letters.add(grade.letter);
            counts.add(n);
            sum += n;
        }
        int[] countArray = new int[counts.size()];
        for (int i = 0; i < countArray.length; i++) countArray[i] = counts.get(i);
        view.findViewById(R.id.pie_empty).setVisibility(sum == 0 ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.pie_row).setVisibility(sum == 0 ? View.GONE : View.VISIBLE);
        PieChartView pieChart = view.findViewById(R.id.pie_chart);
        pieChart.setData(letters, countArray, String.valueOf(sum), getString(R.string.course_unit));
        LinearLayout pieLegend = view.findViewById(R.id.pie_legend);
        pieLegend.removeAllViews();
        List<String> slices = new ArrayList<>();
        for (int i = 0; i < letters.size(); i++) {
            String letter = letters.get(i);
            int n = countArray[i];
            String item = getString(R.string.pie_legend_item, letter, n, Math.round(n * 100.0 / sum));
            slices.add(item);
            Ui.add(pieLegend, Ui.legend(requireContext(), Ui.gradeColor(letter), item), 4);
        }
        pieChart.setContentDescription(sum == 0 ? null
                : getString(R.string.chart_pie_description, Format.join(", ", slices)));
    }

    private void stat(int id, String title, String value, String note, int progress) {
        View card = view.findViewById(id);
        ((TextView) card.findViewById(R.id.stat_title)).setText(title);
        ((TextView) card.findViewById(R.id.stat_value)).setText(value);
        ((TextView) card.findViewById(R.id.stat_note)).setText(note);
        LinearProgressIndicator bar = card.findViewById(R.id.stat_progress);
        bar.setVisibility(progress >= 0 ? View.VISIBLE : View.GONE);
        if (progress >= 0) bar.setProgressCompat(progress, true);
    }

    /** Mỗi lần quay lại tab Tổng quan thì biểu đồ chạy lại hiệu ứng vẽ. */
    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden || view == null) return;
        ((LineChartView) view.findViewById(R.id.line_chart)).animateIn();
        ((PieChartView) view.findViewById(R.id.pie_chart)).animateIn();
    }

    private static double[] toArray(List<Double> list) {
        double[] a = new double[list.size()];
        for (int i = 0; i < a.length; i++) a[i] = list.get(i);
        return a;
    }
}
