package com.appgiuaky.gpaplanner.components.aiadvisor;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.appgiuaky.gpaplanner.AppViewModel;
import com.appgiuaky.gpaplanner.R;
import com.appgiuaky.gpaplanner.components.Ui;
import com.appgiuaky.gpaplanner.services.GeminiService;
import com.appgiuaky.gpaplanner.services.GeminiService.ChatMessage;
import com.appgiuaky.gpaplanner.types.Course;
import com.appgiuaky.gpaplanner.types.CourseStatus;
import com.appgiuaky.gpaplanner.types.Semester;
import com.appgiuaky.gpaplanner.utils.GpaCalculator;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.divider.MaterialDivider;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.slider.Slider;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Chức năng 7 & 8: cố vấn học vụ AI (hỏi đáp dựa trên bảng điểm) và lập lịch ôn thi bằng Gemini. */
public class AIAdvisorFragment extends Fragment {

    private static final List<String> SUGGESTIONS = Arrays.asList(
            "Các kỳ sau em cần đạt bao nhiêu điểm để tốt nghiệp loại Giỏi?",
            "Nên học cải thiện môn nào để tăng GPA nhiều nhất?",
            "Kỳ này em cần chú ý môn nào nhất?");

    private AppViewModel vm;
    private View view;
    private int section;
    private EditText chatInput;
    private LinearLayout chatList;
    private NestedScrollView chatScroll;
    private Slider hours;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_advisor, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        this.view = view;
        vm = new ViewModelProvider(requireActivity()).get(AppViewModel.class);
        chatInput = view.findViewById(R.id.chat_input);
        chatList = view.findViewById(R.id.chat_list);
        chatScroll = view.findViewById(R.id.chat_scroll);
        hours = view.findViewById(R.id.hours);

        MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.inflateMenu(R.menu.menu_advisor);
        toolbar.setOnMenuItemClickListener(item -> {
            showKeyDialog();
            return true;
        });
        ((TextView) view.findViewById(R.id.missing_key_text)).setText("Cố vấn AI dùng Google Gemini (" + GeminiService.MODEL
                + "). Tạo key miễn phí tại aistudio.google.com/apikey rồi nhập vào đây. Key chỉ được lưu trên máy này.");
        view.findViewById(R.id.enter_key).setOnClickListener(v -> showKeyDialog());

        ((TabLayout) view.findViewById(R.id.tabs)).addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                section = tab.getPosition();
                updatePanels();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        // Hỏi đáp
        view.findViewById(R.id.send).setOnClickListener(v -> send());
        chatInput.addTextChangedListener(new Ui.SimpleWatcher(this::updateSendButton));
        vm.getChat().observe(getViewLifecycleOwner(), messages -> renderChat());
        vm.getChatLoading().observe(getViewLifecycleOwner(), loading -> {
            renderChat();
            updateSendButton();
        });
        vm.getChatError().observe(getViewLifecycleOwner(), error -> {
            TextView errorView = view.findViewById(R.id.chat_error);
            errorView.setVisibility(error == null ? View.GONE : View.VISIBLE);
            errorView.setText(error);
            // Trả câu hỏi gửi lỗi về ô nhập để người dùng gửi lại
            String failed = vm.takeFailedInput();
            if (error != null && failed != null) chatInput.setText(failed);
        });

        // Lịch ôn thi
        hours.addOnChangeListener((slider, value, fromUser) -> updateHoursLabel());
        updateHoursLabel();
        view.findViewById(R.id.generate).setOnClickListener(v -> vm.generateStudyPlan(exams(), (int) hours.getValue()));
        vm.getStudyPlanLoading().observe(getViewLifecycleOwner(), loading -> renderPlanState());
        vm.getStudyPlanError().observe(getViewLifecycleOwner(), error -> renderPlanState());
        vm.getStudyPlan().observe(getViewLifecycleOwner(), plan -> renderPlanState());

        vm.getApiKey().observe(getViewLifecycleOwner(), key -> updatePanels());
        vm.getData().observe(getViewLifecycleOwner(), data -> renderExams());
    }

    private void showKeyDialog() {
        Ui.inputDialog(requireContext(), "Gemini API key", "API key", vm.getApiKey().getValue(),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
                "Tạo key tại aistudio.google.com/apikey. Để trống rồi Lưu để xóa key.",
                s -> true, vm::saveApiKey);
    }

    /** Chưa có key thì chỉ hiện thẻ hướng dẫn; có key thì hiện mục đang chọn. */
    private void updatePanels() {
        String key = vm.getApiKey().getValue();
        boolean hasKey = key != null && !key.isEmpty();
        view.findViewById(R.id.missing_key).setVisibility(hasKey ? View.GONE : View.VISIBLE);
        view.findViewById(R.id.chat_panel).setVisibility(hasKey && section == 0 ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.plan_panel).setVisibility(hasKey && section == 1 ? View.VISIBLE : View.GONE);
    }

    // ---------------------------------------------------------------- Hỏi đáp

    private boolean chatLoading() {
        return Boolean.TRUE.equals(vm.getChatLoading().getValue());
    }

    private void send() {
        String text = chatInput.getText().toString();
        if (text.trim().isEmpty() || chatLoading()) return;
        chatInput.setText("");
        vm.sendChat(text);
    }

    private void updateSendButton() {
        View send = view.findViewById(R.id.send);
        boolean enabled = !chatInput.getText().toString().trim().isEmpty() && !chatLoading();
        send.setEnabled(enabled);
        send.setAlpha(enabled ? 1f : 0.38f);
    }

    private void renderChat() {
        chatList.removeAllViews();
        List<ChatMessage> messages = vm.getChat().getValue();
        if (messages == null || messages.isEmpty()) {
            Ui.add(chatList, Ui.text(requireContext(),
                    "Hỏi cố vấn về bảng điểm, mục tiêu GPA, môn nên học cải thiện... Cố vấn đọc được toàn bộ bảng điểm của bạn. Ví dụ:",
                    com.google.android.material.R.attr.textAppearanceBodyMedium,
                    com.google.android.material.R.attr.colorOnSurfaceVariant), 0);
            for (String s : SUGGESTIONS) {
                MaterialButton chip = new MaterialButton(requireContext(), null,
                        com.google.android.material.R.attr.materialButtonOutlinedStyle);
                chip.setText(s);
                chip.setAllCaps(false);
                chip.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                chip.setOnClickListener(v -> vm.sendChat(s));
                Ui.add(chatList, chip, 8);
            }
            return;
        }
        for (ChatMessage m : messages) Ui.add(chatList, bubble(m), 8);
        if (chatLoading()) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setGravity(Gravity.CENTER_VERTICAL);
            CircularProgressIndicator progress = new CircularProgressIndicator(requireContext());
            progress.setIndeterminate(true);
            progress.setIndicatorSize(Ui.dp(requireContext(), 16));
            progress.setTrackThickness(Ui.dp(requireContext(), 2));
            row.addView(progress);
            TextView text = Ui.text(requireContext(), "Đang trả lời...",
                    com.google.android.material.R.attr.textAppearanceBodyMedium,
                    com.google.android.material.R.attr.colorOnSurfaceVariant);
            text.setPadding(Ui.dp(requireContext(), 8), 0, 0, 0);
            row.addView(text);
            Ui.add(chatList, row, 8);
        }
        // Cuộn xuống tin nhắn mới nhất
        chatScroll.post(() -> chatScroll.smoothScrollTo(0, chatList.getHeight()));
    }

    /** Bong bóng tin nhắn: của người dùng nằm bên phải, của cố vấn nằm bên trái. */
    private View bubble(ChatMessage m) {
        TextView tv = Ui.text(requireContext(), m.text, com.google.android.material.R.attr.textAppearanceBodyMedium);
        tv.setTextIsSelectable(true);
        int pad = Ui.dp(requireContext(), 12);
        tv.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(Ui.dp(requireContext(), 16));
        bg.setColor(Ui.color(requireContext(), m.fromUser
                ? com.google.android.material.R.attr.colorPrimaryContainer
                : com.google.android.material.R.attr.colorSurfaceVariant));
        tv.setBackground(bg);

        LinearLayout row = new LinearLayout(requireContext());
        row.setGravity(m.fromUser ? Gravity.END : Gravity.START);
        row.setPadding(m.fromUser ? Ui.dp(requireContext(), 48) : 0, 0, m.fromUser ? 0 : Ui.dp(requireContext(), 24), 0);
        row.addView(tv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    // ---------------------------------------------------------------- Lịch ôn thi

    private List<Course> pendingCourses() {
        List<Course> pending = new ArrayList<>();
        for (Semester s : vm.current().semesters) {
            for (Course c : s.courses) if (GpaCalculator.status(c) != CourseStatus.COMPLETED) pending.add(c);
        }
        return pending;
    }

    private List<GeminiService.Exam> exams() {
        List<GeminiService.Exam> exams = new ArrayList<>();
        for (Course c : pendingCourses()) {
            Long date = vm.examDates.get(c.id);
            if (date != null) exams.add(new GeminiService.Exam(c, date));
        }
        return exams;
    }

    private void updateHoursLabel() {
        ((TextView) view.findViewById(R.id.hours_label))
                .setText("Thời gian ôn mỗi ngày: " + (int) hours.getValue() + " giờ");
    }

    private void renderExams() {
        List<Course> pending = pendingCourses();
        view.findViewById(R.id.plan_empty).setVisibility(pending.isEmpty() ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.exam_card).setVisibility(pending.isEmpty() ? View.GONE : View.VISIBLE);
        LinearLayout list = view.findViewById(R.id.exam_list);
        list.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (int i = 0; i < pending.size(); i++) {
            Course course = pending.get(i);
            Long date = vm.examDates.get(course.id);
            if (i > 0) list.addView(new MaterialDivider(requireContext()));
            View row = inflater.inflate(R.layout.item_exam, list, false);
            ((TextView) row.findViewById(R.id.exam_name)).setText(course.name);
            ((TextView) row.findViewById(R.id.exam_meta)).setText(
                    course.code + " • " + course.credits + " TC • " + GpaCalculator.status(course).label);
            MaterialButton dateButton = row.findViewById(R.id.exam_date);
            dateButton.setText(date == null ? "Ngày thi" : GeminiService.formatDate(date));
            dateButton.setIconResource(date == null ? R.drawable.ic_date_range : 0);
            dateButton.setOnClickListener(v -> pickDate(course, date));
            View clear = row.findViewById(R.id.exam_clear);
            clear.setVisibility(date == null ? View.GONE : View.VISIBLE);
            clear.setOnClickListener(v -> {
                vm.examDates.remove(course.id);
                renderExams();
            });
            list.addView(row);
        }
        renderPlanState();
    }

    /** Chọn ngày thi; chỉ cho chọn từ hôm nay trở đi. */
    private void pickDate(Course course, Long current) {
        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Ngày thi " + course.name)
                .setSelection(current != null ? current : AppViewModel.todayUtcMillis())
                .setCalendarConstraints(new CalendarConstraints.Builder()
                        .setValidator(DateValidatorPointForward.from(AppViewModel.todayUtcMillis()))
                        .build())
                .build();
        picker.addOnPositiveButtonClickListener(selection -> {
            vm.examDates.put(course.id, selection);
            renderExams();
        });
        picker.show(getParentFragmentManager(), "exam_date");
    }

    private void renderPlanState() {
        boolean loading = Boolean.TRUE.equals(vm.getStudyPlanLoading().getValue());
        int count = exams().size();
        MaterialButton generate = view.findViewById(R.id.generate);
        generate.setText(loading ? "Đang lập lịch..." : "Lập lịch ôn thi (" + count + " môn)");
        generate.setEnabled(count > 0 && !loading);
        view.findViewById(R.id.plan_progress).setVisibility(loading ? View.VISIBLE : View.GONE);

        String error = vm.getStudyPlanError().getValue();
        TextView errorView = view.findViewById(R.id.plan_error);
        errorView.setVisibility(error == null ? View.GONE : View.VISIBLE);
        errorView.setText(error);

        String plan = vm.getStudyPlan().getValue();
        view.findViewById(R.id.plan_card).setVisibility(plan == null ? View.GONE : View.VISIBLE);
        ((TextView) view.findViewById(R.id.plan_text)).setText(plan);
    }
}
