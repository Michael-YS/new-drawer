enum PhotoStatus {
  pending,
  done,
  skipped,
  trashed,
  missing;

  static PhotoStatus fromString(String value) {
    return PhotoStatus.values.firstWhere(
      (e) => e.name == value,
      orElse: () => PhotoStatus.pending,
    );
  }
}

const _unchanged = Object();

class Photo {
  final int? id;
  final int sourceFolderId;
  final String path;
  final PhotoStatus status;
  final String? destination;
  final String? originalPath;
  final int? trashedAt;
  final int? processedAt;

  Photo({
    this.id,
    required this.sourceFolderId,
    required this.path,
    this.status = PhotoStatus.pending,
    this.destination,
    this.originalPath,
    this.trashedAt,
    this.processedAt,
  });

  Map<String, dynamic> toMap() {
    return {
      if (id != null) 'id': id,
      'source_folder_id': sourceFolderId,
      'path': path,
      'status': status.name,
      'destination': destination,
      'original_path': originalPath,
      'trashed_at': trashedAt,
      'processed_at': processedAt,
    };
  }

  factory Photo.fromMap(Map<String, dynamic> map) {
    return Photo(
      id: map['id'] as int?,
      sourceFolderId: map['source_folder_id'] as int,
      path: map['path'] as String,
      status: PhotoStatus.fromString(map['status'] as String),
      destination: map['destination'] as String?,
      originalPath: map['original_path'] as String?,
      trashedAt: map['trashed_at'] as int?,
      processedAt: map['processed_at'] as int?,
    );
  }

  Photo copyWith({
    int? id,
    int? sourceFolderId,
    String? path,
    PhotoStatus? status,
    Object? destination = _unchanged,
    Object? originalPath = _unchanged,
    Object? trashedAt = _unchanged,
    Object? processedAt = _unchanged,
  }) {
    return Photo(
      id: id ?? this.id,
      sourceFolderId: sourceFolderId ?? this.sourceFolderId,
      path: path ?? this.path,
      status: status ?? this.status,
      destination: identical(destination, _unchanged)
          ? this.destination
          : destination as String?,
      originalPath: identical(originalPath, _unchanged)
          ? this.originalPath
          : originalPath as String?,
      trashedAt: identical(trashedAt, _unchanged)
          ? this.trashedAt
          : trashedAt as int?,
      processedAt: identical(processedAt, _unchanged)
          ? this.processedAt
          : processedAt as int?,
    );
  }
}
