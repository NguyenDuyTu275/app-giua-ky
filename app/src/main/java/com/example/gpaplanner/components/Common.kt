package com.example.gpaplanner.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Thanh tiêu đề của từng màn hình (insets đã được Scaffold ngoài xử lý). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenTopBar(title: String, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(title = { Text(title) }, actions = actions, windowInsets = WindowInsets(0, 0, 0, 0))
}

/** Màu của từng điểm chữ, dùng chung cho nhãn điểm và biểu đồ. */
fun gradeColor(letter: String): Color = when (letter) {
    "A" -> Color(0xFF2E7D32)
    "B+" -> Color(0xFF558B2F)
    "B" -> Color(0xFF1565C0)
    "C+" -> Color(0xFF00838F)
    "C" -> Color(0xFFF57F17)
    "D+" -> Color(0xFFEF6C00)
    "D" -> Color(0xFFD84315)
    else -> Color(0xFFC62828)
}

@Composable
fun GradeBadge(letter: String, modifier: Modifier = Modifier) {
    Surface(color = gradeColor(letter), shape = RoundedCornerShape(6.dp), modifier = modifier) {
        Text(
            letter,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
