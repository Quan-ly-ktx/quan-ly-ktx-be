-- ==============================================================================
-- KTX HUB / PDMS - V3 SEED DEMO USERS
-- Tạo 3 tài khoản mặc định đại diện cho 3 vai trò (MANAGER, TECHNICIAN, TENANT)
-- Mật khẩu mặc định: Password@123 (mã hóa BCrypt 12 rounds qua pgcrypto)
-- Tuân thủ triệt để BR-09 (CCCD 12 số) và BR-11 (Exclusive Subtyping)
-- ==============================================================================

-- 1. Quản lý (MANAGER)
INSERT INTO users (id, username, email, phone, password_hash, role, status)
VALUES (
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'manager',
    'manager@dorm.vn',
    '0901000001',
    crypt('Password@123', gen_salt('bf', 12)),
    'MANAGER',
    'ACTIVE'
) ON CONFLICT (username) DO NOTHING;

INSERT INTO staff_profiles (id, user_id, full_name, phone, active)
VALUES (
    'b0000000-0000-0000-0000-000000000001'::uuid,
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'Nguyễn Văn Quản Lý',
    '0901000001',
    true
) ON CONFLICT (user_id) DO NOTHING;

-- 2. Kỹ thuật viên (TECHNICIAN)
INSERT INTO users (id, username, email, phone, password_hash, role, status)
VALUES (
    'a0000000-0000-0000-0000-000000000002'::uuid,
    'technician',
    'technician@dorm.vn',
    '0902000002',
    crypt('Password@123', gen_salt('bf', 12)),
    'TECHNICIAN',
    'ACTIVE'
) ON CONFLICT (username) DO NOTHING;

INSERT INTO staff_profiles (id, user_id, full_name, phone, active)
VALUES (
    'b0000000-0000-0000-0000-000000000002'::uuid,
    'a0000000-0000-0000-0000-000000000002'::uuid,
    'Trần Kỹ Thuật',
    '0902000002',
    true
) ON CONFLICT (user_id) DO NOTHING;

-- 3. Khách thuê (TENANT)
INSERT INTO users (id, username, email, phone, password_hash, role, status)
VALUES (
    'a0000000-0000-0000-0000-000000000003'::uuid,
    'tenant',
    'tenant@dorm.vn',
    '0903000003',
    crypt('Password@123', gen_salt('bf', 12)),
    'TENANT',
    'ACTIVE'
) ON CONFLICT (username) DO NOTHING;

INSERT INTO tenants (
    id,
    user_id,
    tenant_code,
    full_name,
    gender,
    id_card_number,
    phone,
    email,
    permanent_address,
    date_of_birth,
    emergency_contact_name,
    emergency_contact_phone,
    resident_status
) VALUES (
    'c0000000-0000-0000-0000-000000000001'::uuid,
    'a0000000-0000-0000-0000-000000000003'::uuid,
    'TN-0001',
    'Lê Khách Thuê',
    'MALE',
    '079201000001',
    '0903000003',
    'tenant@dorm.vn',
    'Khu phố 6, Phường Linh Trung, TP. Thủ Đức, TP. Hồ Chí Minh',
    '2001-05-15',
    'Lê Văn Phụ Huynh',
    '0909999888',
    'ACTIVE'
) ON CONFLICT (tenant_code) DO NOTHING;

-- 4. Khách thuê thử nghiệm Email OTP (hoangducchinh730@gmail.com)
INSERT INTO users (id, username, email, phone, password_hash, role, status)
VALUES (
    'a0000000-0000-0000-0000-000000000004'::uuid,
    'chinhhd',
    'hoangducchinh730@gmail.com',
    '0904000004',
    crypt('Password@123', gen_salt('bf', 12)),
    'TENANT',
    'ACTIVE'
) ON CONFLICT (username) DO NOTHING;

INSERT INTO tenants (
    id,
    user_id,
    tenant_code,
    full_name,
    gender,
    id_card_number,
    phone,
    email,
    permanent_address,
    date_of_birth,
    emergency_contact_name,
    emergency_contact_phone,
    resident_status
) VALUES (
    'c0000000-0000-0000-0000-000000000002'::uuid,
    'a0000000-0000-0000-0000-000000000004'::uuid,
    'TN-0002',
    'Hoàng Đức Chính',
    'MALE',
    '079201000002',
    '0904000004',
    'hoangducchinh730@gmail.com',
    'TP. Hồ Chí Minh',
    '2002-01-01',
    'Người thân',
    '0908888777',
    'ACTIVE'
) ON CONFLICT (tenant_code) DO NOTHING;
