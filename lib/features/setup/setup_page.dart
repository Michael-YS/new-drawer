import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/providers/providers.dart';

class SetupPage extends ConsumerStatefulWidget {
  const SetupPage({super.key});

  @override
  ConsumerState<SetupPage> createState() => _SetupPageState();
}

class _SetupPageState extends ConsumerState<SetupPage> {
  bool _isLoading = false;

  Future<void> _selectRootDirectory() async {
    final fileService = ref.read(fileServiceProvider);
    final result = await fileService.pickDirectory();
    if (result != null) {
      if (!mounted) return;
      setState(() => _isLoading = true);
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
          setState(() => _isLoading = false);
        }
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Photo Organizer Setup'),
        centerTitle: true,
      ),
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(32.0),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(
                Icons.photo_library_outlined,
                size: 80,
                color: Colors.deepPurple,
              ),
              const SizedBox(height: 24),
              Text(
                'Welcome to Photo Organizer',
                style: Theme.of(context).textTheme.headlineSmall,
              ),
              const SizedBox(height: 16),
              const Text(
                'First, set up your target root directory where all organized photos will be stored.',
                textAlign: TextAlign.center,
                style: TextStyle(color: Colors.grey),
              ),
              const SizedBox(height: 48),
              if (_isLoading)
                const CircularProgressIndicator()
              else
                FilledButton.icon(
                  onPressed: _selectRootDirectory,
                  icon: const Icon(Icons.folder_open),
                  label: const Text('Select Target Root Directory'),
                ),
            ],
          ),
        ),
      ),
    );
  }
}
