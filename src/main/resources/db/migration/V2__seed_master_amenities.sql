-- ==============================================================================
-- KTX HUB / PDMS - V2 SEED MASTER AMENITIES
-- Danh mục tiện nghi phòng mặc định theo đặc tả Conceptual & Logical ERD
-- ==============================================================================

INSERT INTO amenities (id, code, name, category) VALUES
    (gen_random_uuid(), 'AC', 'Điều hòa không khí', 'Điện máy'),
    (gen_random_uuid(), 'FRIDGE', 'Tủ lạnh', 'Điện máy'),
    (gen_random_uuid(), 'WATER_HEATER', 'Bình nóng lạnh', 'Vệ sinh'),
    (gen_random_uuid(), 'DESK', 'Bàn học cá nhân', 'Nội thất'),
    (gen_random_uuid(), 'WARDROBE', 'Tủ quần áo có khóa', 'Nội thất'),
    (gen_random_uuid(), 'CURTAIN', 'Rèm che riêng tư', 'Nội thất'),
    (gen_random_uuid(), 'WIFI_ROUTER', 'Bộ phát Wi-Fi tốc độ cao', 'Điện máy')
ON CONFLICT (code) DO NOTHING;
