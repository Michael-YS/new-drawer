import 'dart:io';
import 'package:path/path.dart' as p;

abstract class FileService {
  Future<List<String>> scanFolder(String path, {bool recursive = true});
  Future<void> createDirectory(String path);
  Future<void> moveFile(String sourcePath, String destinationPath);
  Future<void> deleteFile(String path);
  Future<bool> fileExists(String path);
  Future<bool> directoryExists(String path);
}

class WindowsFileService implements FileService {
  static const _supportedExtensions = {
    '.jpg',
    '.jpeg',
    '.png',
    '.gif',
    '.webp',
    '.heic'
  };

  @override
  Future<List<String>> scanFolder(String path, {bool recursive = true}) async {
    final directory = Directory(path);
    if (!await directory.exists()) {
      return [];
    }

    final List<String> imagePaths = [];

    await for (final entity in directory.list(recursive: recursive)) {
      if (entity is File) {
        final ext = p.extension(entity.path).toLowerCase();
        if (_supportedExtensions.contains(ext)) {
          imagePaths.add(entity.path);
        }
      }
    }

    return imagePaths;
  }

  @override
  Future<void> createDirectory(String path) async {
    final directory = Directory(path);
    if (!await directory.exists()) {
      await directory.create(recursive: true);
    }
  }

  @override
  Future<void> moveFile(String sourcePath, String destinationPath) async {
    final sourceFile = File(sourcePath);
    if (!await sourceFile.exists()) {
      throw Exception('Source file does not exist: $sourcePath');
    }

    final destFile = File(destinationPath);
    if (await destFile.exists()) {
      final ext = p.extension(destinationPath);
      final baseName = p.basenameWithoutExtension(destinationPath);
      final dir = p.dirname(destinationPath);
      int counter = 1;
      String newPath;
      do {
        newPath = p.join(dir, '${baseName}_$counter$ext');
        counter++;
      } while (await File(newPath).exists());
      destinationPath = newPath;
    }

    try {
      await sourceFile.rename(destinationPath);
    } catch (e) {
      if (e.toString().contains('Cross-device link')) {
        final bytes = await sourceFile.readAsBytes();
        await File(destinationPath).writeAsBytes(bytes);
        await sourceFile.delete();
      } else {
        rethrow;
      }
    }
  }

  @override
  Future<void> deleteFile(String path) async {
    final file = File(path);
    if (await file.exists()) {
      await file.delete();
    }
  }

  @override
  Future<bool> fileExists(String path) async {
    return await File(path).exists();
  }

  @override
  Future<bool> directoryExists(String path) async {
    return await Directory(path).exists();
  }
}