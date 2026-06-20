import 'package:flutter/services.dart';
import 'file_service.dart';

class AndroidFileService implements FileService {
  static const _channel = MethodChannel('com.example.new_drawer/saf');

  @override
  String pathForDir(String rootPath, String folderName) {
    return '$rootPath#$folderName';
  }

  @override
  String pathForFile(String rootPath, String folderName, String fileName) {
    return '$rootPath#$folderName/$fileName';
  }

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
  Future<void> createDirectory(String path) async {
    final parts = _splitPath(path);
    final name = parts.relSegments.isEmpty
        ? throw Exception('Cannot create root directory')
        : parts.relSegments.last;
    final parentRel = parts.relSegments.sublist(0, parts.relSegments.length - 1).join('/');
    await _channel.invokeMethod<void>('createDirectory', {
      'treeUri': parts.treeUri,
      'relativePath': parentRel,
      'name': name,
    });
  }

  @override
  Future<String> moveFile(String sourcePath, String destinationPath) async {
    final parts = _splitPath(destinationPath);
    if (parts.relSegments.isEmpty) {
      throw Exception('Destination path must include filename');
    }
    final parentRel =
        parts.relSegments.sublist(0, parts.relSegments.length - 1).join('/');
    final newUri = await _channel.invokeMethod<String>('moveFile', {
      'sourceUri': sourcePath,
      'destTreeUri': parts.treeUri,
      'destRelativePath': parentRel,
    });
    if (newUri == null) {
      throw Exception('moveFile returned null');
    }
    return newUri;
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

  @override
  Future<String> moveToOriginal(String sourceUri, String originalUri) async {
    final result = await _channel.invokeMethod<String>('moveToOriginal', {
      'sourceUri': _toDocUri(sourceUri),
      'originalUri': _toDocUri(originalUri),
    });
    if (result == null) {
      throw Exception('moveToOriginal returned null');
    }
    return result;
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
