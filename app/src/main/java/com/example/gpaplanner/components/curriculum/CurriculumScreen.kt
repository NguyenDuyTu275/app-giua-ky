package com.example.gpaplanner.components.curriculum

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.gpaplanner.AppViewModel
import com.example.gpaplanner.components.GradeBadge
import com.example.gpaplanner.components.ScreenTopBar
import com.example.gpaplanner.components.gradeColor
import com.example.gpaplanner.types.Course
import com.example.gpaplanner.types.CourseStatus
import com.example.gpaplanner.types.Semester
import com.example.gpaplanner.utils.GpaCalculator
import com.example.gpaplanner.utils.fmt

/** Trạng thái của một môn trên cây chương trình. */
private enum class NodeState(val label: String) {
    PASSED("Đã đạt"),
    FAILED("Nợ môn"),
    /** Lần học đã được thay bằng một lần học lại/cải thiện có điểm cao hơn. */
    REPLACED("Đã học lại"),
    IN_PROGRESS("Đang học"),
    PLANNED("Chưa học"),
}

@Composable
private fun NodeState.color(): Color = when (this) {
    NodeState.PASSED -> gradeColor("A")
    NodeState.FAILED -> MaterialTheme.colorScheme.error
    NodeState.IN_PROGRESS -> MaterialTheme.colorScheme.tertiary
    NodeState.REPLACED, NodeState.PLANNED -> MaterialTheme.colorScheme.outline
}

private class Segment(val label: String, val credits: Int, val color: Color)

/** Chức năng 5: cây chương trình đào tạo theo học kỳ và tiến độ tích lũy tín chỉ. */
@Composable
fun CurriculumScreen(vm: AppViewModel) {
    val data = vm.data
    val total = GpaCalculator.cumulative(data.semesters)
    val bestIds = GpaCalculator.bestCourses(data.semesters).map { it.id }.toSet()
    // Lưu học kỳ đang thu gọn; mặc định mở hết
    val collapsed = remember { mutableStateMapOf<String, Boolean>() }
    var editingCredits by remember { mutableStateOf(false) }

    fun stateOf(c: Course): NodeState = when (GpaCalculator.status(c)) {
        CourseStatus.IN_PROGRESS -> NodeState.IN_PROGRESS
        CourseStatus.PLANNED -> NodeState.PLANNED
        CourseStatus.COMPLETED -> when {
            c.countInGpa && c.id !in bestIds -> NodeState.REPLACED
            GpaCalculator.courseGrade(c)!!.point > 0 -> NodeState.PASSED
            else -> NodeState.FAILED
        }
    }

    val gpaCourses = data.semesters.flatMap { it.courses }.filter { it.countInGpa }
    val inProgress = gpaCourses.filter { stateOf(it) == NodeState.IN_PROGRESS }.sumOf { it.credits }
    val planned = gpaCourses.filter { stateOf(it) == NodeState.PLANNED }.sumOf { it.credits }
    val failed = gpaCourses.count { stateOf(it) == NodeState.FAILED }
    val segments = listOf(
        Segment("Đã tích lũy", total.credits, NodeState.PASSED.color()),
        Segment("Đang học", inProgress, NodeState.IN_PROGRESS.color()),
        Segment("Chưa học", planned, MaterialTheme.colorScheme.primary),
        Segment(
            "Chưa xếp kỳ",
            (data.totalProgramCredits - total.credits - inProgress - planned).coerceAtLeast(0),
            MaterialTheme.colorScheme.outlineVariant,
        ),
    )

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Chương trình đào tạo") {
            IconButton(onClick = { editingCredits = true }) {
                Icon(Icons.Default.Edit, contentDescription = "Sửa tổng tín chỉ chương trình")
            }
        }
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tiến độ chương trình", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${total.credits}/${data.totalProgramCredits} tín chỉ tích lũy " +
                            "(${total.credits * 100 / data.totalProgramCredits}%)" +
                            if (failed > 0) " • Nợ $failed môn" else "",
                    )
                    CreditBar(segments)
                    segments.forEach { LegendItem(it.color, "${it.label}: ${it.credits} TC") }
                }
            }

            if (data.semesters.isEmpty()) {
                Text("Chưa có học kỳ nào. Thêm học kỳ và môn học trong tab Bảng điểm.")
            }
            data.semesters.forEach { semester ->
                val expanded = collapsed[semester.id] != true
                SemesterNode(
                    semester = semester,
                    expanded = expanded,
                    onToggle = { collapsed[semester.id] = expanded },
                    stateOf = ::stateOf,
                )
            }
        }
    }

    if (editingCredits) {
        ProgramCreditsDialog(
            initial = data.totalProgramCredits,
            onDismiss = { editingCredits = false },
            onSave = {
                vm.setProgramCredits(it)
                editingCredits = false
            },
        )
    }
}

/** Thanh tín chỉ nhiều đoạn: đã tích lũy / đang học / chưa học / chưa xếp kỳ. */
@Composable
private fun CreditBar(segments: List<Segment>) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
    ) {
        segments.filter { it.credits > 0 }.forEach {
            Box(
                Modifier
                    .weight(it.credits.toFloat())
                    .fillMaxHeight()
                    .background(it.color)
            )
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(12.dp)
                .background(color, CircleShape)
        )
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SemesterNode(
    semester: Semester,
    expanded: Boolean,
    onToggle: () -> Unit,
    stateOf: (Course) -> NodeState,
) {
    val summary = GpaCalculator.semesterSummary(semester)
    val done = semester.courses.count { GpaCalculator.status(it) == CourseStatus.COMPLETED }
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(semester.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${semester.courses.sumOf { it.credits }} TC • $done/${semester.courses.size} môn có điểm" +
                        if (summary.credits > 0) " • GPA kỳ ${summary.gpa4.fmt()}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Thu gọn" else "Mở rộng",
            )
        }
        if (expanded) {
            Column(Modifier.padding(bottom = 8.dp)) {
                semester.courses.forEachIndexed { index, course ->
                    CourseNode(course, stateOf(course), isLast = index == semester.courses.lastIndex)
                }
            }
        }
    }
}

/** Một nhánh của cây: đường nối từ học kỳ xuống môn, chấm màu theo trạng thái. */
@Composable
private fun CourseNode(course: Course, state: NodeState, isLast: Boolean) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val stateColor = state.color()
    val score = GpaCalculator.courseScore(course)
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(start = 20.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(
            Modifier
                .width(20.dp)
                .fillMaxHeight()
        ) {
            val x = 4.dp.toPx()
            val y = size.height / 2
            val stroke = 2.dp.toPx()
            drawLine(lineColor, Offset(x, 0f), Offset(x, if (isLast) y else size.height), strokeWidth = stroke)
            drawLine(lineColor, Offset(x, y), Offset(size.width, y), strokeWidth = stroke)
        }
        Box(
            Modifier
                .size(10.dp)
                .background(stateColor, CircleShape)
        )
        Column(
            Modifier
                .weight(1f)
                .padding(start = 10.dp, top = 8.dp, bottom = 8.dp)
        ) {
            Text(course.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                "${course.code} • ${course.credits} TC • " +
                    (if (course.countInGpa) "" else "Không tính GPA • ") + state.label,
                style = MaterialTheme.typography.bodySmall,
                color = stateColor,
            )
        }
        if (score != null) GradeBadge(GpaCalculator.letterOf(score).letter)
    }
}

@Composable
private fun ProgramCreditsDialog(initial: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var text by remember { mutableStateOf(initial.toString()) }
    val value = text.trim().toIntOrNull()?.takeIf { it in 1..500 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tổng tín chỉ chương trình") },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                label = { Text("Số tín chỉ") }, singleLine = true, isError = value == null,
                supportingText = { Text("Số tín chỉ cần tích lũy để tốt nghiệp (1 - 500)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(value!!) }, enabled = value != null) { Text("Lưu") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } },
    )
}
