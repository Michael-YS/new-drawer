import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/providers/providers.dart';
import '../../core/models/target_folder.dart';

class TargetFoldersPage extends ConsumerWidget {
  const TargetFoldersPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final folders = ref.watch(targetFoldersProvider);
    final rootDirs = ref.watch(targetRootDirsProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Target Folders'),
        actions: [
          IconButton(
            icon: const Icon(Icons.folder_open_outlined),
            tooltip: 'Manage Root Directories',
            onPressed: () => _showRootDirsDialog(context, ref),
          ),
        ],
      ),
      body: folders.isEmpty
          ? Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Icon(Icons.folder_off_outlined, size: 64, color: Colors.grey),
                  const SizedBox(height: 16),
                  const Text('No target folders yet'),
                  const SizedBox(height: 8),
                  FilledButton.icon(
                    onPressed: () => _addFolder(context, ref),
                    icon: const Icon(Icons.add),
                    label: const Text('Add Target Folder'),
                  ),
                ],
              ),
            )
          : ReorderableListView.builder(
              padding: const EdgeInsets.all(16),
              itemCount: folders.length,
              // ignore: deprecated_member_use
              onReorder: (oldIndex, newIndex) {
                if (newIndex > oldIndex) newIndex--;
                ref.read(targetFoldersProvider.notifier).reorder(oldIndex, newIndex);
              },
              itemBuilder: (context, index) {
                final folder = folders[index];
                final rootDir = rootDirs.firstWhere(
                  (d) => d.id == folder.rootDirId,
                  orElse: () => rootDirs.first,
                );
                return Card(
                  key: ValueKey(folder.id),
                  margin: const EdgeInsets.only(bottom: 8),
                  child: ListTile(
                    leading: const Icon(Icons.folder),
                    title: Text(folder.displayName),
                    subtitle: Text(
                      '${rootDir.displayName} / ${folder.name}',
                      style: const TextStyle(fontSize: 12, color: Colors.grey),
                    ),
                    trailing: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        IconButton(
                          icon: const Icon(Icons.edit_outlined),
                          tooltip: 'Rename',
                          onPressed: () => _renameFolder(context, ref, folder),
                        ),
                        IconButton(
                          icon: const Icon(Icons.delete_outline),
                          tooltip: 'Remove',
                          onPressed: () => _confirmRemove(context, ref, folder.id!),
                        ),
                        const Icon(Icons.drag_handle),
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
    final controller = TextEditingController();
    final rootDirs = ref.read(targetRootDirsProvider);
    final multiMode = ref.read(multiRootModeProvider);

    if (rootDirs.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Please set up a target root directory first')),
      );
      return;
    }

    final defaultRootDir = rootDirs.firstWhere((d) => d.isDefault, orElse: () => rootDirs.first);
    final canChooseRoot = multiMode && rootDirs.length > 1;
    int selectedRootId = defaultRootDir.id!;

    showDialog(
      context: context,
      builder: (dialogContext) => StatefulBuilder(
        builder: (context, setState) => AlertDialog(
          title: const Text('Create Target Folder'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: controller,
                autofocus: true,
                decoration: const InputDecoration(
                  labelText: 'Folder name',
                  hintText: 'e.g., Vacation, Anime, Screenshots',
                ),
              ),
              if (canChooseRoot) ...[
                const SizedBox(height: 16),
                DropdownButtonFormField<int>(
                  decoration: const InputDecoration(labelText: 'Root Directory'),
                  initialValue: selectedRootId,
                  items: rootDirs.map((d) => DropdownMenuItem(
                    value: d.id,
                    child: Text(d.displayName),
                  )).toList(),
                  onChanged: (value) {
                    if (value != null) setState(() => selectedRootId = value);
                  },
                ),
              ],
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(dialogContext),
              child: const Text('Cancel'),
            ),
            FilledButton(
              onPressed: () async {
                if (controller.text.trim().isNotEmpty) {
                  Navigator.pop(dialogContext);
                  try {
                    await ref.read(targetFoldersProvider.notifier).add(
                      controller.text.trim(),
                      selectedRootId,
                    );
                  } catch (e) {
                    if (context.mounted) {
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(content: Text('Failed to create folder: $e')),
                      );
                    }
                  }
                }
              },
              child: const Text('Create'),
            ),
          ],
        ),
      ),
    );
  }

  void _renameFolder(BuildContext context, WidgetRef ref, TargetFolder folder) {
    final controller = TextEditingController(text: folder.displayName);
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Rename Folder'),
        content: TextField(
          controller: controller,
          autofocus: true,
          decoration: const InputDecoration(labelText: 'Display name'),
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
                ref.read(targetFoldersProvider.notifier).rename(folder.id!, controller.text.trim());
              }
            },
            child: const Text('Rename'),
          ),
        ],
      ),
    );
  }

  void _confirmRemove(BuildContext context, WidgetRef ref, int id) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Remove Target Folder?'),
        content: const Text('The folder will be removed from the list. The actual folder on disk will not be deleted.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () {
              Navigator.pop(context);
              ref.read(targetFoldersProvider.notifier).remove(id);
            },
            child: const Text('Remove'),
          ),
        ],
      ),
    );
  }

  void _showRootDirsDialog(BuildContext context, WidgetRef ref) {
    final rootDirs = ref.read(targetRootDirsProvider);

    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Root Directories'),
        content: SizedBox(
          width: 300,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              ...rootDirs.map((dir) => ListTile(
                leading: Icon(dir.isDefault ? Icons.star : Icons.folder),
                title: Text(dir.displayName),
                subtitle: Text(dir.path, maxLines: 1, overflow: TextOverflow.ellipsis),
                trailing: dir.isDefault ? const Icon(Icons.check, color: Colors.green) : null,
              )),
              const Divider(),
              SwitchListTile(
                title: const Text('Multi-root mode'),
                subtitle: const Text('Allow multiple root directories'),
                value: ref.read(multiRootModeProvider),
                onChanged: (value) {
                  ref.read(multiRootModeProvider.notifier).state = value;
                },
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Close'),
          ),
        ],
      ),
    );
  }
}
