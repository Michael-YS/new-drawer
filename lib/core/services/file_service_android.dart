import 'package:flutter/services.dart';
import 'file_service.dart';

class AndroidFileService implements FileService {
  static const _channel = MethodChannel('com.github.Michael_YS.Drawer/saf');

  @override
  String basenameOf(String path) {
    if (!path.startsWith('content://')) return path.substring(path.lastIndexOf('/') + 1);
    final uri = Uri.parse(path);
    if (uri.pathSegments.isEmpty) return path;
    final lastSegment = uri.pathSegments.last;
    final decoded = Uri.decodeComponent(lastSegment);
    final slashIdx = decoded.lastIndexOf('/');
    return slashIdx >= 0 ? decoded.substring(slashIdx + 1) : decoded;
  }

  @override
  Future<String?> pickDirectory() async {
    return await _channel.invokeMethod<String?>('pickDirectory');
  }

  @override
  Future<List<String>> scanFolder(String path, {bool recursive = true}) async {
    final result = await _channel.invokeMethod<List<Object?>>('listFiles', {
      'treeUri': path,
      'recursive': recursive,
    });
    if (result == null) return const [];
    return result.whereType<String>().toList();
  }

  @override
  Future<void> ensureSubdirectory(String rootPath, String folderName) async {
    final parts = _splitPath(rootPath);
    final segments = [...parts.relSegments, folderName];
    final existing = await _findBySegments(parts.treeUri, segments);
    if (existing != null) return;
    final parentRel = parts.relSegments.join('/');
    await _channel.invokeMethod<void>('createDirectory', {
      'treeUri': parts.treeUri,
      'relativePath': parentRel,
      'name': folderName,
    });
  }

  @override
  Future<String> moveToSubdirectory(
    String sourcePath,
    String rootPath,
    String folderName,
  ) async {
    final rootParts = _splitPath(rootPath);
    final fileName = basenameOf(sourcePath);
    final segments = [...rootParts.relSegments, folderName, fileName];
    return _moveAlongSegments(sourcePath, rootParts.treeUri, segments);
  }

  @override
  Future<String> moveToTrash(String sourcePath, String rootPath) async {
    await ensureSubdirectory(rootPath, '.trash');
    final rootParts = _splitPath(rootPath);
    final name = basenameOf(sourcePath);
    final dotIdx = name.lastIndexOf('.');
    final ext = dotIdx >= 0 ? name.substring(dotIdx) : '';
    final baseName = dotIdx >= 0 ? name.substring(0, dotIdx) : name;
    final timestamp = DateTime.now().millisecondsSinceEpoch;
    final trashName = '${baseName}__$timestamp$ext';
    final segments = [...rootParts.relSegments, '.trash', trashName];
    return _moveAlongSegments(sourcePath, rootParts.treeUri, segments);
  }

  @override
  Future<String> moveToOriginal(String sourcePath, String originalPath) async {
    final originalParts = _splitPath(originalPath);
    return _moveAlongSegments(sourcePath, originalParts.treeUri, originalParts.relSegments);
  }

  @override
  Future<void> deleteFile(String path) async {
    final ok = await _channel.invokeMethod<bool>('deleteFile', {
      'uri': _toDocUri(path),
    });
    if (ok != true) {
      throw Exception('Failed to delete file: $path');
    }
  }

  @override
  Future<bool> fileExists(String path) async {
    if (!path.startsWith('content://')) return false;
    try {
      final result = await _channel.invokeMethod<bool>('fileExists', {
        'uri': _toDocUri(path),
      });
      return result ?? false;
    } catch (_) {
      return true;
    }
  }

  @override
  Future<bool> directoryExists(String path) {
    return Future.value(path.startsWith('content://'));
  }

  Future<String> _moveAlongSegments(
    String sourcePath,
    String treeUri,
    List<String> segments,
  ) async {
    if (segments.isEmpty) {
      throw Exception('Destination path must include filename');
    }
    final parentRel = segments.sublist(0, segments.length - 1).join('/');
    final newUri = await _channel.invokeMethod<String>('moveFile', {
      'sourceUri': sourcePath,
      'destTreeUri': treeUri,
      'destRelativePath': parentRel,
    });
    if (newUri == null) {
      throw Exception('moveFile returned null');
    }
    return newUri;
  }

  Future<String?> _findBySegments(String treeUri, List<String> segments) async {
    final docId = segments.join('/');
    final uri = '$treeUri/document/${segments.map(Uri.encodeComponent).join('%2F')}';
    try {
      final result = await _channel.invokeMethod<String>('findFile', {
        'treeUri': treeUri,
        'documentId': docId,
      });
      return result ?? uri;
    } catch (_) {
      return null;
    }
  }

  _PathParts _splitPath(String path) {
    if (!path.startsWith('content://')) {
      throw Exception('AndroidFileService expects content:// paths, got: $path');
    }
    final hashIdx = path.indexOf('#');
    if (hashIdx < 0) {
      return _PathParts(treeUri: path, relSegments: const []);
    }
    final treeUri = path.substring(0, hashIdx);
    final rel = path.substring(hashIdx + 1);
    final relSegments = rel.isEmpty
        ? const <String>[]
        : rel.split('/').where((s) => s.isNotEmpty).toList();
    return _PathParts(treeUri: treeUri, relSegments: relSegments);
  }

  String _toDocUri(String path) {
    final hashIdx = path.indexOf('#');
    if (hashIdx < 0) return path;
    final treeUri = path.substring(0, hashIdx);
    final rel = path.substring(hashIdx + 1);
    if (rel.isEmpty) return treeUri;
    final docId = rel.split('/').map(Uri.encodeComponent).join('%2F');
    return '$treeUri/document/$docId';
  }
}

class _PathParts {
  final String treeUri;
  final List<String> relSegments;

  const _PathParts({required this.treeUri, required this.relSegments});
}
