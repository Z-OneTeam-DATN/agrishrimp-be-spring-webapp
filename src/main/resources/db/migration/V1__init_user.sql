-- =============================================
-- Migration: V1__init_authentication_module.sql
-- Mô tả    : Thiết kế DB cho module Auth (Login/Register/Forgot Pass)
-- Nghiệp vụ: Hỗ trợ đăng nhập đa kênh (Email/SĐT) + OTP Zalo/SMS/Email
-- =============================================

-- 1. Bảng users: Lưu trữ thông tin người dùng
CREATE TABLE users (
                       user_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    -- Họ tên (Max 50 chars)
                       full_name VARCHAR(50) NOT NULL,

    -- Identifier: Cho phép login bằng SĐT hoặc Email.
                       phone_number VARCHAR(15) UNIQUE,
                       email VARCHAR(100) UNIQUE,

    -- Security: Mật khẩu đã mã hóa
                       password_hash VARCHAR(255),

    -- 0: Nữ, 1: Nam, 2: Khác (Mapping Enum [MALE, FEMALE])
                       gender TINYINT DEFAULT 0,
                       date_of_birth DATE,
                       avatar_url TEXT,

    -- Newsletter: Default True
                       is_newsletter_subscribed BOOLEAN DEFAULT TRUE COMMENT 'True: Nhận tin KM, False: Không nhận',

    -- Trạng thái tài khoản
                       status ENUM('ACTIVE', 'INACTIVE', 'BANNED', 'UNVERIFIED') DEFAULT 'UNVERIFIED',

    -- Đăng nhập Social (Google/Facebook)
                       auth_provider VARCHAR(20) DEFAULT 'LOCAL', -- Values: LOCAL, GOOGLE, FACEBOOK
                       provider_id VARCHAR(100),

                       is_deleted BOOLEAN DEFAULT FALSE,
                       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                       updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    -- Index để tối ưu tốc độ tìm kiếm khi user đăng nhập
                       INDEX idx_phone (phone_number),
                       INDEX idx_email (email)
);

-- 2. Bảng roles:
CREATE TABLE roles (
                       role_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                       role_name VARCHAR(50) UNIQUE NOT NULL,
                       description VARCHAR(255)
);

-- 3. Bảng users_roles:
CREATE TABLE users_roles (
                             user_id BIGINT NOT NULL,
                             role_id BIGINT NOT NULL,
                             PRIMARY KEY (user_id, role_id),
                             CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
                             CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles(role_id) ON DELETE CASCADE
);

-- 4. Bảng otp_verifications:
CREATE TABLE otp_verifications (
                                   id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                   recipient VARCHAR(100) NOT NULL,
                                   otp_code VARCHAR(10) NOT NULL,
                                   expiry_time DATETIME NOT NULL,
                                   is_used BOOLEAN DEFAULT FALSE,
                                   otp_type ENUM('REGISTER_VERIFY', 'FORGOT_PASSWORD') NOT NULL DEFAULT 'REGISTER_VERIFY',
                                   channel ENUM('ZALO', 'EMAIL', 'SMS_BRANDNAME') NOT NULL DEFAULT 'EMAIL',
                                   provider_transaction_id VARCHAR(100),
                                   delivery_status ENUM('PENDING', 'SENT', 'FAILED') DEFAULT 'PENDING',
                                   created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                   INDEX idx_otp_recipient (recipient)
);

-- 5. Bảng refresh_tokens: Quản lý phiên đăng nhập (JWT)
CREATE TABLE refresh_tokens (
                                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                user_id BIGINT NOT NULL,
                                token VARCHAR(255) UNIQUE NOT NULL,
                                expiry_date DATETIME NOT NULL,
                                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                CONSTRAINT fk_token_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

-- DATA SEEDING
INSERT INTO roles (role_name, description) VALUES
                                               ('ROLE_ADMIN', 'Quản trị viên hệ thống'),
                                               ('ROLE_STAFF', 'Nhân viên vận hành'),
                                               ('ROLE_CUSTOMER', 'Khách hàng thành viên'),
                                               ('ROLE_SELLER', 'Đối tác bán hàng');