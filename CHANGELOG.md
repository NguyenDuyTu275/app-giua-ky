# Nhật ký thay đổi

## 2026-10-13

### Sửa lỗi lưu dữ liệu và crash

- **Lưu file an toàn:** thêm `services/DataFile.java`. Dữ liệu được ghi ra file tạm rồi mới đổi tên đè lên `academic.json`, nên tắt app giữa lúc lưu không làm hỏng file. Trước đây có thể mất cả bảng điểm.
- **File hỏng không bị ghi đè:** file dữ liệu bị lỗi được đổi tên thành `academic.json.corrupt-<thời điểm>` để giữ lại, app hiện thông báo rồi dùng dữ liệu mẫu.
- **Lưu không còn chờ AI:** `AppViewModel` ghi file trên một luồng riêng, tách khỏi luồng gọi Gemini. Sửa điểm thì màn hình cập nhật ngay, kể cả khi AI đang trả lời.
- **Bỏ một nguyên nhân crash:** dữ liệu được chuyển sang JSON trên luồng chính, không còn hai luồng cùng đọc và sửa danh sách môn.
- **Lỗi lưu không còn bị bỏ qua:** `MainActivity` hiện Toast khi lưu lỗi hoặc khi file bị hỏng.
- **Lỗi chia cho 0:** thêm `GpaCalculator.progressPercent()`. Tab Tổng quan và Chương trình không còn crash khi tổng tín chỉ chương trình bằng 0.
- **Kiểm tra số tín chỉ:** thêm `Course.isValidCredits()` (số nguyên từ 1 đến 20). Hộp thoại môn học và nhập Excel dùng chung hàm này. Trước đây nhập Excel nhận cả 50 tín chỉ và cắt 3.7 thành 3.
- **Giới hạn chat AI:** mỗi lần hỏi chỉ gửi 20 tin nhắn gần nhất (`GeminiService.recentHistory()`).

### Cải thiện giao diện

- **Xoay màn hình không mất dữ liệu ở tab Kế hoạch:** GPA mục tiêu, số tín chỉ, lựa chọn "Học cải thiện", môn đang chọn ở "Cứu môn" và tab con đang mở được lưu trong `AppViewModel`.
- **Biểu đồ đường không chồng chữ:** tên học kỳ dài bị cắt kèm "…". Khi có nhiều kỳ, biểu đồ chỉ hiện xen kẽ các nhãn vừa chỗ.
- **Chữ hiển thị chuyển sang `res/values/strings.xml`:** áp dụng cho layout, menu, 5 Fragment, hộp thoại môn học và `Ui`. Các thông báo trong `services/`, `types/` và `AppViewModel` vẫn viết trong code vì các lớp này không có `Context`.
- **Hỗ trợ TalkBack:**
  - Hai biểu đồ có mô tả để trình đọc màn hình đọc được.
  - Học kỳ trên cây chương trình báo trạng thái "Đang mở" / "Đã thu gọn".
  - Nút xóa thành phần điểm và nút bỏ ngày thi được nới lên 48dp.
- **Xóa môn có nút Hoàn tác:** xóa môn là xóa ngay, kèm Snackbar có nút "Hoàn tác". Xóa môn **không còn hỏi xác nhận**; xóa học kỳ vẫn hỏi như cũ.
- **Giảm vẽ lại thừa:** thêm `components/TabFragment.java`. Tab đang ẩn chỉ vẽ lại khi được mở. Chat chỉ thêm tin nhắn mới, không vẽ lại cả cuộc trò chuyện.

### Giao diện sáng / tối

- **Chọn chế độ sáng / tối / theo hệ thống:** nút "Giao diện" trên thanh tiêu đề tab Tổng quan. Lựa chọn được lưu (`ThemeSettings.java`) và áp dụng ngay.
- **Bảng màu riêng cho app:** màu Material 3 tông xanh `#1E5AA8` cho cả hai chế độ (`values/colors.xml`, `values-night/colors.xml`). Bỏ màu động theo hình nền (Android 12+) để app luôn cùng một tông màu.
- **Thẻ nhẹ hơn:** nền thẻ sáng hơn, bo góc 16dp. Thẻ GPA hệ 4 ở Tổng quan được tô màu nhấn.
- **Đổi tên tab "Cứu môn" thành "Điểm cần đạt"** cho đúng chức năng: tính điểm tối thiểu cần ở các thành phần còn lại của môn đang học.

### Test

- Thêm `DataFileTest`, `CourseTest`, `GeminiServiceTest` và một test `progressPercent` trong `GpaCalculatorTest`.
- Tổng cộng 19 test, tất cả đều qua. Lint giảm từ khoảng 70 xuống 8 cảnh báo.

### Tài liệu và cấu hình

- `.gitignore`: thêm `CLAUDE.md` (luật làm việc cho Claude Code, chỉ dùng trên máy cá nhân).
- `GIAI_THICH_CODE.md`: sửa tên model thành `gemini-2.5-flash` cho khớp với code, thêm mô tả cách lưu file mới.

### Lưu ý cho cả nhóm

- **Build:** Gradle không build được nếu đường dẫn thư mục dự án có dấu tiếng Việt (ví dụ "Máy tính"). Hãy để dự án ở đường dẫn không dấu, ví dụ `C:\Projects\app-giua-ky`.
- **Chưa kiểm tra trên máy thật:** các thay đổi giao diện chưa được chạy thử trên điện thoại hoặc máy ảo. Cần thử:
  - Xoay màn hình ở tab Kế hoạch.
  - Xóa một môn rồi bấm Hoàn tác.
  - Sửa điểm khi AI đang trả lời.
  - Nhập file Excel có môn 3.7 tín chỉ.
  - Bật TalkBack và thử với hai biểu đồ.
- **Các phần trong `GIAI_THICH_CODE.md` đã cũ:** số lượng unit test ("10 unit test") và bảng ở Mục 1 chưa có các file mới (`DataFile`, `TabFragment`, các test mới).
