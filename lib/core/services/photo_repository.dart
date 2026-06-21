import 'package:sqflite/sqflite.dart';
import '../database/database.dart';
import '../models/source_folder.dart';
import '../models/target_root_dir.dart';
import '../models/target_folder.dart';
import '../models/photo.dart';

class SourceFolderRepository {
  Future<Database> get _db => AppDatabase.database;

  Future<int> insert(SourceFolder folder) async {
    final db = await _db;
    return await db.insert('source_folders', folder.toMap());
  }

  Future<List<SourceFolder>> getAll() async {
    final db = await _db;
    final maps = await db.query('source_folders');
    return maps.map((m) => SourceFolder.fromMap(m)).toList();
  }

  Future<List<SourceFolder>> getEnabled() async {
    final db = await _db;
    final maps = await db.query(
      'source_folders',
      where: 'enabled = ?',
      whereArgs: [1],
    );
    return maps.map((m) => SourceFolder.fromMap(m)).toList();
  }

  Future<void> update(SourceFolder folder) async {
    final db = await _db;
    await db.update(
      'source_folders',
      folder.toMap(),
      where: 'id = ?',
      whereArgs: [folder.id],
    );
  }

  Future<void> delete(int id) async {
    final db = await _db;
    await db.delete(
      'source_folders',
      where: 'id = ?',
      whereArgs: [id],
    );
  }
}

class TargetRootDirRepository {
  Future<Database> get _db => AppDatabase.database;

  Future<int> insert(TargetRootDir dir) async {
    final db = await _db;
    return await db.insert('target_root_dirs', dir.toMap());
  }

  Future<List<TargetRootDir>> getAll() async {
    final db = await _db;
    final maps = await db.query('target_root_dirs');
    return maps.map((m) => TargetRootDir.fromMap(m)).toList();
  }

  Future<TargetRootDir?> getDefault() async {
    final db = await _db;
    final maps = await db.query(
      'target_root_dirs',
      where: 'is_default = ?',
      whereArgs: [1],
      limit: 1,
    );
    if (maps.isEmpty) return null;
    return TargetRootDir.fromMap(maps.first);
  }

  Future<void> update(TargetRootDir dir) async {
    final db = await _db;
    await db.update(
      'target_root_dirs',
      dir.toMap(),
      where: 'id = ?',
      whereArgs: [dir.id],
    );
  }

  Future<void> delete(int id) async {
    final db = await _db;
    await db.delete(
      'target_root_dirs',
      where: 'id = ?',
      whereArgs: [id],
    );
  }

  Future<void> clearDefault() async {
    final db = await _db;
    await db.update(
      'target_root_dirs',
      {'is_default': 0},
      where: 'is_default = ?',
      whereArgs: [1],
    );
  }
}

class TargetFolderRepository {
  Future<Database> get _db => AppDatabase.database;

  Future<int> insert(TargetFolder folder) async {
    final db = await _db;
    return await db.insert('target_folders', folder.toMap());
  }

  Future<List<TargetFolder>> getAll() async {
    final db = await _db;
    final maps = await db.query('target_folders', orderBy: 'sort_order ASC');
    return maps.map((m) => TargetFolder.fromMap(m)).toList();
  }

  Future<List<TargetFolder>> getByRootDir(int rootDirId) async {
    final db = await _db;
    final maps = await db.query(
      'target_folders',
      where: 'root_dir_id = ?',
      whereArgs: [rootDirId],
      orderBy: 'sort_order ASC',
    );
    return maps.map((m) => TargetFolder.fromMap(m)).toList();
  }

  Future<void> update(TargetFolder folder) async {
    final db = await _db;
    await db.update(
      'target_folders',
      folder.toMap(),
      where: 'id = ?',
      whereArgs: [folder.id],
    );
  }

  Future<void> delete(int id) async {
    final db = await _db;
    await db.delete(
      'target_folders',
      where: 'id = ?',
      whereArgs: [id],
    );
  }

  Future<void> updateLastUsed(int id) async {
    final db = await _db;
    await db.update(
      'target_folders',
      {'last_used_at': DateTime.now().millisecondsSinceEpoch},
      where: 'id = ?',
      whereArgs: [id],
    );
  }

  Future<void> updateSortOrders(List<TargetFolder> folders) async {
    final db = await _db;
    final batch = db.batch();
    for (int i = 0; i < folders.length; i++) {
      batch.update(
        'target_folders',
        {'sort_order': i},
        where: 'id = ?',
        whereArgs: [folders[i].id],
      );
    }
    await batch.commit();
  }
}

class PhotoRepository {
  Future<Database> get _db => AppDatabase.database;

  Future<int> insert(Photo photo) async {
    final db = await _db;
    try {
      final id = await db.insert('photos', photo.toMap());
      return id;
    } on DatabaseException catch (e) {
      if (e.isUniqueConstraintError()) {
        final existing = await getByPath(photo.path);
        if (existing != null && existing.status == PhotoStatus.missing) {
          await update(existing.copyWith(
            status: PhotoStatus.pending,
            destination: null,
            originalPath: null,
            trashedAt: null,
            processedAt: null,
          ));
        }
        return existing?.id ?? -1;
      }
      rethrow;
    }
  }

  Future<int> insertBatch(List<Photo> photos) async {
    if (photos.isEmpty) return 0;
    final db = await _db;
    final newIds = <int>[];
    await db.transaction((txn) async {
      for (final photo in photos) {
        try {
          final id = await txn.insert(
            'photos',
            photo.toMap(),
            conflictAlgorithm: ConflictAlgorithm.ignore,
          );
          if (id != 0) {
            newIds.add(id);
          } else {
            await _reactivateIfMissing(txn, photo);
          }
        } on DatabaseException catch (e) {
          if (!e.isUniqueConstraintError()) rethrow;
          await _reactivateIfMissing(txn, photo);
        }
      }
    });
    return newIds.length;
  }

  Future<void> _reactivateIfMissing(Transaction txn, Photo photo) async {
    final existing = await getByPathInTxn(txn, photo.path);
    if (existing != null && existing.status == PhotoStatus.missing) {
      await updateInTxn(
        txn,
        existing.copyWith(
          status: PhotoStatus.pending,
          destination: null,
          originalPath: null,
          trashedAt: null,
          processedAt: null,
        ),
      );
    }
  }

  Future<Photo?> getByPathInTxn(Transaction txn, String path) async {
    final maps = await txn.query(
      'photos',
      where: 'path = ?',
      whereArgs: [path],
      limit: 1,
    );
    if (maps.isEmpty) return null;
    return Photo.fromMap(maps.first);
  }

  Future<void> updateInTxn(Transaction txn, Photo photo) async {
    await txn.update(
      'photos',
      photo.toMap(),
      where: 'id = ?',
      whereArgs: [photo.id],
    );
  }

  Future<Photo?> getByPath(String path) async {
    final db = await _db;
    final maps = await db.query(
      'photos',
      where: 'path = ?',
      whereArgs: [path],
      limit: 1,
    );
    if (maps.isEmpty) return null;
    return Photo.fromMap(maps.first);
  }

  Future<Photo?> getById(int id) async {
    final db = await _db;
    final maps = await db.query(
      'photos',
      where: 'id = ?',
      whereArgs: [id],
      limit: 1,
    );
    if (maps.isEmpty) return null;
    return Photo.fromMap(maps.first);
  }

  Future<List<Photo>> getBySourceFolder(int sourceFolderId) async {
    final db = await _db;
    final maps = await db.query(
      'photos',
      where: 'source_folder_id = ?',
      whereArgs: [sourceFolderId],
    );
    return maps.map((m) => Photo.fromMap(m)).toList();
  }

  Future<List<Photo>> getAll() async {
    final db = await _db;
    final maps = await db.query('photos');
    return maps.map((m) => Photo.fromMap(m)).toList();
  }

  Future<void> markMissing(int id) async {
    final db = await _db;
    await db.update(
      'photos',
      {'status': PhotoStatus.missing.name},
      where: 'id = ?',
      whereArgs: [id],
    );
  }

  Future<List<Photo>> getByStatus(PhotoStatus status) async {
    final db = await _db;
    final maps = await db.query(
      'photos',
      where: 'status = ?',
      whereArgs: [status.name],
    );
    return maps.map((m) => Photo.fromMap(m)).toList();
  }

  Future<Photo?> getPendingFirst() async {
    final db = await _db;
    final maps = await db.query(
      'photos',
      where: 'status = ?',
      whereArgs: ['pending'],
      orderBy: 'id ASC',
      limit: 1,
    );
    if (maps.isEmpty) return null;
    return Photo.fromMap(maps.first);
  }

  Future<List<Photo>> getAllPending() async {
    final db = await _db;
    final maps = await db.query(
      'photos',
      where: 'status = ?',
      whereArgs: ['pending'],
    );
    return maps.map((m) => Photo.fromMap(m)).toList();
  }

  Future<int> getPendingCount() async {
    final db = await _db;
    final result = await db.rawQuery(
      'SELECT COUNT(*) as count FROM photos WHERE status = ?',
      ['pending'],
    );
    return result.first['count'] as int;
  }

  Future<int> getTotalCount() async {
    final db = await _db;
    final result = await db.rawQuery(
      'SELECT COUNT(*) as count FROM photos WHERE status IN (?, ?, ?)',
      ['pending', 'done', 'skipped'],
    );
    return result.first['count'] as int;
  }

  Future<void> update(Photo photo) async {
    final db = await _db;
    await db.update(
      'photos',
      photo.toMap(),
      where: 'id = ?',
      whereArgs: [photo.id],
    );
  }

  Future<void> delete(int id) async {
    final db = await _db;
    await db.delete(
      'photos',
      where: 'id = ?',
      whereArgs: [id],
    );
  }

  Future<void> deleteBySourceFolder(int sourceFolderId, {bool onlyPending = true}) async {
    final db = await _db;
    if (onlyPending) {
      await db.delete(
        'photos',
        where: 'source_folder_id = ? AND status = ?',
        whereArgs: [sourceFolderId, 'pending'],
      );
    } else {
      await db.delete(
        'photos',
        where: 'source_folder_id = ?',
        whereArgs: [sourceFolderId],
      );
    }
  }

  Future<void> resetAllStatus() async {
    final db = await _db;
    await db.update(
      'photos',
      {
        'status': 'pending',
        'destination': null,
        'original_path': null,
        'trashed_at': null,
        'processed_at': null,
      },
      where: 'status IN (?, ?)',
      whereArgs: ['done', 'skipped'],
    );
  }

  Future<void> deleteAllPending() async {
    final db = await _db;
    await db.delete('photos', where: 'status = ?', whereArgs: ['pending']);
  }

  Future<void> deleteAllPhotos() async {
    final db = await _db;
    await db.delete('photos');
  }

  Future<Photo?> getLastProcessed() async {
    final db = await _db;
    final maps = await db.query(
      'photos',
      orderBy: 'id DESC',
      limit: 1,
    );
    if (maps.isEmpty) return null;
    return Photo.fromMap(maps.first);
  }
}
