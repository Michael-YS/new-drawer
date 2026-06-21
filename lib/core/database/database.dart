import 'dart:async';

import 'package:sqflite/sqflite.dart';
import 'package:path/path.dart';

class AppDatabase {
  static Future<Database>? _opening;
  static const String _dbName = 'photo_organizer.db';
  static const int _dbVersion = 3;

  static Future<Database> get database {
    return _opening ??= _initDatabase();
  }

  static Future<Database> _initDatabase() async {
    final dbPath = await getDatabasesPath();
    final path = join(dbPath, _dbName);

    return await openDatabase(
      path,
      version: _dbVersion,
      onCreate: _onCreate,
      onUpgrade: _onUpgrade,
    );
  }

  static Future<void> _onCreate(Database db, int version) async {
    await db.execute('''
      CREATE TABLE source_folders (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        path TEXT UNIQUE NOT NULL,
        display_name TEXT NOT NULL,
        enabled INTEGER NOT NULL DEFAULT 1,
        recursive INTEGER NOT NULL DEFAULT 1,
        added_at INTEGER NOT NULL
      )
    ''');

    await db.execute('''
      CREATE TABLE target_root_dirs (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        path TEXT UNIQUE NOT NULL,
        display_name TEXT NOT NULL,
        is_default INTEGER NOT NULL DEFAULT 0,
        added_at INTEGER NOT NULL
      )
    ''');

    await db.execute('''
      CREATE TABLE target_folders (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        root_dir_id INTEGER NOT NULL,
        name TEXT NOT NULL,
        display_name TEXT NOT NULL,
        sort_order INTEGER NOT NULL DEFAULT 0,
        last_used_at INTEGER,
        FOREIGN KEY (root_dir_id) REFERENCES target_root_dirs(id)
      )
    ''');

    await db.execute('''
      CREATE TABLE photos (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        source_folder_id INTEGER NOT NULL,
        path TEXT UNIQUE NOT NULL,
        status TEXT NOT NULL DEFAULT 'pending',
        destination TEXT,
        original_path TEXT,
        trashed_at INTEGER,
        processed_at INTEGER,
        FOREIGN KEY (source_folder_id) REFERENCES source_folders(id)
      )
    ''');

    await _createPhotosIndexes(db);
  }

  static Future<void> _createPhotosIndexes(DatabaseExecutor db) async {
    await db.execute(
      'CREATE INDEX IF NOT EXISTS idx_photos_path ON photos(path)',
    );
    await db.execute(
      'CREATE INDEX IF NOT EXISTS idx_photos_source_folder_id ON photos(source_folder_id)',
    );
    await db.execute(
      'CREATE INDEX IF NOT EXISTS idx_photos_status ON photos(status)',
    );
  }

  static Future<void> _onUpgrade(
    Database db,
    int oldVersion,
    int newVersion,
  ) async {
    if (oldVersion < 2) {
      await db.transaction((txn) async {
        await txn.delete('photos');
        await txn.delete('target_folders');
        await txn.delete('target_root_dirs');
        await txn.delete('source_folders');
        await txn.execute(
          "DELETE FROM sqlite_sequence WHERE name IN ('photos', 'target_folders', 'target_root_dirs', 'source_folders')",
        );
      });
    }
    if (oldVersion < 3) {
      await _createPhotosIndexes(db);
    }
  }

  static Future<void> clearAll() async {
    final db = await database;
    await db.transaction((txn) async {
      await txn.delete('photos');
      await txn.delete('target_folders');
      await txn.delete('target_root_dirs');
      await txn.delete('source_folders');
      await txn.execute(
        "DELETE FROM sqlite_sequence WHERE name IN ('photos', 'target_folders', 'target_root_dirs', 'source_folders')",
      );
    });
  }
}