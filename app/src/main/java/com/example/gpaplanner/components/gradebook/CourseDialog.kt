package com.example.gpaplanner.components.gradebook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.gpaplanner.types.Course
import com.example.gpaplanner.types.GradeComponent
import com.example.gpaplanner.utils.GpaCalculator
import com.example.gpaplanner.utils.MockData
import com.example.gpaplanner.utils.parseNumber
import com.example.gpaplanner.utils.plain
import kotlin.math.abs

private class ComponentInput(name: String, weight: String, score: String) {
    var name by mutableStateOf(name)
    var weight by mutableStateOf(weight)
    var score by mutableStateOf(score)
}

/** Hộp thoại thêm/sửa môn học và các điểm thành phần. [initial] = null nghĩa là thêm môn mới. */
@Composable
fun CourseDialog(
    initial: Course?,
    onDismiss: () -> Unit,
    onSave: (Course) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val base = initial ?: Course(code = "", name = "", credits = 3, components = MockData.defaultComponents())
    var code by remember { mutableStateOf(base.code) }
    var name by remember { mutableStateOf(base.name) }
    var credits by remember { mutableStateOf(base.credits.toString()) }
    var countInGpa by remember { mutableStateOf(base.countInGpa) }
    val components = remember {
        base.components.map { ComponentInput(it.name, it.weight.plain(), it.score?.plain().orEmpty()) }.toMutableStateList()
    }
    var error by remember { mutableStateOf<String?>(null) }
    val weightSum = components.sumOf { parseNumber(it.weight) ?: 0.0 }
    val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    fun buildCourse(): Course? {
        val creditsValue = credits.trim().toIntOrNull()
        error = when {
            code.isBlank() -> "Nhập mã môn"
            name.isBlank() -> "Nhập tên môn"
            creditsValue == null || creditsValue !in 1..20 -> "Số tín chỉ phải từ 1 đến 20"
            components.isEmpty() -> "Cần ít nhất một điểm thành phần"
            components.any { it.name.isBlank() } -> "Nhập tên cho mọi điểm thành phần"
            components.any { (parseNumber(it.weight) ?: 0.0) <= 0 } -> "Trọng số phải là số dương"
            abs(weightSum - 100) > 0.01 -> "Tổng trọng số phải bằng 100%"
            components.any { it.score.isNotBlank() && parseNumber(it.score)?.let { s -> s in 0.0..10.0 } != true } ->
                "Điểm phải là số từ 0 đến 10"
            else -> null
        }
        if (error != null) return null
        return base.copy(
            code = code.trim(),
            name = name.trim(),
            credits = creditsValue!!,
            countInGpa = countInGpa,
            components = components.map {
                GradeComponent(it.name.trim(), parseNumber(it.weight)!!, it.score.takeIf { s -> s.isNotBlank() }?.let(::parseNumber))
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        // Rộng gần hết màn hình để hàng điểm thành phần (tên, %, điểm) đủ chỗ
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(horizontal = 16.dp),
        title = { Text(if (initial == null) "Thêm môn học" else "Sửa môn học") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = code, onValueChange = { code = it }, label = { Text("Mã môn") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Tên môn") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = credits, onValueChange = { credits = it }, label = { Text("Số tín chỉ") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = countInGpa, onCheckedChange = { countInGpa = it })
                    Spacer(Modifier.width(6.dp))
                    Text("Tính GPA", style = MaterialTheme.typography.bodyMedium)
                }

                Text("Điểm thành phần (để trống nếu chưa có điểm)", style = MaterialTheme.typography.titleSmall)
                components.forEachIndexed { index, c ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = c.name, onValueChange = { c.name = it }, label = { Text("Tên") },
                            singleLine = true, modifier = Modifier.weight(1.6f),
                        )
                        OutlinedTextField(
                            value = c.weight, onValueChange = { c.weight = it }, label = { Text("%") },
                            singleLine = true, keyboardOptions = decimal, modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = c.score, onValueChange = { c.score = it }, label = { Text("Điểm") },
                            singleLine = true, keyboardOptions = decimal, modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { components.removeAt(index) }) {
                            Icon(Icons.Default.Close, contentDescription = "Xóa thành phần")
                        }
                    }
                }
                TextButton(onClick = { components += ComponentInput("", "", "") }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("Thêm thành phần")
                }
                Text(
                    "Tổng trọng số: ${GpaCalculator.round2(weightSum).plain()}%",
                    color = if (abs(weightSum - 100) > 0.01) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Xóa môn học này") }
                }
            }
        },
        confirmButton = { TextButton(onClick = { buildCourse()?.let(onSave) }) { Text("Lưu") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } },
    )
}
