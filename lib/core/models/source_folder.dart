class SourceFolder {
  final int? id;
  final String path;
  final String displayName;
  final bool enabled;
  final bool recursive;
  final int addedAt;

  SourceFolder({
    this.id,
    required this.path,
    required this.displayName,
    this.enabled = true,
    this.recursive = true,
    required this.addedAt,
  });

  Map<String, dynamic> toMap() {
    return {
      if (id != null) 'id': id,
      'path': path,
      'display_name': displayName,
      'enabled': enabled ? 1 : 0,
      'recursive': recursive ? 1 : 0,
      'added_at': addedAt,
    };
  }

  factory SourceFolder.fromMap(Map<String, dynamic> map) {
    return SourceFolder(
      id: map['id'] as int?,
      path: map['path'] as String,
      displayName: map['display_name'] as String,
      enabled: (map['enabled'] as int) == 1,
      recursive: (map['recursive'] as int) == 1,
      addedAt: map['added_at'] as int,
    );
  }

  SourceFolder copyWith({
    int? id,
    String? path,
    String? displayName,
    bool? enabled,
    bool? recursive,
    int? addedAt,
  }) {
    return SourceFolder(
      id: id ?? this.id,
      path: path ?? this.path,
      displayName: displayName ?? this.displayName,
      enabled: enabled ?? this.enabled,
      recursive: recursive ?? this.recursive,
      addedAt: addedAt ?? this.addedAt,
    );
  }
}