package com.appgiuaky.gpaplanner.components.dashboard;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.appgiuaky.gpaplanner.AppViewModel;
import com.appgiuaky.gpaplanner.R;
import com.appgiuaky.gpaplanner.components.Ui;
import com.appgiuaky.gpaplanner.types.AcademicData;
import com.appgiuaky.gpaplanner.types.Course;
import com.appgiuaky.gpaplanner.types.CourseStatus;
import com.appgiuaky.gpaplanner.types.LetterGrade;
import com.appgiuaky.gpaplanner.types.Semester;
import com.appgiuaky.gpaplanner.utils.Format;
import com.appgiuaky.gpaplanner.utils.GpaCalculator;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.List;

/** Chức năng 4: thẻ thống kê, biểu đồ đường GPA theo kỳ và biểu đồ tròn phân bố điểm chữ. */
public class DashboardFragment extends Fragment {

    private View view;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        this.view = view;
        LinearLayout legend = view.findViewById(R.id.line_legend);
        legend.addView(Ui.legend(requireContext(), LineChartView.SEMESTER_COLOR, "GPA học kỳ"));
        View cumulative = Ui.legend(requireContext(), LineChartView.CUMULATIVE_COLOR, "GPA tích lũy");
        cumulative.setPadding(Ui.dp(requireContext(), 16), 0, 0, 0);
        legend.addView(cumulative);

        AppViewModel vm = new ViewModelProvider(requireActivity()).get(AppViewModel.class);
        vm.getData().observe(getViewLifecycleOwner(), this::render);
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

        stat(R.id.stat_gpa4, "GPA tích lũy (hệ 4)", hasGpa ? Format.fmt(total.gpa4) : "—",
                hasGpa ? "Xếp loại: " + GpaCalculator.classify(total.gpa4).label : "Chưa có điểm", -1);
        stat(R.id.stat_gpa10, "Điểm TB hệ 10", hasGpa ? Format.fmt(total.gpa10) : "—", "Trung bình theo tín chỉ", -1);
        int progress = Math.min(100, total.credits * 100 / data.totalProgramCredits);
        stat(R.id.stat_credits, "Tín chỉ tích lũy", total.credits + "/" + data.totalProgramCredits,
                progress + "% chương trình", progress);
        stat(R.id.stat_passed, "Môn đã qua", String.valueOf(best.size() - failed),
                (failed > 0 ? "Nợ " + failed + " môn" : "Không nợ môn") + " • Đang học " + inProgress, -1);

        // Biểu đồ đường: các kỳ đã có điểm
        List<String> labels = new ArrayList<>();
        List<Double> semesterGpa = new ArrayList<>();
        List<Double> cumulativeGpa = new ArrayList<>();
        for (int i = 0; i < data.semesters.size(); i++) {
            GpaCalculator.Summary sem = GpaCalculator.semesterSummary(data.semesters.get(i));
            if (sem.credits == 0) continue;
            labels.add(data.semesters.get(i).name.replace("Học kỳ", "HK"));
            semesterGpa.add(sem.gpa4);
            cumulativeGpa.add(GpaCalculator.cumulative(data.semesters.subList(0, i + 1)).gpa4);
        }
        boolean noLine = labels.isEmpty();
        view.findViewById(R.id.line_empty).setVisibility(noLine ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.line_chart).setVisibility(noLine ? View.GONE : View.VISIBLE);
        view.findViewById(R.id.line_legend).setVisibility(noLine ? View.GONE : View.VISIBLE);
        ((LineChartView) view.findViewById(R.id.line_chart)).setData(labels, toArray(semesterGpa), toArray(cumulativeGpa));

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
        ((PieChartView) view.findViewById(R.id.pie_chart)).setData(letters, countArray);
        LinearLayout pieLegend = view.findViewById(R.id.pie_legend);
        pieLegend.removeAllViews();
        for (int i = 0; i < letters.size(); i++) {
            String letter = letters.get(i);
            int n = countArray[i];
            Ui.add(pieLegend, Ui.legend(requireContext(), Ui.gradeColor(letter),
                    letter + ": " + n + " môn (" + Math.round(n * 100.0 / sum) + "%)"), 4);
        }
    }

    private void stat(int id, String title, String value, String note, int progress) {
        View card = view.findViewById(id);
        ((TextView) card.findViewById(R.id.stat_title)).setText(title);
        ((TextView) card.findViewById(R.id.stat_value)).setText(value);
        ((TextView) card.findViewById(R.id.stat_note)).setText(note);
        LinearProgressIndicator bar = card.findViewById(R.id.stat_progress);
        bar.setVisibility(progress >= 0 ? View.VISIBLE : View.GONE);
        if (progress >= 0) bar.setProgress(progress);
    }

    private static double[] toArray(List<Double> list) {
        double[] a = new double[list.size()];
        for (int i = 0; i < a.length; i++) a[i] = list.get(i);
        return a;
    }
}
