package com.drawer.server

import com.drawer.core.scanner.DirHandle
import com.drawer.core.scanner.EntryHandle
import com.drawer.core.scanner.FileSystemGateway
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Contract test for [NioFileSystemGateway]. Drives the real
 * [java.nio.file] surface via JUnit's [@TempDir] so failures point at
 * actual filesystem behavior, not at the in-memory fake.
 *
 * Each test scopes to one gateway method, matching the per-method
 * coverage style used for the in-memory fake in `:core-scanner`.
 */
class NioFileSystemGatewayTest {

    @Test
    fun `listChildren returns the files and subdirectories of a real directory`(@TempDir tmp: Path) {
        Files.writeString(tmp.resolve("a.txt"), "hello")
        Files.createDirectory(tmp.resolve("sub"))

        val gateway = NioFileSystemGateway()
        val root = gateway.dirOf(tmp)

        val names = gateway.listChildren(root).map { it.name }.toSet()

        assertEquals(setOf("a.txt", "sub"), names)
    }

    @Test
    fun `isDirectory returns true for a subdir entry and false for a file entry`(@TempDir tmp: Path) {
        Files.writeString(tmp.resolve("a.txt"), "x")
        Files.createDirectory(tmp.resolve("sub"))

        val gateway = NioFileSystemGateway()
        val root = gateway.dirOf(tmp)
        val children = gateway.listChildren(root)

        val file = children.single { it.name == "a.txt" }
        val subdir = children.single { it.name == "sub" }

        assertEquals(false, gateway.isDirectory(file))
        assertEquals(true, gateway.isDirectory(subdir))
    }

    @Test
    fun `dirHandle returns a usable DirHandle for a subdir entry so traversal can recurse`(@TempDir tmp: Path) {
        Files.createDirectory(tmp.resolve("sub"))
        Files.writeString(tmp.resolve("sub").resolve("inside.txt"), "data")

        val gateway = NioFileSystemGateway()
        val root = gateway.dirOf(tmp)
        val subdirEntry = gateway.listChildren(root).single { it.name == "sub" }
        val subdirHandle = gateway.dirHandle(subdirEntry)!!

        val insideNames = gateway.listChildren(subdirHandle).map { it.name }.toSet()

        assertEquals(setOf("inside.txt"), insideNames)
    }

    @Test
    fun `readMagicBytes returns the leading prefix of a real file`(@TempDir tmp: Path) {
        val bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + "data".toByteArray()
        Files.write(tmp.resolve("photo.jpg"), bytes)

        val gateway = NioFileSystemGateway()
        val root = gateway.dirOf(tmp)
        val file = gateway.listChildren(root).single()

        val read = gateway.readMagicBytes(file, 4)

        assertEquals(listOf<Byte>(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()), read.toList())
    }
}