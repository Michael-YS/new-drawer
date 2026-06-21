import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/database/database.dart';
import '../../core/models/photo.dart';
import '../../core/providers/providers.dart';

class SettingsPage extends ConsumerWidget {
  const SettingsPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final showSkipped = ref.watch(showSkippedProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Settings'),
      ),
      body: ListView(
        children: [
          const _SectionHeader(title: 'Source Folders'),
          ListTile(
            leading: const Icon(Icons.folder_outlined),
            title: const Text('Manage Source Folders'),
            subtitle: const Text('Add, remove, or disable source folders'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => Navigator.pushNamed(context, '/source-folders'),
          ),
          const Divider(),
          const _SectionHeader(title: 'Target Folders'),
          ListTile(
            leading: const Icon(Icons.folder_copy_outlined),
            title: const Text('Manage Target Folders'),
            subtitle: const Text('Create and organize category folders'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => Navigator.pushNamed(context, '/target-folders'),
          ),
          const Divider(),
          const _SectionHeader(title: 'Display'),
          SwitchListTile(
            secondary: const Icon(Icons.visibility_outlined),
            title: const Text('Show skipped photos'),
            subtitle: const Text('Include skipped photos in the queue'),
            value: showSkipped,
            onChanged: (value) {
              ref.read(showSkippedProvider.notifier).state = value;
            },
          ),
          Consumer(
            builder: (context, ref, _) {
              final downscale = ref.watch(downscaleHighResProvider);
              return SwitchListTile(
                secondary: const Icon(Icons.high_quality_outlined),
                title: const Text('Downscale high-res photos'),
                subtitle: const Text(
                  'Decode images at up to 2048px instead of full resolution. '
                  'Recommended — fixes mosaic/pixelation on 50MP+ photos and '
                  'reduces memory and load time. Disable only if you need to '
                  'zoom in past 2K.',
                ),
                value: downscale,
                onChanged: (v) =>
                    ref.read(downscaleHighResProvider.notifier).state = v,
              );
            },
          ),
          const Divider(),
          const _SectionHeader(title: 'Data'),
          ListTile(
            leading: const Icon(Icons.refresh),
            title: const Text('Reset all processing status'),
            subtitle: const Text('Mark all photos as pending again'),
            onTap: () => _confirmReset(context, ref),
          ),
          ListTile(
            leading: const Icon(Icons.delete_sweep_outlined),
            title: const Text('Clear trash'),
            subtitle: const Text('Permanently delete all trashed photos'),
            onTap: () => _confirmClearTrash(context, ref),
          ),
          ListTile(
            leading: const Icon(Icons.delete_forever_outlined, color: Colors.red),
            title: const Text('Clear database', style: TextStyle(color: Colors.red)),
            subtitle: const Text('Wipe all data: source folders, target folders, photos'),
            onTap: () => _confirmClearDatabase(context, ref),
          ),
          const Divider(),
          const _SectionHeader(title: 'About'),
          const ListTile(
            leading: Icon(Icons.info_outline),
            title: Text('Photo Organizer'),
            subtitle: Text('Version 1.0.0'),
          ),
        ],
      ),
    );
  }

  void _confirmReset(BuildContext context, WidgetRef ref) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Reset All Status?'),
        content: const Text('This will mark all processed photos as pending again. Files will not be moved.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () async {
              Navigator.pop(context);
              final repo = ref.read(photoRepoProvider);
              await repo.resetAllStatus();
              await ref.read(currentPhotoProvider.notifier).refresh();
              ref.invalidate(photoStatsProvider);
              if (context.mounted) {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('All statuses reset')),
                );
              }
            },
            child: const Text('Reset'),
          ),
        ],
      ),
    );
  }

  void _confirmClearTrash(BuildContext context, WidgetRef ref) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Clear Trash?'),
        content: const Text('This will permanently delete all trashed photos. This action cannot be undone.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancel'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: Colors.red),
            onPressed: () async {
              Navigator.pop(context);
              try {
                final photoRepo = ref.read(photoRepoProvider);
                final fileService = ref.read(fileServiceProvider);
                final trashedPhotos = await photoRepo.getByStatus(PhotoStatus.trashed);

                for (final photo in trashedPhotos) {
                  if (photo.destination != null) {
                    await fileService.deleteFile(photo.destination!);
                  }
                  if (photo.id != null) {
                    await photoRepo.delete(photo.id!);
                  }
                }

                await ref.read(currentPhotoProvider.notifier).refresh();
                ref.invalidate(photoStatsProvider);
                if (context.mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('Trash cleared')),
                  );
                }
              } catch (e) {
                if (context.mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(content: Text('Failed to clear trash: $e')),
                  );
                }
              }
            },
            child: const Text('Clear Trash'),
          ),
        ],
      ),
    );
  }

  void _confirmClearDatabase(BuildContext context, WidgetRef ref) {
    showDialog(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Clear database?'),
        content: const Text(
          'This will permanently delete ALL data: source folders, target root directories, target folders, and photo records.\n\nFiles on disk are NOT touched. The app will return to the initial setup screen.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext),
            child: const Text('Cancel'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: Colors.red),
            onPressed: () async {
              Navigator.pop(dialogContext);
              try {
                await AppDatabase.clearAll();
                await ref.read(sourceFoldersProvider.notifier).refresh();
                await ref.read(targetRootDirsProvider.notifier).refresh();
                await ref.read(targetFoldersProvider.notifier).refresh();
                ref.read(lastActionProvider.notifier).state = null;
                ref.invalidate(photoStatsProvider);
                await ref.read(currentPhotoProvider.notifier).refresh();
                if (context.mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('Database cleared')),
                  );
                }
              } catch (e) {
                if (context.mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(content: Text('Failed to clear database: $e')),
                  );
                }
              }
            },
            child: const Text('Clear Everything'),
          ),
        ],
      ),
    );
  }
}

class _SectionHeader extends StatelessWidget {
  final String title;

  const _SectionHeader({required this.title});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
      child: Text(
        title,
        style: TextStyle(
          fontSize: 14,
          fontWeight: FontWeight.bold,
          color: Theme.of(context).colorScheme.primary,
        ),
      ),
    );
  }
}
