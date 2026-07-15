package com.drawer.v2.persistence

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File

/** Opens the per-user desktop index, creating its schema on first launch. */
fun createDesktopDrawerDatabase(file: File): DrawerDatabase {
    file.parentFile?.mkdirs()
    val isNew = !file.exists()
    val driver = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}")
    if (isNew) DrawerDatabase.Schema.create(driver)
    return DrawerDatabase(driver)
}
