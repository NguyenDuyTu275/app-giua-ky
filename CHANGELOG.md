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

### Đăng nhập bằng Google

- Nút tài khoản trên thanh tiêu đề tab Tổng quan, mở hộp thoại để đăng nhập hoặc đăng xuất. Đã đăng nhập thì nút hiện ảnh đại diện (hoặc chữ cái đầu của tên) và tiêu đề có thêm dòng "Xin chào, <tên>".
- Đăng nhập **chỉ để hiển thị**: tên và email lưu trong máy, ảnh đại diện lưu thành file trong máy. Bảng điểm không gắn với tài khoản và không gửi lên mạng.
- Dùng Credential Manager của Android (`AccountViewModel`, `components/account/AccountUi.java`, `services/AvatarStore.java`), **không dùng Firebase**. Thêm 3 thư viện: `androidx.credentials:credentials` và `credentials-play-services-auth` 1.6.0, `com.google.android.libraries.identity.googleid:googleid` 1.2.1.
- Thêm `UserProfileTest` (tổng 31 test).
- Chưa làm đăng nhập Facebook.

**Cài đặt (mỗi người tự làm một lần, cần tài khoản Google):**
1. Vào Google Cloud Console, tạo project rồi cấu hình **OAuth consent screen**. Khi app còn ở chế độ Testing, thêm email của các thành viên vào danh sách Test users.
2. Tạo **OAuth client ID** loại **Android**: package `com.appgiuaky.gpaplanner`, SHA-1 lấy từ khóa debug trên máy mình bằng lệnh `keytool -list -v -keystore %USERPROFILE%\.android\debug.keystore -alias androiddebugkey -storepass android`. Mỗi máy build có SHA-1 riêng, nên phải thêm SHA-1 của từng máy.
3. Tạo thêm **OAuth client ID** loại **Web application**, rồi chép Client ID của nó vào `local.properties`: `GOOGLE_WEB_CLIENT_ID=xxxx.apps.googleusercontent.com`. Sau đó build lại.
4. Máy ảo hoặc điện thoại phải có Google Play và đã thêm một tài khoản Google.

Chưa cấu hình mà bấm đăng nhập thì app chỉ báo "Chưa cấu hình đăng nhập Google", không bị crash.

### Cố vấn AI

- **Đổi model sang `gemini-3.8-flash`:** Google không còn cấp `gemini-2.5-flash` cho người dùng mới, nên key tạo gần đây bị từ chối.
- **Câu trả lời hiện dần (streaming)** thay vì chờ viết xong cả bài; lịch ôn thi cũng vậy. Mức suy nghĩ đặt `thinkingLevel: low` để bắt đầu trả lời sớm.
- **Model dự phòng:** khi model chính quá tải hoặc hết lượt (429/500/503/504, hoặc 12 giây chưa có chữ), tự thử `gemini-3.6-flash` rồi `gemini-3.5-flash-lite`.
- **Không còn treo "Đang trả lời…":** quá 40 giây chưa có chữ thì báo lỗi và trả câu hỏi về ô nhập. Trước đây, khi mạng hỏng, bước tìm địa chỉ máy chủ (DNS) có thể treo hàng phút mà không báo gì.
- **Báo đúng lỗi:** đọc được câu báo lỗi của Google trong phản hồi dạng stream (trước đây đọc tới hết nên bị treo); lỗi mạng hiện bằng tiếng Việt.
- Mỗi lần gọi chạy trên một luồng riêng, nên một lần gọi bị treo không làm lần gửi sau phải chờ.
- Thêm 8 test cho phần đọc dữ liệu stream, bỏ markdown và chọn model dự phòng (tổng 27 test).

### Biểu đồ, thanh điều hướng và hiệu ứng

- **Biểu đồ GPA theo học kỳ (`LineChartView`):**
  - Đường GPA kỳ màu đỏ, cong mượt, có vùng tô nhạt dần bên dưới. Đường GPA tích lũy màu xanh, nét đứt nên vẫn phân biệt được khi không nhìn rõ màu.
  - Chấm dạng vòng, số GPA hiện ngay cạnh mỗi điểm.
  - Đường vẽ dần từ trái sang.
  - Chạm vào một kỳ để xem ô số liệu, chạm lại để ẩn. Với TalkBack, mỗi lần chạm đúp chuyển sang kỳ tiếp theo.
- **Biểu đồ điểm chữ (`PieChartView`):** các phần cách nhau một khe nhỏ, vành quay dần khi mở, số môn hiện to ở giữa. Chạm vào một phần thì phần đó dày lên và giữa vành hiện điểm chữ cùng số môn.
- Hai biểu đồ chạy lại hiệu ứng mỗi lần quay lại tab Tổng quan. Thanh tiến độ tín chỉ (Tổng quan, Chương trình) chạy dần từ trái sang.
- **Thanh điều hướng dưới:**
  - Bo tròn hai góc trên.
  - Mục đang chọn là viên đỏ với icon trắng, nhãn đỏ đậm.
  - Icon nảy nhẹ khi bấm.
- **Chuyển tab:** màn hình mới trượt nhẹ vào từ phía của tab được chọn (`res/anim/tab_*.xml`). Đổi mục con ở Kế hoạch và Cố vấn AI, cùng tin nhắn chat mới, hiện mờ dần (`Ui.fadeIn()`).
- **Hiệu ứng ấn:** nút, chip và dòng môn học thu nhỏ nhẹ khi ấn (`res/animator/press_scale*.xml`). Học kỳ trên cây chương trình mở/đóng mượt.
- Hiệu ứng biểu đồ, mờ dần và ấn nút dùng Animator nên tự tắt khi máy đặt "Thang thời lượng animator" về 0 (ví dụ bật "Xóa hiệu ứng động"). Chưa kiểm tra hiệu ứng chuyển tab trong trường hợp này.

### Giao diện theo phong cách gradeptit.web.app

- **Màu đỏ PTIT `#BB2019`** thay cho tông xanh, có cả bản sáng và tối (`values/colors.xml`, `values-night/colors.xml`). Chữ và viền dùng tông xám xanh như trang web. Trang web không có chế độ tối nên bản tối là tự dựng, cùng tông đỏ.
- **Thanh tiêu đề đỏ chuyển màu, chữ trắng**, nối liền với dải sau thanh trạng thái (`bg_header.xml`, `status_bar_background` trong `activity_main.xml`). Icon thanh trạng thái luôn trắng.
- **Thẻ trắng viền mảnh, bóng nhẹ, bo góc 16dp.** Thẻ học kỳ ở Bảng điểm có viền hồng `#FF9999`. Thẻ GPA hệ 4 ở Tổng quan có nền đỏ chuyển màu.
- **GPA học kỳ hiện dạng viên:** viên đỏ "GPA 3.27" và viên viền đỏ "Hệ 10: 7.77" (`Ui.tag()`).
- **Tab Điểm cần đạt:** ô "Cần ≥ …" nền hồng viền đỏ, chuyển sang xanh khi chắc chắn đạt, giống ô điểm mục tiêu trên web.
- **Chip, ô nhập, thanh điều hướng dưới:** chip chọn thì nền đỏ chữ trắng; ô nhập nền hồng nhạt; mục đang chọn ở thanh dưới màu đỏ.
- **Font Be Vietnam Pro** (giấy phép SIL OFL, file giấy phép ở `licenses/BeVietnamPro-OFL.txt`). Trang web dùng Outfit nhưng Outfit thiếu 96/134 chữ tiếng Việt có dấu, nên chọn font cùng kiểu dáng có đủ dấu. APK nặng thêm khoảng 410KB.
- Giữ nguyên tên app, icon app, màu điểm chữ (A, B+, B…) và màu hai đường biểu đồ.

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
