package com.appgiuaky.gpaplanner.components.curriculum;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
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
import com.appgiuaky.gpaplanner.types.Semester;
import com.appgiuaky.gpaplanner.utils.Format;
import com.appgiuaky.gpaplanner.utils.GpaCalculator;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.HashSet;
import java.util.Set;

/** Chức năng 5: cây chương trình đào tạo theo học kỳ và tiến độ tích lũy tín chỉ. */
public class CurriculumFragment extends Fragment {

    /** Trạng thái của một môn trên cây chương trình. */
    private enum NodeState {
        PASSED("Đã đạt"),
        FAILED("Nợ môn"),
        /** Lần học đã được thay bằng một lần học lại/cải thiện có điểm cao hơn. */
        REPLACED("Đã học lại"),
        IN_PROGRESS("Đang học"),
        PLANNED("Chưa học");

        final String label;

        NodeState(String label) {
            this.label = label;
        }
    }

    private AppViewModel vm;
    private View view;
    /** Các học kỳ đang thu gọn (mặc định mở hết). */
    private final Set<String> collapsed = new HashSet<>();
    private Set<String> bestIds = new HashSet<>();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_curriculum, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        this.view = view;
        vm = new ViewModelProvider(requireActivity()).get(AppViewModel.class);
        view.findViewById(R.id.progress_bar).setClipToOutline(true);

        MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.inflateMenu(R.menu.menu_curriculum);
        toolbar.setOnMenuItemClickListener(item -> {
            Ui.inputDialog(requireContext(), "Tổng tín chỉ chương trình", "Số tín chỉ",
                    String.valueOf(vm.current().totalProgramCredits), InputType.TYPE_CLASS_NUMBER,
                    "Số tín chỉ cần tích lũy để tốt nghiệp (1 - 500)",
                    s -> {
                        Integer n = Format.parseInt(s);
                        return n != null && n >= 1 && n <= 500;
                    },
                    s -> vm.setProgramCredits(Format.parseInt(s)));
            return true;
        });

        vm.getData().observe(getViewLifecycleOwner(), this::render);
    }

    private NodeState stateOf(Course c) {
        CourseStatus status = GpaCalculator.status(c);
        if (status == CourseStatus.IN_PROGRESS) return NodeState.IN_PROGRESS;
        if (status == CourseStatus.PLANNED) return NodeState.PLANNED;
        if (c.countInGpa && !bestIds.contains(c.id)) return NodeState.REPLACED;
        return GpaCalculator.courseGrade(c).point > 0 ? NodeState.PASSED : NodeState.FAILED;
    }

    private int colorOf(NodeState state) {
        switch (state) {
            case PASSED: return Ui.gradeColor("A");
            case FAILED: return Ui.color(requireContext(), androidx.appcompat.R.attr.colorError);
            case IN_PROGRESS: return Ui.color(requireContext(), com.google.android.material.R.attr.colorTertiary);
            default: return Ui.color(requireContext(), com.google.android.material.R.attr.colorOutline);
        }
    }

    private void render(AcademicData data) {
        bestIds = new HashSet<>();
        for (Course c : GpaCalculator.bestCourses(data.semesters)) bestIds.add(c.id);
        GpaCalculator.Summary total = GpaCalculator.cumulative(data.semesters);

        int inProgress = 0;
        int planned = 0;
        int failed = 0;
        for (Semester s : data.semesters) {
            for (Course c : s.courses) {
                if (!c.countInGpa) continue;
                NodeState state = stateOf(c);
                if (state == NodeState.IN_PROGRESS) inProgress += c.credits;
                else if (state == NodeState.PLANNED) planned += c.credits;
                else if (state == NodeState.FAILED) failed++;
            }
        }
        int unscheduled = Math.max(0, data.totalProgramCredits - total.credits - inProgress - planned);

        ((TextView) view.findViewById(R.id.progress_text)).setText(total.credits + "/" + data.totalProgramCredits
                + " tín chỉ tích lũy (" + (total.credits * 100 / data.totalProgramCredits) + "%)"
                + (failed > 0 ? " • Nợ " + failed + " môn" : ""));

        String[] labels = {"Đã tích lũy", "Đang học", "Chưa học", "Chưa xếp kỳ"};
        int[] credits = {total.credits, inProgress, planned, unscheduled};
        int[] colors = {
                colorOf(NodeState.PASSED),
                colorOf(NodeState.IN_PROGRESS),
                Ui.color(requireContext(), androidx.appcompat.R.attr.colorPrimary),
                Ui.color(requireContext(), com.google.android.material.R.attr.colorOutlineVariant),
        };
        LinearLayout bar = view.findViewById(R.id.progress_bar);
        LinearLayout legend = view.findViewById(R.id.legend);
        bar.removeAllViews();
        legend.removeAllViews();
        for (int i = 0; i < labels.length; i++) {
            if (credits[i] > 0) {
                View segment = new View(requireContext());
                segment.setBackgroundColor(colors[i]);
                bar.addView(segment, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, credits[i]));
            }
            Ui.add(legend, Ui.legend(requireContext(), colors[i], labels[i] + ": " + credits[i] + " TC"), 4);
        }

        view.findViewById(R.id.empty).setVisibility(data.semesters.isEmpty() ? View.VISIBLE : View.GONE);
        LinearLayout semesters = view.findViewById(R.id.semesters);
        semesters.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (Semester semester : data.semesters) {
            View node = inflater.inflate(R.layout.item_tree_semester, semesters, false);
            Ui.add(semesters, node, 12);
            bindSemester(node, semester, inflater);
        }
    }

    private void bindSemester(View node, Semester semester, LayoutInflater inflater) {
        GpaCalculator.Summary summary = GpaCalculator.semesterSummary(semester);
        int credits = 0;
        int done = 0;
        for (Course c : semester.courses) {
            credits += c.credits;
            if (GpaCalculator.status(c) == CourseStatus.COMPLETED) done++;
        }
        ((TextView) node.findViewById(R.id.tree_name)).setText(semester.name);
        ((TextView) node.findViewById(R.id.tree_summary)).setText(credits + " TC • " + done + "/" + semester.courses.size()
                + " môn có điểm" + (summary.credits > 0 ? " • GPA kỳ " + Format.fmt(summary.gpa4) : ""));

        LinearLayout courses = node.findViewById(R.id.tree_courses);
        ImageView expand = node.findViewById(R.id.tree_expand);
        Runnable applyExpanded = () -> {
            boolean expanded = !collapsed.contains(semester.id);
            courses.setVisibility(expanded ? View.VISIBLE : View.GONE);
            expand.setImageResource(expanded ? R.drawable.ic_expand_less : R.drawable.ic_expand_more);
            expand.setContentDescription(expanded ? "Thu gọn" : "Mở rộng");
        };
        applyExpanded.run();
        node.findViewById(R.id.tree_header).setOnClickListener(v -> {
            if (!collapsed.remove(semester.id)) collapsed.add(semester.id);
            applyExpanded.run();
        });

        for (int i = 0; i < semester.courses.size(); i++) {
            Course course = semester.courses.get(i);
            NodeState state = stateOf(course);
            int color = colorOf(state);
            View row = inflater.inflate(R.layout.item_tree_course, courses, false);
            ((TreeLineView) row.findViewById(R.id.tree_line)).setLast(i == semester.courses.size() - 1);
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(color);
            row.findViewById(R.id.tree_dot).setBackground(dot);
            ((TextView) row.findViewById(R.id.tree_course_name)).setText(course.name);
            TextView meta = row.findViewById(R.id.tree_course_meta);
            meta.setText(course.code + " • " + course.credits + " TC • "
                    + (course.countInGpa ? "" : "Không tính GPA • ") + state.label);
            meta.setTextColor(color);
            Double score = GpaCalculator.courseScore(course);
            if (score != null) {
                ((FrameLayout) row.findViewById(R.id.tree_badge))
                        .addView(Ui.badge(requireContext(), GpaCalculator.letterOf(score).letter));
            }
            courses.addView(row);
        }
    }
}
