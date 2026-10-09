# Giải thích code: GPA Planner (App giữa kỳ)

Ứng dụng Android tính GPA từ điểm thành phần theo quy chế tín chỉ, và lập kế hoạch học tập để đạt GPA mục tiêu.

| Mục | Giá trị |
|---|---|
| Ngôn ngữ | Java 11 |
| Giao diện | XML Views (Empty Views Activity) + Material Components 3 |
| Cấu hình build | Groovy DSL (`build.gradle`) |
| Min SDK | API 24 (Android 7.0) |
| Mã ứng dụng (package) | `com.appgiuaky.gpaplanner` |
| Thư viện ngoài | `androidx.appcompat`, `com.google.android.material`, JUnit (test) |

Không dùng thư viện nào khác: JSON đọc/ghi bằng `org.json` có sẵn trong Android, file Excel tự ghi/đọc bằng `java.util.zip`, gọi AI bằng `HttpURLConnection`.

---

## 1. Cấu trúc thư mục

```
app/src/main/java/com/appgiuaky/gpaplanner/
├── MainActivity.java          # Màn hình chính: thanh điều hướng 5 tab, đổi Fragment
├── AppViewModel.java          # Giữ dữ liệu dùng chung, lưu file, gọi AI ở luồng nền
├── components/                # Giao diện, mỗi thư mục là một chức năng
│   ├── Ui.java                # Hàm dựng giao diện dùng chung (nhãn điểm, thẻ, hộp thoại)
│   ├── dashboard/             # Chức năng 4: thẻ thống kê, biểu đồ đường & tròn
│   │   ├── DashboardFragment.java
│   │   ├── LineChartView.java # Biểu đồ đường tự vẽ bằng Canvas
│   │   └── PieChartView.java  # Biểu đồ vành khuyên tự vẽ bằng Canvas
│   ├── gradebook/             # Chức năng 1 & 6: bảng điểm CRUD, xuất/nhập Excel
│   │   ├── GradebookFragment.java
│   │   └── CourseDialog.java  # Hộp thoại thêm/sửa môn + điểm thành phần
│   ├── simulator/             # Chức năng 2 & 3: mục tiêu GPA, "cứu môn", học cải thiện
│   │   └── SimulatorFragment.java
│   ├── curriculum/            # Chức năng 5: cây chương trình đào tạo theo kỳ
│   │   ├── CurriculumFragment.java
│   │   └── TreeLineView.java  # Đường nối "├─" của cây
│   └── aiadvisor/             # Chức năng 7 & 8: cố vấn AI, lập lịch ôn thi
│       └── AIAdvisorFragment.java
├── services/
│   ├── GeminiService.java     # Gọi API Gemini + các câu prompt
│   └── ExcelIO.java           # Xuất/nhập file .xlsx
├── types/                     # Kiểu dữ liệu
│   ├── AcademicData.java      # Toàn bộ bảng điểm + đọc/ghi JSON
│   ├── Semester.java, Course.java, GradeComponent.java
│   ├── LetterGrade.java       # Một bậc thang điểm (A, B+, ...)
│   ├── Classification.java    # Xếp loại (Xuất sắc, Giỏi, ...)
│   └── CourseStatus.java      # Đã học / Đang học / Chưa học
└── utils/
    ├── GpaCalculator.java     # TOÀN BỘ công thức tính điểm (xem mục 3)
    ├── Format.java            # Định dạng/đọc số
    └── MockData.java          # Dữ liệu mẫu 4 học kỳ, 21 môn

app/src/main/res/
├── layout/      # XML giao diện: fragment_*.xml (màn hình), item_*.xml (một dòng/thẻ), dialog_*.xml
├── menu/        # bottom_nav.xml (5 tab), menu trên thanh tiêu đề từng màn hình
├── drawable/    # Icon vector (ic_*.xml), icon app
└── values/      # themes.xml (Material 3, sáng/tối), strings.xml, colors.xml

app/src/test/java/.../utils/GpaCalculatorTest.java   # 10 unit test cho công thức
```

---

## 2. Luồng dữ liệu

```
 ┌──────────────┐  bấm tab   ┌──────────────────────────┐
 │ MainActivity │ ─────────▶ │ 5 Fragment (show / hide) │
 └──────────────┘            └────────────┬─────────────┘
                                          │ observe(LiveData) / gọi hàm
                                          ▼
                             ┌──────────────────────────┐   ghi    ┌───────────────────┐
                             │       AppViewModel        │ ───────▶ │ files/academic.json│
                             │  LiveData<AcademicData>   │ ◀─────── │  (bộ nhớ trong máy)│
                             └────────────┬─────────────┘   đọc    └───────────────────┘
                                          │ luồng nền (ExecutorService)
                                          ▼
                             ┌──────────────────────────┐
                             │  GeminiService (HTTP)     │ ──▶ Google Gemini API
                             └──────────────────────────┘
```

- **MainActivity** chỉ có `FrameLayout` (chỗ đặt màn hình) và `BottomNavigationView`. Khi bấm tab, `showTab()` hiện Fragment của tab đó và **ẩn** (không hủy) các Fragment khác, nên quay lại tab cũ vẫn giữ nguyên trạng thái (ô đã nhập, môn đang chọn...).
- **AppViewModel** sống suốt vòng đời Activity (kể cả khi xoay màn hình). Mọi Fragment lấy chung một ViewModel qua `new ViewModelProvider(requireActivity())`.
- Khi dữ liệu đổi (thêm/sửa/xóa môn...), ViewModel gọi `save()`: ghi file JSON rồi `data.setValue(...)`. Mọi Fragment đang `observe` sẽ tự vẽ lại, ví dụ sửa điểm ở Bảng điểm thì Tổng quan và Kế hoạch cập nhật theo.
- Gọi mạng (Gemini) **không được chạy trên luồng giao diện**, nên ViewModel dùng `ExecutorService` chạy ở luồng nền rồi trả kết quả bằng `postValue(...)`.
- Lần đầu mở app (chưa có file), dữ liệu lấy từ `MockData` để demo được ngay.

---

## 3. Công thức tính điểm (`GpaCalculator.java`)

Mọi công thức nằm trong **một file duy nhất** và được kiểm tra bằng `GpaCalculatorTest`. Muốn đổi quy chế của trường thì chỉ cần sửa file này.

### 3.1 Thang điểm (hằng `SCALE`)

| Điểm hệ 10 | Điểm chữ | Hệ 4 |
|---|---|---|
| 8.5 – 10 | A | 4.0 |
| 8.0 – 8.4 | B+ | 3.5 |
| 7.0 – 7.9 | B | 3.0 |
| 6.5 – 6.9 | C+ | 2.5 |
| 5.5 – 6.4 | C | 2.0 |
| 5.0 – 5.4 | D+ | 1.5 |
| 4.0 – 4.9 | D | 1.0 |
| dưới 4.0 | F | 0.0 |

Xếp loại theo GPA tích lũy hệ 4 (`Classification`): Xuất sắc ≥ 3.6, Giỏi ≥ 3.2, Khá ≥ 2.5, Trung bình ≥ 2.0, Yếu ≥ 1.0, còn lại Kém.

### 3.2 Điểm một môn

```
Điểm học phần = Σ (điểm thành phần × trọng số %) / 100   → làm tròn 1 chữ số thập phân
```

Ví dụ: Chuyên cần 9 (10%), Giữa kỳ 7.5 (30%), Cuối kỳ 8 (60%) → 0.9 + 2.25 + 4.8 = 7.95 → **8.0 → B+**.

Hàm `round()` làm tròn 6 chữ số trước rồi mới làm tròn 1 chữ số, để tránh lỗi số thực (8.2499999… phải thành 8.3 chứ không phải 8.2).

Trạng thái môn (`status`): đủ điểm mọi thành phần là **Đã học**, có ít nhất một điểm là **Đang học**, chưa có điểm nào là **Chưa học**. Chỉ môn "Đã học" mới có điểm học phần.

### 3.3 GPA học kỳ và GPA tích lũy

```
GPA = Σ (điểm hệ 4 × số tín chỉ) / Σ số tín chỉ
```

| | GPA học kỳ (`semesterSummary`) | GPA tích lũy (`cumulative`) |
|---|---|---|
| Môn F | **có** tính | **không** tính (chưa tích lũy) |
| Môn học lại / cải thiện | tính lần học trong kỳ đó | chỉ lấy **lần điểm cao nhất** (gộp theo mã môn) |
| Môn "Không tính GPA" (GDTC, GDQP) | không tính | không tính |

`bestCourses()` gộp các lần học theo mã môn (không phân biệt hoa/thường) và giữ lần có điểm cao nhất.

### 3.4 Kế hoạch đạt GPA mục tiêu (`planTarget`)

Đây là yêu cầu chính của đề bài: *các kỳ sau cần trung bình bao nhiêu, đạt loại gì để được GPA mong muốn*.

```
GPA trung bình cần đạt = (mục tiêu × (TC hiện có + TC sắp học) − GPA hiện tại × TC hiện có) / TC sắp học
```

Ví dụ với dữ liệu mẫu: đang có 40 TC, GPA 3.1125; muốn đạt Giỏi (3.2) khi học thêm 90 TC:
`(3.2 × 130 − 3.1125 × 40) / 90 = (416 − 124.5) / 90 ≈ 3.24`.

App đưa ra 2 phương án:
- **Phương án 1:** mọi môn đạt cùng một mức. Đó là mức thấp nhất có điểm hệ 4 ≥ 3.24, tức **B+** (3.5).
- **Phương án 2:** kết hợp mức đó với mức ngay dưới (B = 3.0). Tỉ lệ tín chỉ cần đạt B+ là `(3.24 − 3.0) / (3.5 − 3.0) ≈ 0.478`. Nhân với 90 TC rồi làm tròn lên được **43 TC đạt B+**, còn **47 TC đạt B**. Kiểm tra: 43 × 3.5 + 47 × 3.0 = 291.5 = 416 − 124.5 ✓

Nếu kể cả đạt A hết mà vẫn không đủ, app báo "Không thể" kèm GPA tối đa có thể đạt.

### 3.5 "Cứu môn" (`rescue`)

Môn đang học còn thiếu điểm: với mỗi mức A…D, tìm điểm trung bình **nhỏ nhất** (bước 0.1, từ 0 đến 10) cần đạt ở các phần còn thiếu, sao cho điểm học phần (đã làm tròn) ≥ ngưỡng của mức đó.

Ví dụ: Chuyên cần 9 (10%), Giữa kỳ 8 (30%) → đã có 0.9 + 2.4 = 3.3 điểm; Cuối kỳ chiếm 60%. Để đạt A cần `3.3 + 0.6x ≥ 8.45`, suy ra **x = 8.6**. Kết quả 0.0 nghĩa là chắc chắn đạt, `null` nghĩa là không thể đạt.

### 3.6 Giả lập học cải thiện

`cumulative(semesters, improvements)` nhận thêm bảng *id môn → điểm chữ dự kiến*. Môn nào có điểm dự kiến **cao hơn** điểm hiện tại thì được tính bằng điểm mới. Danh sách môn được xếp theo mức tăng GPA tối đa `(4.0 − điểm hiện tại) × tín chỉ`, nên môn đứng đầu là môn đáng học cải thiện nhất.

---

## 4. Từng chức năng

| # | Chức năng | File chính | Ghi chú |
|---|---|---|---|
| 1 | Bảng điểm (thêm/sửa/xóa) | `GradebookFragment`, `CourseDialog` | Hộp thoại kiểm tra: mã/tên không trống, 1–20 TC, tổng trọng số = 100%, điểm 0–10. Nút Lưu tự xử lý để **không đóng** hộp thoại khi còn lỗi. |
| 2 | Kế hoạch GPA mục tiêu + cứu môn | `SimulatorFragment` (tab Mục tiêu GPA, Cứu môn) | Mục 3.4, 3.5 |
| 3 | Giả lập học cải thiện | `SimulatorFragment` (tab Học cải thiện) | Mục 3.6 |
| 4 | Tổng quan | `DashboardFragment`, `LineChartView`, `PieChartView` | Biểu đồ tự vẽ bằng `Canvas` trong `onDraw()`, không dùng thư viện biểu đồ |
| 5 | Chương trình đào tạo | `CurriculumFragment`, `TreeLineView` | Trạng thái môn: Đã đạt / Nợ môn / Đã học lại / Đang học / Chưa học; thanh tiến độ 4 đoạn; sửa tổng tín chỉ chương trình |
| 6 | Xuất/Nhập Excel | `ExcelIO`, nút ⋮ trên Bảng điểm | Dùng hộp chọn file của hệ thống (Storage Access Framework), không cần xin quyền bộ nhớ |
| 7 | Cố vấn học vụ AI | `AIAdvisorFragment` (tab Hỏi đáp), `GeminiService.advisorPrompt` | Gửi kèm toàn bộ bảng điểm dạng văn bản để AI trả lời theo số liệu thật |
| 8 | Lập lịch ôn thi | `AIAdvisorFragment` (tab Lịch ôn thi), `GeminiService.studyPlanRequest` | Chọn ngày thi bằng `MaterialDatePicker` (chỉ cho chọn từ hôm nay), số giờ ôn/ngày bằng `Slider` |

### Định dạng file Excel

Mỗi dòng là một môn: `Học kỳ | Mã môn | Tên môn | Số TC | Tính GPA | Điểm hệ 10 | Điểm chữ | Điểm hệ 4`, sau đó lặp lại bộ 3 cột `Thành phần | Trọng số % | Điểm` cho từng điểm thành phần. Khi nhập, 3 cột điểm tổng kết bị bỏ qua vì app tự tính lại. File .xlsx thực chất là file zip chứa các file XML: `ExcelIO` tự ghi các file đó khi xuất, và đọc lại bằng `XmlPullParser` khi nhập (hỗ trợ cả file tạo từ Excel thật, có `sharedStrings.xml`).

### Gemini API

- Gọi REST `models/gemini-3.8-flash:generateContent`, key gửi qua header `x-goog-api-key`.
- Người dùng tự nhập key (nút 🔑 trên tab Cố vấn AI). Key chỉ lưu trong `SharedPreferences` trên máy, không có trong code.
- Prompt yêu cầu trả lời tiếng Việt, văn bản thuần; app còn bỏ ký hiệu markdown (`**`, `#`) trước khi hiển thị.
- Gửi lỗi (sai key, mất mạng...) thì app hiện thông báo lỗi và trả câu hỏi về ô nhập để gửi lại.

---

## 5. Lưu dữ liệu

- Bảng điểm: `files/academic.json` trong bộ nhớ riêng của app (`AcademicData.toJson()` / `fromJson()`). Định dạng giữ nguyên như bản 1.0 (viết bằng Kotlin), nên người đã cài bản cũ cập nhật lên vẫn còn dữ liệu.
- API key: `SharedPreferences` tên `settings`.
- Ngày thi đã chọn chỉ giữ trong bộ nhớ (mất khi tắt hẳn app).

---

## 6. Build, chạy, kiểm thử

Mở thư mục dự án bằng **Android Studio** rồi bấm Run. Hoặc dùng dòng lệnh (Windows dùng `gradlew.bat`):

| Lệnh | Tác dụng |
|---|---|
| `gradlew assembleDebug` | Build bản debug → `app/build/outputs/apk/debug/app-debug.apk` |
| `gradlew testDebugUnitTest` | Chạy 10 unit test của `GpaCalculator` |
| `gradlew lintDebug` | Kiểm tra lỗi (ví dụ dùng API mới hơn Android 7.0) |
| `gradlew assembleRelease` | Build bản phát hành đã ký → `app/build/outputs/apk/release/app-release.apk` (~1.9 MB) |

**Ký bản release:** `app/build.gradle` đọc thông tin khóa từ `keystore.properties` và file khóa `release.jks` ở thư mục gốc. **Hai file này không có trên git** (xem `.gitignore`) và phải được sao lưu cẩn thận: mất khóa thì không phát hành được bản cập nhật cài đè lên app cũ. Máy không có hai file này vẫn build được bản debug.

**Phát hành bản mới:** tăng `versionCode` (và `versionName`) trong `app/build.gradle`. Nếu không tăng, cài bản mới đè lên bản cũ sẽ bị từ chối.

---

## 7. Muốn sửa thì sửa ở đâu?

| Muốn | Sửa |
|---|---|
| Thang điểm khác (ví dụ thêm A+) | `SCALE` trong `GpaCalculator.java` |
| Mốc xếp loại | `Classification.java` |
| Cơ cấu điểm mặc định của môn mới (10/30/60) | `MockData.defaultComponents()` |
| Dữ liệu mẫu | `MockData.create()` |
| Màu nhãn điểm chữ | `Ui.gradeColor()` |
| Màu chủ đạo của app | `res/values/themes.xml` (`colorPrimary`) |
| Nội dung câu hỏi gửi AI | `GeminiService.advisorPrompt()` và `STUDY_PLANNER_PROMPT` |
| Thêm một tab mới | `res/menu/bottom_nav.xml` + `MainActivity.TABS` / `create()` + Fragment mới |

Sau khi sửa công thức, chạy lại `gradlew testDebugUnitTest` để chắc không làm sai các phép tính cũ.
