package com.appgiuaky.gpaplanner.components.gradebook;

import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.appgiuaky.gpaplanner.AppViewModel;
import com.appgiuaky.gpaplanner.R;
import com.appgiuaky.gpaplanner.components.TabFragment;
import com.appgiuaky.gpaplanner.components.Ui;
import com.appgiuaky.gpaplanner.services.ExcelIO;
import com.appgiuaky.gpaplanner.types.AcademicData;
import com.appgiuaky.gpaplanner.types.Course;
import com.appgiuaky.gpaplanner.types.GradeComponent;
import com.appgiuaky.gpaplanner.types.Semester;
import com.appgiuaky.gpaplanner.utils.Format;
import com.appgiuaky.gpaplanner.utils.GpaCalculator;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.snackbar.Snackbar;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/** Chức năng 1 & 6: bảng điểm theo học kỳ (thêm/sửa/xóa), xuất/nhập file Excel. */
public class GradebookFragment extends TabFragment {

    private static final int MENU_RENAME = 1;
    private static final int MENU_DELETE = 2;
    /** Đủ lớn để hai đầu viên GPA tròn hẳn. */
    private static final int PILL_RADIUS_DP = 100;

    private AppViewModel vm;
    private LinearLayout semestersView;
    private View empty;

    private final ActivityResultLauncher<String> exportLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument(ExcelIO.MIME), this::exportTo);
    private final ActivityResultLauncher<String[]> importLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::importFrom);

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_gradebook, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        vm = new ViewModelProvider(requireActivity()).get(AppViewModel.class);
        semestersView = view.findViewById(R.id.semesters);
        empty = view.findViewById(R.id.empty);

        MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.inflateMenu(R.menu.menu_gradebook);
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_add_semester) {
                semesterNameDialog(getString(R.string.add_semester),
                        getString(R.string.semester_default_name, vm.current().semesters.size() + 1), vm::addSemester);
            } else if (id == R.id.action_export) {
                exportLauncher.launch(getString(R.string.export_file_name));
            } else if (id == R.id.action_import) {
                importLauncher.launch(new String[]{ExcelIO.MIME, "application/octet-stream"});
            }
            return true;
        });

        observeWhenVisible(vm.getData(), this::render);
    }

    private void toast(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
    }

    private void exportTo(Uri uri) {
        if (uri == null) return;
        try (OutputStream out = requireContext().getContentResolver().openOutputStream(uri)) {
            ExcelIO.export(vm.current().semesters, out);
            toast(getString(R.string.export_done));
        } catch (Exception e) {
            toast(getString(R.string.export_failed, e.getMessage()));
        }
    }

    private void importFrom(Uri uri) {
        if (uri == null) return;
        List<Semester> imported;
        try (InputStream in = requireContext().getContentResolver().openInputStream(uri)) {
            imported = ExcelIO.importFile(in);
        } catch (Exception e) {
            toast(getString(R.string.import_read_failed, e.getMessage()));
            return;
        }
        int courses = 0;
        for (Semester s : imported) courses += s.courses.size();
        Ui.confirm(requireContext(), getString(R.string.import_confirm_title),
                getString(R.string.import_confirm_message, imported.size(), courses),
                () -> {
                    vm.replaceSemesters(imported);
                    toast(getString(R.string.import_done));
                });
    }

    private void semesterNameDialog(String title, String initial, java.util.function.Consumer<String> onSave) {
        Ui.inputDialog(requireContext(), title, getString(R.string.semester_name_hint), initial,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, null,
                s -> !s.isEmpty(), onSave);
    }

    /** Deletes right away and offers Undo, which is faster than a confirmation dialog for one course. */
    private void deleteWithUndo(Semester semester, Course course) {
        int index = semester.courses.indexOf(course);
        vm.deleteCourse(semester.id, course.id);
        Snackbar.make(requireView(), getString(R.string.course_deleted, course.name), Snackbar.LENGTH_LONG)
                .setAnchorView(requireActivity().findViewById(R.id.bottom_nav))
                .setAction(R.string.undo, v -> {
                    if (!vm.restoreCourse(semester.id, index, course)) toast(getString(R.string.undo_failed));
                })
                .show();
    }

    private void render(AcademicData data) {
        empty.setVisibility(data.semesters.isEmpty() ? View.VISIBLE : View.GONE);
        semestersView.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (Semester semester : data.semesters) {
            View card = inflater.inflate(R.layout.item_semester, semestersView, false);
            Ui.add(semestersView, card, 12);
            bindSemester(card, semester, inflater);
        }
    }

    private void bindSemester(View card, Semester semester, LayoutInflater inflater) {
        GpaCalculator.Summary summary = GpaCalculator.semesterSummary(semester);
        int credits = 0;
        for (Course c : semester.courses) credits += c.credits;
        ((TextView) card.findViewById(R.id.semester_name)).setText(semester.name);
        boolean hasScore = summary.credits > 0;
        ((TextView) card.findViewById(R.id.semester_summary)).setText(hasScore
                ? getString(R.string.semester_credits, credits)
                : getString(R.string.semester_summary_no_score, credits));
        LinearLayout pills = card.findViewById(R.id.semester_pills);
        pills.setVisibility(hasScore ? View.VISIBLE : View.GONE);
        if (hasScore) {
            int primary = Ui.color(requireContext(), androidx.appcompat.R.attr.colorPrimary);
            int onPrimary = Ui.color(requireContext(), com.google.android.material.R.attr.colorOnPrimary);
            pills.addView(Ui.tag(requireContext(), getString(R.string.semester_pill_gpa, Format.fmt(summary.gpa4)),
                    primary, primary, onPrimary, PILL_RADIUS_DP));
            TextView gpa10 = Ui.tag(requireContext(), getString(R.string.semester_pill_gpa10, Format.fmt(summary.gpa10)),
                    Color.TRANSPARENT, primary, primary, PILL_RADIUS_DP);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginStart(Ui.dp(requireContext(), 6));
            pills.addView(gpa10, lp);
        }

        card.findViewById(R.id.add_course).setOnClickListener(v -> CourseDialog.show(
                requireContext(), null, course -> vm.saveCourse(semester.id, course), null));
        View menuButton = card.findViewById(R.id.semester_menu);
        menuButton.setOnClickListener(v -> {
            PopupMenu menu = new PopupMenu(requireContext(), menuButton);
            menu.getMenu().add(0, MENU_RENAME, 0, R.string.rename);
            menu.getMenu().add(0, MENU_DELETE, 1, R.string.delete_semester);
            menu.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == MENU_RENAME) {
                    semesterNameDialog(getString(R.string.rename_semester), semester.name,
                            name -> vm.renameSemester(semester.id, name));
                } else {
                    Ui.confirm(requireContext(), getString(R.string.delete_semester_title, semester.name),
                            getString(R.string.delete_semester_message, semester.courses.size()),
                            () -> vm.deleteSemester(semester.id));
                }
                return true;
            });
            menu.show();
        });

        card.findViewById(R.id.no_courses).setVisibility(semester.courses.isEmpty() ? View.VISIBLE : View.GONE);
        LinearLayout courses = card.findViewById(R.id.courses);
        for (Course course : semester.courses) {
            View row = inflater.inflate(R.layout.item_course, courses, false);
            bindCourse(row, course);
            row.setOnClickListener(v -> CourseDialog.show(requireContext(), course,
                    edited -> vm.saveCourse(semester.id, edited),
                    () -> deleteWithUndo(semester, course)));
            courses.addView(new com.google.android.material.divider.MaterialDivider(requireContext()));
            courses.addView(row);
        }
    }

    private void bindCourse(View row, Course course) {
        ((TextView) row.findViewById(R.id.course_name)).setText(course.name);
        ((TextView) row.findViewById(R.id.course_meta)).setText(Ui.courseMeta(requireContext(), course));
        List<String> parts = new ArrayList<>();
        for (GradeComponent g : course.components) {
            parts.add(getString(R.string.component_score, g.name,
                    g.score == null ? getString(R.string.no_value) : Format.fmt(g.score, 1)));
        }
        ((TextView) row.findViewById(R.id.course_components)).setText(Format.join("   ", parts));

        LinearLayout result = row.findViewById(R.id.course_result);
        Double score = GpaCalculator.courseScore(course);
        if (score != null) {
            result.addView(Ui.text(requireContext(), Format.fmt(score, 1),
                    com.google.android.material.R.attr.textAppearanceTitleMedium));
            result.addView(Ui.badge(requireContext(), GpaCalculator.letterOf(score).letter));
        } else {
            result.addView(Ui.text(requireContext(), GpaCalculator.status(course).label,
                    com.google.android.material.R.attr.textAppearanceLabelLarge,
                    com.google.android.material.R.attr.colorTertiary));
        }
    }
}
