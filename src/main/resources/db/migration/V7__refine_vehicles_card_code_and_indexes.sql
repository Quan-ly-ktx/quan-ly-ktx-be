-- ==============================================================================
-- KTX HUB / PDMS - V7 REFINE VEHICLES CARD CODE AND INDEXES
-- 1. Xóa ràng buộc UNIQUE toàn bảng trên vehicles.card_code và vehicles.license_plate
-- 2. Tạo Partial Unique Index trên card_code (chỉ ACTIVE và deleted_at IS NULL)
--    -> Cho phép tái cấp phát mã thẻ từ vật lý khi xe cũ đã REVOKED
-- 3. Tạo Partial Unique Index trên license_plate (status != 'REVOKED' và deleted_at IS NULL)
-- 4. Bổ sung index hiệu năng cho contracts(tenant_id, status) và tenants(resident_status, deleted_at)
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- 1. BỎ UNIQUE CONSTRAINT TOÀN BẢNG TRÊN VEHICLES
-- ------------------------------------------------------------------------------
ALTER TABLE vehicles DROP CONSTRAINT IF EXISTS vehicles_card_code_key;
ALTER TABLE vehicles DROP CONSTRAINT IF EXISTS vehicles_license_plate_key;

-- ------------------------------------------------------------------------------
-- 2. TẠO PARTIAL UNIQUE INDEXES TRÊN VEHICLES
-- ------------------------------------------------------------------------------
CREATE UNIQUE INDEX uq_vehicles_active_card_code
    ON vehicles (card_code)
    WHERE status = 'ACTIVE' AND deleted_at IS NULL;

CREATE UNIQUE INDEX uq_vehicles_active_license_plate
    ON vehicles (license_plate)
    WHERE status != 'REVOKED' AND deleted_at IS NULL;

-- ------------------------------------------------------------------------------
-- 3. TẠO INDEX HIỆU NĂNG CHO CONTRACTS & TENANTS
-- ------------------------------------------------------------------------------
CREATE INDEX idx_contracts_tenant_status ON contracts (tenant_id, status);
CREATE INDEX idx_tenants_resident_status ON tenants (resident_status, deleted_at);
