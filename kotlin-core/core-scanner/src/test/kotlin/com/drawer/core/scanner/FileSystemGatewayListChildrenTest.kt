package com.drawer.core.scanner

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Contract test for [FileSystemGateway.listChildren].
 *
 * One test = one behavior. Add more tests as new behaviors land on the
 * gateway interface; keep each test focused on a single observable.
 */
class FileSystemGatewayListChildrenTest {

    @Test
    fun `listChildren returns immediate child entries with their names`() {
        val fake = FakeFileSystemGateway()
        val root = fake.newDir("root")
        fake.newFile(root, "a.jpg")
        fake.newFile(root, "b.png")

        val children = fake.listChildren(root)

        assertEquals(2, children.size)
        assertEquals(
            setOf("a.jpg", "b.png"),
            children.map { it.name }.toSet(),
        )
    }
}