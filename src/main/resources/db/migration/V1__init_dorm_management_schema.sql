-- ==============================================================================
-- KTX HUB / PDMS - V1 DATABASE SCHEMA MIGRATION
-- Target RDBMS: PostgreSQL 15+ (Standard 3NF / BCNF, UUID v4, English Enums)
-- ==============================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "btree_gist";

-- ------------------------------------------------------------------------------
-- 1. USERS & PROFILES
-- ------------------------------------------------------------------------------
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username TEXT NOT NULL UNIQUE,
    email TEXT NOT NULL UNIQUE,
    phone TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    role TEXT NOT NULL CHECK (role IN ('MANAGER', 'TECHNICIAN', 'TENANT')),
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'LOCKED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE staff_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    full_name TEXT NOT NULL,
    phone TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_staff_profiles_user_id UNIQUE (user_id)
);

CREATE TABLE tenants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    tenant_code TEXT NOT NULL UNIQUE,
    full_name TEXT NOT NULL,
    gender TEXT NOT NULL CHECK (gender IN ('MALE', 'FEMALE')),
    id_card_number TEXT NOT NULL UNIQUE CHECK (id_card_number ~ '^[0-9]{12}$'),
    phone TEXT NOT NULL,
    email TEXT NOT NULL,
    permanent_address TEXT NOT NULL,
    date_of_birth DATE,
    emergency_contact_name TEXT,
    emergency_contact_phone TEXT,
    resident_status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (resident_status IN ('ACTIVE', 'CHECKED_OUT', 'RESERVED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ DEFAULT NULL
);

-- ------------------------------------------------------------------------------
-- 2. ROOMS, BEDS & AMENITIES
-- ------------------------------------------------------------------------------
CREATE TABLE rooms (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_number TEXT NOT NULL UNIQUE,
    floor INT NOT NULL CHECK (floor >= 1 AND floor <= 10),
    room_type TEXT NOT NULL CHECK (room_type IN ('FOUR_BEDS', 'SIX_BEDS', 'EIGHT_BEDS', 'SLEEPBOX')),
    gender_policy TEXT NOT NULL CHECK (gender_policy IN ('MALE', 'FEMALE')),
    total_beds INT NOT NULL CHECK (total_beds > 0 AND total_beds <= 16),
    occupied_beds INT NOT NULL DEFAULT 0 CHECK (occupied_beds >= 0 AND occupied_beds <= total_beds),
    base_price NUMERIC(12,2) NOT NULL CHECK (base_price >= 0),
    status TEXT NOT NULL DEFAULT 'AVAILABLE' CHECK (status IN ('AVAILABLE', 'OCCUPIED', 'MAINTENANCE')),
    deleted_at TIMESTAMPTZ DEFAULT NULL
);

CREATE TABLE beds (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id UUID NOT NULL REFERENCES rooms(id) ON DELETE RESTRICT,
    bed_code TEXT NOT NULL,
    slot_key TEXT NOT NULL UNIQUE,
    price NUMERIC(12,2) NOT NULL CHECK (price >= 0),
    status TEXT NOT NULL DEFAULT 'AVAILABLE' CHECK (status IN ('AVAILABLE', 'OCCUPIED', 'MAINTENANCE'))
);

CREATE TABLE amenities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    category TEXT NOT NULL
);

CREATE TABLE room_amenities (
    room_id UUID NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    amenity_id UUID NOT NULL REFERENCES amenities(id) ON DELETE CASCADE,
    quantity INT NOT NULL DEFAULT 1 CHECK (quantity > 0),
    PRIMARY KEY (room_id, amenity_id)
);

-- ------------------------------------------------------------------------------
-- 3. CONTRACTS & FINANCIAL OPERATIONS
-- ------------------------------------------------------------------------------
CREATE TABLE contracts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contract_code TEXT NOT NULL UNIQUE,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    bed_id UUID NOT NULL REFERENCES beds(id) ON DELETE RESTRICT,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    monthly_rent NUMERIC(12,2) NOT NULL CHECK (monthly_rent >= 0),
    deposit_amount NUMERIC(12,2) NOT NULL CHECK (deposit_amount >= 0),
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'EXPIRING_SOON', 'TERMINATED', 'CANCELLED')),
    contract_document_url TEXT,
    signed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ DEFAULT NULL,
    CONSTRAINT chk_contract_dates CHECK (end_date >= start_date)
);

-- Ngăn chặn 2 hợp đồng có hiệu lực trùng giường cùng một khoảng thời gian
ALTER TABLE contracts
ADD CONSTRAINT no_overlapping_bed_contracts
EXCLUDE USING gist (
    bed_id WITH =,
    daterange(start_date, end_date, '[]') WITH &&
)
WHERE (status IN ('ACTIVE', 'EXPIRING_SOON') AND deleted_at IS NULL);

CREATE TABLE invoices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_code TEXT NOT NULL UNIQUE,
    contract_id UUID NOT NULL REFERENCES contracts(id) ON DELETE RESTRICT,
    billing_period TEXT NOT NULL CHECK (billing_period ~ '^[0-9]{4}-[0-9]{2}$'),
    kind TEXT NOT NULL DEFAULT 'ALL_INCLUSIVE_RENT' CHECK (kind IN ('ALL_INCLUSIVE_RENT', 'OTHER')),
    amount NUMERIC(12,2) NOT NULL CHECK (amount >= 0),
    due_date DATE NOT NULL,
    status TEXT NOT NULL DEFAULT 'UNPAID' CHECK (status IN ('PAID', 'PENDING_CONFIRMATION', 'UNPAID')),
    issued_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    paid_at TIMESTAMPTZ DEFAULT NULL,
    deleted_at TIMESTAMPTZ DEFAULT NULL
);

CREATE TABLE payment_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id UUID NOT NULL REFERENCES invoices(id) ON DELETE RESTRICT,
    receipt_code TEXT NOT NULL UNIQUE,
    amount NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    payment_method TEXT NOT NULL CHECK (payment_method IN ('VIETQR', 'CASH', 'BANK_TRANSFER')),
    confirmed_by_staff_id UUID REFERENCES staff_profiles(id) ON DELETE SET NULL,
    bank_reference TEXT,
    proof_image_url TEXT,
    paid_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE facility_expenses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recorded_by_staff_id UUID NOT NULL REFERENCES staff_profiles(id) ON DELETE RESTRICT,
    expense_date DATE NOT NULL,
    expense_type TEXT NOT NULL CHECK (expense_type IN ('ELECTRICITY', 'WATER', 'INTERNET', 'OTHER')),
    title TEXT NOT NULL,
    amount NUMERIC(12,2) NOT NULL CHECK (amount >= 0),
    invoice_code TEXT,
    invoice_photo_url TEXT,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ DEFAULT NULL
);

-- ------------------------------------------------------------------------------
-- 4. MAINTENANCE, DISCIPLINE & VEHICLES
-- ------------------------------------------------------------------------------
CREATE TABLE maintenance_tickets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_code TEXT NOT NULL UNIQUE,
    room_id UUID NOT NULL REFERENCES rooms(id) ON DELETE RESTRICT,
    bed_id UUID REFERENCES beds(id) ON DELETE SET NULL,
    reporter_tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    assignee_staff_id UUID REFERENCES staff_profiles(id) ON DELETE SET NULL,
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    category TEXT NOT NULL CHECK (category IN ('PLUMBING', 'AIR_CONDITIONING', 'ELECTRICAL', 'FURNITURE', 'LOCKS', 'OTHER')),
    priority TEXT NOT NULL DEFAULT 'NORMAL' CHECK (priority IN ('URGENT', 'HIGH', 'NORMAL')),
    status TEXT NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED', 'CANCELLED')),
    deadline TEXT,
    resolution_notes TEXT,
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE ticket_replacement_parts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL REFERENCES maintenance_tickets(id) ON DELETE CASCADE,
    part_name TEXT NOT NULL,
    quantity INT NOT NULL DEFAULT 1 CHECK (quantity > 0),
    unit_cost NUMERIC(12,2) NOT NULL CHECK (unit_cost >= 0),
    total_cost NUMERIC(12,2) NOT NULL CHECK (total_cost >= 0)
);

CREATE TABLE violations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    violation_code TEXT NOT NULL UNIQUE,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    issued_by_staff_id UUID NOT NULL REFERENCES staff_profiles(id) ON DELETE RESTRICT,
    violation_type TEXT NOT NULL CHECK (violation_type IN ('LATE_RETURN', 'NOISE', 'HYGIENE', 'OUTSIDE_GUEST', 'SMOKING_FIRE', 'OTHER')),
    level TEXT NOT NULL DEFAULT 'REMINDER' CHECK (level IN ('REMINDER', 'WARNING', 'FINE')),
    fine_amount NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (fine_amount >= 0),
    note TEXT,
    occurred_date DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE vehicles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    vehicle_type TEXT NOT NULL DEFAULT 'SCOOTER' CHECK (vehicle_type IN ('SCOOTER', 'MANUAL_MOTORCYCLE', 'ELECTRIC_MOTORBIKE', 'ELECTRIC_BICYCLE', 'BICYCLE')),
    license_plate TEXT NOT NULL UNIQUE,
    brand_and_color TEXT NOT NULL,
    card_code TEXT UNIQUE,
    status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'ACTIVE', 'REVOKED')),
    registration_front_url TEXT,
    registration_back_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ------------------------------------------------------------------------------
-- 5. B-TREE INDEXES FOR PERFORMANCE
-- ------------------------------------------------------------------------------
CREATE INDEX idx_beds_room_id ON beds (room_id);
CREATE INDEX idx_contracts_tenant_id ON contracts (tenant_id);
CREATE INDEX idx_contracts_bed_id ON contracts (bed_id);
CREATE INDEX idx_invoices_contract_id ON invoices (contract_id);
CREATE INDEX idx_payment_transactions_invoice_id ON payment_transactions (invoice_id);
CREATE INDEX idx_payment_transactions_confirmed_by ON payment_transactions (confirmed_by_staff_id);
CREATE INDEX idx_facility_expenses_recorded_by ON facility_expenses (recorded_by_staff_id);
CREATE INDEX idx_facility_expenses_date ON facility_expenses (expense_date);
CREATE INDEX idx_maintenance_tickets_room_id ON maintenance_tickets (room_id);
CREATE INDEX idx_maintenance_tickets_bed_id ON maintenance_tickets (bed_id);
CREATE INDEX idx_maintenance_tickets_reporter ON maintenance_tickets (reporter_tenant_id);
CREATE INDEX idx_maintenance_tickets_assignee ON maintenance_tickets (assignee_staff_id);
CREATE INDEX idx_ticket_parts_ticket_id ON ticket_replacement_parts (ticket_id);
CREATE INDEX idx_violations_tenant_id ON violations (tenant_id);
CREATE INDEX idx_violations_issued_by ON violations (issued_by_staff_id);
CREATE INDEX idx_vehicles_tenant_id ON vehicles (tenant_id);

-- Composite & Partial indexes
CREATE INDEX idx_invoices_status_due ON invoices (status, due_date) WHERE deleted_at IS NULL;
CREATE INDEX idx_tickets_priority_workflow ON maintenance_tickets (status, priority, created_at);
CREATE INDEX idx_vehicles_plate_lookup ON vehicles (license_plate);
CREATE INDEX idx_tenants_active ON tenants (id) WHERE deleted_at IS NULL;
CREATE INDEX idx_rooms_active ON rooms (id) WHERE deleted_at IS NULL;

-- ------------------------------------------------------------------------------
-- 6. GENDER ISOLATION TRIGGER (BR-02)
-- ------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION validate_gender_isolation()
RETURNS TRIGGER AS $$
DECLARE
    v_tenant_gender TEXT;
    v_room_gender_policy TEXT;
BEGIN
    SELECT gender INTO v_tenant_gender FROM tenants WHERE id = NEW.tenant_id;
    SELECT r.gender_policy INTO v_room_gender_policy 
    FROM beds b 
    JOIN rooms r ON b.room_id = r.id 
    WHERE b.id = NEW.bed_id;
    
    IF v_tenant_gender IS NOT NULL AND v_room_gender_policy IS NOT NULL AND v_tenant_gender <> v_room_gender_policy THEN
        RAISE EXCEPTION 'VI PHAM NGUYEN TAC CACH LY GIOI TINH (BR-02): Khach thue [%] khong the xep vao phong [%]', 
            v_tenant_gender, v_room_gender_policy;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_check_gender_isolation
BEFORE INSERT OR UPDATE ON contracts
FOR EACH ROW
EXECUTE FUNCTION validate_gender_isolation();

-- ------------------------------------------------------------------------------
-- 7. FINANCIAL NET PROFIT VIEW (BR-03)
-- ------------------------------------------------------------------------------
CREATE OR REPLACE VIEW v_financial_profit_summary AS
WITH monthly_revenue AS (
    SELECT 
        i.billing_period,
        COALESCE(SUM(pt.amount), 0) AS collected_revenue
    FROM invoices i
    LEFT JOIN payment_transactions pt ON pt.invoice_id = i.id
    WHERE i.status = 'PAID' AND i.deleted_at IS NULL
    GROUP BY i.billing_period
),
monthly_repairs AS (
    SELECT 
        to_char(t.resolved_at, 'YYYY-MM') AS billing_period,
        COALESCE(SUM(p.total_cost), 0) AS repair_expenses
    FROM maintenance_tickets t
    LEFT JOIN ticket_replacement_parts p ON p.ticket_id = t.id
    WHERE t.status = 'RESOLVED'
    GROUP BY to_char(t.resolved_at, 'YYYY-MM')
),
monthly_facility_utilities AS (
    SELECT 
        to_char(fe.expense_date, 'YYYY-MM') AS billing_period,
        COALESCE(SUM(CASE WHEN fe.expense_type = 'ELECTRICITY' THEN fe.amount ELSE 0 END), 0) AS electricity_bill,
        COALESCE(SUM(CASE WHEN fe.expense_type = 'WATER' THEN fe.amount ELSE 0 END), 0) AS water_bill,
        COALESCE(SUM(CASE WHEN fe.expense_type = 'INTERNET' THEN fe.amount ELSE 0 END), 0) AS internet_bill,
        COALESCE(SUM(fe.amount), 0) AS total_facility_utilities
    FROM facility_expenses fe
    WHERE fe.deleted_at IS NULL
    GROUP BY to_char(fe.expense_date, 'YYYY-MM')
)
SELECT 
    p.billing_period,
    COALESCE(rev.collected_revenue, 0) AS total_revenue,
    COALESCE(rep.repair_expenses, 0) AS repair_costs,
    COALESCE(fac.electricity_bill, 0) AS electricity_expenses,
    COALESCE(fac.water_bill, 0) AS water_expenses,
    COALESCE(fac.internet_bill, 0) AS internet_expenses,
    COALESCE(fac.total_facility_utilities, 0) AS total_utility_overhead,
    -- Lợi nhuận ròng = Doanh thu thực thu - Chi phí sửa chữa - Chi phí điện nước tổng cơ sở
    (COALESCE(rev.collected_revenue, 0) 
     - COALESCE(rep.repair_expenses, 0) 
     - COALESCE(fac.total_facility_utilities, 0)) AS net_profit
FROM (
    SELECT DISTINCT billing_period FROM invoices WHERE deleted_at IS NULL
    UNION 
    SELECT DISTINCT to_char(expense_date, 'YYYY-MM') FROM facility_expenses WHERE deleted_at IS NULL
) p
LEFT JOIN monthly_revenue rev ON rev.billing_period = p.billing_period
LEFT JOIN monthly_repairs rep ON rep.billing_period = p.billing_period
LEFT JOIN monthly_facility_utilities fac ON fac.billing_period = p.billing_period;
