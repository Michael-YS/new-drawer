package com.drawer.v2.persistence

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.drawer.v2.domain.FileFingerprint
import com.drawer.v2.domain.OperationJournalEntry
import com.drawer.v2.domain.OperationStage
import com.drawer.v2.domain.StorageBackend
import com.drawer.v2.domain.StorageRef
import com.drawer.v2.domain.SuppressedItem
import com.drawer.v2.domain.SuppressionReason
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.io.path.createTempDirectory

class SqlDelightStoresTest {
    @Test
    fun `journal survives store recreation semantics`() = runTest {
        val database = database()
        val store = SqlDelightOperationJournalStore(database)
        val source = ref("source/photo.jpg")
        val entry = OperationJournalEntry(
            operationId = "op-1",
            stage = OperationStage.FINALIZED,
            source = source,
            sourceParent = ref("source"),
            sourceName = "photo.jpg",
            targetParent = ref("target"),
            targetName = "photo.jpg",
            finalTarget = ref("target/photo.jpg"),
        )

        store.replace(entry)

        assertEquals(entry, store.active())
        store.clear()
        assertNull(store.active())
    }

    @Test
    fun `suppression requires matching fingerprint`() = runTest {
        val store = SqlDelightSuppressionStore(database())
        val file = ref("source/photo.jpg")
        store.suppress(
            SuppressedItem("source-1", file, FileFingerprint(12, 34), SuppressionReason.SKIPPED),
        )

        assertTrue(store.isSuppressed("source-1", file, FileFingerprint(12, 34)))
        assertFalse(store.isSuppressed("source-1", file, FileFingerprint(13, 34)))
        store.clear(SuppressionReason.SKIPPED)
        assertFalse(store.isSuppressed("source-1", file, FileFingerprint(12, 34)))
    }

    @Test
    fun `desktop factory reopens an existing local index`() {
        val file = createTempDirectory("drawer-db-").resolve("drawer.db").toFile()
        val target = com.drawer.v2.domain.TargetRoot(ref("target"), "Target")
        SqlDelightConfigurationStore(createDesktopDrawerDatabase(file)).saveTargetRoot(target)

        assertEquals(target, SqlDelightConfigurationStore(createDesktopDrawerDatabase(file)).targetRoot())
    }

    private fun database(): DrawerDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        DrawerDatabase.Schema.create(driver)
        return DrawerDatabase(driver)
    }

    private fun ref(token: String) = StorageRef(StorageBackend.NIO, token)
}
