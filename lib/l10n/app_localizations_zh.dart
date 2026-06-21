// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Chinese (`zh`).
class AppLocalizationsZh extends AppLocalizations {
  AppLocalizationsZh([String locale = 'zh']) : super(locale);

  @override
  String get appTitle => '照片整理';

  @override
  String get commonCancel => '取消';

  @override
  String get commonCreate => '创建';

  @override
  String get commonRemove => '移除';

  @override
  String get commonRename => '重命名';

  @override
  String appScanningProgress(String folder, int current, int total) {
    return '正在扫描 $folder: $current/$total';
  }

  @override
  String get setupAppBarTitle => '照片整理 - 初始化';

  @override
  String get setupWelcome => '欢迎使用照片整理';

  @override
  String get setupWelcomeSubtitle => '设置目标根目录,所有整理后的照片都将保存在这里。';

  @override
  String get setupStep1Title => '设置目标根目录';

  @override
  String get setupStep1Description => '整理后的照片将存放在此。你创建的所有分类文件夹都位于该目录下。';

  @override
  String get setupStep2Title => '添加来源文件夹';

  @override
  String get setupStep2Description => '用于扫描照片的文件夹。稍后可以在来源文件夹页面继续添加或移除。';

  @override
  String get setupButtonTarget => '选择目标根目录';

  @override
  String get setupButtonSource => '选择来源文件夹';

  @override
  String get setupAdding => '添加中…';

  @override
  String get setupAllSet => '准备就绪——正在进入整理页面…';

  @override
  String setupFailedTarget(String error) {
    return '设置目标目录失败:$error';
  }

  @override
  String setupFailedSource(String error) {
    return '添加来源文件夹失败:$error';
  }

  @override
  String get organizerAppBarTitle => '照片整理';

  @override
  String get organizerTooltipSourceFolders => '来源文件夹';

  @override
  String get organizerTooltipSettings => '设置';

  @override
  String organizerTextProcessed(int done, int total) {
    return '已处理:$done / $total';
  }

  @override
  String organizerTextRemaining(int count) {
    return '剩余 $count 张';
  }

  @override
  String get organizerTextLoading => '加载中…';

  @override
  String get organizerTextError => '出错了';

  @override
  String get organizerTextAllPhotosProcessed => '所有照片已处理完毕!';

  @override
  String get organizerTextRescan => '重新扫描新照片';

  @override
  String get organizerTextNoTargetFolders => '暂无目标文件夹';

  @override
  String get organizerTextAddFolder => '添加文件夹';

  @override
  String get organizerTextNew => '新建';

  @override
  String get organizerTextUndo => '撤销';

  @override
  String get organizerTextSkip => '跳过';

  @override
  String get organizerTextMove => '移动';

  @override
  String get organizerTextDel => '删除';

  @override
  String get organizerTextCannotLoadImage => '无法加载图片';

  @override
  String organizerTextLoadError(String error) {
    return '加载失败:$error';
  }

  @override
  String organizerTextFailedToMove(String error) {
    return '移动失败:$error';
  }

  @override
  String get organizerDialogCreateNewFolderTitle => '新建文件夹';

  @override
  String get organizerLabelFolderName => '文件夹名称';

  @override
  String get organizerHintFolderName => '例如:旅行';

  @override
  String get sourceAppBarTitle => '来源文件夹';

  @override
  String get sourceTooltipRescanAll => '重新扫描全部';

  @override
  String get sourceTextNoFolders => '尚未添加来源文件夹';

  @override
  String get sourceButtonAddFolder => '添加来源文件夹';

  @override
  String get sourceTooltipDisable => '停用';

  @override
  String get sourceTooltipEnable => '启用';

  @override
  String get sourceTooltipRemove => '移除';

  @override
  String get sourceConfirmRemoveTitle => '移除来源文件夹?';

  @override
  String get sourceConfirmRemoveMessage => '此文件夹中待处理的照片将被移除,已处理的照片会保留。';

  @override
  String sourceErrorAdd(String error) {
    return '添加来源文件夹失败:$error';
  }

  @override
  String get targetAppBarTitle => '目标文件夹';

  @override
  String get targetTooltipManageRoots => '管理根目录';

  @override
  String get targetTextNoFoldersYet => '暂无目标文件夹';

  @override
  String get targetButtonAddFolder => '添加目标文件夹';

  @override
  String get targetErrorNoRootFirst => '请先设置目标根目录';

  @override
  String get targetDialogCreateTitle => '创建目标文件夹';

  @override
  String get targetLabelRootDirectory => '根目录';

  @override
  String get targetLabelFolderName => '文件夹名称';

  @override
  String get targetHintFolderName => '例如:旅行、动漫、截图';

  @override
  String targetErrorCreateFolder(String error) {
    return '创建文件夹失败:$error';
  }

  @override
  String get targetDialogRenameTitle => '重命名文件夹';

  @override
  String get targetLabelDisplayName => '显示名称';

  @override
  String get targetDialogRemoveTitle => '移除目标文件夹?';

  @override
  String get targetDialogRemoveMessage => '该文件夹将从列表中移除,但磁盘上的实际文件夹不会被删除。';

  @override
  String get targetDialogRootsTitle => '根目录';

  @override
  String get targetLabelMultiRoot => '多根目录模式';

  @override
  String get targetSubtitleMultiRoot => '允许多个根目录';

  @override
  String get targetTextClose => '关闭';

  @override
  String get settingsAppBarTitle => '设置';

  @override
  String get settingsSectionSource => '来源文件夹';

  @override
  String get settingsManageSourceTitle => '管理来源文件夹';

  @override
  String get settingsManageSourceSubtitle => '添加、移除或停用来源文件夹';

  @override
  String get settingsSectionTarget => '目标文件夹';

  @override
  String get settingsManageTargetTitle => '管理目标文件夹';

  @override
  String get settingsManageTargetSubtitle => '创建并整理分类文件夹';

  @override
  String get settingsSectionDisplay => '显示';

  @override
  String get settingsShowSkippedTitle => '显示已跳过的照片';

  @override
  String get settingsShowSkippedSubtitle => '将已跳过的照片包含在队列中';

  @override
  String get settingsDownscaleTitle => '降采样高分辨率照片';

  @override
  String get settingsDownscaleSubtitle =>
      '以最大 2048px 解码图片,而非全分辨率。推荐开启——可修复 5000 万像素以上照片的锯齿/像素化问题,并降低内存和加载时间。只有当你需要放大超过 2K 时才关闭。';

  @override
  String get settingsSectionData => '数据';

  @override
  String get settingsResetStatusTitle => '重置所有处理状态';

  @override
  String get settingsResetStatusSubtitle => '将所有照片重新标记为待处理';

  @override
  String get settingsClearTrashTitle => '清空回收站';

  @override
  String get settingsClearTrashSubtitle => '永久删除回收站中的所有照片';

  @override
  String get settingsClearDbTitle => '清除数据库';

  @override
  String get settingsClearDbSubtitle => '清除所有数据:来源文件夹、目标文件夹、照片';

  @override
  String get settingsSectionAbout => '关于';

  @override
  String get settingsAboutAppTitle => '照片整理';

  @override
  String settingsAboutAppSubtitle(String version) {
    return '版本 $version';
  }

  @override
  String get settingsConfirmResetTitle => '重置所有状态?';

  @override
  String get settingsConfirmResetMessage => '这会将所有已处理的照片重新标记为待处理,但不会移动文件。';

  @override
  String get settingsSnackReset => '所有状态已重置';

  @override
  String get settingsButtonReset => '重置';

  @override
  String get settingsConfirmClearTrashTitle => '清空回收站?';

  @override
  String get settingsConfirmClearTrashMessage => '将永久删除回收站中的所有照片,此操作无法撤销。';

  @override
  String get settingsSnackClearTrash => '回收站已清空';

  @override
  String settingsErrorClearTrash(String error) {
    return '清空回收站失败:$error';
  }

  @override
  String get settingsButtonClearTrash => '清空回收站';

  @override
  String get settingsConfirmClearDbTitle => '清除数据库?';

  @override
  String get settingsConfirmClearDbMessage =>
      '将永久删除所有数据:来源文件夹、目标根目录、目标文件夹以及照片记录。\\n\\n磁盘上的文件不会被删除,应用将返回初始设置页面。';

  @override
  String get settingsSnackClearDb => '数据库已清除';

  @override
  String settingsErrorClearDb(String error) {
    return '清除数据库失败:$error';
  }

  @override
  String get settingsButtonClearDb => '清除全部';
}
