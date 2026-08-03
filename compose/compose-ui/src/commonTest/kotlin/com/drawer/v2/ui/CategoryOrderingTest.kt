package com.drawer.v2.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class CategoryOrderingTest {
    @Test
    fun `selected category moves to front and recent list stays at four`() {
        assertEquals(
            listOf("Archive", "Travel", "Family", "Work"),
            prioritizeCategory(listOf("Travel", "Family", "Work", "Receipts"), "Archive"),
        )
    }

    @Test
    fun `reselecting a category does not duplicate it`() {
        assertEquals(
            listOf("Work", "Travel", "Family", "Receipts"),
            prioritizeCategory(listOf("Travel", "Family", "Work", "Receipts"), "Work"),
        )
    }
}
