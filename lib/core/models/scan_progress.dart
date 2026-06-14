class ScanProgress {
  final bool isScanning;
  final int current;
  final int total;
  final String? currentFolder;

  const ScanProgress({
    this.isScanning = false,
    this.current = 0,
    this.total = 0,
    this.currentFolder,
  });

  double get fraction => total > 0 ? current / total : 0;

  ScanProgress copyWith({
    bool? isScanning,
    int? current,
    int? total,
    String? currentFolder,
  }) {
    return ScanProgress(
      isScanning: isScanning ?? this.isScanning,
      current: current ?? this.current,
      total: total ?? this.total,
      currentFolder: currentFolder ?? this.currentFolder,
    );
  }
}
