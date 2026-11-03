-- ------------------------------------------------------------------------------
-- 1. DROP STATUS COLUMN IN ROOMS (Rooms do not store status, tracking is Bed-level)
-- ------------------------------------------------------------------------------
ALTER TABLE rooms DROP COLUMN IF EXISTS status;

-- ------------------------------------------------------------------------------
-- 2. ENFORCE BED MAINTENANCE INVARIANT (BR-01 & Logical-ERD v3.0)
-- Ngan chan chuyen giuong dang co khach cu tru (OCCUPIED) hoac co hop dong ACTIVE sang MAINTENANCE
-- ------------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION validate_bed_maintenance()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.status = 'MAINTENANCE' THEN
        IF OLD.status = 'OCCUPIED' THEN
            RAISE EXCEPTION 'VI PHAM NGUYEN TAC BAO TRI GIUONG (BR-01): Khong the chuyen giuong dang co nguoi o sang trang thai bao tri';
        END IF;

        IF EXISTS (
            SELECT 1 FROM contracts
            WHERE bed_id = NEW.id
              AND status IN ('ACTIVE', 'EXPIRING_SOON')
              AND deleted_at IS NULL
        ) THEN
            RAISE EXCEPTION 'VI PHAM NGUYEN TAC BAO TRI GIUONG (BR-01): Khong the chuyen giuong dang co hop dong hieu luc sang trang thai bao tri';
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_check_bed_maintenance ON beds;
CREATE TRIGGER trg_check_bed_maintenance
BEFORE UPDATE OF status ON beds
FOR EACH ROW
EXECUTE FUNCTION validate_bed_maintenance();
