import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:collection/collection.dart';
import 'core/providers/providers.dart';
import 'features/setup/setup_page.dart';
import 'features/organizer/organizer_page.dart';
import 'features/source_folders/source_folders_page.dart';
import 'features/target_folders/target_folders_page.dart';
import 'features/settings/settings_page.dart';

class App extends ConsumerWidget {
  const App({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final rootDirs = ref.watch(targetRootDirsProvider);
    final sourceFolders = ref.watch(sourceFoldersProvider);
    final scanProgress = ref.watch(photoScannerProvider);
    final defaultRootDir = rootDirs.isEmpty ? null : (rootDirs.where((d) => d.isDefault).firstOrNull ?? rootDirs.first);
    final setupComplete = defaultRootDir != null && sourceFolders.isNotEmpty;

    return MaterialApp(
      title: 'Photo Organizer',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
        useMaterial3: true,
      ),
      home: setupComplete
          ? const OrganizerPage()
          : const SetupPage(),
      routes: {
        '/setup': (context) => const SetupPage(),
        '/organizer': (context) => const OrganizerPage(),
        '/source-folders': (context) => const SourceFoldersPage(),
        '/target-folders': (context) => const TargetFoldersPage(),
        '/settings': (context) => const SettingsPage(),
      },
      builder: (context, child) {
        if (scanProgress.isScanning) {
          return Column(
            children: [
              LinearProgressIndicator(
                value: scanProgress.fraction,
                backgroundColor: Colors.grey[300],
              ),
              if (scanProgress.currentFolder != null)
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
                  child: Row(
                    children: [
                      Text(
                        'Scanning ${scanProgress.currentFolder}: ${scanProgress.current}/${scanProgress.total}',
                        style: Theme.of(context).textTheme.bodySmall,
                      ),
                    ],
                  ),
                ),
              Expanded(child: child ?? const SizedBox()),
            ],
          );
        }
        return child ?? const SizedBox();
      },
    );
  }
}
