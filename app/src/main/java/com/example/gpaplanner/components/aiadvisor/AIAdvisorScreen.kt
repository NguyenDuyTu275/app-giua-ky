package com.example.gpaplanner.components.aiadvisor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Slider
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gpaplanner.AppViewModel
import com.example.gpaplanner.components.ScreenTopBar
import com.example.gpaplanner.services.GeminiService
import com.example.gpaplanner.services.GeminiService.ChatMessage
import com.example.gpaplanner.types.Course
import com.example.gpaplanner.types.CourseStatus
import com.example.gpaplanner.utils.GpaCalculator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private enum class Section(val label: String) {
    CHAT("Hỏi đáp"),
    STUDY_PLAN("Lịch ôn thi"),
}

private val SUGGESTIONS = listOf(
    "Các kỳ sau em cần đạt bao nhiêu điểm để tốt nghiệp loại Giỏi?",
    "Nên học cải thiện môn nào để tăng GPA nhiều nhất?",
    "Kỳ này em cần chú ý môn nào nhất?",
)

private val DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/** Chức năng 7 & 8: cố vấn học vụ AI (hỏi đáp dựa trên bảng điểm) và lập lịch ôn thi bằng Gemini. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIAdvisorScreen(vm: AppViewModel) {
    var section by rememberSaveable { mutableIntStateOf(0) }
    var editingKey by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Cố vấn học vụ AI") {
            IconButton(onClick = { editingKey = true }) {
                Icon(Icons.Default.Settings, contentDescription = "Gemini API key")
            }
        }
        SecondaryTabRow(selectedTabIndex = section) {
            Section.entries.forEachIndexed { index, s ->
                Tab(selected = section == index, onClick = { section = index }, text = { Text(s.label) })
            }
        }
        if (vm.apiKey.isBlank()) {
            MissingKeyCard(onClick = { editingKey = true })
        } else {
            when (Section.entries[section]) {
                Section.CHAT -> ChatSection(vm)
                Section.STUDY_PLAN -> StudyPlanSection(vm)
            }
        }
    }

    if (editingKey) {
        ApiKeyDialog(
            initial = vm.apiKey,
            onDismiss = { editingKey = false },
            onSave = {
                vm.saveApiKey(it)
                editingKey = false
            },
        )
    }
}

@Composable
private fun MissingKeyCard(onClick: () -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Chưa có Gemini API key", style = MaterialTheme.typography.titleMedium)
            Text(
                "Cố vấn AI dùng Google Gemini (${GeminiService.MODEL}). Tạo key miễn phí tại " +
                    "aistudio.google.com/apikey rồi nhập vào đây. Key chỉ được lưu trên máy này."
            )
            Button(onClick = onClick) { Text("Nhập API key") }
        }
    }
}

@Composable
private fun ApiKeyDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var key by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gemini API key") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Tạo key tại aistudio.google.com/apikey. Để trống rồi Lưu để xóa key.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = key, onValueChange = { key = it }, label = { Text("API key") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(key) }) { Text("Lưu") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } },
    )
}

// ---------------------------------------------------------------- Hỏi đáp

@Composable
private fun ChatSection(vm: AppViewModel) {
    val listState = rememberLazyListState()
    // Cuộn xuống tin nhắn mới nhất (hoặc dòng "Đang trả lời...")
    LaunchedEffect(vm.chat.size, vm.chatLoading) {
        if (vm.chat.isNotEmpty()) listState.animateScrollToItem(vm.chat.lastIndex + if (vm.chatLoading) 1 else 0)
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (vm.chat.isEmpty()) {
                item {
                    Text(
                        "Hỏi cố vấn về bảng điểm, mục tiêu GPA, môn nên học cải thiện... Cố vấn đọc được toàn bộ bảng điểm của bạn. Ví dụ:",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(SUGGESTIONS) { s ->
                    SuggestionChip(
                        onClick = {
                            vm.chatInput = s
                            vm.sendChat()
                        },
                        label = { Text(s) },
                    )
                }
            }
            items(vm.chat) { MessageBubble(it) }
            if (vm.chatLoading) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("Đang trả lời...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        vm.chatError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
        }
        Row(
            Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = vm.chatInput,
                onValueChange = { vm.chatInput = it },
                placeholder = { Text("Nhập câu hỏi...") },
                maxLines = 4,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = vm::sendChat, enabled = vm.chatInput.isNotBlank() && !vm.chatLoading) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Gửi")
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val fromUser = message.fromUser
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = if (fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(start = if (fromUser) 48.dp else 0.dp, end = if (fromUser) 0.dp else 24.dp),
        ) {
            SelectionContainer {
                Text(message.text, modifier = Modifier.padding(12.dp))
            }
        }
    }
}

// ---------------------------------------------------------------- Lịch ôn thi

@Composable
private fun StudyPlanSection(vm: AppViewModel) {
    val pending = vm.data.semesters.flatMap { it.courses }.filter { GpaCalculator.status(it) != CourseStatus.COMPLETED }
    val examDates = vm.examDates
    var hoursPerDay by rememberSaveable { mutableIntStateOf(4) }
    var pickingFor by remember { mutableStateOf<Course?>(null) }
    val exams = pending.mapNotNull { c -> examDates[c.id]?.let { c to it } }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Chọn ngày thi cho các môn cần ôn. AI sẽ xếp lịch theo từng ngày, ưu tiên môn thi sớm và môn cần điểm cao.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (pending.isEmpty()) {
            Text("Không có môn nào đang học. Thêm môn chưa đủ điểm thành phần trong Bảng điểm.")
        } else {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    pending.forEachIndexed { index, course ->
                        if (index > 0) HorizontalDivider()
                        ExamRow(
                            course = course,
                            date = examDates[course.id],
                            onPick = { pickingFor = course },
                            onClear = { examDates.remove(course.id) },
                        )
                    }
                }
            }
        }

        Text("Thời gian ôn mỗi ngày: $hoursPerDay giờ", style = MaterialTheme.typography.titleSmall)
        Slider(
            value = hoursPerDay.toFloat(),
            onValueChange = { hoursPerDay = it.roundToInt() },
            valueRange = 1f..12f,
            steps = 10,
        )
        Button(
            onClick = { vm.generateStudyPlan(exams, hoursPerDay) },
            enabled = exams.isNotEmpty() && !vm.studyPlanLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (vm.studyPlanLoading) "Đang lập lịch..." else "Lập lịch ôn thi (${exams.size} môn)")
        }
        if (vm.studyPlanLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        vm.studyPlanError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        vm.studyPlan?.let { plan ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Lịch ôn thi", style = MaterialTheme.typography.titleMedium)
                    SelectionContainer { Text(plan) }
                }
            }
        }
    }

    pickingFor?.let { course ->
        ExamDatePicker(
            initial = examDates[course.id],
            onDismiss = { pickingFor = null },
            onPick = {
                examDates[course.id] = it
                pickingFor = null
            },
        )
    }
}

@Composable
private fun ExamRow(course: Course, date: LocalDate?, onPick: () -> Unit, onClear: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(course.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                "${course.code} • ${course.credits} TC • ${GpaCalculator.status(course).label}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onPick) {
            if (date == null) {
                Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Ngày thi", modifier = Modifier.padding(start = 4.dp))
            } else {
                Text(date.format(DATE))
            }
        }
        if (date != null) {
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Close, contentDescription = "Bỏ môn này")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExamDatePicker(initial: LocalDate?, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = !utcToDate(utcTimeMillis).isBefore(today)
            override fun isSelectableYear(year: Int) = year >= today.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let { onPick(utcToDate(it)) } },
                enabled = state.selectedDateMillis != null,
            ) { Text("Chọn") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } },
    ) {
        DatePicker(state)
    }
}

/** DatePicker trả về mốc 0h UTC của ngày được chọn. */
private fun utcToDate(utcMillis: Long): LocalDate = Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()
