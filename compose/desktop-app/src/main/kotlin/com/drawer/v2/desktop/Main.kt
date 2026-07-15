package com.drawer.v2.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.drawer.v2.ui.DrawerApp

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Drawer v2",
    ) {
        DrawerApp()
    }
}
