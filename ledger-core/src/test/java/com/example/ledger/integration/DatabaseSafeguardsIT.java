package com.example.ledger.integration;

import com.example.ledger.domain.enums.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.sql.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class DatabaseSafeguardsIT extends PostgresIntegrationTest {
    private Connection owner() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "ledger_migrator", "test-migration-only");
    }

    private void sql(Connection connection, String query, Object... args) throws SQLException {
        try (var statement = connection.prepareStatement(query)) {
            for (int i = 0; i < args.length; i++) { statement.setObject(i + 1, args[i]); }
            statement.execute();
        }
    }

    private void header(Connection connection, UUID ledger, UUID transaction) throws SQLException {
        sql(connection, "INSERT INTO ledger.journal_transaction(id,ledger_id,description) VALUES (?,?,'Direct SQL')", transaction, ledger);
    }

    private void line(Connection connection, UUID ledger, UUID transaction, UUID account, int number, String side, String amount) throws SQLException {
        sql(connection, """
                INSERT INTO ledger.journal_entry(id,ledger_id,transaction_id,account_id,line_number,side,amount)
                VALUES (?,?,?,?,?,?,CAST(? AS numeric))
                """, UUID.randomUUID(), ledger, transaction, account, number, side, amount);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DRAFT", "EMPTY", "UNBALANCED", "ONE_ACCOUNT", "GAPPED", "ONE_SIDED"})
    void invalidJournalsActuallyFailAtCommit(String kind) throws Exception {
        var ledger = ledger();
        var a = account(ledger.id(), AccountType.ASSET, false);
        var b = account(ledger.id(), AccountType.LIABILITY, false);
        try (var connection = owner()) {
            connection.setAutoCommit(false);
            var transaction = UUID.randomUUID();
            header(connection, ledger.id(), transaction);
            if (!kind.equals("EMPTY")) {
                line(connection, ledger.id(), transaction, a.id(), 1, "DEBIT", "10");
                line(connection, ledger.id(), transaction, kind.equals("ONE_ACCOUNT") ? a.id() : b.id(),
                        kind.equals("GAPPED") ? 3 : 2, kind.equals("ONE_SIDED") ? "DEBIT" : "CREDIT",
                        kind.equals("UNBALANCED") ? "9" : "10");
            }
            if (!kind.equals("DRAFT")) {
                sql(connection, "UPDATE ledger.journal_transaction SET state='POSTED' WHERE id=?", transaction);
            }
            assertThatThrownBy(connection::commit).isInstanceOfSatisfying(SQLException.class,
                    e -> assertThat(e.getSQLState()).isEqualTo("23514"));
            connection.rollback();
        }
        assertThat(transactions(ledger.id())).isZero();
    }

    @Test
    void postedHeadersAndEntriesRejectMutationAndLateInsertionEvenAsOwner() throws Exception {
        var ledger = ledger();
        var a = account(ledger.id(), AccountType.ASSET, false);
        var b = account(ledger.id(), AccountType.LIABILITY, false);
        var tx = post(ledger.id(), transfer(a.id(), b.id(), "10"));
        try (var connection = owner()) {
            assertThatThrownBy(() -> sql(connection, "UPDATE ledger.journal_transaction SET description='changed' WHERE id=?", tx.id()))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> sql(connection, "DELETE FROM ledger.journal_transaction WHERE id=?", tx.id()))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> sql(connection, "UPDATE ledger.journal_entry SET amount=11 WHERE transaction_id=?", tx.id()))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> sql(connection, "DELETE FROM ledger.journal_entry WHERE transaction_id=?", tx.id()))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> line(connection, ledger.id(), tx.id(), a.id(), 3, "DEBIT", "1"))
                    .isInstanceOfSatisfying(SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("23514"));
            assertThatThrownBy(() -> sql(connection, "UPDATE ledger.account SET type='EXPENSE' WHERE id=?", a.id()))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> sql(connection, "UPDATE ledger.ledger SET currency='USD' WHERE id=?", ledger.id()))
                    .isInstanceOf(SQLException.class);
        }
        assertThat(journals.get(ledger.id(), tx.id())).isEqualTo(tx);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "0.001", "999999999999.999", "1000000000000", "NaN", "Infinity"})
    void databaseRejectsBadMoneyRatherThanRounding(String amount) throws Exception {
        var ledger = ledger();
        var a = account(ledger.id(), AccountType.ASSET, false);
        try (var connection = owner()) {
            connection.setAutoCommit(false);
            var tx = UUID.randomUUID();
            header(connection, ledger.id(), tx);
            assertThatThrownBy(() -> line(connection, ledger.id(), tx, a.id(), 1, "DEBIT", amount))
                    .isInstanceOf(SQLException.class);
            connection.rollback();
        }
    }

    @Test
    void abandonedIdempotencyReservationCannotCommit() throws Exception {
        var ledger = ledger();
        try (var connection = owner()) {
            connection.setAutoCommit(false);
            sql(connection, """
                    INSERT INTO ledger.idempotency_record(ledger_id,operation,idempotency_key,fingerprint,transaction_id)
                    VALUES (?,'POST','abandoned',?,?)
                    """, ledger.id(), "a".repeat(64), UUID.randomUUID());
            assertThatThrownBy(connection::commit).isInstanceOfSatisfying(SQLException.class,
                    e -> assertThat(e.getSQLState()).isEqualTo("23503"));
            connection.rollback();
        }
    }

    @Test
    void injectedSecondLineFailureRollsBackHeaderEntriesAndIdempotencyClaim() throws Exception {
        var ledger = ledger();
        var a = account(ledger.id(), AccountType.ASSET, false);
        var b = account(ledger.id(), AccountType.LIABILITY, false);
        var request = transfer(a.id(), b.id(), "10");
        try (var connection = owner()) {
            sql(connection, """
                    CREATE FUNCTION ledger.test_fail_second_line() RETURNS trigger LANGUAGE plpgsql AS $$
                    BEGIN
                        IF NEW.ledger_id = '%s'::uuid AND NEW.line_number = 2 THEN
                            RAISE EXCEPTION 'Injected failure on second line' USING ERRCODE='23514';
                        END IF;
                        RETURN NEW;
                    END;
                    $$
                    """.formatted(ledger.id()));
            sql(connection, """
                    CREATE TRIGGER test_fail_second_line BEFORE INSERT ON ledger.journal_entry
                    FOR EACH ROW EXECUTE FUNCTION ledger.test_fail_second_line()
                    """);
            try {
                assertThatThrownBy(() -> journals.post(ledger.id(), "rollback-key", request))
                        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            } finally {
                sql(connection, "DROP TRIGGER test_fail_second_line ON ledger.journal_entry");
                sql(connection, "DROP FUNCTION ledger.test_fail_second_line()");
            }
        }
        assertThat(transactions(ledger.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ledger.journal_entry WHERE ledger_id=?", Long.class, ledger.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ledger.idempotency_record WHERE ledger_id=?", Long.class, ledger.id())).isZero();
        journals.post(ledger.id(), "rollback-key", request);
        assertThat(transactions(ledger.id())).isEqualTo(1);
        balance(ledger.id(), a.id(), "10.00");
    }

    @Test
    void runtimeCannotDisableSafeguardsOrDeleteHistory() {
        assertThatThrownBy(() -> jdbc.execute("ALTER TABLE ledger.journal_entry DISABLE TRIGGER ALL"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE ledger.journal_entry"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> jdbc.execute("DELETE FROM ledger.journal_entry"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> jdbc.execute("CREATE TABLE ledger.bypass(id integer)"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> jdbc.execute("UPDATE ledger.idempotency_record SET fingerprint=repeat('a',64)"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test
    void databaseRejectsCrossLedgerEntriesAtInsert() throws Exception {
        var ledger = ledger();
        var elsewhere = account(ledger().id(), AccountType.ASSET, false);
        try (var connection = owner()) {
            connection.setAutoCommit(false);
            var tx = UUID.randomUUID();
            header(connection, ledger.id(), tx);
            assertThatThrownBy(() -> line(connection, ledger.id(), tx, elsewhere.id(), 1, "DEBIT", "1"))
                    .isInstanceOfSatisfying(SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("23503"));
            connection.rollback();
        }
    }
}
