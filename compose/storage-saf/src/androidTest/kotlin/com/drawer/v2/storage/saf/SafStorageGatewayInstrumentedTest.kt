package com.drawer.v2.storage.saf

import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.drawer.v2.domain.StorageBackend
import com.drawer.v2.domain.StorageRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises Android's SAF URI handling on-device without relying on a user's
 * private documents. Real moves additionally require a user-selected tree.
 */
@RunWith(AndroidJUnit4::class)
class SafStorageGatewayInstrumentedTest {
    private val gateway = SafStorageGateway(InstrumentationRegistry.getInstrumentation().targetContext)

    @Test
    fun acceptsExternalStorageTreeAndKeepsTreeAndDocumentReferences() {
        val tree = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3APictures")

        val directory = gateway.directoryFromTreeUri(tree)

        assertEquals(StorageBackend.SAF, directory.backend)
        assertTrue(directory.token.startsWith("$tree\n"))
        assertTrue(directory.token.endsWith("primary%3APictures"))
    }

    @Test
    fun rejectsCloudAndNonTreeUris() {
        val cloudTree = Uri.parse("content://com.example.cloud.documents/tree/root")
        val fileUri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3APictures")

        assertFalse(gateway.isSupportedLocalTree(cloudTree))
        assertFalse(gateway.isSupportedLocalTree(fileUri))
    }

    @Test
    fun recognizesDescendantsOnlyWithinTheSameDocumentTree() {
        val tree = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3APictures")
        val parent = gateway.directoryFromTreeUri(tree)
        val childUri = DocumentsContract.buildDocumentUriUsingTree(tree, "primary:Pictures/Family")
        val otherUri = DocumentsContract.buildDocumentUriUsingTree(tree, "primary:Picturesque")
        val child = StorageRef(StorageBackend.SAF, "$tree\n$childUri")
        val other = StorageRef(StorageBackend.SAF, "$tree\n$otherUri")

        assertTrue(gateway.isSameOrDescendant(child, parent))
        assertTrue(gateway.isSameOrDescendant(parent, parent))
        assertFalse(gateway.isSameOrDescendant(other, parent))
    }
}
