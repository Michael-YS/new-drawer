// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for English (`en`).
class AppLocalizationsEn extends AppLocalizations {
  AppLocalizationsEn([String locale = 'en']) : super(locale);

  @override
  String get appTitle => 'Photo Organizer';

  @override
  String get commonCancel => 'Cancel';

  @override
  String get commonCreate => 'Create';

  @override
  String get commonRemove => 'Remove';

  @override
  String get commonRename => 'Rename';

  @override
  String appScanningProgress(String folder, int current, int total) {
    return 'Scanning $folder: $current/$total';
  }

  @override
  String get setupAppBarTitle => 'Photo Organizer Setup';

  @override
  String get setupWelcome => 'Welcome to Photo Organizer';

  @override
  String get setupWelcomeSubtitle =>
      'Set up your target root directory where all organized photos will be stored.';

  @override
  String get setupStep1Title => 'Set target root';

  @override
  String get setupStep1Description =>
      'Where organized photos will be stored. All category folders you create live under this directory.';

  @override
  String get setupStep2Title => 'Add a source folder';

  @override
  String get setupStep2Description =>
      'A folder to scan for photos. You can add more or remove this later from the source folders screen.';

  @override
  String get setupButtonTarget => 'Select Target Root Directory';

  @override
  String get setupButtonSource => 'Select Source Folder';

  @override
  String get setupAdding => 'Adding…';

  @override
  String get setupAllSet => 'All set — proceeding to organizer…';

  @override
  String setupFailedTarget(String error) {
    return 'Failed to set target directory: $error';
  }

  @override
  String setupFailedSource(String error) {
    return 'Failed to add source folder: $error';
  }

  @override
  String get organizerAppBarTitle => 'Photo Organizer';

  @override
  String get organizerTooltipSourceFolders => 'Source Folders';

  @override
  String get organizerTooltipSettings => 'Settings';

  @override
  String organizerTextProcessed(int done, int total) {
    return 'Processed: $done / $total';
  }

  @override
  String organizerTextRemaining(int count) {
    return '$count remaining';
  }

  @override
  String get organizerTextLoading => 'Loading…';

  @override
  String get organizerTextError => 'Error';

  @override
  String get organizerTextAllPhotosProcessed => 'All photos processed!';

  @override
  String get organizerTextRescan => 'Rescan for new photos';

  @override
  String get organizerTextNoTargetFolders => 'No target folders';

  @override
  String get organizerTextAddFolder => 'Add Folder';

  @override
  String get organizerTextNew => 'New';

  @override
  String get organizerTextUndo => 'Undo';

  @override
  String get organizerTextSkip => 'Skip';

  @override
  String get organizerTextMove => 'Move';

  @override
  String get organizerTextDel => 'Del';

  @override
  String get organizerTextCannotLoadImage => 'Cannot load image';

  @override
  String organizerTextLoadError(String error) {
    return 'Load error: $error';
  }

  @override
  String organizerTextFailedToMove(String error) {
    return 'Failed to move: $error';
  }

  @override
  String get organizerDialogCreateNewFolderTitle => 'Create New Folder';

  @override
  String get organizerLabelFolderName => 'Folder name';

  @override
  String get organizerHintFolderName => 'e.g., Vacation';

  @override
  String get sourceAppBarTitle => 'Source Folders';

  @override
  String get sourceTooltipRescanAll => 'Re-scan All';

  @override
  String get sourceTextNoFolders => 'No source folders added';

  @override
  String get sourceButtonAddFolder => 'Add Source Folder';

  @override
  String get sourceTooltipDisable => 'Disable';

  @override
  String get sourceTooltipEnable => 'Enable';

  @override
  String get sourceTooltipRemove => 'Remove';

  @override
  String get sourceConfirmRemoveTitle => 'Remove Source Folder?';

  @override
  String get sourceConfirmRemoveMessage =>
      'Pending photos from this folder will be removed. Done photos will be kept.';

  @override
  String sourceErrorAdd(String error) {
    return 'Failed to add source folder: $error';
  }

  @override
  String get targetAppBarTitle => 'Target Folders';

  @override
  String get targetTooltipManageRoots => 'Manage Root Directories';

  @override
  String get targetTextNoFoldersYet => 'No target folders yet';

  @override
  String get targetButtonAddFolder => 'Add Target Folder';

  @override
  String get targetErrorNoRootFirst =>
      'Please set up a target root directory first';

  @override
  String get targetDialogCreateTitle => 'Create Target Folder';

  @override
  String get targetLabelRootDirectory => 'Root Directory';

  @override
  String get targetLabelFolderName => 'Folder name';

  @override
  String get targetHintFolderName => 'e.g., Vacation, Anime, Screenshots';

  @override
  String targetErrorCreateFolder(String error) {
    return 'Failed to create folder: $error';
  }

  @override
  String get targetDialogRenameTitle => 'Rename Folder';

  @override
  String get targetLabelDisplayName => 'Display name';

  @override
  String get targetDialogRemoveTitle => 'Remove Target Folder?';

  @override
  String get targetDialogRemoveMessage =>
      'The folder will be removed from the list. The actual folder on disk will not be deleted.';

  @override
  String get targetDialogRootsTitle => 'Root Directories';

  @override
  String get targetLabelMultiRoot => 'Multi-root mode';

  @override
  String get targetSubtitleMultiRoot => 'Allow multiple root directories';

  @override
  String get targetTextClose => 'Close';

  @override
  String get settingsAppBarTitle => 'Settings';

  @override
  String get settingsSectionSource => 'Source Folders';

  @override
  String get settingsManageSourceTitle => 'Manage Source Folders';

  @override
  String get settingsManageSourceSubtitle =>
      'Add, remove, or disable source folders';

  @override
  String get settingsSectionTarget => 'Target Folders';

  @override
  String get settingsManageTargetTitle => 'Manage Target Folders';

  @override
  String get settingsManageTargetSubtitle =>
      'Create and organize category folders';

  @override
  String get settingsSectionDisplay => 'Display';

  @override
  String get settingsShowSkippedTitle => 'Show skipped photos';

  @override
  String get settingsShowSkippedSubtitle =>
      'Include skipped photos in the queue';

  @override
  String get settingsDownscaleTitle => 'Downscale high-res photos';

  @override
  String get settingsDownscaleSubtitle =>
      'Decode images at up to 2048px instead of full resolution. Recommended — fixes mosaic/pixelation on 50MP+ photos and reduces memory and load time. Disable only if you need to zoom in past 2K.';

  @override
  String get settingsSectionData => 'Data';

  @override
  String get settingsResetStatusTitle => 'Reset all processing status';

  @override
  String get settingsResetStatusSubtitle => 'Mark all photos as pending again';

  @override
  String get settingsClearTrashTitle => 'Clear trash';

  @override
  String get settingsClearTrashSubtitle =>
      'Permanently delete all trashed photos';

  @override
  String get settingsClearDbTitle => 'Clear database';

  @override
  String get settingsClearDbSubtitle =>
      'Wipe all data: source folders, target folders, photos';

  @override
  String get settingsSectionAbout => 'About';

  @override
  String get settingsAboutAppTitle => 'Photo Organizer';

  @override
  String settingsAboutAppSubtitle(String version) {
    return 'Version $version';
  }

  @override
  String get settingsConfirmResetTitle => 'Reset All Status?';

  @override
  String get settingsConfirmResetMessage =>
      'This will mark all processed photos as pending again. Files will not be moved.';

  @override
  String get settingsSnackReset => 'All statuses reset';

  @override
  String get settingsButtonReset => 'Reset';

  @override
  String get settingsConfirmClearTrashTitle => 'Clear Trash?';

  @override
  String get settingsConfirmClearTrashMessage =>
      'This will permanently delete all trashed photos. This action cannot be undone.';

  @override
  String get settingsSnackClearTrash => 'Trash cleared';

  @override
  String settingsErrorClearTrash(String error) {
    return 'Failed to clear trash: $error';
  }

  @override
  String get settingsButtonClearTrash => 'Clear Trash';

  @override
  String get settingsConfirmClearDbTitle => 'Clear database?';

  @override
  String get settingsConfirmClearDbMessage =>
      'This will permanently delete ALL data: source folders, target root directories, target folders, and photo records.\\n\\nFiles on disk are NOT touched. The app will return to the initial setup screen.';

  @override
  String get settingsSnackClearDb => 'Database cleared';

  @override
  String settingsErrorClearDb(String error) {
    return 'Failed to clear database: $error';
  }

  @override
  String get settingsButtonClearDb => 'Clear Everything';
}
