import 'dart:io';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/source_folder.dart';
import '../models/target_root_dir.dart';
import '../models/target_folder.dart';
import '../models/photo.dart';
import '../models/scan_progress.dart';
import '../services/file_service.dart';
import '../services/file_service_android.dart';
import '../services/photo_repository.dart';

final fileServiceProvider = Provider<FileService>((ref) {
  if (Platform.isAndroid) return AndroidFileService();
  return WindowsFileService();
});

final sourceFolderRepoProvider = Provider((ref) => SourceFolderRepository());
final targetRootDirRepoProvider = Provider((ref) => TargetRootDirRepository());
final targetFolderRepoProvider = Provider((ref) => TargetFolderRepository());
final photoRepoProvider = Provider((ref) => PhotoRepository());

final sourceFoldersProvider = StateNotifierProvider<SourceFoldersNotifier, List<SourceFolder>>((ref) {
  return SourceFoldersNotifier(ref);
});

class SourceFoldersNotifier extends StateNotifier<List<SourceFolder>> {
  final Ref ref;

  SourceFoldersNotifier(this.ref) : super([]) {
    _load();
  }

  Future<void> _load() async {
    final repo = ref.read(sourceFolderRepoProvider);
    state = await repo.getAll();
  }

  Future<void> add(String path, String displayName, {bool recursive = true}) async {
    final repo = ref.read(sourceFolderRepoProvider);
    final folder = SourceFolder(
      path: path,
      displayName: displayName,
      recursive: recursive,
      addedAt: DateTime.now().millisecondsSinceEpoch,
    );
    final id = await repo.insert(folder);
    state = [...state, folder.copyWith(id: id)];
    await ref.read(photoScannerProvider.notifier).scanFolder(folder.copyWith(id: id));
  }

  Future<void> remove(int id) async {
    final repo = ref.read(sourceFolderRepoProvider);
    await repo.delete(id);
    final photoRepo = ref.read(photoRepoProvider);
    await photoRepo.deleteBySourceFolder(id, onlyPending: true);
    state = state.where((f) => f.id != id).toList();
    ref.read(photoScannerProvider.notifier).scanAll();
  }

  Future<void> toggleEnabled(int id) async {
    final repo = ref.read(sourceFolderRepoProvider);
    final folder = state.firstWhere((f) => f.id == id);
    final updated = folder.copyWith(enabled: !folder.enabled);
    await repo.update(updated);
    state = state.map((f) => f.id == id ? updated : f).toList();
  }

  Future<void> refresh() async {
    await _load();
  }
}

final targetRootDirsProvider = StateNotifierProvider<TargetRootDirsNotifier, List<TargetRootDir>>((ref) {
  return TargetRootDirsNotifier(ref);
});

class TargetRootDirsNotifier extends StateNotifier<List<TargetRootDir>> {
  final Ref ref;

  TargetRootDirsNotifier(this.ref) : super([]) {
    _load();
  }

  Future<void> _load() async {
    final repo = ref.read(targetRootDirRepoProvider);
    state = await repo.getAll();
  }

  Future<void> add(String path, String displayName, {bool isDefault = false}) async {
    final repo = ref.read(targetRootDirRepoProvider);
    if (isDefault) {
      await repo.clearDefault();
    }
    final dir = TargetRootDir(
      path: path,
      displayName: displayName,
      isDefault: isDefault,
      addedAt: DateTime.now().millisecondsSinceEpoch,
    );
    final id = await repo.insert(dir);
    final newDir = dir.copyWith(id: id);
    state = [
      ...state.map((d) => isDefault ? d.copyWith(isDefault: false) : d),
      newDir,
    ];
  }

  Future<void> setDefault(int id) async {
    final repo = ref.read(targetRootDirRepoProvider);
    await repo.clearDefault();
    final dir = state.firstWhere((d) => d.id == id);
    final updated = dir.copyWith(isDefault: true);
    await repo.update(updated);
    state = state.map((d) => d.id == id ? updated : d.copyWith(isDefault: false)).toList();
  }

  Future<void> remove(int id) async {
    final repo = ref.read(targetRootDirRepoProvider);
    await repo.delete(id);
    state = state.where((d) => d.id != id).toList();
  }

  TargetRootDir? get defaultRootDir {
    try {
      return state.firstWhere((d) => d.isDefault);
    } catch (_) {
      return state.isNotEmpty ? state.first : null;
    }
  }

  Future<void> refresh() async {
    await _load();
  }
}

final targetFoldersProvider = StateNotifierProvider<TargetFoldersNotifier, List<TargetFolder>>((ref) {
  return TargetFoldersNotifier(ref);
});

class TargetFoldersNotifier extends StateNotifier<List<TargetFolder>> {
  final Ref ref;

  TargetFoldersNotifier(this.ref) : super([]) {
    _load();
  }

  Future<void> _load() async {
    final repo = ref.read(targetFolderRepoProvider);
    state = await repo.getAll();
  }

  Future<TargetFolder> add(String name, int rootDirId) async {
    final repo = ref.read(targetFolderRepoProvider);
    final fileService = ref.read(fileServiceProvider);
    final rootDirs = ref.read(targetRootDirsProvider);
    final rootDir = rootDirs.firstWhere((d) => d.id == rootDirId);

    await fileService.ensureSubdirectory(rootDir.path, name);

    final folder = TargetFolder(
      rootDirId: rootDirId,
      name: name,
      displayName: name,
      sortOrder: state.length,
    );
    final id = await repo.insert(folder);
    final newFolder = folder.copyWith(id: id);
    state = [...state, newFolder];

    return newFolder;
  }

  Future<void> remove(int id) async {
    final repo = ref.read(targetFolderRepoProvider);
    await repo.delete(id);
    state = state.where((f) => f.id != id).toList();
  }

  Future<void> rename(int id, String newDisplayName) async {
    final repo = ref.read(targetFolderRepoProvider);
    final folder = state.firstWhere((f) => f.id == id);
    final updated = folder.copyWith(displayName: newDisplayName);
    await repo.update(updated);
    state = state.map((f) => f.id == id ? updated : f).toList();
  }

  Future<void> reorder(int oldIndex, int newIndex) async {
    final items = [...state];
    final item = items.removeAt(oldIndex);
    items.insert(newIndex, item);
    final repo = ref.read(targetFolderRepoProvider);
    await repo.updateSortOrders(items);
    state = items.asMap().entries.map((e) => e.value.copyWith(sortOrder: e.key)).toList();
  }

  Future<void> updateLastUsed(int id) async {
    final repo = ref.read(targetFolderRepoProvider);
    await repo.updateLastUsed(id);
    state = state.map((f) => f.id == id ? f.copyWith(lastUsedAt: DateTime.now().millisecondsSinceEpoch) : f).toList();
  }

  Future<void> refresh() async {
    await _load();
  }
}

final photoScannerProvider = StateNotifierProvider<PhotoScannerNotifier, ScanProgress>((ref) {
  return PhotoScannerNotifier(ref);
});

class PhotoScannerNotifier extends StateNotifier<ScanProgress> {
  final Ref ref;

  PhotoScannerNotifier(this.ref) : super(const ScanProgress());

  Future<void> scanFolder(SourceFolder folder) async {
    final fileService = ref.read(fileServiceProvider);
    final photoRepo = ref.read(photoRepoProvider);

    try {
      final paths = await fileService.scanFolder(folder.path, recursive: folder.recursive);

      state = ScanProgress(isScanning: true, current: 0, total: paths.length, currentFolder: folder.displayName);

      const batchSize = 500;
      for (int i = 0; i < paths.length; i += batchSize) {
        final end = (i + batchSize).clamp(0, paths.length);
        final batch = paths.sublist(i, end);
        final photos = batch
            .map((p) => Photo(
                  sourceFolderId: folder.id!,
                  path: p,
                  status: PhotoStatus.pending,
                ))
            .toList(growable: false);
        await photoRepo.insertBatch(photos);
        state = state.copyWith(current: end);
      }

      ref.invalidate(currentPhotoProvider);
      ref.invalidate(photoStatsProvider);
    } finally {
      state = const ScanProgress();
    }
  }

  Future<void> _markMissingForFolder(SourceFolder folder) async {
    final fileService = ref.read(fileServiceProvider);
    final photoRepo = ref.read(photoRepoProvider);
    final existing = await photoRepo.getBySourceFolder(folder.id!);
    for (final photo in existing) {
      if (photo.status == PhotoStatus.trashed) continue;
      if (photo.status == PhotoStatus.missing) continue;
      final stillExists = await fileService.fileExists(photo.path);
      if (!stillExists) {
        await photoRepo.markMissing(photo.id!);
      }
    }
  }

  Future<void> scanAll() async {
    state = const ScanProgress(isScanning: true);
    final folders = ref.read(sourceFoldersProvider).where((f) => f.enabled).toList();
    try {
      for (final folder in folders) {
        await _markMissingForFolder(folder);
        await scanFolder(folder);
      }
      ref.invalidate(currentPhotoProvider);
      ref.invalidate(photoStatsProvider);
    } finally {
      state = const ScanProgress();
    }
  }
}

final currentPhotoProvider = StateNotifierProvider<CurrentPhotoNotifier, Photo?>((ref) {
  return CurrentPhotoNotifier(ref);
});

class CurrentPhotoNotifier extends StateNotifier<Photo?> {
  final Ref ref;
  bool _disposed = false;

  CurrentPhotoNotifier(this.ref) : super(null) {
    _load();
  }

  @override
  void dispose() {
    _disposed = true;
    super.dispose();
  }

  Future<void> _load() async {
    final repo = ref.read(photoRepoProvider);
    final photo = await repo.getPendingFirst();
    if (_disposed) return;
    state = photo;
  }

  Future<void> refresh() async {
    await _load();
  }

  Future<void> moveToTarget(TargetFolder target) async {
    if (state == null) return;

    final photoRepo = ref.read(photoRepoProvider);
    final fileService = ref.read(fileServiceProvider);
    final rootDirs = ref.read(targetRootDirsProvider);
    final targetFoldersNotifier = ref.read(targetFoldersProvider.notifier);

    final rootDir = rootDirs.firstWhere((d) => d.id == target.rootDirId);

    try {
      final newUri = await fileService.moveToSubdirectory(
        state!.path,
        rootDir.path,
        target.name,
      );

      final updated = state!.copyWith(
        status: PhotoStatus.done,
        destination: newUri,
        processedAt: DateTime.now().millisecondsSinceEpoch,
      );
      await photoRepo.update(updated);

      await targetFoldersNotifier.updateLastUsed(target.id!);

      final repo = ref.read(photoRepoProvider);
      state = await repo.getPendingFirst();
    } catch (e) {
      rethrow;
    }
  }

  Future<void> skip() async {
    if (state == null) return;

    final photoRepo = ref.read(photoRepoProvider);
    final updated = state!.copyWith(
      status: PhotoStatus.skipped,
      processedAt: DateTime.now().millisecondsSinceEpoch,
    );
    await photoRepo.update(updated);

    final repo = ref.read(photoRepoProvider);
    state = await repo.getPendingFirst();
  }

  Future<void> trash() async {
    if (state == null) return;

    final photoRepo = ref.read(photoRepoProvider);
    final fileService = ref.read(fileServiceProvider);
    final rootDirs = ref.read(targetRootDirsProvider);

    final defaultRootDir = rootDirs.firstWhere((d) => d.isDefault, orElse: () => rootDirs.first);
    final timestamp = DateTime.now().millisecondsSinceEpoch;

    final newUri = await fileService.moveToTrash(state!.path, defaultRootDir.path);

    final updated = state!.copyWith(
      status: PhotoStatus.trashed,
      originalPath: state!.path,
      destination: newUri,
      trashedAt: timestamp,
      processedAt: timestamp,
    );
    await photoRepo.update(updated);

    state = await photoRepo.getPendingFirst();
  }
}

final photoStatsProvider = FutureProvider<PhotoStats>((ref) async {
  final repo = ref.read(photoRepoProvider);
  final pendingCount = await repo.getPendingCount();
  final totalCount = await repo.getTotalCount();
  return PhotoStats(pending: pendingCount, total: totalCount);
});

class PhotoStats {
  final int pending;
  final int total;

  PhotoStats({required this.pending, required this.total});
}

final multiRootModeProvider = StateProvider<bool>((ref) => false);

final showSkippedProvider = StateProvider<bool>((ref) => false);

final downscaleHighResProvider = StateProvider<bool>((ref) => true);

final lastActionProvider = StateProvider<LastAction?>((ref) => null);

class LastAction {
  final Photo photo;
  final ActionType type;

  LastAction({required this.photo, required this.type});
}

enum ActionType { move, skip, trash }
