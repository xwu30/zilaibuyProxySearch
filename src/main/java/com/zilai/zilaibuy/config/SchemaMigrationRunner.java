package com.zilai.zilaibuy.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Applies one-time schema patches that Hibernate ddl-auto:update cannot handle,
 * such as adding values to existing MySQL ENUM columns.
 * Each statement is idempotent and safe to re-run on every startup.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaMigrationRunner {

    private final JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void runPatches() {
        patchVasRequestStatusEnum();
        patchParcelStatusEnum();
        migrateBalanceCnyToJpy();
    }

    /**
     * Add PAID to the vas_requests.status ENUM column.
     * MySQL ENUM columns are not auto-updated by Hibernate when new values are added to the Java enum.
     */
    private void patchParcelStatusEnum() {
        try {
            jdbcTemplate.execute(
                "ALTER TABLE forwarding_parcels MODIFY COLUMN status " +
                "ENUM('ANNOUNCED','IN_WAREHOUSE','PACKING','SHIPPED','DELIVERED','DELETED') NOT NULL DEFAULT 'ANNOUNCED'"
            );
            log.info("Schema patch applied: forwarding_parcels.status ENUM updated to include DELETED");
        } catch (Exception e) {
            log.debug("Schema patch forwarding_parcels.status: {} (may already be up to date)", e.getMessage());
        }
    }

    /**
     * Rename balance_cny → balance_jpy in the users table.
     * Copies data from the old column if it still exists, then drops it.
     */
    private void migrateBalanceCnyToJpy() {
        try {
            // Check if old column still exists
            Integer oldExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'balance_cny'",
                Integer.class
            );
            if (oldExists != null && oldExists > 0) {
                // Add new column (Hibernate may not have done it yet at this point)
                jdbcTemplate.execute(
                    "ALTER TABLE users ADD COLUMN IF NOT EXISTS balance_jpy DECIMAL(12,2) NOT NULL DEFAULT 0.00"
                );
                // Copy existing balances
                jdbcTemplate.execute("UPDATE users SET balance_jpy = balance_cny");
                // Drop old column
                jdbcTemplate.execute("ALTER TABLE users DROP COLUMN balance_cny");
                log.info("Schema migration: users.balance_cny renamed to balance_jpy, data copied.");
            }
        } catch (Exception e) {
            log.debug("Schema migration balance_cny→balance_jpy: {} (may already be done)", e.getMessage());
        }
    }

    private void patchVasRequestStatusEnum() {
        try {
            jdbcTemplate.execute(
                "ALTER TABLE vas_requests MODIFY COLUMN status " +
                "ENUM('PENDING','PROCESSING','DONE','PAID','CANCELLED') NOT NULL DEFAULT 'PENDING'"
            );
            log.info("Schema patch applied: vas_requests.status ENUM updated to include PAID");
        } catch (Exception e) {
            // Log but don't fail startup — patch may already be applied or table may not exist yet
            log.debug("Schema patch vas_requests.status: {} (may already be up to date)", e.getMessage());
        }
    }
}
