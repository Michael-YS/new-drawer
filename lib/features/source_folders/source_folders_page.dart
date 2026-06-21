import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/providers/providers.dart';
import '../../l10n/app_localizations.dart';

class SourceFoldersPage extends ConsumerWidget {
  const SourceFoldersPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final folders = ref.watch(sourceFoldersProvider);

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.sourceAppBarTitle),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            tooltip: l10n.sourceTooltipRescanAll,
            onPressed: () => ref.read(photoScannerProvider.notifier).scanAll(),
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
                  Text(l10n.sourceTextNoFolders),
                  const SizedBox(height: 8),
                  FilledButton.icon(
                    onPressed: () => _addFolder(context, ref),
                    icon: const Icon(Icons.add),
                    label: Text(l10n.sourceButtonAddFolder),
                  ),
                ],
              ),
            )
          : ReorderableListView.builder(
              padding: const EdgeInsets.all(16),
              itemCount: folders.length,
              // ignore: deprecated_member_use
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
                          tooltip: folder.enabled
                              ? l10n.sourceTooltipDisable
                              : l10n.sourceTooltipEnable,
                          onPressed: () {
                            ref.read(sourceFoldersProvider.notifier).toggleEnabled(folder.id!);
                          },
                        ),
                        IconButton(
                          icon: const Icon(Icons.delete_outline),
                          tooltip: l10n.sourceTooltipRemove,
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
    final fileService = ref.read(fileServiceProvider);
    final result = await fileService.pickDirectory();
    if (result != null) {
      final l10n = AppLocalizations.of(context);
      final name = fileService.basenameOf(result);
      try {
        await ref.read(sourceFoldersProvider.notifier).add(result, name);
      } catch (e) {
        if (context.mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text(l10n.sourceErrorAdd(e.toString()))),
          );
        }
      }
    }
  }

  void _confirmRemove(BuildContext context, WidgetRef ref, int id) {
    final l10n = AppLocalizations.of(context);
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(l10n.sourceConfirmRemoveTitle),
        content: Text(l10n.sourceConfirmRemoveMessage),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: Text(l10n.commonCancel),
          ),
          FilledButton(
            onPressed: () {
              Navigator.pop(context);
              ref.read(sourceFoldersProvider.notifier).remove(id);
            },
            child: Text(l10n.commonRemove),
          ),
        ],
      ),
    );
  }
}
