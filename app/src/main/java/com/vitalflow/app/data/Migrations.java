package com.vitalflow.app.data;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * All Room database migrations for VitalFlow.
 *
 * <h3>Rules</h3>
 * <ol>
 *   <li>Every DB version bump MUST have a corresponding {@code Migration} constant here.</li>
 *   <li>Add the new constant to the {@code .addMigrations()} call in {@code DatabaseModule}.</li>
 *   <li>Bump {@code AppDatabase.version} AND commit the generated {@code app/schemas/} JSON before
 *       merging to main.</li>
 *   <li>{@code fallbackToDestructiveMigration(false)} is intentional — a missing migration
 *       crashes rather than silently wiping user data.</li>
 * </ol>
 *
 * <h3>Adding a migration (template)</h3>
 * <pre>{@code
 * static final Migration MIGRATION_9_10 = new Migration(9, 10) {
 *     \@Override
 *     public void migrate(\@NonNull SupportSQLiteDatabase db) {
 *         // Example: add a new table
 *         db.execSQL("CREATE TABLE IF NOT EXISTS `medication_history` ("
 *                 + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
 *                 + "`medId` TEXT, "
 *                 + "`medName` TEXT, "
 *                 + "`action` TEXT, "
 *                 + "`timestamp` INTEGER NOT NULL)");
 *
 *         // Example: add a column to an existing table
 *         // db.execSQL("ALTER TABLE `medications` ADD COLUMN `refillDate` TEXT");
 *     }
 * };
 * }</pre>
 */
public final class Migrations {

    private Migrations() { /* no instances */ }

    // -------------------------------------------------------------------------
    // v8 → v9
    // -------------------------------------------------------------------------
    /**
     * Version 9 enabled {@code exportSchema = true} in {@link AppDatabase} so Room can generate
     * the JSON schema artefact at build time.  No table structures were changed; this migration
     * exists purely to let existing v8 installs upgrade without data loss.
     */
    public static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // No structural changes — schema export enabled, tables unchanged.
        }
    };

    // -------------------------------------------------------------------------
    // v9 → v10  (template — uncomment when MedicationHistory entity is added)
    // -------------------------------------------------------------------------
    public static final Migration MIGRATION_9_10 = new Migration(9, 10) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `medication_history` ("
                    + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`medId` TEXT, "
                    + "`medName` TEXT, "
                    + "`action` TEXT, "
                    + "`timestamp` INTEGER NOT NULL)");
        }
    };
}
