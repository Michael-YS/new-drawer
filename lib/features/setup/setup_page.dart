import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/providers/providers.dart';

class SetupPage extends ConsumerStatefulWidget {
  const SetupPage({super.key});

  @override
  ConsumerState<SetupPage> createState() => _SetupPageState();
}

class _SetupPageState extends ConsumerState<SetupPage> {
  bool _targetLoading = false;
  bool _sourceLoading = false;

  Future<void> _selectRootDirectory() async {
    if (_targetLoading) return;
    final fileService = ref.read(fileServiceProvider);
    final result = await fileService.pickDirectory();
    if (result == null) return;
    if (!mounted) return;
    setState(() => _targetLoading = true);
    try {
      await ref.read(targetRootDirsProvider.notifier).add(
        result,
        'Default',
        isDefault: true,
      );
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to set target directory: $e')),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _targetLoading = false);
      }
    }
  }

  Future<void> _selectSourceFolder() async {
    if (_sourceLoading) return;
    final fileService = ref.read(fileServiceProvider);
    final result = await fileService.pickDirectory();
    if (result == null) return;
    if (!mounted) return;
    setState(() => _sourceLoading = true);
    try {
      final name = fileService.basenameOf(result);
      await ref.read(sourceFoldersProvider.notifier).add(result, name);
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to add source folder: $e')),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _sourceLoading = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final targetDirs = ref.watch(targetRootDirsProvider);
    final sourceFolders = ref.watch(sourceFoldersProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Photo Organizer Setup'),
        centerTitle: true,
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(24.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const SizedBox(height: 16),
            const Icon(
              Icons.photo_library_outlined,
              size: 64,
              color: Colors.deepPurple,
            ),
            const SizedBox(height: 16),
            Text(
              'Welcome to Photo Organizer',
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.headlineSmall,
            ),
            const SizedBox(height: 24),
            _SetupSection(
              step: 1,
              title: 'Set target root',
              description:
                  'Where organized photos will be stored. All category folders you create live under this directory.',
              isLoading: _targetLoading,
              isDone: targetDirs.isNotEmpty,
              doneSummary: targetDirs.isNotEmpty
                  ? targetDirs.first.path
                  : null,
              buttonLabel: 'Select Target Root Directory',
              buttonIcon: Icons.folder_open,
              onPressed: _selectRootDirectory,
            ),
            const SizedBox(height: 16),
            _SetupSection(
              step: 2,
              title: 'Add a source folder',
              description:
                  'A folder to scan for photos. You can add more or remove this later from the source folders screen.',
              isLoading: _sourceLoading,
              isDone: sourceFolders.isNotEmpty,
              doneSummary: sourceFolders.isNotEmpty
                  ? sourceFolders.first.path
                  : null,
              buttonLabel: 'Select Source Folder',
              buttonIcon: Icons.source_outlined,
              onPressed: _selectSourceFolder,
            ),
            const SizedBox(height: 24),
            if (targetDirs.isNotEmpty && sourceFolders.isNotEmpty)
              Center(
                child: Text(
                  'All set — proceeding to organizer…',
                  style: TextStyle(color: Colors.grey.shade600),
                ),
              ),
          ],
        ),
      ),
    );
  }
}

class _SetupSection extends StatelessWidget {
  const _SetupSection({
    required this.step,
    required this.title,
    required this.description,
    required this.isLoading,
    required this.isDone,
    required this.doneSummary,
    required this.buttonLabel,
    required this.buttonIcon,
    required this.onPressed,
  });

  final int step;
  final String title;
  final String description;
  final bool isLoading;
  final bool isDone;
  final String? doneSummary;
  final String buttonLabel;
  final IconData buttonIcon;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      elevation: 1,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                CircleAvatar(
                  radius: 14,
                  backgroundColor:
                      isDone ? Colors.green : theme.colorScheme.primary,
                  child: isDone
                      ? const Icon(Icons.check, size: 16, color: Colors.white)
                      : Text(
                          '$step',
                          style: const TextStyle(
                            color: Colors.white,
                            fontSize: 14,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                ),
                const SizedBox(width: 12),
                Text(
                  title,
                  style: theme.textTheme.titleMedium
                      ?.copyWith(fontWeight: FontWeight.bold),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Text(
              description,
              style: TextStyle(color: Colors.grey.shade700, fontSize: 13),
            ),
            const SizedBox(height: 12),
            if (isDone && doneSummary != null)
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                decoration: BoxDecoration(
                  color: Colors.green.shade50,
                  borderRadius: BorderRadius.circular(6),
                  border: Border.all(color: Colors.green.shade200),
                ),
                child: Row(
                  children: [
                    Icon(Icons.check_circle,
                        size: 16, color: Colors.green.shade700),
                    const SizedBox(width: 6),
                    Expanded(
                      child: Text(
                        doneSummary!,
                        style: TextStyle(
                          fontSize: 12,
                          color: Colors.green.shade900,
                          fontFamily: 'monospace',
                        ),
                        overflow: TextOverflow.ellipsis,
                        maxLines: 2,
                      ),
                    ),
                  ],
                ),
              )
            else if (isLoading)
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 8),
                child: Row(
                  children: [
                    SizedBox(
                      width: 16,
                      height: 16,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    ),
                    SizedBox(width: 12),
                    Text('Adding…'),
                  ],
                ),
              )
            else
              Align(
                alignment: Alignment.centerLeft,
                child: FilledButton.icon(
                  onPressed: onPressed,
                  icon: Icon(buttonIcon),
                  label: Text(buttonLabel),
                ),
              ),
          ],
        ),
      ),
    );
  }
}
