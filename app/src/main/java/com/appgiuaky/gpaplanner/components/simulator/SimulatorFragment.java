package com.appgiuaky.gpaplanner.components.simulator;

import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.appgiuaky.gpaplanner.AppViewModel;
import com.appgiuaky.gpaplanner.R;
import com.appgiuaky.gpaplanner.components.Ui;
import com.appgiuaky.gpaplanner.types.AcademicData;
import com.appgiuaky.gpaplanner.types.Classification;
import com.appgiuaky.gpaplanner.types.Course;
import com.appgiuaky.gpaplanner.types.CourseStatus;
import com.appgiuaky.gpaplanner.types.GradeComponent;
import com.appgiuaky.gpaplanner.types.LetterGrade;
import com.appgiuaky.gpaplanner.types.Semester;
import com.appgiuaky.gpaplanner.utils.Format;
import com.appgiuaky.gpaplanner.utils.GpaCalculator;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.divider.MaterialDivider;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Chức năng 2 & 3: lập kế hoạch đạt GPA mục tiêu, máy tính "Cứu môn" và giả lập học cải thiện. */
public class SimulatorFragment extends Fragment {

    private static final int GAP = 12;
    private static final int TITLE_MEDIUM = com.google.android.material.R.attr.textAppearanceTitleMedium;
    private static final int TITLE_SMALL = com.google.android.material.R.attr.textAppearanceTitleSmall;
    private static final int BODY_MEDIUM = com.google.android.material.R.attr.textAppearanceBodyMedium;
    private static final int BODY_SMALL = com.google.android.material.R.attr.textAppearanceBodySmall;
    private static final int ON_SURFACE_VARIANT = com.google.android.material.R.attr.colorOnSurfaceVariant;
    private static final int ERROR = androidx.appcompat.R.attr.colorError;
    private static final int PRIMARY = androidx.appcompat.R.attr.colorPrimary;

    /** Các mức xếp loại có thể chọn làm mục tiêu (Trung bình trở lên). */
    private static final List<Classification> TARGETS = new ArrayList<>();

    static {
        for (Classification c : Classification.values()) {
            if (c.minGpa >= Classification.AVERAGE.minGpa) TARGETS.add(c);
        }
    }

    private AppViewModel vm;
    private View view;
    private EditText targetInput;
    private EditText creditsInput;
    private boolean inputsInitialized;
    private String rescueCourseId;
    /** Điểm dự kiến khi học cải thiện: id môn -> điểm chữ. */
    private final Map<String, LetterGrade> improvements = new HashMap<>();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_simulator, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        this.view = view;
        vm = new ViewModelProvider(requireActivity()).get(AppViewModel.class);
        targetInput = view.findViewById(R.id.target_input);
        creditsInput = view.findViewById(R.id.credits_input);

        ChipGroup chips = view.findViewById(R.id.target_chips);
        for (Classification c : TARGETS) {
            Chip chip = Ui.filterChip(chips, c.label + " " + Format.fmt(c.minGpa, 1));
            chip.setOnClickListener(v -> targetInput.setText(Format.plain(c.minGpa)));
            chips.addView(chip);
        }
        targetInput.addTextChangedListener(new Ui.SimpleWatcher(this::renderTarget));
        creditsInput.addTextChangedListener(new Ui.SimpleWatcher(this::renderTarget));

        TabLayout tabs = view.findViewById(R.id.tabs);
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                showSection(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        vm.getData().observe(getViewLifecycleOwner(), data -> {
            if (!inputsInitialized) initInputs(data);
            renderTarget();
            renderRescue();
            renderImprove();
        });
    }

    private void showSection(int index) {
        view.findViewById(R.id.section_target).setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.section_rescue).setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.section_improve).setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        ((NestedScrollView) view.findViewById(R.id.scroll)).scrollTo(0, 0);
    }

    /** Giá trị mặc định: mức xếp loại kế tiếp cao hơn GPA hiện tại, và số tín chỉ còn lại của chương trình. */
    private void initInputs(AcademicData data) {
        inputsInitialized = true;
        GpaCalculator.Summary current = GpaCalculator.cumulative(data.semesters);
        Classification target = TARGETS.get(0);
        for (Classification c : TARGETS) if (c.minGpa > current.gpa4) target = c;
        targetInput.setText(Format.plain(target.minGpa));
        creditsInput.setText(String.valueOf(Math.max(0, data.totalProgramCredits - current.credits)));
    }

    private Context ctx() {
        return requireContext();
    }

    private LinearLayout gradeLine(LetterGrade grade, String text) {
        LinearLayout row = new LinearLayout(ctx());
        row.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout badgeBox = new FrameLayout(ctx());
        badgeBox.addView(Ui.badge(ctx(), grade.letter), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(badgeBox, new LinearLayout.LayoutParams(Ui.dp(ctx(), 48), ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(Ui.text(ctx(), text, BODY_MEDIUM),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    // ---------------------------------------------------------------- Mục tiêu GPA

    private void renderTarget() {
        if (view == null || vm.current() == null) return;
        AcademicData data = vm.current();
        GpaCalculator.Summary current = GpaCalculator.cumulative(data.semesters);
        int remainingCredits = Math.max(0, data.totalProgramCredits - current.credits);

        ((TextView) view.findViewById(R.id.target_current)).setText(current.credits > 0
                ? "GPA tích lũy " + Format.fmt(current.gpa4) + " (" + GpaCalculator.classify(current.gpa4).label
                + ") trên " + current.credits + "/" + data.totalProgramCredits + " tín chỉ"
                : "Chưa có môn nào được tính GPA");

        Double target = Format.parseNumber(targetInput.getText().toString());
        if (target != null && (target <= 0 || target > 4)) target = null;
        Integer credits = Format.parseInt(creditsInput.getText().toString());
        if (credits != null && credits <= 0) credits = null;

        TextInputLayout targetLayout = view.findViewById(R.id.target_input_layout);
        targetLayout.setError(target == null ? "Nhập GPA từ 0 đến 4" : null);
        TextInputLayout creditsLayout = view.findViewById(R.id.credits_input_layout);
        creditsLayout.setHelperText("Số tín chỉ còn lại của chương trình: " + remainingCredits);
        creditsLayout.setError(credits == null ? "Nhập số tín chỉ lớn hơn 0" : null);

        ChipGroup chips = view.findViewById(R.id.target_chips);
        for (int i = 0; i < TARGETS.size(); i++) {
            ((Chip) chips.getChildAt(i)).setChecked(target != null && target == TARGETS.get(i).minGpa);
        }

        LinearLayout result = view.findViewById(R.id.target_result);
        result.removeAllViews();
        if (target == null || credits == null) return;

        GpaCalculator.TargetPlan plan = GpaCalculator.planTarget(current, target, credits);
        LetterGrade uniform = plan.uniformGrade;
        LinearLayout card = Ui.card(result, 0);
        Ui.add(card, Ui.text(ctx(), "Kết quả", TITLE_MEDIUM), 0);
        if (uniform == null) {
            Ui.add(card, Ui.text(ctx(), "Không thể đạt GPA " + Format.fmt(target) + " với " + credits
                    + " tín chỉ: kể cả đạt A tất cả, GPA tích lũy cao nhất chỉ là " + Format.fmt(plan.maxReachable)
                    + " (" + GpaCalculator.classify(plan.maxReachable).label + ").", BODY_MEDIUM, ERROR), 8);
            return;
        }
        if (uniform == GpaCalculator.PASSING.get(GpaCalculator.PASSING.size() - 1)) {
            Ui.add(card, Ui.text(ctx(), "Chỉ cần qua tất cả các môn (từ D trở lên) là đạt mục tiêu.", BODY_MEDIUM), 8);
        } else {
            Ui.add(card, Ui.text(ctx(), "GPA trung bình cần đạt cho " + credits + " tín chỉ sắp học:", BODY_MEDIUM), 8);
            TextView value = Ui.text(ctx(), Format.fmt(plan.requiredAvg),
                    com.google.android.material.R.attr.textAppearanceHeadlineMedium);
            value.setTypeface(value.getTypeface(), android.graphics.Typeface.BOLD);
            Ui.add(card, value, 0);
        }
        Ui.add(card, new MaterialDivider(ctx()), 8);
        Ui.add(card, Ui.text(ctx(), "Phương án 1: mọi môn cùng một mức", TITLE_SMALL), 8);
        Ui.add(card, gradeLine(uniform, "Tất cả " + credits + " TC đạt từ " + uniform.letter
                + " (điểm hệ 10 ≥ " + Format.plain(uniform.minScore) + ")"), 8);
        GpaCalculator.Mix mix = plan.mix;
        if (mix != null) {
            Ui.add(card, Ui.text(ctx(), "Phương án 2: kết hợp hai mức", TITLE_SMALL), 8);
            Ui.add(card, gradeLine(mix.high, mix.highCredits + " TC đạt từ " + mix.high.letter
                    + " (≥ " + Format.plain(mix.high.minScore) + ")"), 8);
            Ui.add(card, gradeLine(mix.low, mix.lowCredits + " TC còn lại đạt từ " + mix.low.letter
                    + " (≥ " + Format.plain(mix.low.minScore) + ")"), 8);
        }
        Ui.add(card, new MaterialDivider(ctx()), 8);
        Ui.add(card, Ui.text(ctx(), "Nếu đạt A tất cả, GPA tích lũy tối đa là " + Format.fmt(plan.maxReachable) + ".",
                BODY_SMALL, ON_SURFACE_VARIANT), 8);
    }

    // ---------------------------------------------------------------- Cứu môn

    private void renderRescue() {
        List<Course> pending = new ArrayList<>();
        for (Semester s : vm.current().semesters) {
            for (Course c : s.courses) if (GpaCalculator.status(c) != CourseStatus.COMPLETED) pending.add(c);
        }
        boolean none = pending.isEmpty();
        view.findViewById(R.id.rescue_empty).setVisibility(none ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.rescue_label).setVisibility(none ? View.GONE : View.VISIBLE);
        ChipGroup chips = view.findViewById(R.id.rescue_chips);
        chips.removeAllViews();
        LinearLayout content = view.findViewById(R.id.rescue_content);
        content.removeAllViews();
        if (none) return;

        Course course = pending.get(0);
        for (Course c : pending) if (c.id.equals(rescueCourseId)) course = c;
        for (Course c : pending) {
            Chip chip = Ui.filterChip(chips, c.name);
            chip.setChecked(c == course);
            chip.setOnClickListener(v -> {
                rescueCourseId = c.id;
                renderRescue();
            });
            chips.addView(chip);
        }

        LinearLayout info = Ui.card(content, 0);
        Ui.add(info, Ui.text(ctx(), course.name, TITLE_MEDIUM), 0);
        Ui.add(info, Ui.text(ctx(), course.code + " • " + course.credits + " TC", BODY_SMALL, ON_SURFACE_VARIANT), 0);
        List<String> remaining = new ArrayList<>();
        for (GradeComponent g : course.components) {
            Ui.add(info, Ui.text(ctx(), g.name + " (" + Format.plain(g.weight) + "%): "
                    + (g.score == null ? "chưa có" : Format.fmt(g.score, 1)), BODY_MEDIUM), 4);
            if (g.score == null) remaining.add(g.name + " (" + Format.plain(g.weight) + "%)");
        }

        LinearLayout table = Ui.card(content, GAP);
        Ui.add(table, Ui.text(ctx(), "Điểm trung bình cần đạt ở: " + Format.join(", ", remaining), TITLE_SMALL), 0);
        for (GpaCalculator.RescueRow row : GpaCalculator.rescue(course)) {
            LinearLayout line = gradeLine(row.grade, "Điểm HP ≥ " + Format.plain(row.grade.minScore));
            String need;
            int color;
            if (row.need == null) {
                need = "Không thể";
                color = ERROR;
            } else if (row.need == 0.0) {
                need = "Chắc chắn đạt";
                color = PRIMARY;
            } else {
                need = "Cần ≥ " + Format.fmt(row.need, 1);
                color = com.google.android.material.R.attr.colorOnSurface;
            }
            line.addView(Ui.text(ctx(), need, TITLE_SMALL, color));
            Ui.add(table, line, 8);
        }
    }

    // ---------------------------------------------------------------- Học cải thiện

    private void renderImprove() {
        List<Semester> semesters = vm.current().semesters;
        GpaCalculator.Summary current = GpaCalculator.cumulative(semesters);
        GpaCalculator.Summary simulated = GpaCalculator.cumulative(semesters, improvements);

        // Môn chưa đạt A, xếp theo mức tăng GPA tối đa nếu học lại được A
        List<Course> candidates = new ArrayList<>();
        for (Course c : GpaCalculator.bestCourses(semesters)) {
            if (GpaCalculator.courseGrade(c).point < 4.0) candidates.add(c);
        }
        Collections.sort(candidates, (a, b) -> Double.compare(gain(b), gain(a)));

        LinearLayout section = view.findViewById(R.id.section_improve);
        section.removeAllViews();

        LinearLayout summary = Ui.card(section, 0);
        LinearLayout header = new LinearLayout(ctx());
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(Ui.text(ctx(), "GPA tích lũy giả lập", TITLE_MEDIUM),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (!improvements.isEmpty()) {
            MaterialButton reset = new MaterialButton(ctx(), null, androidx.appcompat.R.attr.borderlessButtonStyle);
            reset.setText("Đặt lại");
            reset.setOnClickListener(v -> {
                improvements.clear();
                renderImprove();
            });
            header.addView(reset);
        }
        Ui.add(summary, header, 0);

        LinearLayout values = new LinearLayout(ctx());
        values.setGravity(Gravity.CENTER_VERTICAL);
        values.addView(Ui.text(ctx(), Format.fmt(current.gpa4), com.google.android.material.R.attr.textAppearanceHeadlineSmall));
        ImageView arrow = new ImageView(ctx());
        arrow.setImageResource(R.drawable.ic_arrow_forward);
        LinearLayout.LayoutParams arrowLp = new LinearLayout.LayoutParams(Ui.dp(ctx(), 20), Ui.dp(ctx(), 20));
        arrowLp.setMargins(Ui.dp(ctx(), 8), 0, Ui.dp(ctx(), 8), 0);
        values.addView(arrow, arrowLp);
        TextView sim = Ui.text(ctx(), Format.fmt(simulated.gpa4), com.google.android.material.R.attr.textAppearanceHeadlineMedium);
        sim.setTypeface(sim.getTypeface(), android.graphics.Typeface.BOLD);
        values.addView(sim);
        double delta = simulated.gpa4 - current.gpa4;
        if (delta > 0.00001) {
            TextView deltaText = Ui.text(ctx(), "+" + Format.fmt(delta), TITLE_MEDIUM, PRIMARY);
            deltaText.setPadding(Ui.dp(ctx(), 8), 0, 0, 0);
            values.addView(deltaText);
        }
        Ui.add(summary, values, 0);
        Ui.add(summary, Ui.text(ctx(), "Xếp loại: " + GpaCalculator.classify(current.gpa4).label + " → "
                + GpaCalculator.classify(simulated.gpa4).label + " • " + simulated.credits + " TC tích lũy", BODY_SMALL), 0);

        if (candidates.isEmpty()) {
            Ui.add(section, Ui.text(ctx(), "Không có môn nào để cải thiện (mọi môn đã đạt A hoặc chưa có điểm tổng kết).",
                    BODY_MEDIUM), GAP);
            return;
        }
        Ui.add(section, Ui.text(ctx(), "Chọn điểm dự kiến nếu học lại/cải thiện. Môn ở trên cùng giúp tăng GPA nhiều nhất.",
                BODY_SMALL, ON_SURFACE_VARIANT), GAP);
        LinearLayout list = Ui.card(section, GAP);
        list.setPadding(0, Ui.dp(ctx(), 4), 0, Ui.dp(ctx(), 4));
        LayoutInflater inflater = LayoutInflater.from(ctx());
        for (int i = 0; i < candidates.size(); i++) {
            if (i > 0) list.addView(new MaterialDivider(ctx()));
            list.addView(improvementRow(inflater, list, candidates.get(i)));
        }
    }

    private static double gain(Course c) {
        return (4.0 - GpaCalculator.courseGrade(c).point) * c.credits;
    }

    private View improvementRow(LayoutInflater inflater, ViewGroup parent, Course course) {
        LetterGrade current = GpaCalculator.courseGrade(course);
        LetterGrade selected = improvements.get(course.id);
        View row = inflater.inflate(R.layout.item_improvement, parent, false);
        ((TextView) row.findViewById(R.id.improve_name)).setText(course.name);
        ((TextView) row.findViewById(R.id.improve_meta)).setText(course.code + " • " + course.credits + " TC");
        ((FrameLayout) row.findViewById(R.id.improve_current)).addView(Ui.badge(ctx(), current.letter));

        MaterialButton choose = row.findViewById(R.id.improve_choose);
        choose.setText(selected != null ? selected.letter : "Giữ");
        choose.setContentDescription("Chọn điểm dự kiến cho " + course.name);
        choose.setOnClickListener(v -> {
            PopupMenu menu = new PopupMenu(ctx(), choose);
            menu.getMenu().add(0, 0, 0, "Giữ nguyên");
            List<LetterGrade> options = new ArrayList<>();
            for (LetterGrade g : GpaCalculator.PASSING) {
                if (g.point > current.point) {
                    options.add(g);
                    menu.getMenu().add(0, options.size(), options.size(), g.letter + " (≥ " + Format.plain(g.minScore) + ")");
                }
            }
            menu.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 0) improvements.remove(course.id);
                else improvements.put(course.id, options.get(item.getItemId() - 1));
                renderImprove();
                return true;
            });
            menu.show();
        });
        return row;
    }
}
