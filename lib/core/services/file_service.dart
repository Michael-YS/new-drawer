import 'dart:io';
import 'package:file_picker/file_picker.dart';
import 'package:path/path.dart' as p;

abstract class FileService {
  String basenameOf(String path);

  Future<String?> pickDirectory();
  Future<List<String>> scanFolder(String path, {bool recursive = true});
  Future<void> ensureSubdirectory(String rootPath, String folderName);
  Future<String> moveToSubdirectory(
    String sourcePath,
    String rootPath,
    String folderName,
  );
  Future<String> moveToTrash(String sourcePath, String rootPath);
  Future<String> moveToOriginal(String sourcePath, String originalPath);
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
  String basenameOf(String path) => p.basename(path);

  @override
  Future<String?> pickDirectory() async {
    return await FilePicker.platform.getDirectoryPath();
  }

  @override
  Future<List<String>> scanFolder(String path, {bool recursive = true}) async {
    final directory = Directory(path);
    if (!await directory.exists()) {
      return [];
    }

    final imagePaths = <String>[];
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
  Future<void> ensureSubdirectory(String rootPath, String folderName) async {
    final path = p.join(rootPath, folderName);
    final directory = Directory(path);
    if (!await directory.exists()) {
      await directory.create(recursive: true);
    }
  }

  @override
  Future<String> moveToSubdirectory(
    String sourcePath,
    String rootPath,
    String folderName,
  ) async {
    final fileName = p.basename(sourcePath);
    final dir = folderName.isEmpty
        ? rootPath
        : p.join(rootPath, folderName);
    return _moveToDir(sourcePath, dir, fileName);
  }

  @override
  Future<String> moveToTrash(String sourcePath, String rootPath) async {
    await ensureSubdirectory(rootPath, '.trash');
    final name = p.basename(sourcePath);
    final ext = p.extension(name);
    final baseName = p.basenameWithoutExtension(name);
    final timestamp = DateTime.now().millisecondsSinceEpoch;
    final trashName = '${baseName}__$timestamp$ext';
    return _moveToDir(sourcePath, p.join(rootPath, '.trash'), trashName);
  }

  @override
  Future<String> moveToOriginal(String sourcePath, String originalPath) async {
    final fileName = p.basename(originalPath);
    final dir = p.dirname(originalPath);
    return _moveToDir(sourcePath, dir, fileName);
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

  Future<String> _moveToDir(
    String sourcePath,
    String dir,
    String fileName,
  ) async {
    final sourceFile = File(sourcePath);
    if (!await sourceFile.exists()) {
      throw Exception('Source file does not exist: $sourcePath');
    }

    final destinationDir = Directory(dir);
    if (!await destinationDir.exists()) {
      await destinationDir.create(recursive: true);
    }

    var destinationPath = p.join(dir, fileName);
    final destFile = File(destinationPath);
    if (await destFile.exists()) {
      final ext = p.extension(fileName);
      final baseName = p.basenameWithoutExtension(fileName);
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
    } on FileSystemException {
      await sourceFile.copy(destinationPath);
      await sourceFile.delete();
    }
    return destinationPath;
  }
}
