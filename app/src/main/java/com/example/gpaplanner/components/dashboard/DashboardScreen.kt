package com.example.gpaplanner.components.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.example.gpaplanner.AppViewModel
import com.example.gpaplanner.components.ScreenTopBar
import com.example.gpaplanner.components.gradeColor
import com.example.gpaplanner.types.CourseStatus
import com.example.gpaplanner.utils.GpaCalculator
import com.example.gpaplanner.utils.fmt
import kotlin.math.roundToInt

private class ChartPoint(val label: String, val semesterGpa: Double, val cumulativeGpa: Double)

/** Chức năng 4: thẻ thống kê, biểu đồ đường GPA theo kỳ và biểu đồ tròn phân bố điểm chữ. */
@Composable
fun DashboardScreen(vm: AppViewModel) {
    val data = vm.data
    val total = GpaCalculator.cumulative(data.semesters)
    val best = GpaCalculator.bestCourses(data.semesters)
    val failed = best.count { GpaCalculator.courseGrade(it)!!.point == 0.0 }
    val inProgress = data.semesters.flatMap { it.courses }.count { GpaCalculator.status(it) == CourseStatus.IN_PROGRESS }
    val hasGpa = total.credits > 0

    val points = data.semesters.indices.mapNotNull { i ->
        val sem = GpaCalculator.semesterSummary(data.semesters[i])
        if (sem.credits == 0) return@mapNotNull null
        ChartPoint(
            label = data.semesters[i].name.replace("Học kỳ", "HK"),
            semesterGpa = sem.gpa4,
            cumulativeGpa = GpaCalculator.cumulative(data.semesters.take(i + 1)).gpa4,
        )
    }
    val gradeCounts = GpaCalculator.SCALE
        .map { grade -> grade.letter to best.count { GpaCalculator.courseGrade(it)!!.letter == grade.letter } }
        .filter { it.second > 0 }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Tổng quan học tập")
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    title = "GPA tích lũy (hệ 4)",
                    value = if (hasGpa) total.gpa4.fmt() else "—",
                    note = if (hasGpa) "Xếp loại: ${GpaCalculator.classify(total.gpa4).label}" else "Chưa có điểm",
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    title = "Điểm TB hệ 10",
                    value = if (hasGpa) total.gpa10.fmt() else "—",
                    note = "Trung bình theo tín chỉ",
                    modifier = Modifier.weight(1f),
                )
            }
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val progress = (total.credits.toFloat() / data.totalProgramCredits).coerceIn(0f, 1f)
                StatCard(
                    title = "Tín chỉ tích lũy",
                    value = "${total.credits}/${data.totalProgramCredits}",
                    note = "${(progress * 100).toInt()}% chương trình",
                    modifier = Modifier.weight(1f),
                    progress = progress,
                )
                StatCard(
                    title = "Môn đã qua",
                    value = "${best.size - failed}",
                    note = (if (failed > 0) "Nợ $failed môn" else "Không nợ môn") + " • Đang học $inProgress",
                    modifier = Modifier.weight(1f),
                )
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("GPA theo học kỳ", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    if (points.isEmpty()) Text("Chưa có học kỳ nào có điểm.") else GpaLineChart(points)
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Phân bố điểm chữ", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    if (gradeCounts.isEmpty()) Text("Chưa có môn nào có điểm.") else GradePieChart(gradeCounts)
                }
            }
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, note: String, modifier: Modifier, progress: Float? = null) {
    Card(modifier.fillMaxHeight()) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            if (progress != null) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
            }
            Text(note, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun GpaLineChart(points: List<ChartPoint>) {
    // Cặp xanh dương - cam dễ phân biệt (kể cả với người mù màu), rõ trên cả nền sáng và tối
    val semesterColor = Color(0xFF1E88E5)
    val cumulativeColor = Color(0xFFF4511E)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        val left = 24.dp.toPx()
        val right = size.width - 16.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = size.height - 22.dp.toPx()
        fun y(gpa: Double) = bottom - (gpa / 4.0).toFloat() * (bottom - top)
        fun x(i: Int) = if (points.size == 1) (left + right) / 2 else left + i * (right - left) / (points.size - 1)

        for (g in 0..4) {
            val gy = y(g.toDouble())
            drawLine(gridColor, Offset(left, gy), Offset(right, gy), strokeWidth = 1.dp.toPx())
            val text = measurer.measure("$g", labelStyle)
            drawText(text, topLeft = Offset(left - text.size.width - 8.dp.toPx(), gy - text.size.height / 2))
        }
        points.forEachIndexed { i, p ->
            val text = measurer.measure(p.label, labelStyle)
            drawText(text, topLeft = Offset(x(i) - text.size.width / 2, bottom + 4.dp.toPx()))
        }

        fun series(values: List<Double>, color: Color) {
            val path = Path()
            values.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
            drawPath(path, color, style = Stroke(width = 3.dp.toPx()))
            values.forEachIndexed { i, v -> drawCircle(color, radius = 5.dp.toPx(), center = Offset(x(i), y(v))) }
        }
        series(points.map { it.cumulativeGpa }, cumulativeColor)
        series(points.map { it.semesterGpa }, semesterColor)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 8.dp)) {
        LegendItem(semesterColor, "GPA học kỳ")
        LegendItem(cumulativeColor, "GPA tích lũy")
    }
}

@Composable
private fun GradePieChart(counts: List<Pair<String, Int>>) {
    val total = counts.sumOf { it.second }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 26.dp.toPx()
                var start = -90f
                counts.forEach { (letter, n) ->
                    val sweep = 360f * n / total
                    drawArc(
                        color = gradeColor(letter),
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(width = stroke),
                    )
                    start += sweep
                }
            }
            Text("$total môn", style = MaterialTheme.typography.titleSmall)
        }
        Spacer(Modifier.width(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            counts.forEach { (letter, n) -> LegendItem(gradeColor(letter), "$letter: $n môn (${(n * 100.0 / total).roundToInt()}%)") }
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
