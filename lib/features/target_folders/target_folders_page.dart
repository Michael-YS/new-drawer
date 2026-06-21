import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/providers/providers.dart';
import '../../core/models/target_folder.dart';
import '../../l10n/app_localizations.dart';

class TargetFoldersPage extends ConsumerWidget {
  const TargetFoldersPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final folders = ref.watch(targetFoldersProvider);
    final rootDirs = ref.watch(targetRootDirsProvider);

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.targetAppBarTitle),
        actions: [
          IconButton(
            icon: const Icon(Icons.folder_open_outlined),
            tooltip: l10n.targetTooltipManageRoots,
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
                  Text(l10n.targetTextNoFoldersYet),
                  const SizedBox(height: 8),
                  FilledButton.icon(
                    onPressed: () => _addFolder(context, ref),
                    icon: const Icon(Icons.add),
                    label: Text(l10n.targetButtonAddFolder),
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
                          tooltip: l10n.commonRename,
                          onPressed: () => _renameFolder(context, ref, folder),
                        ),
                        IconButton(
                          icon: const Icon(Icons.delete_outline),
                          tooltip: l10n.commonRemove,
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
    final l10n = AppLocalizations.of(context);
    final controller = TextEditingController();
    final rootDirs = ref.read(targetRootDirsProvider);
    final multiMode = ref.read(multiRootModeProvider);

    if (rootDirs.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(l10n.targetErrorNoRootFirst)),
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
          title: Text(l10n.targetDialogCreateTitle),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: controller,
                autofocus: true,
                decoration: InputDecoration(
                  labelText: l10n.targetLabelFolderName,
                  hintText: l10n.targetHintFolderName,
                ),
              ),
              if (canChooseRoot) ...[
                const SizedBox(height: 16),
                DropdownButtonFormField<int>(
                  decoration: InputDecoration(labelText: l10n.targetLabelRootDirectory),
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
              child: Text(l10n.commonCancel),
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
                        SnackBar(content: Text(l10n.targetErrorCreateFolder(e.toString()))),
                      );
                    }
                  }
                }
              },
              child: Text(l10n.commonCreate),
            ),
          ],
        ),
      ),
    );
  }

  void _renameFolder(BuildContext context, WidgetRef ref, TargetFolder folder) {
    final l10n = AppLocalizations.of(context);
    final controller = TextEditingController(text: folder.displayName);
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(l10n.targetDialogRenameTitle),
        content: TextField(
          controller: controller,
          autofocus: true,
          decoration: InputDecoration(labelText: l10n.targetLabelDisplayName),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: Text(l10n.commonCancel),
          ),
          FilledButton(
            onPressed: () {
              if (controller.text.trim().isNotEmpty) {
                Navigator.pop(context);
                ref.read(targetFoldersProvider.notifier).rename(folder.id!, controller.text.trim());
              }
            },
            child: Text(l10n.commonRename),
          ),
        ],
      ),
    );
  }

  void _confirmRemove(BuildContext context, WidgetRef ref, int id) {
    final l10n = AppLocalizations.of(context);
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(l10n.targetDialogRemoveTitle),
        content: Text(l10n.targetDialogRemoveMessage),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: Text(l10n.commonCancel),
          ),
          FilledButton(
            onPressed: () {
              Navigator.pop(context);
              ref.read(targetFoldersProvider.notifier).remove(id);
            },
            child: Text(l10n.commonRemove),
          ),
        ],
      ),
    );
  }

  void _showRootDirsDialog(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final rootDirs = ref.read(targetRootDirsProvider);

    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(l10n.targetDialogRootsTitle),
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
                title: Text(l10n.targetLabelMultiRoot),
                subtitle: Text(l10n.targetSubtitleMultiRoot),
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
            child: Text(l10n.targetTextClose),
          ),
        ],
      ),
    );
  }
}