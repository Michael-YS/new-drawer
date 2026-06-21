import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/database/database.dart';
import '../../core/models/photo.dart';
import '../../core/providers/providers.dart';
import '../../l10n/app_localizations.dart';

class SettingsPage extends ConsumerWidget {
  const SettingsPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final showSkipped = ref.watch(showSkippedProvider);

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.settingsAppBarTitle),
      ),
      body: ListView(
        children: [
          _SectionHeader(title: l10n.settingsSectionSource),
          ListTile(
            leading: const Icon(Icons.folder_outlined),
            title: Text(l10n.settingsManageSourceTitle),
            subtitle: Text(l10n.settingsManageSourceSubtitle),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => Navigator.pushNamed(context, '/source-folders'),
          ),
          const Divider(),
          _SectionHeader(title: l10n.settingsSectionTarget),
          ListTile(
            leading: const Icon(Icons.folder_copy_outlined),
            title: Text(l10n.settingsManageTargetTitle),
            subtitle: Text(l10n.settingsManageTargetSubtitle),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => Navigator.pushNamed(context, '/target-folders'),
          ),
          const Divider(),
          _SectionHeader(title: l10n.settingsSectionDisplay),
          SwitchListTile(
            secondary: const Icon(Icons.visibility_outlined),
            title: Text(l10n.settingsShowSkippedTitle),
            subtitle: Text(l10n.settingsShowSkippedSubtitle),
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
                title: Text(l10n.settingsDownscaleTitle),
                subtitle: Text(l10n.settingsDownscaleSubtitle),
                value: downscale,
                onChanged: (v) =>
                    ref.read(downscaleHighResProvider.notifier).state = v,
              );
            },
          ),
          const Divider(),
          _SectionHeader(title: l10n.settingsSectionData),
          ListTile(
            leading: const Icon(Icons.refresh),
            title: Text(l10n.settingsResetStatusTitle),
            subtitle: Text(l10n.settingsResetStatusSubtitle),
            onTap: () => _confirmReset(context, ref),
          ),
          ListTile(
            leading: const Icon(Icons.delete_sweep_outlined),
            title: Text(l10n.settingsClearTrashTitle),
            subtitle: Text(l10n.settingsClearTrashSubtitle),
            onTap: () => _confirmClearTrash(context, ref),
          ),
          ListTile(
            leading: const Icon(Icons.delete_forever_outlined, color: Colors.red),
            title: Text(l10n.settingsClearDbTitle, style: const TextStyle(color: Colors.red)),
            subtitle: Text(l10n.settingsClearDbSubtitle),
            onTap: () => _confirmClearDatabase(context, ref),
          ),
          const Divider(),
          _SectionHeader(title: l10n.settingsSectionAbout),
          ListTile(
            leading: const Icon(Icons.info_outline),
            title: Text(l10n.settingsAboutAppTitle),
            subtitle: Text(l10n.settingsAboutAppSubtitle('1.1.0')),
          ),
        ],
      ),
    );
  }

  void _confirmReset(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(l10n.settingsConfirmResetTitle),
        content: Text(l10n.settingsConfirmResetMessage),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: Text(l10n.commonCancel),
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
                  SnackBar(content: Text(l10n.settingsSnackReset)),
                );
              }
            },
            child: Text(l10n.settingsButtonReset),
          ),
        ],
      ),
    );
  }

  void _confirmClearTrash(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(l10n.settingsConfirmClearTrashTitle),
        content: Text(l10n.settingsConfirmClearTrashMessage),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: Text(l10n.commonCancel),
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
                    SnackBar(content: Text(l10n.settingsSnackClearTrash)),
                  );
                }
              } catch (e) {
                if (context.mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(content: Text(l10n.settingsErrorClearTrash(e.toString()))),
                  );
                }
              }
            },
            child: Text(l10n.settingsButtonClearTrash),
          ),
        ],
      ),
    );
  }

  void _confirmClearDatabase(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    showDialog(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(l10n.settingsConfirmClearDbTitle),
        content: Text(l10n.settingsConfirmClearDbMessage),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext),
            child: Text(l10n.commonCancel),
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
                    SnackBar(content: Text(l10n.settingsSnackClearDb)),
                  );
                }
              } catch (e) {
                if (context.mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(content: Text(l10n.settingsErrorClearDb(e.toString()))),
                  );
                }
              }
            },
            child: Text(l10n.settingsButtonClearDb),
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