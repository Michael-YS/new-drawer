package com.drawer.v2.persistence

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

/** Android keeps only the local index/configuration; source media is untouched. */
fun createAndroidDrawerDatabase(context: Context): DrawerDatabase {
    val driver = AndroidSqliteDriver(DrawerDatabase.Schema, context, "drawer-v2.db")
    return DrawerDatabase(driver)
}
