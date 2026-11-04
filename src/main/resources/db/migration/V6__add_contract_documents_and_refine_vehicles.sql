-- ==============================================================================
-- KTX HUB / PDMS - V6 ADD CONTRACT DOCUMENTS AND REFINE VEHICLES
-- 1. Bổ sung bảng contract_documents lưu trữ đa tệp minh chứng hợp đồng (ảnh, pdf, docx, phụ lục gia hạn)
-- 2. Tách brand_and_color thành brand và color trong bảng vehicles
-- 3. Bổ sung deleted_at phục vụ Soft Delete cho bảng vehicles
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- 1. TẠO BẢNG contract_documents
-- ------------------------------------------------------------------------------
CREATE TABLE contract_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contract_id UUID NOT NULL REFERENCES contracts(id) ON DELETE CASCADE,
    file_name TEXT NOT NULL,
    file_url TEXT NOT NULL,
    file_type TEXT NOT NULL,
    file_size BIGINT,
    document_category TEXT NOT NULL DEFAULT 'ORIGINAL_CONTRACT' CHECK (document_category IN ('ORIGINAL_CONTRACT', 'RENEWAL_ANNEX', 'CHECKOUT_HANDOVER', 'OTHER')),
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_contract_documents_contract_id ON contract_documents (contract_id);

-- ------------------------------------------------------------------------------
-- 2. CẬP NHẬT BẢNG vehicles: TÁCH HÃNG XE / MÀU SƠN & BỔ SUNG SOFT DELETE
-- ------------------------------------------------------------------------------
ALTER TABLE vehicles ADD COLUMN brand TEXT;
ALTER TABLE vehicles ADD COLUMN color TEXT;

-- Di chuyển dữ liệu cũ nếu có
UPDATE vehicles SET brand = brand_and_color, color = '' WHERE brand IS NULL;

ALTER TABLE vehicles ALTER COLUMN brand SET NOT NULL;
ALTER TABLE vehicles ALTER COLUMN color SET NOT NULL;
ALTER TABLE vehicles DROP COLUMN brand_and_color;

ALTER TABLE vehicles ADD COLUMN deleted_at TIMESTAMPTZ DEFAULT NULL;

CREATE INDEX idx_vehicles_tenant_deleted ON vehicles (tenant_id, deleted_at);
