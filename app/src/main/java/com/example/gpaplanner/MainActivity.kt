package com.example.gpaplanner

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.example.gpaplanner.components.aiadvisor.AIAdvisorScreen
import com.example.gpaplanner.components.curriculum.CurriculumScreen
import com.example.gpaplanner.components.dashboard.DashboardScreen
import com.example.gpaplanner.components.gradebook.GradebookScreen
import com.example.gpaplanner.components.simulator.SimulatorScreen

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GpaPlannerTheme {
                App(vm)
            }
        }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    DASHBOARD("Tổng quan", Icons.Default.Home),
    GRADEBOOK("Bảng điểm", Icons.AutoMirrored.Filled.List),
    SIMULATOR("Kế hoạch", Icons.Default.Star),
    CURRICULUM("Chương trình", Icons.Default.DateRange),
    ADVISOR("Cố vấn AI", Icons.Default.Face),
}

/** Layout chính: thanh điều hướng dưới cùng chuyển giữa các chức năng. */
@Composable
private fun App(vm: AppViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEachIndexed { index, t ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label, maxLines = 1) },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
        ) {
            when (Tab.entries[tab]) {
                Tab.DASHBOARD -> DashboardScreen(vm)
                Tab.GRADEBOOK -> GradebookScreen(vm)
                Tab.SIMULATOR -> SimulatorScreen(vm)
                Tab.CURRICULUM -> CurriculumScreen(vm)
                Tab.ADVISOR -> AIAdvisorScreen(vm)
            }
        }
    }
}

@Composable
private fun GpaPlannerTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme(primary = Color(0xFF1E5AA8))
    }
    MaterialTheme(colorScheme = colors, content = content)
}
