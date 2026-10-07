package com.example.gpaplanner.components.gradebook

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gpaplanner.AppViewModel
import com.example.gpaplanner.components.GradeBadge
import com.example.gpaplanner.components.ScreenTopBar
import com.example.gpaplanner.types.Course
import com.example.gpaplanner.types.Semester
import com.example.gpaplanner.utils.GpaCalculator
import com.example.gpaplanner.utils.fmt

/** Chức năng 1 & 6: bảng điểm theo học kỳ (thêm/sửa/xóa), xuất/nhập file Excel. */
@Composable
fun GradebookScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val semesters = vm.data.semesters
    var menuOpen by remember { mutableStateOf(false) }
    var addingSemester by remember { mutableStateOf(false) }
    var renamingSemester by remember { mutableStateOf<Semester?>(null) }
    var deletingSemester by remember { mutableStateOf<Semester?>(null) }
    // (id học kỳ, môn đang sửa; null = thêm môn mới)
    var editingCourse by remember { mutableStateOf<Pair<String, Course?>?>(null) }
    var deletingCourse by remember { mutableStateOf<Pair<String, Course>?>(null) }
    var pendingImport by remember { mutableStateOf<List<Semester>?>(null) }

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExcelIO.MIME)) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.openOutputStream(uri)!!.use { ExcelIO.export(vm.data.semesters, it) } }
            .onSuccess { toast("Đã xuất bảng điểm ra file Excel") }
            .onFailure { toast("Xuất file thất bại: ${it.message}") }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.openInputStream(uri)!!.use(ExcelIO::import) }
            .onSuccess { pendingImport = it }
            .onFailure { toast("Không đọc được file: ${it.message}") }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Bảng điểm") {
            IconButton(onClick = { addingSemester = true }) {
                Icon(Icons.Default.Add, contentDescription = "Thêm học kỳ")
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Tùy chọn")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Xuất ra Excel") },
                        onClick = {
                            menuOpen = false
                            exportLauncher.launch("bang_diem.xlsx")
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Nhập từ Excel") },
                        onClick = {
                            menuOpen = false
                            importLauncher.launch(arrayOf(ExcelIO.MIME, "application/octet-stream"))
                        },
                    )
                }
            }
        }

        if (semesters.isEmpty()) {
            Text(
                "Chưa có học kỳ nào. Bấm + để thêm học kỳ, hoặc nhập từ file Excel.",
                modifier = Modifier.padding(16.dp),
            )
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(semesters, key = { it.id }) { semester ->
                SemesterCard(
                    semester = semester,
                    onAddCourse = { editingCourse = semester.id to null },
                    onEditCourse = { editingCourse = semester.id to it },
                    onRename = { renamingSemester = semester },
                    onDelete = { deletingSemester = semester },
                )
            }
        }
    }

    if (addingSemester) {
        NameDialog(
            title = "Thêm học kỳ",
            initial = "Học kỳ ${semesters.size + 1}",
            onDismiss = { addingSemester = false },
            onConfirm = {
                vm.addSemester(it)
                addingSemester = false
            },
        )
    }
    renamingSemester?.let { semester ->
        NameDialog(
            title = "Đổi tên học kỳ",
            initial = semester.name,
            onDismiss = { renamingSemester = null },
            onConfirm = {
                vm.renameSemester(semester.id, it)
                renamingSemester = null
            },
        )
    }
    deletingSemester?.let { semester ->
        ConfirmDialog(
            title = "Xóa ${semester.name}?",
            message = "Toàn bộ ${semester.courses.size} môn trong học kỳ này sẽ bị xóa.",
            onDismiss = { deletingSemester = null },
            onConfirm = {
                vm.deleteSemester(semester.id)
                deletingSemester = null
            },
        )
    }
    editingCourse?.let { (semesterId, course) ->
        CourseDialog(
            initial = course,
            onDismiss = { editingCourse = null },
            onSave = {
                vm.saveCourse(semesterId, it)
                editingCourse = null
            },
            onDelete = course?.let {
                {
                    editingCourse = null
                    deletingCourse = semesterId to it
                }
            },
        )
    }
    deletingCourse?.let { (semesterId, course) ->
        ConfirmDialog(
            title = "Xóa môn ${course.name}?",
            message = "Điểm của môn này sẽ bị xóa khỏi bảng điểm.",
            onDismiss = { deletingCourse = null },
            onConfirm = {
                vm.deleteCourse(semesterId, course.id)
                deletingCourse = null
            },
        )
    }
    pendingImport?.let { imported ->
        ConfirmDialog(
            title = "Nhập bảng điểm từ Excel?",
            message = "Đọc được ${imported.size} học kỳ, ${imported.sumOf { it.courses.size }} môn. " +
                "Bảng điểm hiện tại sẽ được thay thế.",
            onDismiss = { pendingImport = null },
            onConfirm = {
                vm.replaceSemesters(imported)
                pendingImport = null
                toast("Đã nhập bảng điểm")
            },
        )
    }
}

@Composable
private fun SemesterCard(
    semester: Semester,
    onAddCourse: () -> Unit,
    onEditCourse: (Course) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val summary = GpaCalculator.semesterSummary(semester)
    val credits = semester.courses.sumOf { it.credits }
    var menuOpen by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(semester.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (summary.credits > 0) {
                            "GPA kỳ: ${summary.gpa4.fmt()} • Hệ 10: ${summary.gpa10.fmt()} • $credits TC"
                        } else {
                            "$credits TC • chưa có điểm tổng kết"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onAddCourse) {
                    Icon(Icons.Default.Add, contentDescription = "Thêm môn")
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Tùy chọn học kỳ")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Đổi tên") }, onClick = { menuOpen = false; onRename() })
                        DropdownMenuItem(text = { Text("Xóa học kỳ") }, onClick = { menuOpen = false; onDelete() })
                    }
                }
            }
            if (semester.courses.isEmpty()) {
                Text(
                    "Chưa có môn học. Bấm + để thêm.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            semester.courses.forEach { course ->
                HorizontalDivider()
                CourseRow(course, onClick = { onEditCourse(course) })
            }
        }
    }
}

@Composable
private fun CourseRow(course: Course, onClick: () -> Unit) {
    val score = GpaCalculator.courseScore(course)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(course.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                "${course.code} • ${course.credits} TC" + if (course.countInGpa) "" else " • Không tính GPA",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                course.components.joinToString("   ") { "${it.name}: ${it.score?.fmt(1) ?: "—"}" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (score != null) {
            Column(horizontalAlignment = Alignment.End) {
                Text(score.fmt(1), style = MaterialTheme.typography.titleMedium)
                GradeBadge(GpaCalculator.letterOf(score).letter)
            }
        } else {
            Text(
                GpaCalculator.status(course).label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Tên học kỳ") }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text("Lưu") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } },
    )
}

@Composable
private fun ConfirmDialog(title: String, message: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Đồng ý") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } },
    )
}
