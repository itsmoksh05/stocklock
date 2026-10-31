ALTER TABLE reservations ADD COLUMN idempotency_key VARCHAR(255);

UPDATE reservations
SET idempotency_key = 'legacy-reservation-' || id
WHERE idempotency_key IS NULL;

ALTER TABLE reservations ALTER COLUMN idempotency_key SET NOT NULL;

ALTER TABLE reservations
    ADD CONSTRAINT uq_reservations_idempotency_key UNIQUE (idempotency_key);
