package com.drawer.android.saf

import com.drawer.core.scanner.DirHandle

/**
 * Thin TurboModule wrapper that exposes the [com.drawer.core.scanner.FileSystemGateway]
 * surface to the React Native runtime. Concrete TurboModule wiring
 * (codegen, [NativeModule] / [ReactModule] annotations, lifecycle)
 * lives in the consuming app's `android-native-module` package, not
 * here — this module exposes plain Kotlin methods only.
 *
 * When the React Native shell wires up the bridge, it instantiates
 * [SafFileSystemGateway] from the app's `Context` and adapts each call
 * into a Promise-returning JS API.
 */
class ImageOrganizerTurboModule(private val gateway: SafFileSystemGateway) {

    fun listChildrenJson(dirHandle: DirHandle): List<String> =
        gateway.listChildren(dirHandle).map { it.name }
}