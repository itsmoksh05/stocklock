ALTER TABLE reservations
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE reservations
    ADD CONSTRAINT chk_reservations_status CHECK (status IN ('ACTIVE', 'CANCELLED'));
