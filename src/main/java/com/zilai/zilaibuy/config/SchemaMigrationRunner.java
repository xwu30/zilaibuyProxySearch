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
            if (oldExists == null || oldExists == 0) {
                log.info("Schema migration balance_cny→balance_jpy: already done, skipping.");
                return;
            }
            // Check if new column already exists (Hibernate ddl-auto may have created it)
            Integer newExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'balance_jpy'",
                Integer.class
            );
            if (newExists == null || newExists == 0) {
                jdbcTemplate.execute(
                    "ALTER TABLE users ADD COLUMN balance_jpy DECIMAL(12,2) NOT NULL DEFAULT 0.00"
                );
                log.info("Schema migration: added balance_jpy column.");
            }
            // Copy existing balances from old column
            int rows = jdbcTemplate.update("UPDATE users SET balance_jpy = balance_cny");
            log.info("Schema migration: copied balance_cny → balance_jpy for {} users.", rows);
            // Drop old column
            jdbcTemplate.execute("ALTER TABLE users DROP COLUMN balance_cny");
            log.info("Schema migration: dropped balance_cny column. Migration complete.");
        } catch (Exception e) {
            log.error("Schema migration balance_cny→balance_jpy FAILED: {}", e.getMessage(), e);
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
