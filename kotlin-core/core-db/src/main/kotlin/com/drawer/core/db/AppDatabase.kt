package com.drawer.core.db

import java.io.Closeable
import java.sql.Connection
import java.sql.DriverManager

/**
 * SQLite-backed database used by both the Windows Ktor server and the
 * Android NativeModule. Owns the JDBC connection and runs migrations
 * from the stored `user_version` up to [SCHEMA_VERSION] on every [open].
 *
 * Pass a filesystem path or `":memory:"` for ephemeral SQLite (used by
 * tests). On Windows the Ktor server passes a path under the user's
 * app-data dir; on Android the NativeModule passes a path inside the
 * app's private storage. The class prepends the `jdbc:sqlite:` prefix
 * automatically so callers don't have to know JDBC URL syntax.
 *
 * Use [use] to ensure the connection closes:
 * ```
 * AppDatabase(path).use { it.open(); ... }
 * ```
 */
class AppDatabase(path: String) : Closeable {

    private val jdbcUrl: String = if (path.startsWith("jdbc:")) path else "jdbc:sqlite:$path"

    private var conn: Connection? = null

    /**
     * Opens the connection and brings the schema up to date. Safe to call
     * repeatedly — the migration runner skips already-applied versions.
     */
    fun open() {
        val c = DriverManager.getConnection(jdbcUrl)
        c.autoCommit = true
        try {
            val current = readUserVersion(c)
            applyMigrations(c, current)
            writeUserVersion(c, SCHEMA_VERSION)
            conn = c
        } catch (e: Throwable) {
            c.close()
            throw e
        }
    }

    /**
     * Active connection. Accessing this before [open] throws.
     */
    val connection: Connection
        get() = conn ?: error("AppDatabase not open; call open() first")

    /**
     * Reads the schema version stamped in `user_version`. Returns 0 for
     * a brand-new database, which causes every registered migration to
     * apply.
     */
    fun currentVersion(): Int = readUserVersion(conn ?: error("not open"))

    override fun close() {
        conn?.close()
        conn = null
    }

    private fun readUserVersion(c: Connection): Int {
        c.createStatement().use { stmt ->
            stmt.executeQuery("PRAGMA user_version").use { rs ->
                return if (rs.next()) rs.getInt(1) else 0
            }
        }
    }

    private fun writeUserVersion(c: Connection, version: Int) {
        c.createStatement().use { stmt ->
            // PRAGMA doesn't accept parameters, so we interpolate the
            // integer — version is the migration target, never user input.
            stmt.execute("PRAGMA user_version = $version")
        }
    }

    private fun applyMigrations(c: Connection, fromVersion: Int) {
        for (v in Migrations.versions()) {
            if (v <= fromVersion) continue
            val ddl = Migrations.sqlFor(v)
            c.createStatement().use { stmt ->
                stmt.executeUpdate(ddl)
            }
        }
    }

    companion object {
        init {
            // Explicitly load the sqlite-jdbc driver so JDBC URLs resolve
            // without relying on auto-discovery (which is brittle under
            // module-classloader setups like the Android NativeModule).
            Class.forName("org.sqlite.JDBC")
        }

        /**
         * Current target schema version. Bump (and add a migration in
         * [Migrations]) when the schema changes.
         */
        const val SCHEMA_VERSION: Int = 1
    }
}