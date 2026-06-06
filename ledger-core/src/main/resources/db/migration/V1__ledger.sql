CREATE TABLE ledger (
    id uuid PRIMARY KEY,
    code varchar(50) NOT NULL UNIQUE CHECK (code ~ '^[A-Za-z0-9][A-Za-z0-9_-]{0,49}$'),
    name varchar(200) NOT NULL CHECK (length(btrim(name)) > 0),
    currency varchar(3) NOT NULL CHECK (currency IN ('SEK', 'EUR', 'USD')),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);

CREATE TABLE account (
    id uuid PRIMARY KEY,
    ledger_id uuid NOT NULL REFERENCES ledger(id),
    code varchar(50) NOT NULL CHECK (code ~ '^[A-Za-z0-9][A-Za-z0-9_-]{0,49}$'),
    name varchar(200) NOT NULL CHECK (length(btrim(name)) > 0),
    type varchar(10) NOT NULL CHECK (type IN ('ASSET','LIABILITY','EQUITY','REVENUE','EXPENSE')),
    status varchar(6) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','CLOSED')),
    enforce_non_negative_balance boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (ledger_id, code),
    UNIQUE (id, ledger_id)
);

CREATE TABLE journal_transaction (
    id uuid PRIMARY KEY,
    ledger_id uuid NOT NULL REFERENCES ledger(id),
    external_reference varchar(200),
    description varchar(500) NOT NULL CHECK (length(btrim(description)) > 0),
    recorded_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    reversal_of_transaction_id uuid UNIQUE,
    reversal_reason varchar(500),
    state varchar(6) NOT NULL DEFAULT 'DRAFT' CHECK (state IN ('DRAFT','POSTED')),
    UNIQUE (id, ledger_id),
    FOREIGN KEY (reversal_of_transaction_id, ledger_id) REFERENCES journal_transaction(id, ledger_id),
    CHECK (reversal_of_transaction_id IS DISTINCT FROM id),
    CHECK ((reversal_of_transaction_id IS NULL AND reversal_reason IS NULL)
        OR (reversal_of_transaction_id IS NOT NULL AND length(btrim(reversal_reason)) > 0
            AND reversal_reason IS NOT NULL))
);

CREATE TABLE journal_entry (
    id uuid PRIMARY KEY,
    ledger_id uuid NOT NULL,
    transaction_id uuid NOT NULL,
    account_id uuid NOT NULL,
    line_number integer NOT NULL CHECK (line_number BETWEEN 1 AND 100),
    side varchar(6) NOT NULL CHECK (side IN ('DEBIT','CREDIT')),
    -- Unconstrained NUMERIC is intentional: NUMERIC(p,2) would silently round input BEFORE a CHECK.
    amount numeric NOT NULL CHECK (amount > 0 AND amount <= 999999999999.99 AND amount = trunc(amount, 2)),
    description varchar(500),
    FOREIGN KEY (transaction_id, ledger_id) REFERENCES journal_transaction(id, ledger_id),
    FOREIGN KEY (account_id, ledger_id) REFERENCES account(id, ledger_id),
    UNIQUE (transaction_id, line_number)
);

CREATE TABLE idempotency_record (
    ledger_id uuid NOT NULL REFERENCES ledger(id),
    operation varchar(7) NOT NULL CHECK (operation IN ('POST','REVERSE')),
    idempotency_key varchar(128) NOT NULL CHECK (idempotency_key ~ '^[A-Za-z0-9._:-]{1,128}$'),
    fingerprint varchar(64) NOT NULL CHECK (fingerprint ~ '^[0-9a-f]{64}$'),
    transaction_id uuid NOT NULL UNIQUE,
    response_status integer NOT NULL DEFAULT 201 CHECK (response_status = 201),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (ledger_id, operation, idempotency_key),
    -- Reserving the future transaction ID is atomic with posting; an abandoned claim cannot commit.
    FOREIGN KEY (transaction_id, ledger_id) REFERENCES journal_transaction(id, ledger_id)
        DEFERRABLE INITIALLY DEFERRED
);

CREATE INDEX entry_account_totals_idx ON journal_entry (account_id) INCLUDE (side, amount, transaction_id, line_number);
CREATE INDEX transaction_history_idx ON journal_transaction (ledger_id, recorded_at, id);
CREATE INDEX transaction_external_reference_idx ON journal_transaction (ledger_id, external_reference)
    WHERE external_reference IS NOT NULL;

CREATE FUNCTION reject_journal_mutation() RETURNS trigger
LANGUAGE plpgsql SET search_path = ledger, pg_temp AS $$
BEGIN
    RAISE EXCEPTION 'Immutable ledger data cannot be changed or removed' USING ERRCODE = '23514';
END;
$$;

CREATE TRIGGER immutable_ledger BEFORE UPDATE OR DELETE ON ledger
    FOR EACH ROW EXECUTE FUNCTION reject_journal_mutation();
CREATE TRIGGER immutable_entry BEFORE UPDATE OR DELETE ON journal_entry
    FOR EACH ROW EXECUTE FUNCTION reject_journal_mutation();
CREATE TRIGGER immutable_idempotency BEFORE UPDATE OR DELETE ON idempotency_record
    FOR EACH ROW EXECUTE FUNCTION reject_journal_mutation();
CREATE TRIGGER no_journal_truncate BEFORE TRUNCATE ON journal_transaction
    FOR EACH STATEMENT EXECUTE FUNCTION reject_journal_mutation();
CREATE TRIGGER no_entry_truncate BEFORE TRUNCATE ON journal_entry
    FOR EACH STATEMENT EXECUTE FUNCTION reject_journal_mutation();

CREATE FUNCTION guard_journal_header() RETURNS trigger
LANGUAGE plpgsql SET search_path = ledger, pg_temp AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.state <> 'DRAFT' THEN
            RAISE EXCEPTION 'Journal must begin as DRAFT' USING ERRCODE = '23514';
        END IF;
    ELSIF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Journals cannot be deleted' USING ERRCODE = '23514';
    ELSIF OLD.state <> 'DRAFT' OR NEW.state <> 'POSTED'
        OR (to_jsonb(NEW) - 'state') IS DISTINCT FROM (to_jsonb(OLD) - 'state') THEN
        RAISE EXCEPTION 'Only DRAFT to POSTED finalisation is allowed' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER journal_header_guard BEFORE INSERT OR UPDATE OR DELETE ON journal_transaction
    FOR EACH ROW EXECUTE FUNCTION guard_journal_header();

CREATE FUNCTION guard_entry_insert() RETURNS trigger
LANGUAGE plpgsql SET search_path = ledger, pg_temp AS $$
DECLARE header_state text;
BEGIN
    -- Serializes append vs finalise even for direct SQL on separate connections.
    SELECT state INTO header_state FROM journal_transaction
        WHERE id = NEW.transaction_id AND ledger_id = NEW.ledger_id FOR UPDATE;
    IF header_state IS DISTINCT FROM 'DRAFT' THEN
        RAISE EXCEPTION 'Entries may only be inserted into an existing draft' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER journal_entry_insert_guard BEFORE INSERT ON journal_entry
    FOR EACH ROW EXECUTE FUNCTION guard_entry_insert();

CREATE FUNCTION validate_posted_journal() RETURNS trigger
LANGUAGE plpgsql SET search_path = ledger, pg_temp AS $$
DECLARE
    header journal_transaction%ROWTYPE;
    line_count integer;
    account_count integer;
    first_line integer;
    last_line integer;
    debits numeric;
    credits numeric;
BEGIN
    SELECT * INTO STRICT header FROM journal_transaction WHERE id = NEW.id;
    IF header.state <> 'POSTED' THEN
        RAISE EXCEPTION 'A draft journal cannot commit' USING ERRCODE = '23514';
    END IF;
    SELECT count(*), count(DISTINCT account_id), min(line_number), max(line_number),
           coalesce(sum(amount) FILTER (WHERE side = 'DEBIT'), 0),
           coalesce(sum(amount) FILTER (WHERE side = 'CREDIT'), 0)
      INTO line_count, account_count, first_line, last_line, debits, credits
      FROM journal_entry WHERE transaction_id = header.id;
    IF line_count < 2 OR line_count > 100 OR account_count < 2 OR first_line <> 1 OR last_line <> line_count
        OR debits <= 0 OR debits <> credits OR debits > 9999999999999.99 THEN
        RAISE EXCEPTION 'Journal must be complete and balanced within monetary limits' USING ERRCODE = '23514';
    END IF;

    -- Application already holds these locks. Also protect direct SQL posting paths.
    PERFORM a.id FROM account a
        WHERE a.id IN (SELECT account_id FROM journal_entry WHERE transaction_id = header.id)
        ORDER BY a.id FOR UPDATE;
    -- Separate statements after lock acquisition obtain fresh READ COMMITTED snapshots.
    IF EXISTS (
        SELECT 1 FROM account a
        WHERE a.id IN (SELECT account_id FROM journal_entry WHERE transaction_id = header.id)
          AND a.status <> 'ACTIVE'
    ) THEN
        RAISE EXCEPTION 'Closed accounts cannot receive entries' USING ERRCODE = '23514';
    END IF;
    IF EXISTS (
        SELECT 1 FROM account a JOIN journal_entry e ON e.account_id = a.id
        WHERE a.enforce_non_negative_balance
          AND a.id IN (SELECT account_id FROM journal_entry WHERE transaction_id = header.id)
        GROUP BY a.id, a.type
        HAVING sum(CASE WHEN e.side = CASE WHEN a.type IN ('ASSET','EXPENSE') THEN 'DEBIT' ELSE 'CREDIT' END
                   THEN e.amount ELSE -e.amount END) < 0
    ) THEN
        RAISE EXCEPTION 'Normal balance cannot be negative for a protected account' USING ERRCODE = '23514';
    END IF;

    IF header.reversal_of_transaction_id IS NOT NULL THEN
        IF NOT EXISTS (SELECT 1 FROM journal_transaction original
            WHERE original.id = header.reversal_of_transaction_id AND original.state = 'POSTED'
              AND original.reversal_of_transaction_id IS NULL) THEN
            RAISE EXCEPTION 'Only an original posted transaction can be reversed' USING ERRCODE = '23514';
        END IF;
        IF line_count <> (SELECT count(*) FROM journal_entry WHERE transaction_id = header.reversal_of_transaction_id)
            OR EXISTS (
                SELECT 1 FROM journal_entry r LEFT JOIN journal_entry o
                  ON o.transaction_id = header.reversal_of_transaction_id AND o.line_number = r.line_number
                WHERE r.transaction_id = header.id
                  AND (o.id IS NULL OR r.account_id <> o.account_id OR r.amount <> o.amount OR r.side = o.side)
            ) THEN
            RAISE EXCEPTION 'A reversal must exactly swap every original entry' USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NULL;
END;
$$;

-- Check the final row state at COMMIT, including when INSERT was initially DRAFT.
CREATE CONSTRAINT TRIGGER complete_journal_at_commit
    AFTER INSERT OR UPDATE ON journal_transaction DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_posted_journal();

CREATE FUNCTION guard_account_change() RETURNS trigger
LANGUAGE plpgsql SET search_path = ledger, pg_temp AS $$
DECLARE net numeric;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Accounts cannot be deleted' USING ERRCODE = '23514';
    END IF;
    IF (to_jsonb(NEW) - 'status') IS DISTINCT FROM (to_jsonb(OLD) - 'status')
        OR OLD.status <> 'ACTIVE' OR NEW.status <> 'CLOSED' THEN
        RAISE EXCEPTION 'Only closing an active account is allowed' USING ERRCODE = '23514';
    END IF;
    SELECT coalesce(sum(CASE side WHEN 'DEBIT' THEN amount ELSE -amount END), 0)
        INTO net FROM journal_entry WHERE account_id = OLD.id;
    IF net <> 0 THEN
        RAISE EXCEPTION 'Only zero-balance accounts may close' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER account_change_guard BEFORE UPDATE OR DELETE ON account
    FOR EACH ROW EXECUTE FUNCTION guard_account_change();
