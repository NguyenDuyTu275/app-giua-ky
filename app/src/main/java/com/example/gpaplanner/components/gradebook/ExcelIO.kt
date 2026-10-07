package com.example.gpaplanner.components.gradebook

import android.util.Xml
import com.example.gpaplanner.types.Course
import com.example.gpaplanner.types.GradeComponent
import com.example.gpaplanner.types.Semester
import com.example.gpaplanner.utils.GpaCalculator
import com.example.gpaplanner.utils.parseNumber
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.abs

/**
 * Xuất/Nhập bảng điểm dạng file Excel (.xlsx) tối giản, không cần thư viện ngoài.
 * Mỗi dòng là một môn: Học kỳ | Mã môn | Tên môn | Số TC | Tính GPA | Điểm hệ 10 | Điểm chữ | Điểm hệ 4,
 * sau đó lặp lại bộ 3 cột (Thành phần, Trọng số %, Điểm) cho từng điểm thành phần.
 * Khi nhập, 3 cột điểm tổng kết được bỏ qua vì app tự tính lại.
 */
object ExcelIO {

    const val MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    private val HEADER = listOf("Học kỳ", "Mã môn", "Tên môn", "Số TC", "Tính GPA", "Điểm hệ 10", "Điểm chữ", "Điểm hệ 4")
    private const val SHEET = "xl/worksheets/sheet1.xml"
    private const val SHARED_STRINGS = "xl/sharedStrings.xml"

    fun export(semesters: List<Semester>, out: OutputStream) {
        val maxComponents = semesters.flatMap { it.courses }.maxOfOrNull { it.components.size } ?: 0
        val header = HEADER + (1..maxComponents).flatMap { listOf("Thành phần $it", "Trọng số $it (%)", "Điểm $it") }
        val rows: List<List<Any?>> = listOf(header) + semesters.flatMap { s ->
            s.courses.map { c ->
                val score = GpaCalculator.courseScore(c)
                val grade = score?.let(GpaCalculator::letterOf)
                listOf(s.name, c.code, c.name, c.credits, if (c.countInGpa) "Có" else "Không", score, grade?.letter, grade?.point) +
                    c.components.flatMap { listOf(it.name, it.weight, it.score) }
            }
        }
        ZipOutputStream(out).use { zip ->
            fun entry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
            entry("[Content_Types].xml", CONTENT_TYPES)
            entry("_rels/.rels", ROOT_RELS)
            entry("xl/workbook.xml", WORKBOOK)
            entry("xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
            entry(SHEET, sheetXml(rows))
        }
    }

    /** Đọc file .xlsx, trả về danh sách học kỳ. Ném IllegalArgumentException kèm lý do nếu file sai định dạng. */
    fun import(input: InputStream): List<Semester> {
        val files = mutableMapOf<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                if (entry.name == SHEET || entry.name == SHARED_STRINGS) files[entry.name] = zip.readBytes()
            }
        }
        val sheet = files[SHEET] ?: throw IllegalArgumentException("File không phải định dạng Excel .xlsx")
        val shared = files[SHARED_STRINGS]?.let(::parseSharedStrings).orEmpty()

        val semesters = linkedMapOf<String, MutableList<Course>>()
        parseSheet(sheet, shared).drop(1).forEach { (rowNumber, cells) ->
            fun cell(i: Int) = cells.getOrNull(i)?.trim().orEmpty()
            fun fail(reason: String): Nothing = throw IllegalArgumentException("Dòng $rowNumber: $reason")

            if (cells.all { it.isBlank() }) return@forEach
            val semester = cell(0).ifEmpty { fail("thiếu tên học kỳ") }
            val code = cell(1).ifEmpty { fail("thiếu mã môn") }
            val credits = parseNumber(cell(3))?.toInt()?.takeIf { it > 0 } ?: fail("số tín chỉ không hợp lệ")
            val components = (HEADER.size until cells.size step 3).mapNotNull { i ->
                val name = cell(i).ifEmpty { return@mapNotNull null }
                val weight = parseNumber(cell(i + 1))?.takeIf { it > 0 } ?: fail("trọng số của \"$name\" không hợp lệ")
                val score = cell(i + 2).ifEmpty { null }?.let {
                    parseNumber(it)?.takeIf { s -> s in 0.0..10.0 } ?: fail("điểm của \"$name\" không hợp lệ")
                }
                GradeComponent(name, weight, score?.let(GpaCalculator::round2))
            }
            if (components.isEmpty()) fail("chưa có điểm thành phần")
            if (abs(components.sumOf { it.weight } - 100) > 0.01) fail("tổng trọng số phải bằng 100%")
            semesters.getOrPut(semester) { mutableListOf() } += Course(
                code = code,
                name = cell(2).ifEmpty { code },
                credits = credits,
                components = components,
                countInGpa = cell(4).lowercase() !in setOf("không", "khong", "0", "false", "no"),
            )
        }
        if (semesters.isEmpty()) throw IllegalArgumentException("File không có môn học nào")
        return semesters.map { (name, courses) -> Semester(name = name, courses = courses) }
    }

    // ------------------------------------------------------------ Ghi XML

    private fun sheetXml(rows: List<List<Any?>>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
        rows.forEachIndexed { r, row ->
            append("""<row r="${r + 1}">""")
            row.forEachIndexed { c, value ->
                val ref = columnName(c) + (r + 1)
                when (value) {
                    null -> Unit
                    is Number -> append("""<c r="$ref"><v>$value</v></c>""")
                    else -> append("""<c r="$ref" t="inlineStr"><is><t>${escape(value.toString())}</t></is></c>""")
                }
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun columnName(index: Int): String {
        var n = index + 1
        val sb = StringBuilder()
        while (n > 0) {
            sb.insert(0, 'A' + (n - 1) % 26)
            n = (n - 1) / 26
        }
        return sb.toString()
    }

    private fun columnIndex(cellRef: String): Int =
        cellRef.takeWhile { it.isLetter() }.fold(0) { acc, ch -> acc * 26 + (ch.uppercaseChar() - 'A' + 1) } - 1

    // ------------------------------------------------------------ Đọc XML

    private fun parser(bytes: ByteArray): XmlPullParser =
        Xml.newPullParser().apply { setInput(bytes.inputStream(), "UTF-8") }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val result = mutableListOf<String>()
        val text = StringBuilder()
        var inText = false
        val p = parser(bytes)
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "si" -> text.clear()
                    "t" -> inText = true
                }
                XmlPullParser.TEXT -> if (inText) text.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "t" -> inText = false
                    "si" -> result += text.toString()
                }
            }
        }
        return result
    }

    /** Trả về các dòng (số dòng trong Excel, giá trị từng cột dạng chuỗi). */
    private fun parseSheet(bytes: ByteArray, shared: List<String>): List<Pair<Int, List<String>>> {
        val rows = mutableListOf<Pair<Int, List<String>>>()
        var rowNumber = 0
        var row = mutableMapOf<Int, String>()
        var column = 0
        var type: String? = null
        val text = StringBuilder()
        var inValue = false
        val p = parser(bytes)
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "row" -> {
                        rowNumber = p.getAttributeValue(null, "r")?.toIntOrNull() ?: (rowNumber + 1)
                        row = mutableMapOf()
                    }
                    "c" -> {
                        column = p.getAttributeValue(null, "r")?.let(::columnIndex) ?: ((row.keys.maxOrNull() ?: -1) + 1)
                        type = p.getAttributeValue(null, "t")
                        text.clear()
                    }
                    "v", "t" -> inValue = true
                }
                XmlPullParser.TEXT -> if (inValue) text.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "v", "t" -> inValue = false
                    "c" -> row[column] = if (type == "s") shared[text.toString().trim().toInt()] else text.toString()
                    "row" -> rows += rowNumber to List((row.keys.maxOrNull() ?: -1) + 1) { row[it].orEmpty() }
                }
            }
        }
        return rows
    }

    // ------------------------------------------------------------ Khung file .xlsx

    private const val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""

    private const val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private const val WORKBOOK = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets><sheet name="BangDiem" sheetId="1" r:id="rId1"/></sheets>
</workbook>"""

    private const val WORKBOOK_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>"""
}
