package com.drawer.core.db

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Contract test for [AppDatabase]. Opens an in-memory SQLite, lets the
 * migration runner bring the schema up to the current version, and
 * inspects `sqlite_master` to confirm every required table was created.
 *
 * The schema is the source of truth shared between the Windows Ktor
 * server and the Android NativeModule, so getting it right here is what
 * keeps both platforms query-compatible.
 */
class AppDatabaseTest {

    @Test
    fun `open creates the full v1 schema on a fresh database`() {
        AppDatabase(":memory:").use { db ->
            db.open()

            val tables = db.tableNames().toSet()
            assertEquals(
                setOf(
                    "source_folders",
                    "target_root_dirs",
                    "target_folders",
                    "photos",
                    "settings",
                ),
                tables,
            )
        }
    }

    @Test
    fun `open is idempotent - reopening an already-migrated database is a no-op`() {
        AppDatabase(":memory:").use { first ->
            first.open()
        }

        AppDatabase(":memory:").use { second ->
            second.open()
            assertEquals(SCHEMA_VERSION, second.currentVersion())
        }
    }

    @Test
    fun `open stamps user_version with SCHEMA_VERSION after migrating`() {
        AppDatabase(":memory:").use { db ->
            db.open()
            assertEquals(SCHEMA_VERSION, db.currentVersion())
        }
    }

    private fun AppDatabase.tableNames(): List<String> {
        val rs = connection.metaData.getTables(null, null, null, arrayOf("TABLE"))
        val names = mutableListOf<String>()
        while (rs.next()) names += rs.getString("TABLE_NAME")
        rs.close()
        return names
    }

    private companion object {
        const val SCHEMA_VERSION = 1
    }
}