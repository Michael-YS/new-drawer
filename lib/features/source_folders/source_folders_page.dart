import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:file_picker/file_picker.dart';
import '../../core/providers/providers.dart';

class SourceFoldersPage extends ConsumerWidget {
  const SourceFoldersPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final folders = ref.watch(sourceFoldersProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Source Folders'),
      ),
      body: folders.isEmpty
          ? Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Icon(Icons.folder_off_outlined, size: 64, color: Colors.grey),
                  const SizedBox(height: 16),
                  const Text('No source folders added'),
                  const SizedBox(height: 8),
                  FilledButton.icon(
                    onPressed: () => _addFolder(context, ref),
                    icon: const Icon(Icons.add),
                    label: const Text('Add Source Folder'),
                  ),
                ],
              ),
            )
          : ReorderableListView.builder(
              padding: const EdgeInsets.all(16),
              itemCount: folders.length,
              onReorder: (oldIndex, newIndex) {
                // Reorder logic if needed
              },
              itemBuilder: (context, index) {
                final folder = folders[index];
                return Card(
                  key: ValueKey(folder.id),
                  margin: const EdgeInsets.only(bottom: 8),
                  child: ListTile(
                    leading: Icon(
                      folder.enabled ? Icons.folder : Icons.folder_off,
                      color: folder.enabled ? Colors.blue : Colors.grey,
                    ),
                    title: Text(folder.displayName),
                    subtitle: Text(
                      folder.path,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    trailing: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        IconButton(
                          icon: Icon(folder.enabled ? Icons.pause : Icons.play_arrow),
                          tooltip: folder.enabled ? 'Disable' : 'Enable',
                          onPressed: () {
                            ref.read(sourceFoldersProvider.notifier).toggleEnabled(folder.id!);
                          },
                        ),
                        IconButton(
                          icon: const Icon(Icons.delete_outline),
                          tooltip: 'Remove',
                          onPressed: () => _confirmRemove(context, ref, folder.id!),
                        ),
                      ],
                    ),
                  ),
                );
              },
            ),
      floatingActionButton: FloatingActionButton(
        onPressed: () => _addFolder(context, ref),
        child: const Icon(Icons.add),
      ),
    );
  }

  Future<void> _addFolder(BuildContext context, WidgetRef ref) async {
    final result = await FilePicker.platform.getDirectoryPath();
    if (result != null) {
      final name = result.split('/').last;
      await ref.read(sourceFoldersProvider.notifier).add(result, name);
      ref.read(photoScannerProvider.notifier).scanAll();
    }
  }

  void _confirmRemove(BuildContext context, WidgetRef ref, int id) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Remove Source Folder?'),
        content: const Text('Pending photos from this folder will be removed. Done photos will be kept.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () {
              Navigator.pop(context);
              ref.read(sourceFoldersProvider.notifier).remove(id);
            },
            child: const Text('Remove'),
          ),
        ],
      ),
    );
  }
}