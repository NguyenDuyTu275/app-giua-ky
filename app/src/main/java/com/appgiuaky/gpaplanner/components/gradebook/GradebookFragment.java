package com.appgiuaky.gpaplanner.components.gradebook;

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
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.appgiuaky.gpaplanner.AppViewModel;
import com.appgiuaky.gpaplanner.R;
import com.appgiuaky.gpaplanner.components.Ui;
import com.appgiuaky.gpaplanner.services.ExcelIO;
import com.appgiuaky.gpaplanner.types.AcademicData;
import com.appgiuaky.gpaplanner.types.Course;
import com.appgiuaky.gpaplanner.types.GradeComponent;
import com.appgiuaky.gpaplanner.types.Semester;
import com.appgiuaky.gpaplanner.utils.Format;
import com.appgiuaky.gpaplanner.utils.GpaCalculator;
import com.google.android.material.appbar.MaterialToolbar;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/** Chức năng 1 & 6: bảng điểm theo học kỳ (thêm/sửa/xóa), xuất/nhập file Excel. */
public class GradebookFragment extends Fragment {

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
                semesterNameDialog("Thêm học kỳ", "Học kỳ " + (vm.current().semesters.size() + 1), vm::addSemester);
            } else if (id == R.id.action_export) {
                exportLauncher.launch("bang_diem.xlsx");
            } else if (id == R.id.action_import) {
                importLauncher.launch(new String[]{ExcelIO.MIME, "application/octet-stream"});
            }
            return true;
        });

        vm.getData().observe(getViewLifecycleOwner(), this::render);
    }

    private void toast(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
    }

    private void exportTo(Uri uri) {
        if (uri == null) return;
        try (OutputStream out = requireContext().getContentResolver().openOutputStream(uri)) {
            ExcelIO.export(vm.current().semesters, out);
            toast("Đã xuất bảng điểm ra file Excel");
        } catch (Exception e) {
            toast("Xuất file thất bại: " + e.getMessage());
        }
    }

    private void importFrom(Uri uri) {
        if (uri == null) return;
        List<Semester> imported;
        try (InputStream in = requireContext().getContentResolver().openInputStream(uri)) {
            imported = ExcelIO.importFile(in);
        } catch (Exception e) {
            toast("Không đọc được file: " + e.getMessage());
            return;
        }
        int courses = 0;
        for (Semester s : imported) courses += s.courses.size();
        Ui.confirm(requireContext(), "Nhập bảng điểm từ Excel?",
                "Đọc được " + imported.size() + " học kỳ, " + courses + " môn. Bảng điểm hiện tại sẽ được thay thế.",
                () -> {
                    vm.replaceSemesters(imported);
                    toast("Đã nhập bảng điểm");
                });
    }

    private void semesterNameDialog(String title, String initial, java.util.function.Consumer<String> onSave) {
        Ui.inputDialog(requireContext(), title, "Tên học kỳ", initial,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, null,
                s -> !s.isEmpty(), onSave);
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
        ((TextView) card.findViewById(R.id.semester_summary)).setText(summary.credits > 0
                ? "GPA kỳ: " + Format.fmt(summary.gpa4) + " • Hệ 10: " + Format.fmt(summary.gpa10) + " • " + credits + " TC"
                : credits + " TC • chưa có điểm tổng kết");

        card.findViewById(R.id.add_course).setOnClickListener(v -> CourseDialog.show(
                requireContext(), null, course -> vm.saveCourse(semester.id, course), null));
        View menuButton = card.findViewById(R.id.semester_menu);
        menuButton.setOnClickListener(v -> {
            PopupMenu menu = new PopupMenu(requireContext(), menuButton);
            menu.getMenu().add("Đổi tên");
            menu.getMenu().add("Xóa học kỳ");
            menu.setOnMenuItemClickListener(item -> {
                if (item.getTitle().toString().equals("Đổi tên")) {
                    semesterNameDialog("Đổi tên học kỳ", semester.name, name -> vm.renameSemester(semester.id, name));
                } else {
                    Ui.confirm(requireContext(), "Xóa " + semester.name + "?",
                            "Toàn bộ " + semester.courses.size() + " môn trong học kỳ này sẽ bị xóa.",
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
                    () -> Ui.confirm(requireContext(), "Xóa môn " + course.name + "?",
                            "Điểm của môn này sẽ bị xóa khỏi bảng điểm.",
                            () -> vm.deleteCourse(semester.id, course.id))));
            courses.addView(new com.google.android.material.divider.MaterialDivider(requireContext()));
            courses.addView(row);
        }
    }

    private void bindCourse(View row, Course course) {
        ((TextView) row.findViewById(R.id.course_name)).setText(course.name);
        ((TextView) row.findViewById(R.id.course_meta)).setText(
                course.code + " • " + course.credits + " TC" + (course.countInGpa ? "" : " • Không tính GPA"));
        List<String> parts = new ArrayList<>();
        for (GradeComponent g : course.components) {
            parts.add(g.name + ": " + (g.score == null ? "—" : Format.fmt(g.score, 1)));
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
