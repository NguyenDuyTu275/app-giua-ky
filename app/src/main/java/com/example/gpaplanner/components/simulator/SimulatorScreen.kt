package com.example.gpaplanner.components.simulator

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.gpaplanner.AppViewModel
import com.example.gpaplanner.components.GradeBadge
import com.example.gpaplanner.components.ScreenTopBar
import com.example.gpaplanner.types.AcademicData
import com.example.gpaplanner.types.Classification
import com.example.gpaplanner.types.Course
import com.example.gpaplanner.types.CourseStatus
import com.example.gpaplanner.types.LetterGrade
import com.example.gpaplanner.types.Semester
import com.example.gpaplanner.utils.GpaCalculator
import com.example.gpaplanner.utils.fmt
import com.example.gpaplanner.utils.parseNumber
import com.example.gpaplanner.utils.plain

private enum class Section(val label: String) {
    TARGET("Mục tiêu GPA"),
    RESCUE("Cứu môn"),
    IMPROVE("Học cải thiện"),
}

/** Các mức xếp loại người dùng có thể chọn làm mục tiêu (Trung bình trở lên). */
private val TARGETS = Classification.entries.filter { it.minGpa >= Classification.AVERAGE.minGpa }

/** Chức năng 2 & 3: lập kế hoạch đạt GPA mục tiêu, máy tính "Cứu môn" và giả lập học cải thiện. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimulatorScreen(vm: AppViewModel) {
    var section by rememberSaveable { mutableIntStateOf(0) }
    // Đặt ở đây để giữ lựa chọn giả lập khi chuyển qua lại giữa các mục
    val improvements = remember { mutableStateMapOf<String, LetterGrade>() }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Kế hoạch học tập")
        SecondaryTabRow(selectedTabIndex = section) {
            Section.entries.forEachIndexed { index, s ->
                Tab(selected = section == index, onClick = { section = index }, text = { Text(s.label, maxLines = 1) })
            }
        }
        key(section) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (Section.entries[section]) {
                    Section.TARGET -> TargetPlanner(vm.data)
                    Section.RESCUE -> RescueCalculator(vm.data.semesters)
                    Section.IMPROVE -> ImprovementSimulator(vm.data.semesters, improvements)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Mục tiêu GPA

@Composable
private fun TargetPlanner(data: AcademicData) {
    val current = GpaCalculator.cumulative(data.semesters)
    val remainingCredits = (data.totalProgramCredits - current.credits).coerceAtLeast(0)
    // Mặc định: mức xếp loại kế tiếp cao hơn GPA hiện tại
    val defaultTarget = TARGETS.lastOrNull { it.minGpa > current.gpa4 } ?: TARGETS.first()
    var targetText by rememberSaveable { mutableStateOf(defaultTarget.minGpa.plain()) }
    var creditsText by rememberSaveable { mutableStateOf(remainingCredits.toString()) }
    val target = parseNumber(targetText)?.takeIf { it > 0 && it <= 4 }
    val credits = creditsText.trim().toIntOrNull()?.takeIf { it > 0 }
    val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Hiện tại", style = MaterialTheme.typography.titleMedium)
            Text(
                if (current.credits > 0) {
                    "GPA tích lũy ${current.gpa4.fmt()} (${GpaCalculator.classify(current.gpa4).label}) " +
                        "trên ${current.credits}/${data.totalProgramCredits} tín chỉ"
                } else {
                    "Chưa có môn nào được tính GPA"
                },
            )
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Mục tiêu", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TARGETS.forEach { c ->
                    FilterChip(
                        selected = target == c.minGpa,
                        onClick = { targetText = c.minGpa.plain() },
                        label = { Text("${c.label} ${c.minGpa.fmt(1)}") },
                    )
                }
            }
            OutlinedTextField(
                value = targetText, onValueChange = { targetText = it },
                label = { Text("GPA tích lũy mong muốn (hệ 4)") },
                singleLine = true, keyboardOptions = decimal, isError = target == null,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = creditsText, onValueChange = { creditsText = it },
                label = { Text("Số tín chỉ sẽ học thêm") },
                supportingText = { Text("Số tín chỉ còn lại của chương trình: $remainingCredits") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = credits == null, modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (target == null || credits == null) {
        Text("Nhập GPA từ 0 đến 4 và số tín chỉ lớn hơn 0.", color = MaterialTheme.colorScheme.error)
        return
    }
    val plan = GpaCalculator.planTarget(current, target, credits)
    val uniform = plan.uniformGrade

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Kết quả", style = MaterialTheme.typography.titleMedium)
            if (uniform == null) {
                Text(
                    "Không thể đạt GPA ${target.fmt()} với $credits tín chỉ: kể cả đạt A tất cả, GPA tích lũy cao nhất " +
                        "chỉ là ${plan.maxReachable.fmt()} (${GpaCalculator.classify(plan.maxReachable).label}).",
                    color = MaterialTheme.colorScheme.error,
                )
                return@Column
            }
            if (uniform == GpaCalculator.PASSING.last()) {
                Text("Chỉ cần qua tất cả các môn (từ D trở lên) là đạt mục tiêu.")
            } else {
                Text("GPA trung bình cần đạt cho $credits tín chỉ sắp học:")
                Text(plan.requiredAvg.fmt(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider()
            Text("Phương án 1: mọi môn cùng một mức", style = MaterialTheme.typography.titleSmall)
            GradeLine(uniform, "Tất cả $credits TC đạt từ ${uniform.letter} (điểm hệ 10 ≥ ${uniform.minScore.plain()})")
            plan.mix?.let { mix ->
                Text("Phương án 2: kết hợp hai mức", style = MaterialTheme.typography.titleSmall)
                GradeLine(mix.high, "${mix.highCredits} TC đạt từ ${mix.high.letter} (≥ ${mix.high.minScore.plain()})")
                GradeLine(mix.low, "${mix.lowCredits} TC còn lại đạt từ ${mix.low.letter} (≥ ${mix.low.minScore.plain()})")
            }
            HorizontalDivider()
            Text(
                "Nếu đạt A tất cả, GPA tích lũy tối đa là ${plan.maxReachable.fmt()}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GradeLine(grade: LetterGrade, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(48.dp)) { GradeBadge(grade.letter) }
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

// ---------------------------------------------------------------- Cứu môn

@Composable
private fun RescueCalculator(semesters: List<Semester>) {
    val pending = semesters.flatMap { it.courses }.filter { GpaCalculator.status(it) != CourseStatus.COMPLETED }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    if (pending.isEmpty()) {
        Text("Không có môn nào đang học. Thêm môn chưa đủ điểm thành phần trong Bảng điểm để tính.")
        return
    }
    val course = pending.firstOrNull { it.id == selectedId } ?: pending.first()
    val remaining = course.components.filter { it.score == null }

    Text("Chọn môn đang học", style = MaterialTheme.typography.titleSmall)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        pending.forEach { c ->
            FilterChip(selected = c.id == course.id, onClick = { selectedId = c.id }, label = { Text(c.name) })
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(course.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "${course.code} • ${course.credits} TC",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            course.components.forEach {
                Text("${it.name} (${it.weight.plain()}%): ${it.score?.fmt(1) ?: "chưa có"}")
            }
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Điểm trung bình cần đạt ở: " + remaining.joinToString(", ") { "${it.name} (${it.weight.plain()}%)" },
                style = MaterialTheme.typography.titleSmall,
            )
            GpaCalculator.rescue(course).forEach { (grade, need) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(48.dp)) { GradeBadge(grade.letter) }
                    Text(
                        "Điểm HP ≥ ${grade.minScore.plain()}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        when (need) {
                            null -> "Không thể"
                            0.0 -> "Chắc chắn đạt"
                            else -> "Cần ≥ ${need.fmt(1)}"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = when (need) {
                            null -> MaterialTheme.colorScheme.error
                            0.0 -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Học cải thiện

@Composable
private fun ImprovementSimulator(semesters: List<Semester>, improvements: SnapshotStateMap<String, LetterGrade>) {
    val current = GpaCalculator.cumulative(semesters)
    val simulated = GpaCalculator.cumulative(semesters, improvements)
    // Môn chưa đạt A, xếp theo mức tăng GPA tối đa nếu học lại được A
    val candidates = GpaCalculator.bestCourses(semesters)
        .map { it to GpaCalculator.courseGrade(it)!! }
        .filter { (_, grade) -> grade.point < 4.0 }
        .sortedByDescending { (course, grade) -> (4.0 - grade.point) * course.credits }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("GPA tích lũy giả lập", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (improvements.isNotEmpty()) {
                    TextButton(onClick = { improvements.clear() }) { Text("Đặt lại") }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(current.gpa4.fmt(), style = MaterialTheme.typography.headlineSmall)
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .size(20.dp),
                )
                Text(simulated.gpa4.fmt(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                val delta = simulated.gpa4 - current.gpa4
                if (delta > 0.00001) {
                    Text(
                        "+${delta.fmt()}",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            Text(
                "Xếp loại: ${GpaCalculator.classify(current.gpa4).label} → ${GpaCalculator.classify(simulated.gpa4).label}" +
                    " • ${simulated.credits} TC tích lũy",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    if (candidates.isEmpty()) {
        Text("Không có môn nào để cải thiện (mọi môn đã đạt A hoặc chưa có điểm tổng kết).")
        return
    }
    Text(
        "Chọn điểm dự kiến nếu học lại/cải thiện. Môn ở trên cùng giúp tăng GPA nhiều nhất.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 4.dp)) {
            candidates.forEachIndexed { index, (course, grade) ->
                if (index > 0) HorizontalDivider()
                ImprovementRow(
                    course = course,
                    current = grade,
                    selected = improvements[course.id],
                    onSelect = { if (it == null) improvements.remove(course.id) else improvements[course.id] = it },
                )
            }
        }
    }
}

@Composable
private fun ImprovementRow(course: Course, current: LetterGrade, selected: LetterGrade?, onSelect: (LetterGrade?) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(course.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                "${course.code} • ${course.credits} TC",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        GradeBadge(current.letter)
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .size(16.dp),
        )
        Box {
            AssistChip(
                onClick = { menuOpen = true },
                label = { Text(selected?.letter ?: "Giữ") },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = "Chọn điểm dự kiến") },
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Giữ nguyên") }, onClick = { menuOpen = false; onSelect(null) })
                GpaCalculator.PASSING.filter { it.point > current.point }.forEach { grade ->
                    DropdownMenuItem(
                        text = { Text("${grade.letter} (≥ ${grade.minScore.plain()})") },
                        onClick = { menuOpen = false; onSelect(grade) },
                    )
                }
            }
        }
    }
}
