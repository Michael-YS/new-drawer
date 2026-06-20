import 'dart:io' show Platform;

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart' deferred as sqflite_ffi hide SqfliteDatabaseExecutorExt, SqfliteDatabaseExecutorIterateExt, SqfliteDatabaseExt, SqfliteDatabaseFactoryDebug, SqfliteSqlCommandExecutorExt, DatabaseFactoryLoggerDebugExt;
import 'app.dart';

Future<void> main() async {
  if (!kIsWeb && (Platform.isWindows || Platform.isLinux || Platform.isMacOS)) {
    await sqflite_ffi.loadLibrary();
    sqflite_ffi.sqfliteFfiInit();
    sqflite_ffi.databaseFactory = sqflite_ffi.databaseFactoryFfi;
  }

  runApp(
    const ProviderScope(
      child: App(),
    ),
  );
}
