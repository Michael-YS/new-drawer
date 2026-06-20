import 'dart:io';
import 'package:file_picker/file_picker.dart';
import 'package:path/path.dart' as p;

abstract class FileService {
  String pathForDir(String rootPath, String folderName);
  String pathForFile(String rootPath, String folderName, String fileName);
  String basenameOf(String path);

  Future<String?> pickDirectory();
  Future<List<String>> scanFolder(String path, {bool recursive = true});
  Future<void> createDirectory(String path);
  Future<String> moveFile(String sourcePath, String destinationPath);
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
  String pathForDir(String rootPath, String folderName) {
    return p.join(rootPath, folderName);
  }

  @override
  String pathForFile(String rootPath, String folderName, String fileName) {
    return p.join(rootPath, folderName, fileName);
  }

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
  Future<String> moveFile(String sourcePath, String destinationPath) async {
    final sourceFile = File(sourcePath);
    if (!await sourceFile.exists()) {
      throw Exception('Source file does not exist: $sourcePath');
    }

    final destinationDir = Directory(p.dirname(destinationPath));
    if (!await destinationDir.exists()) {
      await destinationDir.create(recursive: true);
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
    } on FileSystemException {
      await sourceFile.copy(destinationPath);
      await sourceFile.delete();
    }
    return destinationPath;
  }

  @override
  Future<String> moveToOriginal(String sourcePath, String originalPath) async {
    return await moveFile(sourcePath, originalPath);
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
