package com.github.Michael_YS.Drawer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : FlutterActivity() {
    private val CHANNEL = "com.github.Michael_YS.Drawer/saf"
    private val PICK_TREE_REQUEST = 1001

    private val TAG = "SafChannel"

    private var pendingResult: MethodChannel.Result? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "pickDirectory" -> handlePickDirectory(result)
                    "listFiles" -> handleListFiles(call, result)
                    "createDirectory" -> handleCreateDirectory(call, result)
                    "moveFile" -> handleMoveFile(call, result)
                    "deleteFile" -> handleDeleteFile(call, result)
                    "fileExists" -> handleFileExists(call, result)
                    "getDisplayName" -> handleGetDisplayName(call, result)
                    "readFile" -> handleReadFile(call, result)
                    "moveToOriginal" -> handleMoveToOriginal(call, result)
                    else -> result.notImplemented()
                }
            }
    }

    private fun handlePickDirectory(result: MethodChannel.Result) {
        if (pendingResult != null) {
            result.error("BUSY", "Another pick is in progress", null)
            return
        }
        pendingResult = result
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        }
        try {
            startActivityForResult(intent, PICK_TREE_REQUEST)
        } catch (e: Exception) {
            pendingResult = null
            result.error("PICK_FAILED", e.message, null)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_TREE_REQUEST) {
            val result = pendingResult
            pendingResult = null
            if (result == null) return

            if (resultCode == Activity.RESULT_OK && data?.data != null) {
                val treeUri = data.data!!
                try {
                    contentResolver.takePersistableUriPermission(
                        treeUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                    result.success(treeUri.toString())
                } catch (e: SecurityException) {
                    Log.w(TAG, "takePersistableUriPermission failed: ${e.message}")
                    result.success(treeUri.toString())
                } catch (e: Exception) {
                    result.error("PERMISSION_FAILED", e.message, null)
                }
            } else {
                result.success(null)
            }
        }
    }

    private fun handleListFiles(call: io.flutter.plugin.common.MethodCall, result: MethodChannel.Result) {
        val treeUriStr = call.argument<String>("treeUri")
        val recursive = call.argument<Boolean>("recursive") ?: true
        if (treeUriStr == null) {
            result.error("ARG_MISSING", "treeUri is required", null)
            return
        }
        scope.launch {
            try {
                val files = withContext(Dispatchers.IO) {
                    listFilesInTree(Uri.parse(treeUriStr), recursive)
                }
                result.success(files)
            } catch (e: Exception) {
                Log.e(TAG, "listFiles failed", e)
                result.error("LIST_FAILED", e.message, null)
            }
        }
    }

    private fun listFilesInTree(treeUri: Uri, recursive: Boolean): List<String> {
        val root = DocumentFile.fromTreeUri(this, treeUri)
            ?: throw Exception("Cannot open tree URI: $treeUri")
        val results = mutableListOf<String>()
        walkTree(root, results, recursive)
        return results
    }

    private fun walkTree(
        dir: DocumentFile,
        results: MutableList<String>,
        recursive: Boolean
    ) {
        val children = try {
            dir.listFiles()
        } catch (e: Exception) {
            Log.e(TAG, "walkTree: listFiles() threw at ${dir.uri}: ${e.message}", e)
            return
        }
        for (file in children) {
            if (file.isDirectory) {
                if (recursive) {
                    walkTree(file, results, recursive)
                }
            } else {
                val name = file.name ?: continue
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext in SUPPORTED_EXTS) {
                    results.add(file.uri.toString())
                }
            }
        }
    }

    private fun handleCreateDirectory(
        call: io.flutter.plugin.common.MethodCall,
        result: MethodChannel.Result
    ) {
        val treeUriStr = call.argument<String>("treeUri")
        val relativePath = call.argument<String>("relativePath") ?: ""
        val name = call.argument<String>("name")
        if (treeUriStr == null || name == null) {
            result.error("ARG_MISSING", "treeUri and name are required", null)
            return
        }
        scope.launch {
            try {
                val newDirUri = withContext(Dispatchers.IO) {
                    val root = DocumentFile.fromTreeUri(
                        this@MainActivity,
                        Uri.parse(treeUriStr)
                    ) ?: throw Exception("Cannot open tree URI")
                    val parent = navigateToDirCreate(root, relativePath)
                    val existing = parent.findFile(name)
                    if (existing != null && existing.isDirectory) {
                        existing.uri.toString()
                    } else {
                        val created = parent.createDirectory(name)
                            ?: throw Exception("createDirectory returned null for $name")
                        created.uri.toString()
                    }
                }
                result.success(newDirUri)
            } catch (e: Exception) {
                Log.e(TAG, "createDirectory failed", e)
                result.error("CREATE_FAILED", e.message, null)
            }
        }
    }

    private fun navigateToDirCreate(root: DocumentFile, relativePath: String): DocumentFile {
        if (relativePath.isEmpty()) return root
        var current = root
        for (segment in relativePath.split("/").filter { it.isNotEmpty() }) {
            var found: DocumentFile? = null
            for (child in current.listFiles()) {
                if (child.isDirectory && child.name == segment) {
                    found = child
                    break
                }
            }
            if (found == null) {
                found = current.createDirectory(segment)
                    ?: throw Exception("Failed to create directory: $segment")
            }
            current = found
        }
        return current
    }

    private fun navigateToDir(root: DocumentFile, relativePath: String): DocumentFile? {
        if (relativePath.isEmpty()) return root
        var current: DocumentFile = root
        for (segment in relativePath.split("/").filter { it.isNotEmpty() }) {
            var found: DocumentFile? = null
            for (child in current.listFiles()) {
                if (child.isDirectory && child.name == segment) {
                    found = child
                    break
                }
            }
            if (found == null) return null
            current = found
        }
        return current
    }

    private fun handleMoveFile(
        call: io.flutter.plugin.common.MethodCall,
        result: MethodChannel.Result
    ) {
        val sourceUriStr = call.argument<String>("sourceUri")
        val destTreeUriStr = call.argument<String>("destTreeUri")
        val destRelativePath = call.argument<String>("destRelativePath") ?: ""
        if (sourceUriStr == null || destTreeUriStr == null) {
            result.error("ARG_MISSING", "sourceUri and destTreeUri are required", null)
            return
        }
        scope.launch {
            try {
                val newUri = withContext(Dispatchers.IO) {
                    val sourceDocUri = Uri.parse(sourceUriStr)
                    val source = DocumentFile.fromSingleUri(
                        this@MainActivity,
                        sourceDocUri
                    ) ?: throw Exception("Source file not found: $sourceUriStr")
                    val sourceName = source.name
                        ?: throw Exception("Source has no name")

                    val sourceParent = source.parentFile
                        ?: resolveParent(sourceDocUri)
                        ?: throw Exception("Cannot resolve source parent")

                    val root = DocumentFile.fromTreeUri(
                        this@MainActivity,
                        Uri.parse(destTreeUriStr)
                    ) ?: throw Exception("Cannot open dest tree URI")
                    val destDir = navigateToDirCreate(root, destRelativePath)

                    var targetName = sourceName
                    var counter = 1
                    while (findFileInDir(destDir, targetName) != null) {
                        targetName = withSuffix(sourceName, counter)
                        counter++
                    }

                    val movedUri = DocumentsContract.moveDocument(
                        contentResolver,
                        source.uri,
                        sourceParent.uri,
                        destDir.uri
                    ) ?: throw Exception("moveDocument returned null")

                    if (targetName != sourceName) {
                        DocumentsContract.renameDocument(
                            contentResolver,
                            movedUri,
                            targetName
                        )?.toString() ?: movedUri.toString()
                    } else {
                        movedUri.toString()
                    }
                }
                result.success(newUri)
            } catch (e: Exception) {
                Log.e(TAG, "moveFile failed", e)
                result.error("MOVE_FAILED", e.message, null)
            }
        }
    }

    private fun deriveTreeUri(documentUri: Uri): Uri {
        val s = documentUri.toString()
        val idx = s.indexOf("/document/")
        return if (idx >= 0) Uri.parse(s.substring(0, idx)) else documentUri
    }

    private fun resolveParent(documentUri: Uri): DocumentFile? {
        val docId = try {
            DocumentsContract.getDocumentId(documentUri)
        } catch (e: Exception) {
            return null
        }
        val parentDocId = docId.substringBeforeLast("/", "")
        val treeUri = deriveTreeUri(documentUri)
        return if (parentDocId.isEmpty() || parentDocId == docId) {
            DocumentFile.fromTreeUri(this, treeUri)
        } else {
            val parentUri = treeUri.buildUpon()
                .appendPath("document")
                .appendPath(parentDocId)
                .build()
            DocumentFile.fromTreeUri(this, parentUri)
                ?: DocumentFile.fromSingleUri(this, parentUri)
        }
    }

    private fun findFileInDir(dir: DocumentFile, name: String): DocumentFile? {
        for (child in dir.listFiles()) {
            if (!child.isDirectory && child.name == name) return child
        }
        return null
    }

    private fun withSuffix(originalName: String, counter: Int): String {
        val dotIdx = originalName.lastIndexOf('.')
        return if (dotIdx > 0) {
            "${originalName.substring(0, dotIdx)}_$counter${originalName.substring(dotIdx)}"
        } else {
            "${originalName}_$counter"
        }
    }

    private fun handleDeleteFile(
        call: io.flutter.plugin.common.MethodCall,
        result: MethodChannel.Result
    ) {
        val uriStr = call.argument<String>("uri")
        if (uriStr == null) {
            result.error("ARG_MISSING", "uri is required", null)
            return
        }
        scope.launch {
            try {
                val ok = withContext(Dispatchers.IO) {
                    val file = DocumentFile.fromSingleUri(
                        this@MainActivity,
                        Uri.parse(uriStr)
                    ) ?: return@withContext false
                    try {
                        file.delete()
                    } catch (e: Exception) {
                        false
                    }
                }
                result.success(ok)
            } catch (e: Exception) {
                Log.e(TAG, "deleteFile failed", e)
                result.error("DELETE_FAILED", e.message, null)
            }
        }
    }

    private fun handleFileExists(
        call: io.flutter.plugin.common.MethodCall,
        result: MethodChannel.Result
    ) {
        val uriStr = call.argument<String>("uri")
        if (uriStr == null) {
            result.error("ARG_MISSING", "uri is required", null)
            return
        }
        scope.launch {
            try {
                val exists = withContext(Dispatchers.IO) {
                    try {
                        val file = DocumentFile.fromSingleUri(
                            this@MainActivity,
                            Uri.parse(uriStr)
                        ) ?: return@withContext false
                        file.exists()
                    } catch (e: Exception) {
                        Log.e(TAG, "fileExists: threw for $uriStr: ${e.message}", e)
                        true
                    }
                }
                result.success(exists)
            } catch (e: Exception) {
                Log.e(TAG, "fileExists failed", e)
                result.error("EXISTS_FAILED", e.message, null)
            }
        }
    }

    private fun handleGetDisplayName(
        call: io.flutter.plugin.common.MethodCall,
        result: MethodChannel.Result
    ) {
        val uriStr = call.argument<String>("uri")
        if (uriStr == null) {
            result.error("ARG_MISSING", "uri is required", null)
            return
        }
        scope.launch {
            try {
                val name = withContext(Dispatchers.IO) {
                    try {
                        DocumentFile.fromSingleUri(
                            this@MainActivity,
                            Uri.parse(uriStr)
                        )?.name
                    } catch (e: Exception) {
                        null
                    }
                }
                result.success(name)
            } catch (e: Exception) {
                Log.e(TAG, "getDisplayName failed", e)
                result.error("NAME_FAILED", e.message, null)
            }
        }
    }

    private fun handleReadFile(
        call: io.flutter.plugin.common.MethodCall,
        result: MethodChannel.Result
    ) {
        val uriStr = call.argument<String>("uri")
        if (uriStr == null) {
            result.error("ARG_MISSING", "uri is required", null)
            return
        }
        scope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(Uri.parse(uriStr))?.use {
                        it.readBytes()
                    } ?: throw Exception("Cannot open input stream for $uriStr")
                }
                result.success(bytes)
            } catch (e: Exception) {
                Log.e(TAG, "readFile failed", e)
                result.error("READ_FAILED", e.message, null)
            }
        }
    }

    private fun handleMoveToOriginal(
        call: io.flutter.plugin.common.MethodCall,
        result: MethodChannel.Result
    ) {
        val sourceUriStr = call.argument<String>("sourceUri")
        val originalUriStr = call.argument<String>("originalUri")
        if (sourceUriStr == null || originalUriStr == null) {
            result.error("ARG_MISSING", "sourceUri and originalUri are required", null)
            return
        }
        scope.launch {
            try {
                val newUri = withContext(Dispatchers.IO) {
                    val sourceDocUri = Uri.parse(sourceUriStr)
                    val source = DocumentFile.fromSingleUri(
                        this@MainActivity,
                        sourceDocUri
                    ) ?: throw Exception("Source file not found: $sourceUriStr")
                    val sourceName = source.name
                        ?: throw Exception("Source has no name")

                    val sourceParent = source.parentFile
                        ?: DocumentFile.fromTreeUri(
                            this@MainActivity,
                            deriveTreeUri(sourceDocUri)
                        ) ?: throw Exception("Cannot resolve source parent")

                    val originalUri = Uri.parse(originalUriStr)
                    val originalDocId = try {
                        DocumentsContract.getDocumentId(originalUri)
                    } catch (e: Exception) {
                        originalUri.lastPathSegment
                    } ?: throw Exception("originalUri has no documentId")
                    val originalName = originalDocId.substringAfterLast("/", originalDocId)

                    val parentDocId = originalDocId.substringBeforeLast("/", "")
                    val originalTreeUri = deriveTreeUri(originalUri)
                    val parent = if (parentDocId.isEmpty() || parentDocId == originalDocId) {
                        DocumentFile.fromTreeUri(this@MainActivity, originalTreeUri)
                    } else {
                        val parentUri = originalTreeUri.buildUpon()
                            .appendPath("document")
                            .appendPath(parentDocId)
                            .build()
                        DocumentFile.fromTreeUri(this@MainActivity, parentUri)
                            ?: DocumentFile.fromSingleUri(this@MainActivity, parentUri)
                    } ?: throw Exception("Cannot resolve original parent")

                    var targetName = originalName
                    var counter = 1
                    while (findFileInDir(parent, targetName) != null) {
                        targetName = withSuffix(originalName, counter)
                        counter++
                    }

                    val movedUri = DocumentsContract.moveDocument(
                        contentResolver,
                        source.uri,
                        sourceParent.uri,
                        parent.uri
                    ) ?: throw Exception("moveDocument returned null")

                    if (targetName != originalName) {
                        DocumentsContract.renameDocument(
                            contentResolver,
                            movedUri,
                            targetName
                        )?.toString() ?: movedUri.toString()
                    } else {
                        movedUri.toString()
                    }
                }
                result.success(newUri)
            } catch (e: Exception) {
                Log.e(TAG, "moveToOriginal failed", e)
                result.error("MOVE_FAILED", e.message, null)
            }
        }
    }

    companion object {
        private val SUPPORTED_EXTS = setOf(
            "jpg", "jpeg", "png", "gif", "webp", "heic"
        )
    }
}
