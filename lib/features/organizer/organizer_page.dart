import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:photo_view/photo_view.dart';
import 'package:file_picker/file_picker.dart';
import '../../core/providers/providers.dart';
import '../../core/models/photo.dart';

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
      final photo = lastAction.photo;
      if (photo.destination != null) {
        await fileService.moveFile(photo.destination!, photo.path);
      }
      final restored = photo.copyWith(
        status: PhotoStatus.pending,
        destination: null,
      );
      await photoRepo.update(restored);
    } else if (lastAction.type == ActionType.trash) {
      final photo = lastAction.photo;
      if (photo.originalPath != null && photo.destination != null) {
        final destFile = File(photo.destination!);
        if (await destFile.exists()) {
          String targetPath = photo.originalPath!;
          final targetFile = File(targetPath);
          if (await targetFile.exists()) {
            targetPath = '${photo.originalPath}_restored';
          }
          await fileService.moveFile(photo.destination!, targetPath);
        }
      }
      final restored = photo.copyWith(
        status: PhotoStatus.pending,
        originalPath: null,
        trashedAt: null,
      );
      await photoRepo.update(restored);
    } else if (lastAction.type == ActionType.skip) {
      final photo = lastAction.photo;
      final restored = photo.copyWith(status: PhotoStatus.pending);
      await photoRepo.update(restored);
    }

    ref.read(lastActionProvider.notifier).state = null;
    ref.read(currentPhotoProvider.notifier).refresh();
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

    final recentFolders = targetFolders.where((f) => f.lastUsedAt != null).toList()
      ..sort((a, b) => (b.lastUsedAt ?? 0).compareTo(a.lastUsedAt ?? 0));
    final otherFolders = targetFolders.where((f) => f.lastUsedAt == null).toList();
    final sortedFolders = [...recentFolders, ...otherFolders];

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
                        child: currentPhoto.path.startsWith('content://')
                            ? Image.file(File(currentPhoto.path), fit: BoxFit.contain)
                            : PhotoView(
                                imageProvider: FileImage(File(currentPhoto.path)),
                                minScale: PhotoViewComputedScale.contained,
                                maxScale: PhotoViewComputedScale.covered * 3,
                              ),
                      ),
                      Container(
                        padding: const EdgeInsets.all(8),
                        child: Text(
                          currentPhoto.path.split('/').last,
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
                    TextButton.icon(
                      onPressed: _undo,
                      icon: const Icon(Icons.undo),
                      label: const Text('Undo'),
                    )
                  else
                    const SizedBox(width: 80),
                  FilledButton.icon(
                    onPressed: () async {
                      ref.read(lastActionProvider.notifier).state = LastAction(photo: currentPhoto, type: ActionType.skip);
                      await ref.read(currentPhotoProvider.notifier).skip();
                      ref.invalidate(photoStatsProvider);
                    },
                    icon: const Icon(Icons.skip_next),
                    label: const Text('Skip'),
                  ),
                  FilledButton.icon(
                    onPressed: () => _showMoveDialog(),
                    icon: const Icon(Icons.folder_open),
                    label: const Text('Move to...'),
                  ),
                  FilledButton.icon(
                    onPressed: () async {
                      ref.read(lastActionProvider.notifier).state = LastAction(photo: currentPhoto, type: ActionType.trash);
                      await ref.read(currentPhotoProvider.notifier).trash();
                      ref.invalidate(photoStatsProvider);
                    },
                    icon: const Icon(Icons.delete_outline),
                    label: const Text('Delete'),
                    style: FilledButton.styleFrom(
                      backgroundColor: Colors.red.shade400,
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

  void _showMoveDialog() async {
    final result = await FilePicker.platform.getDirectoryPath();
    if (result != null && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Please use preset folders for better organization')),
      );
    }
  }
}