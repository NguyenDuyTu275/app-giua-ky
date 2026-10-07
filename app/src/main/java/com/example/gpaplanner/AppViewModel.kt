package com.example.gpaplanner

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.gpaplanner.services.GeminiService
import com.example.gpaplanner.services.GeminiService.ChatMessage
import com.example.gpaplanner.types.AcademicData
import com.example.gpaplanner.types.Course
import com.example.gpaplanner.types.Semester
import com.example.gpaplanner.utils.MockData
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate

/** Giữ dữ liệu bảng điểm (lưu file JSON trong máy), API key và trạng thái các tác vụ AI. */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val file = File(app.filesDir, "academic.json")
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    var data by mutableStateOf(
        if (file.exists()) json.decodeFromString<AcademicData>(file.readText()) else MockData.create()
    )
        private set

    var apiKey by mutableStateOf(prefs.getString(KEY_API, "").orEmpty())
        private set

    val chat = mutableStateListOf<ChatMessage>()
    var chatInput by mutableStateOf("")
    var chatLoading by mutableStateOf(false)
        private set
    var chatError by mutableStateOf<String?>(null)
        private set

    /** Ngày thi người dùng chọn cho từng môn (id môn -> ngày). */
    val examDates = mutableStateMapOf<String, LocalDate>()
    var studyPlan by mutableStateOf<String?>(null)
        private set
    var studyPlanLoading by mutableStateOf(false)
        private set
    var studyPlanError by mutableStateOf<String?>(null)
        private set

    private fun update(newData: AcademicData) {
        data = newData
        file.writeText(json.encodeToString(newData))
    }

    private fun updateSemester(id: String, change: (Semester) -> Semester) =
        update(data.copy(semesters = data.semesters.map { if (it.id == id) change(it) else it }))

    fun addSemester(name: String) = update(data.copy(semesters = data.semesters + Semester(name = name)))

    fun renameSemester(id: String, name: String) = updateSemester(id) { it.copy(name = name) }

    fun deleteSemester(id: String) = update(data.copy(semesters = data.semesters.filterNot { it.id == id }))

    fun saveCourse(semesterId: String, course: Course) = updateSemester(semesterId) { s ->
        val exists = s.courses.any { it.id == course.id }
        s.copy(courses = if (exists) s.courses.map { if (it.id == course.id) course else it } else s.courses + course)
    }

    fun deleteCourse(semesterId: String, courseId: String) =
        updateSemester(semesterId) { s -> s.copy(courses = s.courses.filterNot { it.id == courseId }) }

    fun setProgramCredits(credits: Int) = update(data.copy(totalProgramCredits = credits))

    fun replaceSemesters(semesters: List<Semester>) = update(data.copy(semesters = semesters))

    fun saveApiKey(key: String) {
        apiKey = key.trim()
        prefs.edit { putString(KEY_API, apiKey) }
    }

    fun sendChat() {
        val text = chatInput.trim()
        if (text.isEmpty() || chatLoading) return
        chatInput = ""
        chatError = null
        chat += ChatMessage(fromUser = true, text = text)
        chatLoading = true
        viewModelScope.launch {
            try {
                val reply = GeminiService.generate(apiKey, GeminiService.advisorPrompt(data), chat.toList())
                chat += ChatMessage(fromUser = false, text = reply)
            } catch (e: Exception) {
                // Trả câu hỏi về ô nhập để người dùng gửi lại
                chat.removeAt(chat.lastIndex)
                chatInput = text
                chatError = e.message ?: "Không gọi được Gemini"
            } finally {
                chatLoading = false
            }
        }
    }

    fun generateStudyPlan(exams: List<Pair<Course, LocalDate>>, hoursPerDay: Int) {
        studyPlanError = null
        studyPlanLoading = true
        viewModelScope.launch {
            try {
                val request = GeminiService.studyPlanRequest(exams, hoursPerDay, LocalDate.now())
                studyPlan = GeminiService.generate(
                    apiKey,
                    GeminiService.STUDY_PLANNER_PROMPT,
                    listOf(ChatMessage(fromUser = true, text = request)),
                )
            } catch (e: Exception) {
                studyPlanError = e.message ?: "Không gọi được Gemini"
            } finally {
                studyPlanLoading = false
            }
        }
    }

    private companion object {
        const val KEY_API = "gemini_api_key"
    }
}
