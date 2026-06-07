import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
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
    final defaultRootDir = rootDirs.isEmpty ? null : (rootDirs.where((d) => d.isDefault).firstOrNull ?? rootDirs.first);

    return MaterialApp(
      title: 'Photo Organizer',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
        useMaterial3: true,
      ),
      home: defaultRootDir == null
          ? const SetupPage()
          : const OrganizerPage(),
      routes: {
        '/setup': (context) => const SetupPage(),
        '/organizer': (context) => const OrganizerPage(),
        '/source-folders': (context) => const SourceFoldersPage(),
        '/target-folders': (context) => const TargetFoldersPage(),
        '/settings': (context) => const SettingsPage(),
      },
    );
  }
}