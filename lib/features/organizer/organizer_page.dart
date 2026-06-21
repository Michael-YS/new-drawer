import 'dart:io';
import 'package:collection/collection.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image/image.dart' as img;
import 'package:photo_view/photo_view.dart';
import '../../core/providers/providers.dart';
import '../../core/models/photo.dart';

final _photoBytesProvider =
    FutureProvider.family<Uint8List?, String>((ref, uri) async {
  if (!uri.startsWith('content://')) return null;
  final channel = const MethodChannel('com.example.new_drawer/saf');
  return await channel.invokeMethod<Uint8List>('readFile', {'uri': uri});
});

class OrganizerPage extends ConsumerStatefulWidget {
  const OrganizerPage({super.key});

  @override
  ConsumerState<OrganizerPage> createState() => _OrganizerPageState();
}

class _OrganizerPageState extends ConsumerState<OrganizerPage> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      ref.read(photoScannerProvider.notifier).scanAll();
    });
  }

  Future<void> _undo() async {
    final lastAction = ref.read(lastActionProvider);
    if (lastAction == null) return;

    final photoRepo = ref.read(photoRepoProvider);
    final fileService = ref.read(fileServiceProvider);

    if (lastAction.type == ActionType.move) {
      final photoId = lastAction.photo.id;
      final current = await photoRepo.getById(photoId!);
      if (current?.destination != null) {
        await fileService.moveToOriginal(current!.destination!, current.path);
      }
      final restored = (current ?? lastAction.photo).copyWith(
        status: PhotoStatus.pending,
        destination: null,
        processedAt: null,
      );
      await photoRepo.update(restored);
    } else if (lastAction.type == ActionType.trash) {
      final photoId = lastAction.photo.id;
      final current = await photoRepo.getById(photoId!);
      if (current?.originalPath != null && current?.destination != null) {
        await fileService.moveToOriginal(current!.destination!, current.originalPath!);
      }
      final restored = (current ?? lastAction.photo).copyWith(
        status: PhotoStatus.pending,
        originalPath: null,
        destination: null,
        trashedAt: null,
        processedAt: null,
      );
      await photoRepo.update(restored);
    } else if (lastAction.type == ActionType.skip) {
      final photo = lastAction.photo;
      final restored = photo.copyWith(
        status: PhotoStatus.pending,
        processedAt: null,
      );
      await photoRepo.update(restored);
    }

    ref.read(lastActionProvider.notifier).state = null;
    ref.invalidate(photoStatsProvider);
    ref.invalidate(currentPhotoProvider);
  }

  Future<void> _createQuickFolder(String name) async {
    final rootDirs = ref.read(targetRootDirsProvider);
    if (rootDirs.isEmpty) return;
    final defaultRootDir = rootDirs.where((d) => d.isDefault).firstOrNull ?? rootDirs.first;
    final folder = await ref.read(targetFoldersProvider.notifier).add(name, defaultRootDir.id!);

    final currentPhoto = ref.read(currentPhotoProvider);
    if (currentPhoto != null) {
      ref.read(lastActionProvider.notifier).state = LastAction(photo: currentPhoto, type: ActionType.move);
    }
    await ref.read(currentPhotoProvider.notifier).moveToTarget(folder);
  }

  @override
  Widget build(BuildContext context) {
    final currentPhoto = ref.watch(currentPhotoProvider);
    final targetFolders = ref.watch(targetFoldersProvider);
    final stats = ref.watch(photoStatsProvider);
    final lastAction = ref.watch(lastActionProvider);

    final sortedFolders = [...targetFolders]
      ..sort((a, b) => (b.id ?? 0).compareTo(a.id ?? 0));

    return Scaffold(
      appBar: AppBar(
        title: const Text('Photo Organizer'),
        centerTitle: true,
        actions: [
          IconButton(
            icon: const Icon(Icons.folder_outlined),
            tooltip: 'Source Folders',
            onPressed: () => Navigator.pushNamed(context, '/source-folders'),
          ),
          IconButton(
            icon: const Icon(Icons.settings_outlined),
            tooltip: 'Settings',
            onPressed: () => Navigator.pushNamed(context, '/settings'),
          ),
        ],
      ),
      body: Column(
        children: [
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            child: stats.when(
              data: (s) => Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Text(
                    'Processed: ${s.total - s.pending} / ${s.total}',
                    style: const TextStyle(fontSize: 14, color: Colors.grey),
                  ),
                  const SizedBox(width: 16),
                  Text(
                    '${s.pending} remaining',
                    style: const TextStyle(fontSize: 14, fontWeight: FontWeight.bold),
                  ),
                ],
              ),
              loading: () => const Text('Loading...'),
              error: (e, st) => const Text('Error'),
            ),
          ),
          Expanded(
            child: currentPhoto == null
                ? Center(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        const Icon(Icons.check_circle_outline, size: 64, color: Colors.green),
                        const SizedBox(height: 16),
                        const Text('All photos processed!', style: TextStyle(fontSize: 18)),
                        const SizedBox(height: 8),
                        TextButton(
                          onPressed: () => ref.read(photoScannerProvider.notifier).scanAll(),
                          child: const Text('Rescan for new photos'),
                        ),
                      ],
                    ),
                  )
                : Column(
                    children: [
                      Expanded(
                        child: _PhotoView(photo: currentPhoto),
                      ),
                      Container(
                        padding: const EdgeInsets.all(8),
                        child: Text(
                          ref.read(fileServiceProvider).basenameOf(currentPhoto.path),
                          style: const TextStyle(fontSize: 12, color: Colors.grey),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                    ],
                  ),
          ),
          if (currentPhoto != null) ...[
            const Divider(height: 1),
            Container(
              height: 120,
              padding: const EdgeInsets.symmetric(vertical: 8),
              child: sortedFolders.isEmpty
                  ? Center(
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          const Text('No target folders', style: TextStyle(color: Colors.grey)),
                          const SizedBox(height: 8),
                          TextButton.icon(
                            onPressed: () => Navigator.pushNamed(context, '/target-folders'),
                            icon: const Icon(Icons.add, size: 18),
                            label: const Text('Add Folder'),
                          ),
                        ],
                      ),
                    )
                  : ListView.builder(
                      scrollDirection: Axis.horizontal,
                      padding: const EdgeInsets.symmetric(horizontal: 8),
                      itemCount: sortedFolders.length + 1,
                      itemBuilder: (context, index) {
                        if (index == sortedFolders.length) {
                          return Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 4),
                            child: ActionChip(
                              avatar: const Icon(Icons.add, size: 18),
                              label: const Text('New'),
                              onPressed: () => _showQuickAddDialog(),
                            ),
                          );
                        }
                        final folder = sortedFolders[index];
                        return Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 4),
                          child: ActionChip(
                            avatar: const Icon(Icons.folder, size: 18),
                            label: Text(folder.displayName),
                            onPressed: () async {
                              ref.read(lastActionProvider.notifier).state = LastAction(photo: currentPhoto, type: ActionType.move);
                              await ref.read(currentPhotoProvider.notifier).moveToTarget(folder);
                              ref.invalidate(photoStatsProvider);
                            },
                          ),
                        );
                      },
                    ),
            ),
            Container(
              padding: const EdgeInsets.all(8),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                children: [
                  if (lastAction != null)
                    Expanded(
                      child: TextButton.icon(
                        onPressed: _undo,
                        icon: const Icon(Icons.undo, size: 18),
                        label: const Text('Undo', style: TextStyle(fontSize: 12)),
                      ),
                    )
                  else
                    const SizedBox(width: 80),
                  Expanded(
                    child: FilledButton.icon(
                      onPressed: () async {
                        ref.read(lastActionProvider.notifier).state = LastAction(photo: currentPhoto, type: ActionType.skip);
                        await ref.read(currentPhotoProvider.notifier).skip();
                        ref.invalidate(photoStatsProvider);
                      },
                      icon: const Icon(Icons.skip_next, size: 18),
                      label: const Text('Skip', style: TextStyle(fontSize: 12)),
                    ),
                  ),
                  Expanded(
                    child: FilledButton.icon(
                      onPressed: () => _showMoveDialog(),
                      icon: const Icon(Icons.folder_open, size: 18),
                      label: const Text('Move', style: TextStyle(fontSize: 12)),
                    ),
                  ),
                  Expanded(
                    child: FilledButton.icon(
                      onPressed: () async {
                        ref.read(lastActionProvider.notifier).state = LastAction(photo: currentPhoto, type: ActionType.trash);
                        await ref.read(currentPhotoProvider.notifier).trash();
                        ref.invalidate(photoStatsProvider);
                      },
                      icon: const Icon(Icons.delete_outline, size: 18),
                      label: const Text('Del', style: TextStyle(fontSize: 12)),
                      style: FilledButton.styleFrom(
                        backgroundColor: Colors.red.shade400,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }

  void _showQuickAddDialog() {
    final controller = TextEditingController();
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Create New Folder'),
        content: TextField(
          controller: controller,
          autofocus: true,
          decoration: const InputDecoration(
            labelText: 'Folder name',
            hintText: 'e.g., Vacation',
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () {
              if (controller.text.trim().isNotEmpty) {
                Navigator.pop(context);
                _createQuickFolder(controller.text.trim());
              }
            },
            child: const Text('Create'),
          ),
        ],
      ),
    );
  }

  Future<void> _showMoveDialog() async {
    final currentPhoto = ref.read(currentPhotoProvider);
    if (currentPhoto == null) return;
    final fileService = ref.read(fileServiceProvider);
    final pickedDir = await fileService.pickDirectory();
    if (pickedDir == null || !mounted) return;
    ref.read(lastActionProvider.notifier).state = LastAction(photo: currentPhoto, type: ActionType.move);
    try {
      final photoRepo = ref.read(photoRepoProvider);
      final basename = fileService.basenameOf(currentPhoto.path);
      final destPath = fileService.pathForFile(pickedDir, '', basename);
      final newUri = await fileService.moveFile(currentPhoto.path, destPath);
      final updated = currentPhoto.copyWith(
        status: PhotoStatus.done,
        destination: newUri,
        processedAt: DateTime.now().millisecondsSinceEpoch,
      );
      await photoRepo.update(updated);
      if (mounted) setState(() {});
      ref.read(currentPhotoProvider.notifier).refresh();
      ref.invalidate(photoStatsProvider);
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to move: $e')),
        );
      }
    }
  }
}

class _PhotoView extends ConsumerWidget {
  final Photo photo;
  const _PhotoView({required this.photo});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final downscale = ref.watch(downscaleHighResProvider);
    if (photo.path.startsWith('content://')) {
      final bytesAsync = ref.watch(_photoBytesProvider(photo.path));
      return bytesAsync.when(
        data: (bytes) {
          if (bytes == null) {
            return const Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(Icons.broken_image_outlined, size: 64, color: Colors.grey),
                  SizedBox(height: 8),
                  Text('Cannot load image', style: TextStyle(color: Colors.grey)),
                ],
              ),
            );
          }
          final displayBytes = downscale ? _downscale(bytes) : bytes;
          return PhotoView(
            imageProvider: MemoryImage(displayBytes),
            minScale: PhotoViewComputedScale.contained,
            maxScale: PhotoViewComputedScale.covered * 3,
            backgroundDecoration: const BoxDecoration(color: Colors.black),
          );
        },
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, st) => Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(Icons.error_outline, size: 64, color: Colors.red),
              SizedBox(height: 8),
              Text('Load error: $e', style: const TextStyle(color: Colors.grey)),
            ],
          ),
        ),
      );
    }
    return PhotoView(
      imageProvider: FileImage(File(photo.path)),
      minScale: PhotoViewComputedScale.contained,
      maxScale: PhotoViewComputedScale.covered * 3,
    );
  }
}

Uint8List _downscale(Uint8List bytes) {
  const maxDim = _kMaxPhotoDimension;
  final decoded = img.decodeImage(bytes);
  if (decoded == null) return bytes;
  if (decoded.width <= maxDim && decoded.height <= maxDim) {
    return bytes;
  }
  final resized = (decoded.width >= decoded.height)
      ? img.copyResize(decoded, width: maxDim)
      : img.copyResize(decoded, height: maxDim);
  return Uint8List.fromList(img.encodeJpg(resized, quality: 88));
}

const _kMaxPhotoDimension = 2048;
