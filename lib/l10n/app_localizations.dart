import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:intl/intl.dart' as intl;

import 'app_localizations_en.dart';
import 'app_localizations_zh.dart';

// ignore_for_file: type=lint

/// Callers can lookup localized strings with an instance of AppLocalizations
/// returned by `AppLocalizations.of(context)`.
///
/// Applications need to include `AppLocalizations.delegate()` in their app's
/// `localizationDelegates` list, and the locales they support in the app's
/// `supportedLocales` list. For example:
///
/// ```dart
/// import 'l10n/app_localizations.dart';
///
/// return MaterialApp(
///   localizationsDelegates: AppLocalizations.localizationsDelegates,
///   supportedLocales: AppLocalizations.supportedLocales,
///   home: MyApplicationHome(),
/// );
/// ```
///
/// ## Update pubspec.yaml
///
/// Please make sure to update your pubspec.yaml to include the following
/// packages:
///
/// ```yaml
/// dependencies:
///   # Internationalization support.
///   flutter_localizations:
///     sdk: flutter
///   intl: any # Use the pinned version from flutter_localizations
///
///   # Rest of dependencies
/// ```
///
/// ## iOS Applications
///
/// iOS applications define key application metadata, including supported
/// locales, in an Info.plist file that is built into the application bundle.
/// To configure the locales supported by your app, you’ll need to edit this
/// file.
///
/// First, open your project’s ios/Runner.xcworkspace Xcode workspace file.
/// Then, in the Project Navigator, open the Info.plist file under the Runner
/// project’s Runner folder.
///
/// Next, select the Information Property List item, select Add Item from the
/// Editor menu, then select Localizations from the pop-up menu.
///
/// Select and expand the newly-created Localizations item then, for each
/// locale your application supports, add a new item and select the locale
/// you wish to add from the pop-up menu in the Value field. This list should
/// be consistent with the languages listed in the AppLocalizations.supportedLocales
/// property.
abstract class AppLocalizations {
  AppLocalizations(String locale)
    : localeName = intl.Intl.canonicalizedLocale(locale.toString());

  final String localeName;

  static AppLocalizations of(BuildContext context) {
    return Localizations.of<AppLocalizations>(context, AppLocalizations)!;
  }

  static const LocalizationsDelegate<AppLocalizations> delegate =
      _AppLocalizationsDelegate();

  /// A list of this localizations delegate along with the default localizations
  /// delegates.
  ///
  /// Returns a list of localizations delegates containing this delegate along with
  /// GlobalMaterialLocalizations.delegate, GlobalCupertinoLocalizations.delegate,
  /// and GlobalWidgetsLocalizations.delegate.
  ///
  /// Additional delegates can be added by appending to this list in
  /// MaterialApp. This list does not have to be used at all if a custom list
  /// of delegates is preferred or required.
  static const List<LocalizationsDelegate<dynamic>> localizationsDelegates =
      <LocalizationsDelegate<dynamic>>[
        delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
      ];

  /// A list of this localizations delegate's supported locales.
  static const List<Locale> supportedLocales = <Locale>[
    Locale('en'),
    Locale('zh'),
  ];

  /// No description provided for @appTitle.
  ///
  /// In en, this message translates to:
  /// **'Photo Organizer'**
  String get appTitle;

  /// No description provided for @commonCancel.
  ///
  /// In en, this message translates to:
  /// **'Cancel'**
  String get commonCancel;

  /// No description provided for @commonCreate.
  ///
  /// In en, this message translates to:
  /// **'Create'**
  String get commonCreate;

  /// No description provided for @commonRemove.
  ///
  /// In en, this message translates to:
  /// **'Remove'**
  String get commonRemove;

  /// No description provided for @commonRename.
  ///
  /// In en, this message translates to:
  /// **'Rename'**
  String get commonRename;

  /// No description provided for @appScanningProgress.
  ///
  /// In en, this message translates to:
  /// **'Scanning {folder}: {current}/{total}'**
  String appScanningProgress(String folder, int current, int total);

  /// No description provided for @setupAppBarTitle.
  ///
  /// In en, this message translates to:
  /// **'Photo Organizer Setup'**
  String get setupAppBarTitle;

  /// No description provided for @setupWelcome.
  ///
  /// In en, this message translates to:
  /// **'Welcome to Photo Organizer'**
  String get setupWelcome;

  /// No description provided for @setupWelcomeSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Set up your target root directory where all organized photos will be stored.'**
  String get setupWelcomeSubtitle;

  /// No description provided for @setupStep1Title.
  ///
  /// In en, this message translates to:
  /// **'Set target root'**
  String get setupStep1Title;

  /// No description provided for @setupStep1Description.
  ///
  /// In en, this message translates to:
  /// **'Where organized photos will be stored. All category folders you create live under this directory.'**
  String get setupStep1Description;

  /// No description provided for @setupStep2Title.
  ///
  /// In en, this message translates to:
  /// **'Add a source folder'**
  String get setupStep2Title;

  /// No description provided for @setupStep2Description.
  ///
  /// In en, this message translates to:
  /// **'A folder to scan for photos. You can add more or remove this later from the source folders screen.'**
  String get setupStep2Description;

  /// No description provided for @setupButtonTarget.
  ///
  /// In en, this message translates to:
  /// **'Select Target Root Directory'**
  String get setupButtonTarget;

  /// No description provided for @setupButtonSource.
  ///
  /// In en, this message translates to:
  /// **'Select Source Folder'**
  String get setupButtonSource;

  /// No description provided for @setupAdding.
  ///
  /// In en, this message translates to:
  /// **'Adding…'**
  String get setupAdding;

  /// No description provided for @setupAllSet.
  ///
  /// In en, this message translates to:
  /// **'All set — proceeding to organizer…'**
  String get setupAllSet;

  /// No description provided for @setupFailedTarget.
  ///
  /// In en, this message translates to:
  /// **'Failed to set target directory: {error}'**
  String setupFailedTarget(String error);

  /// No description provided for @setupFailedSource.
  ///
  /// In en, this message translates to:
  /// **'Failed to add source folder: {error}'**
  String setupFailedSource(String error);

  /// No description provided for @organizerAppBarTitle.
  ///
  /// In en, this message translates to:
  /// **'Photo Organizer'**
  String get organizerAppBarTitle;

  /// No description provided for @organizerTooltipSourceFolders.
  ///
  /// In en, this message translates to:
  /// **'Source Folders'**
  String get organizerTooltipSourceFolders;

  /// No description provided for @organizerTooltipSettings.
  ///
  /// In en, this message translates to:
  /// **'Settings'**
  String get organizerTooltipSettings;

  /// No description provided for @organizerTextProcessed.
  ///
  /// In en, this message translates to:
  /// **'Processed: {done} / {total}'**
  String organizerTextProcessed(int done, int total);

  /// No description provided for @organizerTextRemaining.
  ///
  /// In en, this message translates to:
  /// **'{count} remaining'**
  String organizerTextRemaining(int count);

  /// No description provided for @organizerTextLoading.
  ///
  /// In en, this message translates to:
  /// **'Loading…'**
  String get organizerTextLoading;

  /// No description provided for @organizerTextError.
  ///
  /// In en, this message translates to:
  /// **'Error'**
  String get organizerTextError;

  /// No description provided for @organizerTextAllPhotosProcessed.
  ///
  /// In en, this message translates to:
  /// **'All photos processed!'**
  String get organizerTextAllPhotosProcessed;

  /// No description provided for @organizerTextRescan.
  ///
  /// In en, this message translates to:
  /// **'Rescan for new photos'**
  String get organizerTextRescan;

  /// No description provided for @organizerTextNoTargetFolders.
  ///
  /// In en, this message translates to:
  /// **'No target folders'**
  String get organizerTextNoTargetFolders;

  /// No description provided for @organizerTextAddFolder.
  ///
  /// In en, this message translates to:
  /// **'Add Folder'**
  String get organizerTextAddFolder;

  /// No description provided for @organizerTextNew.
  ///
  /// In en, this message translates to:
  /// **'New'**
  String get organizerTextNew;

  /// No description provided for @organizerTextUndo.
  ///
  /// In en, this message translates to:
  /// **'Undo'**
  String get organizerTextUndo;

  /// No description provided for @organizerTextSkip.
  ///
  /// In en, this message translates to:
  /// **'Skip'**
  String get organizerTextSkip;

  /// No description provided for @organizerTextMove.
  ///
  /// In en, this message translates to:
  /// **'Move'**
  String get organizerTextMove;

  /// No description provided for @organizerTextDel.
  ///
  /// In en, this message translates to:
  /// **'Del'**
  String get organizerTextDel;

  /// No description provided for @organizerTextCannotLoadImage.
  ///
  /// In en, this message translates to:
  /// **'Cannot load image'**
  String get organizerTextCannotLoadImage;

  /// No description provided for @organizerTextLoadError.
  ///
  /// In en, this message translates to:
  /// **'Load error: {error}'**
  String organizerTextLoadError(String error);

  /// No description provided for @organizerTextFailedToMove.
  ///
  /// In en, this message translates to:
  /// **'Failed to move: {error}'**
  String organizerTextFailedToMove(String error);

  /// No description provided for @organizerDialogCreateNewFolderTitle.
  ///
  /// In en, this message translates to:
  /// **'Create New Folder'**
  String get organizerDialogCreateNewFolderTitle;

  /// No description provided for @organizerLabelFolderName.
  ///
  /// In en, this message translates to:
  /// **'Folder name'**
  String get organizerLabelFolderName;

  /// No description provided for @organizerHintFolderName.
  ///
  /// In en, this message translates to:
  /// **'e.g., Vacation'**
  String get organizerHintFolderName;

  /// No description provided for @sourceAppBarTitle.
  ///
  /// In en, this message translates to:
  /// **'Source Folders'**
  String get sourceAppBarTitle;

  /// No description provided for @sourceTooltipRescanAll.
  ///
  /// In en, this message translates to:
  /// **'Re-scan All'**
  String get sourceTooltipRescanAll;

  /// No description provided for @sourceTextNoFolders.
  ///
  /// In en, this message translates to:
  /// **'No source folders added'**
  String get sourceTextNoFolders;

  /// No description provided for @sourceButtonAddFolder.
  ///
  /// In en, this message translates to:
  /// **'Add Source Folder'**
  String get sourceButtonAddFolder;

  /// No description provided for @sourceTooltipDisable.
  ///
  /// In en, this message translates to:
  /// **'Disable'**
  String get sourceTooltipDisable;

  /// No description provided for @sourceTooltipEnable.
  ///
  /// In en, this message translates to:
  /// **'Enable'**
  String get sourceTooltipEnable;

  /// No description provided for @sourceTooltipRemove.
  ///
  /// In en, this message translates to:
  /// **'Remove'**
  String get sourceTooltipRemove;

  /// No description provided for @sourceConfirmRemoveTitle.
  ///
  /// In en, this message translates to:
  /// **'Remove Source Folder?'**
  String get sourceConfirmRemoveTitle;

  /// No description provided for @sourceConfirmRemoveMessage.
  ///
  /// In en, this message translates to:
  /// **'Pending photos from this folder will be removed. Done photos will be kept.'**
  String get sourceConfirmRemoveMessage;

  /// No description provided for @sourceErrorAdd.
  ///
  /// In en, this message translates to:
  /// **'Failed to add source folder: {error}'**
  String sourceErrorAdd(String error);

  /// No description provided for @targetAppBarTitle.
  ///
  /// In en, this message translates to:
  /// **'Target Folders'**
  String get targetAppBarTitle;

  /// No description provided for @targetTooltipManageRoots.
  ///
  /// In en, this message translates to:
  /// **'Manage Root Directories'**
  String get targetTooltipManageRoots;

  /// No description provided for @targetTextNoFoldersYet.
  ///
  /// In en, this message translates to:
  /// **'No target folders yet'**
  String get targetTextNoFoldersYet;

  /// No description provided for @targetButtonAddFolder.
  ///
  /// In en, this message translates to:
  /// **'Add Target Folder'**
  String get targetButtonAddFolder;

  /// No description provided for @targetErrorNoRootFirst.
  ///
  /// In en, this message translates to:
  /// **'Please set up a target root directory first'**
  String get targetErrorNoRootFirst;

  /// No description provided for @targetDialogCreateTitle.
  ///
  /// In en, this message translates to:
  /// **'Create Target Folder'**
  String get targetDialogCreateTitle;

  /// No description provided for @targetLabelRootDirectory.
  ///
  /// In en, this message translates to:
  /// **'Root Directory'**
  String get targetLabelRootDirectory;

  /// No description provided for @targetLabelFolderName.
  ///
  /// In en, this message translates to:
  /// **'Folder name'**
  String get targetLabelFolderName;

  /// No description provided for @targetHintFolderName.
  ///
  /// In en, this message translates to:
  /// **'e.g., Vacation, Anime, Screenshots'**
  String get targetHintFolderName;

  /// No description provided for @targetErrorCreateFolder.
  ///
  /// In en, this message translates to:
  /// **'Failed to create folder: {error}'**
  String targetErrorCreateFolder(String error);

  /// No description provided for @targetDialogRenameTitle.
  ///
  /// In en, this message translates to:
  /// **'Rename Folder'**
  String get targetDialogRenameTitle;

  /// No description provided for @targetLabelDisplayName.
  ///
  /// In en, this message translates to:
  /// **'Display name'**
  String get targetLabelDisplayName;

  /// No description provided for @targetDialogRemoveTitle.
  ///
  /// In en, this message translates to:
  /// **'Remove Target Folder?'**
  String get targetDialogRemoveTitle;

  /// No description provided for @targetDialogRemoveMessage.
  ///
  /// In en, this message translates to:
  /// **'The folder will be removed from the list. The actual folder on disk will not be deleted.'**
  String get targetDialogRemoveMessage;

  /// No description provided for @targetDialogRootsTitle.
  ///
  /// In en, this message translates to:
  /// **'Root Directories'**
  String get targetDialogRootsTitle;

  /// No description provided for @targetLabelMultiRoot.
  ///
  /// In en, this message translates to:
  /// **'Multi-root mode'**
  String get targetLabelMultiRoot;

  /// No description provided for @targetSubtitleMultiRoot.
  ///
  /// In en, this message translates to:
  /// **'Allow multiple root directories'**
  String get targetSubtitleMultiRoot;

  /// No description provided for @targetTextClose.
  ///
  /// In en, this message translates to:
  /// **'Close'**
  String get targetTextClose;

  /// No description provided for @settingsAppBarTitle.
  ///
  /// In en, this message translates to:
  /// **'Settings'**
  String get settingsAppBarTitle;

  /// No description provided for @settingsSectionSource.
  ///
  /// In en, this message translates to:
  /// **'Source Folders'**
  String get settingsSectionSource;

  /// No description provided for @settingsManageSourceTitle.
  ///
  /// In en, this message translates to:
  /// **'Manage Source Folders'**
  String get settingsManageSourceTitle;

  /// No description provided for @settingsManageSourceSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Add, remove, or disable source folders'**
  String get settingsManageSourceSubtitle;

  /// No description provided for @settingsSectionTarget.
  ///
  /// In en, this message translates to:
  /// **'Target Folders'**
  String get settingsSectionTarget;

  /// No description provided for @settingsManageTargetTitle.
  ///
  /// In en, this message translates to:
  /// **'Manage Target Folders'**
  String get settingsManageTargetTitle;

  /// No description provided for @settingsManageTargetSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Create and organize category folders'**
  String get settingsManageTargetSubtitle;

  /// No description provided for @settingsSectionDisplay.
  ///
  /// In en, this message translates to:
  /// **'Display'**
  String get settingsSectionDisplay;

  /// No description provided for @settingsShowSkippedTitle.
  ///
  /// In en, this message translates to:
  /// **'Show skipped photos'**
  String get settingsShowSkippedTitle;

  /// No description provided for @settingsShowSkippedSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Include skipped photos in the queue'**
  String get settingsShowSkippedSubtitle;

  /// No description provided for @settingsDownscaleTitle.
  ///
  /// In en, this message translates to:
  /// **'Downscale high-res photos'**
  String get settingsDownscaleTitle;

  /// No description provided for @settingsDownscaleSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Decode images at up to 2048px instead of full resolution. Recommended — fixes mosaic/pixelation on 50MP+ photos and reduces memory and load time. Disable only if you need to zoom in past 2K.'**
  String get settingsDownscaleSubtitle;

  /// No description provided for @settingsSectionData.
  ///
  /// In en, this message translates to:
  /// **'Data'**
  String get settingsSectionData;

  /// No description provided for @settingsResetStatusTitle.
  ///
  /// In en, this message translates to:
  /// **'Reset all processing status'**
  String get settingsResetStatusTitle;

  /// No description provided for @settingsResetStatusSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Mark all photos as pending again'**
  String get settingsResetStatusSubtitle;

  /// No description provided for @settingsClearTrashTitle.
  ///
  /// In en, this message translates to:
  /// **'Clear trash'**
  String get settingsClearTrashTitle;

  /// No description provided for @settingsClearTrashSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Permanently delete all trashed photos'**
  String get settingsClearTrashSubtitle;

  /// No description provided for @settingsClearDbTitle.
  ///
  /// In en, this message translates to:
  /// **'Clear database'**
  String get settingsClearDbTitle;

  /// No description provided for @settingsClearDbSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Wipe all data: source folders, target folders, photos'**
  String get settingsClearDbSubtitle;

  /// No description provided for @settingsSectionAbout.
  ///
  /// In en, this message translates to:
  /// **'About'**
  String get settingsSectionAbout;

  /// No description provided for @settingsAboutAppTitle.
  ///
  /// In en, this message translates to:
  /// **'Photo Organizer'**
  String get settingsAboutAppTitle;

  /// No description provided for @settingsAboutAppSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Version {version}'**
  String settingsAboutAppSubtitle(String version);

  /// No description provided for @settingsConfirmResetTitle.
  ///
  /// In en, this message translates to:
  /// **'Reset All Status?'**
  String get settingsConfirmResetTitle;

  /// No description provided for @settingsConfirmResetMessage.
  ///
  /// In en, this message translates to:
  /// **'This will mark all processed photos as pending again. Files will not be moved.'**
  String get settingsConfirmResetMessage;

  /// No description provided for @settingsSnackReset.
  ///
  /// In en, this message translates to:
  /// **'All statuses reset'**
  String get settingsSnackReset;

  /// No description provided for @settingsButtonReset.
  ///
  /// In en, this message translates to:
  /// **'Reset'**
  String get settingsButtonReset;

  /// No description provided for @settingsConfirmClearTrashTitle.
  ///
  /// In en, this message translates to:
  /// **'Clear Trash?'**
  String get settingsConfirmClearTrashTitle;

  /// No description provided for @settingsConfirmClearTrashMessage.
  ///
  /// In en, this message translates to:
  /// **'This will permanently delete all trashed photos. This action cannot be undone.'**
  String get settingsConfirmClearTrashMessage;

  /// No description provided for @settingsSnackClearTrash.
  ///
  /// In en, this message translates to:
  /// **'Trash cleared'**
  String get settingsSnackClearTrash;

  /// No description provided for @settingsErrorClearTrash.
  ///
  /// In en, this message translates to:
  /// **'Failed to clear trash: {error}'**
  String settingsErrorClearTrash(String error);

  /// No description provided for @settingsButtonClearTrash.
  ///
  /// In en, this message translates to:
  /// **'Clear Trash'**
  String get settingsButtonClearTrash;

  /// No description provided for @settingsConfirmClearDbTitle.
  ///
  /// In en, this message translates to:
  /// **'Clear database?'**
  String get settingsConfirmClearDbTitle;

  /// No description provided for @settingsConfirmClearDbMessage.
  ///
  /// In en, this message translates to:
  /// **'This will permanently delete ALL data: source folders, target root directories, target folders, and photo records.\\n\\nFiles on disk are NOT touched. The app will return to the initial setup screen.'**
  String get settingsConfirmClearDbMessage;

  /// No description provided for @settingsSnackClearDb.
  ///
  /// In en, this message translates to:
  /// **'Database cleared'**
  String get settingsSnackClearDb;

  /// No description provided for @settingsErrorClearDb.
  ///
  /// In en, this message translates to:
  /// **'Failed to clear database: {error}'**
  String settingsErrorClearDb(String error);

  /// No description provided for @settingsButtonClearDb.
  ///
  /// In en, this message translates to:
  /// **'Clear Everything'**
  String get settingsButtonClearDb;
}

class _AppLocalizationsDelegate
    extends LocalizationsDelegate<AppLocalizations> {
  const _AppLocalizationsDelegate();

  @override
  Future<AppLocalizations> load(Locale locale) {
    return SynchronousFuture<AppLocalizations>(lookupAppLocalizations(locale));
  }

  @override
  bool isSupported(Locale locale) =>
      <String>['en', 'zh'].contains(locale.languageCode);

  @override
  bool shouldReload(_AppLocalizationsDelegate old) => false;
}

AppLocalizations lookupAppLocalizations(Locale locale) {
  // Lookup logic when only language code is specified.
  switch (locale.languageCode) {
    case 'en':
      return AppLocalizationsEn();
    case 'zh':
      return AppLocalizationsZh();
  }

  throw FlutterError(
    'AppLocalizations.delegate failed to load unsupported locale "$locale". This is likely '
    'an issue with the localizations generation tool. Please file an issue '
    'on GitHub with a reproducible sample app and the gen-l10n configuration '
    'that was used.',
  );
}
