class TargetFolder {
  final int? id;
  final int rootDirId;
  final String name;
  final String displayName;
  final int sortOrder;
  final int? lastUsedAt;

  TargetFolder({
    this.id,
    required this.rootDirId,
    required this.name,
    required this.displayName,
    this.sortOrder = 0,
    this.lastUsedAt,
  });

  Map<String, dynamic> toMap() {
    return {
      if (id != null) 'id': id,
      'root_dir_id': rootDirId,
      'name': name,
      'display_name': displayName,
      'sort_order': sortOrder,
      'last_used_at': lastUsedAt,
    };
  }

  factory TargetFolder.fromMap(Map<String, dynamic> map) {
    return TargetFolder(
      id: map['id'] as int?,
      rootDirId: map['root_dir_id'] as int,
      name: map['name'] as String,
      displayName: map['display_name'] as String,
      sortOrder: map['sort_order'] as int? ?? 0,
      lastUsedAt: map['last_used_at'] as int?,
    );
  }

  TargetFolder copyWith({
    int? id,
    int? rootDirId,
    String? name,
    String? displayName,
    int? sortOrder,
    int? lastUsedAt,
  }) {
    return TargetFolder(
      id: id ?? this.id,
      rootDirId: rootDirId ?? this.rootDirId,
      name: name ?? this.name,
      displayName: displayName ?? this.displayName,
      sortOrder: sortOrder ?? this.sortOrder,
      lastUsedAt: lastUsedAt ?? this.lastUsedAt,
    );
  }
}