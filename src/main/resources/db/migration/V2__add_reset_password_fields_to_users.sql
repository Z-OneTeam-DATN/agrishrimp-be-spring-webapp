-- =============================================
-- Migration: V2__add_reset_password_fields_to_users.sql
-- Mô tả    : Thêm trường reset password token và expiry vào bảng users
-- Lý do    : Cập nhật theo Entity User mới thay đổi
-- =============================================

ALTER TABLE users
ADD COLUMN reset_password_token VARCHAR(255) DEFAULT NULL,
ADD COLUMN reset_password_token_expiry DATETIME DEFAULT NULL;