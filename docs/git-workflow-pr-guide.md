# GIT WORKFLOW & PULL REQUEST GUIDE

Quy trình chuẩn hóa cho toàn bộ thành viên Z-One Team.

## Tổng Quan

Tài liệu này mô tả quy trình Git chuẩn của Z-One Team, từ cập nhật branch, commit code, đến tạo Pull Request và review. Mọi thành viên cần tuân thủ để đảm bảo chất lượng và tính nhất quán của codebase.

Hai repo AgriShrimp hiện tại đang dùng `main` là branch gốc để đồng bộ. Nếu một repo khác trong team sử dụng `develop`, hãy thay `main` bằng branch gốc tương ứng theo quy ước của repo đó.

## 1. Cập Nhật Branch Mới Nhất

Trước khi bắt đầu task mới hoặc trước khi tạo Pull Request, luôn đồng bộ branch local với branch gốc mới nhất:

```bash
# Chuyển về main và pull code mới nhất
git checkout main
git pull origin main

# Chuyển sang feature branch của mình
git checkout feature/<ten-task-cu-the>

# Đồng bộ với branch gốc
git rebase main
# hoặc
git merge main
```

Lưu ý:

- Ưu tiên `rebase` để giữ lịch sử commit sạch và dễ review.
- Dùng `merge` nếu branch đã được push lên remote và có người khác đang cùng làm việc trên branch đó.
- Sau khi `rebase`, nếu branch đã push trước đó, cần cân nhắc kỹ trước khi `push --force-with-lease`.

## 2. Commit Code

Mỗi commit phải có message rõ ràng theo chuẩn Conventional Commits:

```bash
git add .
git commit -m "<type>: <mô tả ngắn gọn>"
```

### Các loại commit hợp lệ

| Type | Ý nghĩa và ví dụ |
| --- | --- |
| `feat` | Tính năng mới - `feat: wallet membership integration` |
| `fix` | Sửa lỗi - `fix: transaction history pagination` |
| `refactor` | Cải thiện cấu trúc code - `refactor: wallet data logic` |
| `chore` | Tác vụ hỗ trợ, deps, config - `chore: update dependencies` |
| `docs` | Cập nhật tài liệu - `docs: add git workflow guide` |
| `style` | Format, spacing, không đổi logic - `style: reformat login screen` |
| `test` | Thêm hoặc sửa test - `test: add unit test for auth service` |

## 3. Push Lên Remote Branch

```bash
# Lần đầu tiên push branch mới
git push -u origin feature/<your-branch>

# Các lần push tiếp theo
git push
```

## 4. Tạo Pull Request

### Đặt tên PR

Sử dụng tiền tố viết hoa để phân loại:

- `[FEATURE] Wallet Membership Integration`
- `[BUG] Fix Transaction History Pagination`
- `[REFACTOR] Optimize Wallet Data Logic`
- `[CHORE] Update Dependencies`

### Template mô tả PR

```md
## Description
Mô tả ngắn gọn những gì PR này thực hiện.

---
## Related Issues
Closes #<issue-number>

---
## Changes
### Features
- ...

### Bug Fixes
- ...

### Refactor
- ...

## Screenshots
(Đính kèm nếu có thay đổi UI)

## Testing
- [ ] Backend tested
- [ ] Frontend tested
- [ ] API tested
```

### Ví dụ Pull Request hoàn chỉnh

**Title:** `[FEATURE] Wallet Membership Integration`

```md
## Description
Tích hợp Membership API cho Wallet và tối ưu Transaction History.

## Related Issues
Closes #54
Closes #57
Closes #59

## Changes
### Features
- Tích hợp Membership API
- Thêm infinity scroll cho Transaction History
- Thêm view detail link

### Bug Fixes
- Fix bottom navigation
- Fix wallet card badge

## Testing
- [x] Android
- [x] iOS
- [x] API tested
```

## Checklist Trước Khi Tạo PR

Đảm bảo tất cả các mục dưới đây đã hoàn thành trước khi submit PR:

### Code & Build

- [ ] Pull hoặc rebase từ `main`
- [ ] Resolve conflict nếu có
- [ ] Build thành công
- [ ] Không để lại debug log, code tạm, file không cần thiết

### PR & Review

- [ ] Commit message rõ ràng theo Conventional Commits
- [ ] Push đúng branch
- [ ] Tạo PR theo template
- [ ] Assign reviewer
- [ ] Link đúng issue `Closes #...`

## Quy Ước Đặt Tên Branch

| Loại branch | Format |
| --- | --- |
| Tính năng mới | `feature/<ten-task-cu-the>` |
| Sửa lỗi | `fix/<mo-ta-bug>` |
| Cải tiến code | `refactor/<mo-ta>` |
| Hotfix khẩn cấp | `hotfix/<mo-ta>` |

Quy ước tên branch:

- Viết thường toàn bộ
- Dùng dấu gạch ngang `-` thay cho khoảng trắng
- Đặt tên ngắn gọn, dễ tìm, dễ hiểu

Ví dụ:

- `feature/wallet-membership-integration`
- `fix/transaction-history-pagination`
- `refactor/wallet-data-logic`

## Hướng Dẫn Review Code

Khi được assign review, thành viên cần:

- Kiểm tra logic và tính đúng đắn của code
- Đảm bảo tuân thủ coding convention của team
- Test thử tính năng nếu có thể
- Comment rõ ràng, cụ thể và mang tính xây dựng
- Chỉ approve khi đã thực sự hài lòng với thay đổi
