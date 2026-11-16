-- Clean baseline for KTX Hub PostgreSQL 16+ database.
-- Contains all 20 tables aligned with Logical ERD v3.6 / Conceptual ERD v3.5 / SRS v3.4.1.

-- ============================================================================
-- 1. IDENTITY FOUNDATION (users, tenants, staff_profiles)
-- ============================================================================

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email TEXT NOT NULL,
    password_hash TEXT NOT NULL,
    role TEXT NOT NULL CHECK (role IN ('MANAGER', 'TENANT', 'TECHNICIAN')),
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'LOCKED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_users_email_canonical CHECK (email = lower(btrim(email)))
);
CREATE UNIQUE INDEX uq_users_email_canonical ON users (lower(btrim(email)));

CREATE TABLE tenants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE RESTRICT,
    tenant_code TEXT NOT NULL UNIQUE,
    id_card_number VARCHAR(12) NOT NULL UNIQUE CHECK (id_card_number ~ '^[0-9]{12}$'),
    full_name TEXT NOT NULL,
    gender TEXT NOT NULL CHECK (gender IN ('MALE', 'FEMALE')),
    date_of_birth DATE,
    phone TEXT NOT NULL,
    permanent_address TEXT NOT NULL,
    emergency_contact_name TEXT,
    emergency_contact_phone TEXT,
    resident_status TEXT NOT NULL DEFAULT 'ACTIVE'
        CHECK (resident_status IN ('ACTIVE', 'CHECKED_OUT')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE staff_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE RESTRICT,
    full_name TEXT NOT NULL,
    phone TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE FUNCTION enforce_user_profile_role() RETURNS TRIGGER AS $$
DECLARE
    profile_role TEXT;
BEGIN
    SELECT role INTO profile_role FROM users WHERE id = NEW.user_id;
    IF TG_TABLE_NAME = 'tenants' AND profile_role <> 'TENANT' THEN
        RAISE EXCEPTION 'Tenant profile requires a TENANT user' USING ERRCODE = '23514';
    ELSIF TG_TABLE_NAME = 'staff_profiles' AND profile_role NOT IN ('MANAGER', 'TECHNICIAN') THEN
        RAISE EXCEPTION 'Staff profile requires a MANAGER or TECHNICIAN user' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_tenant_user_role
    BEFORE INSERT OR UPDATE OF user_id ON tenants
    FOR EACH ROW EXECUTE FUNCTION enforce_user_profile_role();

CREATE TRIGGER trg_staff_user_role
    BEFORE INSERT OR UPDATE OF user_id ON staff_profiles
    FOR EACH ROW EXECUTE FUNCTION enforce_user_profile_role();

CREATE FUNCTION protect_existing_profile_roles() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.role IS DISTINCT FROM OLD.role
       AND EXISTS (SELECT 1 FROM tenants WHERE user_id = NEW.id)
       AND NEW.role <> 'TENANT' THEN
        RAISE EXCEPTION 'A User with a Tenant profile must retain the TENANT role'
            USING ERRCODE = '23514';
    END IF;
    IF NEW.role IS DISTINCT FROM OLD.role
       AND EXISTS (SELECT 1 FROM staff_profiles WHERE user_id = NEW.id)
       AND NEW.role NOT IN ('MANAGER', 'TECHNICIAN') THEN
        RAISE EXCEPTION 'A User with a Staff profile must retain a staff role'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_profile_role
    BEFORE UPDATE OF role ON users
    FOR EACH ROW EXECUTE FUNCTION protect_existing_profile_roles();

CREATE FUNCTION set_updated_at() RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated_at BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_tenants_updated_at BEFORE UPDATE ON tenants
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_staff_profiles_updated_at BEFORE UPDATE ON staff_profiles
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ============================================================================
-- 2. ROOM & BED FOUNDATION (rooms, beds, amenities, room_amenities, room_code_registry)
-- ============================================================================

CREATE TABLE rooms (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_number TEXT NOT NULL,
    floor INTEGER NOT NULL CHECK (floor >= 1),
    total_beds SMALLINT NOT NULL CHECK (total_beds IN (4, 6, 8)),
    gender_policy TEXT NOT NULL CHECK (gender_policy IN ('MALE', 'FEMALE')),
    base_price NUMERIC(14, 0) NOT NULL CHECK (base_price > 0),
    archived_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_rooms_number_canonical CHECK (room_number = upper(btrim(room_number)))
);
CREATE UNIQUE INDEX uq_rooms_number_canonical ON rooms (upper(btrim(room_number)));
CREATE TRIGGER trg_rooms_updated_at BEFORE UPDATE ON rooms
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE beds (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id UUID NOT NULL REFERENCES rooms(id) ON DELETE RESTRICT,
    bed_code TEXT NOT NULL CHECK (bed_code = upper(btrim(bed_code))),
    slot_key TEXT NOT NULL CHECK (slot_key = upper(btrim(slot_key))),
    status TEXT NOT NULL DEFAULT 'AVAILABLE'
        CHECK (status IN ('AVAILABLE', 'OCCUPIED', 'MAINTENANCE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_beds_room_bed_code UNIQUE (room_id, bed_code),
    CONSTRAINT uq_beds_id_room_id UNIQUE (id, room_id)
);
CREATE UNIQUE INDEX uq_beds_slot_key_canonical ON beds (upper(btrim(slot_key)));

CREATE FUNCTION validate_bed_slot_key() RETURNS TRIGGER AS $$
DECLARE
    expected_slot_key TEXT;
BEGIN
    SELECT upper(room_number || '-' || NEW.bed_code) INTO expected_slot_key
    FROM rooms WHERE id = NEW.room_id;
    IF expected_slot_key IS NULL OR NEW.slot_key <> expected_slot_key THEN
        RAISE EXCEPTION 'Bed slot_key must be the current Room code and Bed code' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_beds_slot_key
    BEFORE INSERT OR UPDATE OF room_id, bed_code, slot_key ON beds
    FOR EACH ROW EXECUTE FUNCTION validate_bed_slot_key();

CREATE TABLE amenities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL
);

INSERT INTO amenities (code, name) VALUES
    ('AC', 'Điều hòa không khí'),
    ('FRIDGE', 'Tủ lạnh'),
    ('WATER_HEATER', 'Bình nóng lạnh'),
    ('DESK', 'Bàn học cá nhân'),
    ('WARDROBE', 'Tủ quần áo có khóa'),
    ('CURTAIN', 'Rèm che riêng tư'),
    ('WIFI_ROUTER', 'Bộ phát Wi-Fi tốc độ cao')
ON CONFLICT (code) DO NOTHING;

CREATE TABLE room_amenities (
    room_id UUID NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    amenity_id UUID NOT NULL REFERENCES amenities(id) ON DELETE RESTRICT,
    quantity INTEGER NOT NULL DEFAULT 1 CHECK (quantity > 0),
    PRIMARY KEY (room_id, amenity_id)
);

CREATE TABLE room_code_registry (
    code TEXT PRIMARY KEY CHECK (code = upper(btrim(code))),
    room_id UUID NOT NULL REFERENCES rooms(id) ON DELETE RESTRICT,
    is_current BOOLEAN NOT NULL,
    used_in_business BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_room_code_registry_retained_code
        CHECK (is_current OR used_in_business)
);
CREATE UNIQUE INDEX uq_room_code_registry_current_room
    ON room_code_registry (room_id) WHERE is_current;

CREATE FUNCTION assert_room_bed_count() RETURNS TRIGGER AS $$
DECLARE
    checked_room_id UUID;
    expected_count INTEGER;
    actual_count BIGINT;
BEGIN
    IF TG_TABLE_NAME = 'rooms' THEN
        checked_room_id := COALESCE(NEW.id, OLD.id);
    ELSE
        checked_room_id := COALESCE(NEW.room_id, OLD.room_id);
    END IF;
    SELECT total_beds INTO expected_count FROM rooms WHERE id = checked_room_id;
    IF expected_count IS NOT NULL THEN
        SELECT count(*) INTO actual_count FROM beds WHERE room_id = checked_room_id;
        IF actual_count <> expected_count THEN
            RAISE EXCEPTION 'Room % must contain exactly % beds (found %)',
                checked_room_id, expected_count, actual_count USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_rooms_exact_bed_count
    AFTER INSERT OR UPDATE OR DELETE ON rooms
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION assert_room_bed_count();

CREATE CONSTRAINT TRIGGER trg_beds_exact_room_count
    AFTER INSERT OR UPDATE OR DELETE ON beds
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION assert_room_bed_count();

CREATE FUNCTION assert_room_has_current_code() RETURNS TRIGGER AS $$
DECLARE
    checked_room_id UUID;
    expected_code TEXT;
    current_code TEXT;
BEGIN
    IF TG_TABLE_NAME = 'rooms' THEN
        checked_room_id := COALESCE(NEW.id, OLD.id);
    ELSE
        checked_room_id := COALESCE(NEW.room_id, OLD.room_id);
    END IF;

    SELECT room_number INTO expected_code FROM rooms WHERE id = checked_room_id;
    IF expected_code IS NOT NULL THEN
        SELECT code INTO current_code
        FROM room_code_registry
        WHERE room_id = checked_room_id AND is_current;
        IF current_code IS NULL THEN
            RAISE EXCEPTION 'Room % must have exactly one current registry code', checked_room_id
                USING ERRCODE = '23514';
        ELSIF current_code <> expected_code THEN
            RAISE EXCEPTION 'Room % current registry code must equal room_number (%)',
                checked_room_id, expected_code USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_rooms_current_code
    AFTER INSERT OR UPDATE OR DELETE ON rooms
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION assert_room_has_current_code();

CREATE CONSTRAINT TRIGGER trg_registry_current_code
    AFTER INSERT OR UPDATE OR DELETE ON room_code_registry
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION assert_room_has_current_code();

CREATE FUNCTION prevent_room_code_history_reset() RETURNS TRIGGER AS $$
BEGIN
    IF OLD.used_in_business AND NOT NEW.used_in_business THEN
        RAISE EXCEPTION 'Room code business history cannot be cleared'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_room_code_history_monotonic
    BEFORE UPDATE OF used_in_business ON room_code_registry
    FOR EACH ROW EXECUTE FUNCTION prevent_room_code_history_reset();

CREATE FUNCTION protect_room_code_history() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.used_in_business THEN
            RAISE EXCEPTION 'Room codes with business history cannot be deleted'
                USING ERRCODE = '23514';
        END IF;
        RETURN OLD;
    END IF;

    IF OLD.used_in_business AND (
        NEW.code IS DISTINCT FROM OLD.code
        OR NEW.room_id IS DISTINCT FROM OLD.room_id
        OR (NOT OLD.is_current AND NEW.is_current)
    ) THEN
        RAISE EXCEPTION 'Protected Room code history cannot be reassigned or reactivated'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_room_code_history_protected
    BEFORE UPDATE OR DELETE ON room_code_registry
    FOR EACH ROW EXECUTE FUNCTION protect_room_code_history();

-- ============================================================================
-- 3. CONTRACTS, RENEWALS & DOCUMENTS (contracts, contract_renewals, contract_documents)
-- ============================================================================

CREATE TABLE contracts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contract_code TEXT NOT NULL UNIQUE,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    bed_id UUID NOT NULL REFERENCES beds(id) ON DELETE RESTRICT,
    room_code_snapshot TEXT NOT NULL,
    bed_code_snapshot TEXT NOT NULL,
    start_on DATE NOT NULL,
    original_end_on DATE NOT NULL,
    original_monthly_rent NUMERIC(14, 0) NOT NULL CHECK (original_monthly_rent > 0),
    deposit_amount NUMERIC(14, 0) NOT NULL CHECK (deposit_amount >= 0),
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'LIQUIDATED')),
    checked_out_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_contracts_dates CHECK (original_end_on >= start_on),
    CONSTRAINT ck_contracts_start_day CHECK (EXTRACT(DAY FROM start_on) = 1),
    CONSTRAINT ck_contracts_checkout_status CHECK (
        (status = 'ACTIVE' AND checked_out_at IS NULL) OR
        (status = 'LIQUIDATED' AND checked_out_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_contracts_active_bed ON contracts (bed_id) WHERE status = 'ACTIVE';
CREATE UNIQUE INDEX uq_contracts_active_tenant ON contracts (tenant_id) WHERE status = 'ACTIVE';

CREATE FUNCTION protect_room_code_for_contract() RETURNS TRIGGER AS $$
BEGIN
    UPDATE room_code_registry SET used_in_business = TRUE
    WHERE room_id = (SELECT room_id FROM beds WHERE id = NEW.bed_id) AND is_current;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_contract_protects_room_code
    AFTER INSERT OR UPDATE OF bed_id ON contracts
    FOR EACH ROW EXECUTE FUNCTION protect_room_code_for_contract();

CREATE TABLE contract_renewals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contract_id UUID NOT NULL REFERENCES contracts(id) ON DELETE RESTRICT,
    sequence_no INTEGER NOT NULL CHECK (sequence_no > 0),
    signed_on DATE NOT NULL,
    previous_end_on DATE NOT NULL,
    effective_from DATE NOT NULL,
    new_end_on DATE NOT NULL,
    monthly_rent NUMERIC(14, 0) NOT NULL CHECK (monthly_rent > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_contract_renewals_dates CHECK (new_end_on >= effective_from),
    CONSTRAINT ck_contract_renewals_effective_day CHECK (EXTRACT(DAY FROM effective_from) = 1),
    CONSTRAINT uq_contract_renewals_sequence UNIQUE (contract_id, sequence_no),
    CONSTRAINT uq_contract_renewals_effective UNIQUE (contract_id, effective_from),
    CONSTRAINT uq_contract_renewals_id_contract UNIQUE (id, contract_id)
);

CREATE TABLE contract_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contract_id UUID NOT NULL REFERENCES contracts(id) ON DELETE RESTRICT,
    renewal_id UUID,
    document_type TEXT NOT NULL CHECK (document_type IN ('ORIGINAL_CONTRACT', 'RENEWAL_ANNEX', 'CHECKOUT_HANDOVER', 'OTHER')),
    original_filename TEXT NOT NULL,
    mime_type TEXT NOT NULL CHECK (mime_type IN (
        'image/jpeg',
        'image/png',
        'image/webp',
        'application/pdf',
        'application/msword',
        'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
    )),
    storage_file_key TEXT NOT NULL UNIQUE,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_contract_documents_renewal FOREIGN KEY (renewal_id, contract_id)
        REFERENCES contract_renewals(id, contract_id) ON DELETE RESTRICT,
    CONSTRAINT ck_contract_documents_renewal_presence CHECK (
        (document_type = 'RENEWAL_ANNEX' AND renewal_id IS NOT NULL) OR
        (document_type <> 'RENEWAL_ANNEX' AND renewal_id IS NULL)
    )
);

-- ============================================================================
-- 4. INVOICES, PAYMENTS & EXPENSES (invoices, payment_transactions, facility_expenses)
-- ============================================================================

CREATE TABLE invoices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_code TEXT NOT NULL UNIQUE,
    contract_id UUID NOT NULL REFERENCES contracts(id) ON DELETE RESTRICT,
    billing_period DATE NOT NULL,
    amount NUMERIC(14, 0) NOT NULL CHECK (amount > 0),
    due_on DATE NOT NULL,
    status TEXT NOT NULL DEFAULT 'DEBT' CHECK (status IN ('DEBT', 'PENDING_CONFIRMATION', 'PAID')),
    proof_file_key TEXT,
    proof_submitted_at TIMESTAMPTZ,
    issued_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_invoices_billing_period_day1 CHECK (EXTRACT(DAY FROM billing_period) = 1),
    CONSTRAINT uq_invoices_contract_period UNIQUE (contract_id, billing_period),
    CONSTRAINT ck_invoices_proof_consistency CHECK (
        (status = 'PENDING_CONFIRMATION' AND proof_file_key IS NOT NULL AND proof_submitted_at IS NOT NULL) OR
        (status <> 'PENDING_CONFIRMATION')
    )
);

CREATE TABLE payment_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id UUID NOT NULL UNIQUE REFERENCES invoices(id) ON DELETE RESTRICT,
    receipt_code TEXT NOT NULL UNIQUE,
    amount NUMERIC(14, 0) NOT NULL CHECK (amount > 0),
    payment_method TEXT NOT NULL CHECK (payment_method IN ('CASH', 'VIETQR_TRANSFER')),
    confirmed_by_staff_id UUID NOT NULL REFERENCES staff_profiles(id) ON DELETE RESTRICT,
    confirmed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE facility_expenses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recorded_by_staff_id UUID NOT NULL REFERENCES staff_profiles(id) ON DELETE RESTRICT,
    expense_date DATE NOT NULL,
    expense_type TEXT NOT NULL CHECK (expense_type IN ('ELECTRICITY', 'WATER', 'INTERNET', 'SALARY', 'OTHER')),
    title TEXT NOT NULL,
    amount NUMERIC(14, 0) NOT NULL CHECK (amount > 0),
    invoice_code TEXT,
    invoice_file_key TEXT,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TRIGGER trg_facility_expenses_updated_at BEFORE UPDATE ON facility_expenses
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE UNIQUE INDEX uq_facility_expenses_salary_month ON facility_expenses
    (EXTRACT(YEAR FROM expense_date), EXTRACT(MONTH FROM expense_date))
    WHERE expense_type = 'SALARY';

-- ============================================================================
-- 5. OPERATIONS: MAINTENANCE & VIOLATIONS (maintenance_tickets, ticket_replacement_parts, violations)
-- ============================================================================

CREATE TABLE maintenance_tickets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_code TEXT NOT NULL UNIQUE,
    reporter_tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    room_id UUID NOT NULL REFERENCES rooms(id) ON DELETE RESTRICT,
    bed_id UUID,
    assignee_staff_id UUID REFERENCES staff_profiles(id) ON DELETE RESTRICT,
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    category TEXT NOT NULL CHECK (category IN ('ELECTRIC', 'WATER', 'INTERNET', 'FURNITURE', 'AMENITY', 'OTHER')),
    priority TEXT NOT NULL DEFAULT 'NORMAL' CHECK (priority IN ('URGENT', 'HIGH', 'NORMAL')),
    status TEXT NOT NULL DEFAULT 'UNASSIGNED' CHECK (status IN ('UNASSIGNED', 'ASSIGNED', 'IN_PROGRESS', 'COMPLETED')),
    report_image_file_key TEXT,
    deadline_at TIMESTAMPTZ,
    resolution_notes TEXT,
    resolution_image_file_key TEXT,
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_maintenance_tickets_bed_room FOREIGN KEY (bed_id, room_id)
        REFERENCES beds(id, room_id) ON DELETE RESTRICT,
    CONSTRAINT ck_maintenance_tickets_status_staff CHECK (
        (status = 'UNASSIGNED' AND assignee_staff_id IS NULL) OR
        (status IN ('ASSIGNED', 'IN_PROGRESS', 'COMPLETED') AND assignee_staff_id IS NOT NULL)
    ),
    CONSTRAINT ck_maintenance_tickets_completed CHECK (
        (status <> 'COMPLETED') OR
        (resolution_notes IS NOT NULL AND resolution_image_file_key IS NOT NULL AND resolved_at IS NOT NULL)
    )
);

CREATE FUNCTION protect_room_code_for_ticket() RETURNS TRIGGER AS $$
BEGIN
    UPDATE room_code_registry SET used_in_business = TRUE
    WHERE room_id = NEW.room_id AND is_current;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ticket_protects_room_code
    AFTER INSERT OR UPDATE OF room_id ON maintenance_tickets
    FOR EACH ROW EXECUTE FUNCTION protect_room_code_for_ticket();

CREATE TABLE ticket_replacement_parts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL REFERENCES maintenance_tickets(id) ON DELETE RESTRICT,
    part_name TEXT NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_cost NUMERIC(14, 0) NOT NULL CHECK (unit_cost >= 0)
);

CREATE TABLE violations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    violation_code TEXT NOT NULL UNIQUE,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    issued_by_staff_id UUID NOT NULL REFERENCES staff_profiles(id) ON DELETE RESTRICT,
    occurred_on DATE NOT NULL,
    violation_type TEXT NOT NULL CHECK (violation_type IN ('LATE_RETURN', 'NOISE', 'HYGIENE', 'OUTSIDER', 'SMOKING_FIRE', 'OTHER')),
    level TEXT NOT NULL DEFAULT 'REMINDER' CHECK (level IN ('REMINDER', 'WARNING', 'FINE')),
    fine_amount NUMERIC(14, 0) NOT NULL DEFAULT 0 CHECK (fine_amount >= 0),
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_violations_fine_amount CHECK (
        (level = 'FINE' AND fine_amount > 0) OR
        (level IN ('REMINDER', 'WARNING') AND fine_amount = 0)
    )
);

-- ============================================================================
-- 6. OPERATIONS: VEHICLES & NOTIFICATIONS (vehicles, vehicle_change_requests, notifications)
-- ============================================================================

CREATE TABLE vehicles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    vehicle_type TEXT NOT NULL CHECK (vehicle_type IN ('SCOOTER', 'MANUAL_MOTORCYCLE', 'ELECTRIC_MOTORBIKE', 'ELECTRIC_BICYCLE', 'BICYCLE')),
    license_plate TEXT,
    brand TEXT NOT NULL CHECK (length(btrim(brand)) > 0),
    color TEXT NOT NULL CHECK (length(btrim(color)) > 0),
    registration_front_file_key TEXT,
    registration_back_file_key TEXT,
    card_code TEXT,
    status TEXT NOT NULL CHECK (status IN ('PENDING_CARD', 'ISSUED', 'REJECTED', 'REVOKED', 'REPLACED', 'CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    issued_at TIMESTAMPTZ,
    rejected_by_staff_id UUID REFERENCES staff_profiles(id) ON DELETE RESTRICT,
    rejection_reason TEXT,
    rejected_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    replaced_at TIMESTAMPTZ,
    replaced_by_vehicle_id UUID REFERENCES vehicles(id) ON DELETE RESTRICT,
    replaced_vehicle_id UUID REFERENCES vehicles(id) ON DELETE RESTRICT,
    CONSTRAINT uq_vehicles_id_tenant_id UNIQUE (id, tenant_id),
    CONSTRAINT ck_vehicles_card_code_presence CHECK (
        (status IN ('PENDING_CARD', 'REJECTED', 'CANCELLED') AND card_code IS NULL) OR
        (status IN ('ISSUED', 'REPLACED', 'REVOKED') AND card_code IS NOT NULL)
    ),
    CONSTRAINT ck_vehicles_rejection CHECK (
        (status = 'REJECTED' AND rejection_reason IS NOT NULL AND rejected_by_staff_id IS NOT NULL AND rejected_at IS NOT NULL) OR
        (status <> 'REJECTED' AND rejection_reason IS NULL AND rejected_by_staff_id IS NULL AND rejected_at IS NULL)
    ),
    CONSTRAINT ck_vehicles_motor_registration CHECK (
        (vehicle_type IN ('BICYCLE', 'ELECTRIC_BICYCLE')) OR
        (license_plate IS NOT NULL AND registration_front_file_key IS NOT NULL AND registration_back_file_key IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_vehicles_tenant_active_quota ON vehicles (tenant_id)
    WHERE status IN ('PENDING_CARD', 'ISSUED');

CREATE UNIQUE INDEX uq_vehicles_issued_card_code ON vehicles (upper(btrim(card_code)))
    WHERE status = 'ISSUED';

CREATE TABLE vehicle_change_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    current_vehicle_id UUID NOT NULL,
    proposed_vehicle_type TEXT NOT NULL CHECK (proposed_vehicle_type IN ('SCOOTER', 'MANUAL_MOTORCYCLE', 'ELECTRIC_MOTORBIKE', 'ELECTRIC_BICYCLE', 'BICYCLE')),
    proposed_license_plate TEXT,
    proposed_brand TEXT NOT NULL CHECK (length(btrim(proposed_brand)) > 0),
    proposed_color TEXT NOT NULL CHECK (length(btrim(proposed_color)) > 0),
    proposed_registration_front_file_key TEXT,
    proposed_registration_back_file_key TEXT,
    status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    rejection_reason TEXT,
    processed_by_staff_id UUID REFERENCES staff_profiles(id) ON DELETE RESTRICT,
    processed_at TIMESTAMPTZ,
    new_vehicle_id UUID UNIQUE REFERENCES vehicles(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_vehicle_change_requests_current_vehicle FOREIGN KEY (current_vehicle_id, tenant_id)
        REFERENCES vehicles(id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT ck_vehicle_change_requests_status_lifecycle CHECK (
        (status = 'PENDING' AND new_vehicle_id IS NULL AND processed_by_staff_id IS NULL AND processed_at IS NULL AND rejection_reason IS NULL) OR
        (status = 'APPROVED' AND new_vehicle_id IS NOT NULL AND processed_by_staff_id IS NOT NULL AND processed_at IS NOT NULL AND rejection_reason IS NULL) OR
        (status = 'REJECTED' AND new_vehicle_id IS NULL AND processed_by_staff_id IS NOT NULL AND processed_at IS NOT NULL AND rejection_reason IS NOT NULL AND length(btrim(rejection_reason)) > 0) OR
        (status = 'CANCELLED' AND new_vehicle_id IS NULL AND rejection_reason IS NULL AND processed_at IS NOT NULL)
    ),
    CONSTRAINT ck_vehicle_change_requests_proposed_motor CHECK (
        (proposed_vehicle_type IN ('BICYCLE', 'ELECTRIC_BICYCLE')) OR
        (proposed_license_plate IS NOT NULL AND proposed_registration_front_file_key IS NOT NULL AND proposed_registration_back_file_key IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_vehicle_change_requests_pending_tenant ON vehicle_change_requests (tenant_id)
    WHERE status = 'PENDING';

CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    event_type TEXT NOT NULL CHECK (event_type IN (
        'INVOICE_ISSUED',
        'INVOICE_DUE',
        'INVOICE_OVERDUE',
        'PAYMENT_CONFIRMED',
        'PAYMENT_PROOF_REJECTED',
        'VIOLATION_CREATED',
        'VEHICLE_REGISTRATION_REJECTED',
        'VEHICLE_CHANGE_APPROVED',
        'VEHICLE_CHANGE_REJECTED'
    )),
    title TEXT NOT NULL,
    body TEXT NOT NULL,
    dedupe_key TEXT,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_notifications_dedupe_key ON notifications (dedupe_key)
    WHERE dedupe_key IS NOT NULL;
