package com.appgiuaky.gpaplanner.services;

import android.util.Xml;

import com.appgiuaky.gpaplanner.types.Course;
import com.appgiuaky.gpaplanner.types.GradeComponent;
import com.appgiuaky.gpaplanner.types.LetterGrade;
import com.appgiuaky.gpaplanner.types.Semester;
import com.appgiuaky.gpaplanner.utils.Format;
import com.appgiuaky.gpaplanner.utils.GpaCalculator;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Xuất/Nhập bảng điểm dạng file Excel (.xlsx) tối giản, không cần thư viện ngoài.
 * Mỗi dòng là một môn: Học kỳ | Mã môn | Tên môn | Số TC | Tính GPA | Điểm hệ 10 | Điểm chữ | Điểm hệ 4,
 * sau đó lặp lại bộ 3 cột (Thành phần, Trọng số %, Điểm) cho từng điểm thành phần.
 * Khi nhập, 3 cột điểm tổng kết được bỏ qua vì app tự tính lại.
 */
public final class ExcelIO {

    private ExcelIO() {
    }

    public static final String MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private static final List<String> HEADER = Arrays.asList(
            "Học kỳ", "Mã môn", "Tên môn", "Số TC", "Tính GPA", "Điểm hệ 10", "Điểm chữ", "Điểm hệ 4");
    private static final String SHEET = "xl/worksheets/sheet1.xml";
    private static final String SHARED_STRINGS = "xl/sharedStrings.xml";

    public static void export(List<Semester> semesters, OutputStream out) throws IOException {
        int maxComponents = 0;
        for (Semester s : semesters) for (Course c : s.courses) maxComponents = Math.max(maxComponents, c.components.size());

        List<List<Object>> rows = new ArrayList<>();
        List<Object> header = new ArrayList<>(HEADER);
        for (int i = 1; i <= maxComponents; i++) {
            header.add("Thành phần " + i);
            header.add("Trọng số " + i + " (%)");
            header.add("Điểm " + i);
        }
        rows.add(header);
        for (Semester s : semesters) {
            for (Course c : s.courses) {
                Double score = GpaCalculator.courseScore(c);
                LetterGrade grade = score == null ? null : GpaCalculator.letterOf(score);
                List<Object> row = new ArrayList<>(Arrays.<Object>asList(
                        s.name, c.code, c.name, c.credits, c.countInGpa ? "Có" : "Không", score,
                        grade == null ? null : grade.letter, grade == null ? null : grade.point));
                for (GradeComponent g : c.components) {
                    row.add(g.name);
                    row.add(g.weight);
                    row.add(g.score);
                }
                rows.add(row);
            }
        }

        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            entry(zip, "[Content_Types].xml", CONTENT_TYPES);
            entry(zip, "_rels/.rels", ROOT_RELS);
            entry(zip, "xl/workbook.xml", WORKBOOK);
            entry(zip, "xl/_rels/workbook.xml.rels", WORKBOOK_RELS);
            entry(zip, SHEET, sheetXml(rows));
        }
    }

    private static void entry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    /** Đọc file .xlsx, trả về danh sách học kỳ. Ném IllegalArgumentException kèm lý do nếu file sai định dạng. */
    public static List<Semester> importFile(InputStream input) throws IOException {
        Map<String, byte[]> files = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().equals(SHEET) || entry.getName().equals(SHARED_STRINGS)) {
                    files.put(entry.getName(), readAll(zip));
                }
            }
        }
        byte[] sheet = files.get(SHEET);
        if (sheet == null) throw new IllegalArgumentException("File không phải định dạng Excel .xlsx");
        byte[] sharedBytes = files.get(SHARED_STRINGS);

        List<SheetRow> rows;
        try {
            List<String> shared = sharedBytes == null ? new ArrayList<String>() : parseSharedStrings(sharedBytes);
            rows = parseSheet(sheet, shared);
        } catch (XmlPullParserException e) {
            throw new IllegalArgumentException("File Excel bị lỗi: " + e.getMessage());
        }

        Map<String, List<Course>> semesters = new LinkedHashMap<>();
        for (int r = 1; r < rows.size(); r++) {   // bỏ dòng tiêu đề
            SheetRow row = rows.get(r);
            if (row.isBlank()) continue;
            String semester = row.cell(0);
            if (semester.isEmpty()) throw row.fail("thiếu tên học kỳ");
            String code = row.cell(1);
            if (code.isEmpty()) throw row.fail("thiếu mã môn");
            Double creditsValue = Format.parseNumber(row.cell(3));
            if (creditsValue == null || creditsValue.intValue() <= 0) throw row.fail("số tín chỉ không hợp lệ");

            List<GradeComponent> components = new ArrayList<>();
            double weightSum = 0;
            for (int i = HEADER.size(); i < row.cells.size(); i += 3) {
                String name = row.cell(i);
                if (name.isEmpty()) continue;
                Double weight = Format.parseNumber(row.cell(i + 1));
                if (weight == null || weight <= 0) throw row.fail("trọng số của \"" + name + "\" không hợp lệ");
                String scoreText = row.cell(i + 2);
                Double score = null;
                if (!scoreText.isEmpty()) {
                    score = Format.parseNumber(scoreText);
                    if (score == null || score < 0 || score > 10) throw row.fail("điểm của \"" + name + "\" không hợp lệ");
                    score = GpaCalculator.round2(score);
                }
                components.add(new GradeComponent(name, weight, score));
                weightSum += weight;
            }
            if (components.isEmpty()) throw row.fail("chưa có điểm thành phần");
            if (Math.abs(weightSum - 100) > 0.01) throw row.fail("tổng trọng số phải bằng 100%");

            String countText = row.cell(4).toLowerCase(Locale.ROOT);
            boolean countInGpa = !Arrays.asList("không", "khong", "0", "false", "no").contains(countText);
            String name = row.cell(2).isEmpty() ? code : row.cell(2);
            List<Course> list = semesters.get(semester);
            if (list == null) {
                list = new ArrayList<>();
                semesters.put(semester, list);
            }
            list.add(new Course(code, name, creditsValue.intValue(), components, countInGpa));
        }
        if (semesters.isEmpty()) throw new IllegalArgumentException("File không có môn học nào");

        List<Semester> result = new ArrayList<>();
        for (Map.Entry<String, List<Course>> e : semesters.entrySet()) result.add(new Semester(e.getKey(), e.getValue()));
        return result;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
        return out.toByteArray();
    }

    // ------------------------------------------------------------ Ghi XML

    private static String sheetXml(List<List<Object>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
        for (int r = 0; r < rows.size(); r++) {
            sb.append("<row r=\"").append(r + 1).append("\">");
            List<Object> row = rows.get(r);
            for (int c = 0; c < row.size(); c++) {
                Object value = row.get(c);
                if (value == null) continue;
                String ref = columnName(c) + (r + 1);
                if (value instanceof Number) {
                    sb.append("<c r=\"").append(ref).append("\"><v>").append(value).append("</v></c>");
                } else {
                    sb.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t>")
                            .append(escape(value.toString())).append("</t></is></c>");
                }
            }
            sb.append("</row>");
        }
        sb.append("</sheetData></worksheet>");
        return sb.toString();
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String columnName(int index) {
        int n = index + 1;
        StringBuilder sb = new StringBuilder();
        while (n > 0) {
            sb.insert(0, (char) ('A' + (n - 1) % 26));
            n = (n - 1) / 26;
        }
        return sb.toString();
    }

    private static int columnIndex(String cellRef) {
        int result = 0;
        for (int i = 0; i < cellRef.length() && Character.isLetter(cellRef.charAt(i)); i++) {
            result = result * 26 + (Character.toUpperCase(cellRef.charAt(i)) - 'A' + 1);
        }
        return result - 1;
    }

    // ------------------------------------------------------------ Đọc XML

    /** Một dòng của sheet: số dòng trong Excel và giá trị từng cột dạng chuỗi. */
    private static final class SheetRow {
        final int number;
        final List<String> cells;

        SheetRow(int number, List<String> cells) {
            this.number = number;
            this.cells = cells;
        }

        String cell(int i) {
            return i < cells.size() ? cells.get(i).trim() : "";
        }

        boolean isBlank() {
            for (String c : cells) if (!c.trim().isEmpty()) return false;
            return true;
        }

        IllegalArgumentException fail(String reason) {
            return new IllegalArgumentException("Dòng " + number + ": " + reason);
        }
    }

    private static XmlPullParser parser(byte[] bytes) throws XmlPullParserException {
        XmlPullParser p = Xml.newPullParser();
        p.setInput(new ByteArrayInputStream(bytes), "UTF-8");
        return p;
    }

    private static List<String> parseSharedStrings(byte[] bytes) throws XmlPullParserException, IOException {
        List<String> result = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        boolean inText = false;
        XmlPullParser p = parser(bytes);
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            switch (p.getEventType()) {
                case XmlPullParser.START_TAG:
                    if (p.getName().equals("si")) text.setLength(0);
                    else if (p.getName().equals("t")) inText = true;
                    break;
                case XmlPullParser.TEXT:
                    if (inText) text.append(p.getText());
                    break;
                case XmlPullParser.END_TAG:
                    if (p.getName().equals("t")) inText = false;
                    else if (p.getName().equals("si")) result.add(text.toString());
                    break;
                default:
                    break;
            }
        }
        return result;
    }

    private static List<SheetRow> parseSheet(byte[] bytes, List<String> shared) throws XmlPullParserException, IOException {
        List<SheetRow> rows = new ArrayList<>();
        int rowNumber = 0;
        TreeMap<Integer, String> row = new TreeMap<>();
        int column = 0;
        String type = null;
        StringBuilder text = new StringBuilder();
        boolean inValue = false;
        XmlPullParser p = parser(bytes);
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            switch (p.getEventType()) {
                case XmlPullParser.START_TAG:
                    switch (p.getName()) {
                        case "row":
                            Integer r = Format.parseInt(String.valueOf(p.getAttributeValue(null, "r")));
                            rowNumber = r != null ? r : rowNumber + 1;
                            row = new TreeMap<>();
                            break;
                        case "c":
                            String ref = p.getAttributeValue(null, "r");
                            column = ref != null ? columnIndex(ref) : (row.isEmpty() ? 0 : row.lastKey() + 1);
                            type = p.getAttributeValue(null, "t");
                            text.setLength(0);
                            break;
                        case "v":
                        case "t":
                            inValue = true;
                            break;
                        default:
                            break;
                    }
                    break;
                case XmlPullParser.TEXT:
                    if (inValue) text.append(p.getText());
                    break;
                case XmlPullParser.END_TAG:
                    switch (p.getName()) {
                        case "v":
                        case "t":
                            inValue = false;
                            break;
                        case "c":
                            row.put(column, "s".equals(type)
                                    ? shared.get(Integer.parseInt(text.toString().trim()))
                                    : text.toString());
                            break;
                        case "row":
                            int size = row.isEmpty() ? 0 : row.lastKey() + 1;
                            List<String> cells = new ArrayList<>();
                            for (int i = 0; i < size; i++) cells.add(row.containsKey(i) ? row.get(i) : "");
                            rows.add(new SheetRow(rowNumber, cells));
                            break;
                        default:
                            break;
                    }
                    break;
                default:
                    break;
            }
        }
        return rows;
    }

    // ------------------------------------------------------------ Khung file .xlsx

    private static final String CONTENT_TYPES = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">\n"
            + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>\n"
            + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>\n"
            + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>\n"
            + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>\n"
            + "</Types>";

    private static final String ROOT_RELS = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n"
            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>\n"
            + "</Relationships>";

    private static final String WORKBOOK = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">\n"
            + "<sheets><sheet name=\"BangDiem\" sheetId=\"1\" r:id=\"rId1\"/></sheets>\n"
            + "</workbook>";

    private static final String WORKBOOK_RELS = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n"
            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>\n"
            + "</Relationships>";
}
