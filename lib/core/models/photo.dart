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
    String? destination,
    String? originalPath,
    int? trashedAt,
    int? processedAt,
  }) {
    return Photo(
      id: id ?? this.id,
      sourceFolderId: sourceFolderId ?? this.sourceFolderId,
      path: path ?? this.path,
      status: status ?? this.status,
      destination: destination ?? this.destination,
      originalPath: originalPath ?? this.originalPath,
      trashedAt: trashedAt ?? this.trashedAt,
      processedAt: processedAt ?? this.processedAt,
    );
  }
}