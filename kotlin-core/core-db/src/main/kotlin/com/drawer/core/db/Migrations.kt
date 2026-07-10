package com.drawer.core.db

/**
 * DDL for each schema version, applied in ascending order.
 *
 * Versions are immutable once shipped. Bump [SCHEMA_VERSION] in
 * [AppDatabase] and add a new entry here when the schema changes; never
 * edit a past version's SQL — that would corrupt already-migrated user
 * databases.
 */
internal object Migrations {

    /**
     * v1 — initial schema.
     *
     * Mirrors the table list from the merged-refactor plan §3:
     * `source_folders`, `target_root_dirs`, `target_folders`, `photos`,
     * `settings`. Composite (source_folder_id, entry_handle) uniqueness on
     * `photos` lets the same EntryHandle appear under multiple source
     * folders without colliding.
     */
    private val V1_SCHEMA = """
        CREATE TABLE source_folders (
            id              INTEGER PRIMARY KEY AUTOINCREMENT,
            path            TEXT    UNIQUE NOT NULL,
            display_name    TEXT    NOT NULL,
            enabled         INTEGER NOT NULL DEFAULT 1,
            recursive       INTEGER NOT NULL DEFAULT 1,
            added_at        INTEGER NOT NULL
        );

        CREATE TABLE target_root_dirs (
            id              INTEGER PRIMARY KEY AUTOINCREMENT,
            path            TEXT    UNIQUE NOT NULL,
            display_name    TEXT    NOT NULL,
            is_default      INTEGER NOT NULL DEFAULT 0,
            added_at        INTEGER NOT NULL
        );

        CREATE TABLE target_folders (
            id              INTEGER PRIMARY KEY AUTOINCREMENT,
            root_dir_id     INTEGER NOT NULL REFERENCES target_root_dirs(id),
            name            TEXT    NOT NULL,
            display_name    TEXT    NOT NULL,
            sort_order      INTEGER NOT NULL DEFAULT 0,
            last_used_at    INTEGER
        );

        CREATE TABLE photos (
            id                  INTEGER PRIMARY KEY AUTOINCREMENT,
            source_folder_id    INTEGER NOT NULL REFERENCES source_folders(id),
            entry_handle        BLOB    NOT NULL,
            status              TEXT    NOT NULL
                                CHECK (status IN ('pending','done','skipped','trashed','missing')),
            destination_handle  BLOB,
            original_handle     BLOB,
            trashed_at          INTEGER,
            processed_at        INTEGER,
            UNIQUE (source_folder_id, entry_handle)
        );

        CREATE TABLE settings (
            key     TEXT PRIMARY KEY,
            value   TEXT NOT NULL
        );
    """.trimIndent()

    private val all: Map<Int, String> = mapOf(
        1 to V1_SCHEMA,
    )

    /**
     * SQL statements to apply for [version]. Returns the full DDL block;
     * callers split on `;` to execute statement by statement.
     */
    fun sqlFor(version: Int): String =
        all[version] ?: error("no migration registered for version $version")

    /**
     * Returns every registered migration version in ascending order. Used
     * by [AppDatabase] to compute the upgrade path from the stored
     * `user_version` up to [SCHEMA_VERSION].
     */
    fun versions(): List<Int> = all.keys.sorted()
}