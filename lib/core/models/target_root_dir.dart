class TargetRootDir {
  final int? id;
  final String path;
  final String displayName;
  final bool isDefault;
  final int addedAt;

  TargetRootDir({
    this.id,
    required this.path,
    required this.displayName,
    this.isDefault = false,
    required this.addedAt,
  });

  Map<String, dynamic> toMap() {
    return {
      if (id != null) 'id': id,
      'path': path,
      'display_name': displayName,
      'is_default': isDefault ? 1 : 0,
      'added_at': addedAt,
    };
  }

  factory TargetRootDir.fromMap(Map<String, dynamic> map) {
    return TargetRootDir(
      id: map['id'] as int?,
      path: map['path'] as String,
      displayName: map['display_name'] as String,
      isDefault: (map['is_default'] as int) == 1,
      addedAt: map['added_at'] as int,
    );
  }

  TargetRootDir copyWith({
    int? id,
    String? path,
    String? displayName,
    bool? isDefault,
    int? addedAt,
  }) {
    return TargetRootDir(
      id: id ?? this.id,
      path: path ?? this.path,
      displayName: displayName ?? this.displayName,
      isDefault: isDefault ?? this.isDefault,
      addedAt: addedAt ?? this.addedAt,
    );
  }
}