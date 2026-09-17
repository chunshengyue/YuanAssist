# 类与文件索引

> 自动生成的符号索引：按包目录列出每个 Kotlin 文件的顶层类型与函数，用于「已知类名，找文件路径」。
>
> - 只列顶层「类型 + 函数」；顶层 `val`/`var` 常量只在文件没有类型/函数时才列出
> - 用 `rg <类名>` 定位通常更快；本文件适合不知道确切类名、只知道大致归属时浏览
> - **按需查，不必通读**
> - 重新生成（在仓库根目录）：`python tools/gen_modules_index.py`
>
> 导航：[README](README.md) · [entrypoints](entrypoints.md) · [architecture](architecture.md)

---


## `core/`  (29 个文件)

- `AutoSelectScriptBuilder.kt` — `AutoSelectScriptBuilder`
- `AutoTaskEngine.kt` — `AutoTaskEngine`
- `BirdFoodRuntimeManager.kt` — `BirdFoodRuntimeManager`
- `CharacterImportBridge.kt` — `CharacterImportBridge`
- `CharacterImportEngine.kt` — `CharacterImportInferenceDecider`, `FateCorrection`, `FateScoreResult`, `AgentInference`, `CharacterImportFateScorer`, `CharacterImportEngine`
- `CharacterStarDetector.kt` — `CharacterStarDetectionResult`, `CharacterStarCandidate`, `CharacterStarDetector`
- `CombatEngine.kt` — `CombatEngine`
- `CoordinateManager.kt` — `CoordinateManager`
- `DailyBirdFoodBridge.kt` — `DailyBirdFoodBridge`
- `DailyMainline624Bridge.kt` — `DailyMainline624Bridge`
- `DailyPlanCompletion.kt` — `DailyPlanCompletion`
- `DailyPlanGraphBuilder.kt` — `DailyPlanGraphBuilder`
- `DailyScriptLibraryBridge.kt` — `DailyPlanSelection`, `DailyScriptLibraryBridge`
- `DailyScriptRecorderManager.kt` — `DailyScriptRecorderManager`
- `DailyWindowManager.kt` — `DailyWindowManager`
- `GestureDispatcher.kt` — `GestureDispatcher`
- `InventoryStitchEngine.kt` — `InventoryStitchEngine`
- `Mainline624RuntimeManager.kt` — `Mainline624RuntimeManager`
- `OcrPreprocessor.kt` — `OcrPreprocessor`
- `OneKeyDailyBridge.kt` — `OneKeyDailyBridge`
- `RecordEngine.kt` — `RecordEngine`
- `StargazingBridge.kt` — `StargazingBridge`
- `StargazingRuntimeManager.kt` — `StargazingRuntimeManager`
- `TableOcrEngine.kt` — `TableOcrEngine`
- `TemplateMatcher.kt` — `TemplateMatcher`
- `XiuweiCalculator.kt` — `XiuweiJob`, `XiuweiMaterial`, `XiuweiAgentInput`, `XiuweiStagePlan`, `XiuweiCalculationResult`, `XiuweiCatalog`, `XiuweiCalculator`, `XiuweiOptimizer`
- `XiuweiInventoryOcr.kt` — `XiuweiOcrResult`, `XiuweiInventoryOcr`, `recognizeColumn`, `findNameCandidate`, `recognizeNumberAboveName`, `logColumnLines`, `parseNumber`, `bestNameMatch`, `normalizeText`, `NormalizedBitmap`, `normalizeTo1080Width`, `Rect`, `editDistance`
- `YuanAssistApp.kt` — `YuanAssistApp`
- `YuanAssistService.kt` — `ActionItem`, `TargetSwitchTask`, `ScriptConfigJson`, `LocalScriptJson`, `PendingCombatScriptExport`, `CombatScriptExportBridge`, `YuanAssistService`

## `model/`  (16 个文件)

- `ActionItem.kt` — `ActionItem`
- `AgentRepository.kt` — `AgentAttr`, `AgentRepository`
- `AppUpdateModels.kt` — `update`, `GiteeUpdatePayload`, `announcement`
- `BattleStageNavigation.kt` — `BattleStageTarget`, `TaishanFuStageNavigationConfig`, `BattleStageTarget`, `BattleStageTarget`, `encodeStageAutoNavValue`, `decodeStageAutoNavTarget`, `taishanFuStageConfigFromValue`, `taishanFuLevelFromValue`, `isStageAutoNavAutoEnterNextFloorEnabled`, `formatStageAutoNavDisplay`, `TemplateRegionConfig`, `DirectStageNavigationConfig`, `BattleStageNavigationRegistry`
- `CharacterImportModels.kt` — `CharacterImportConfig`, `CharacterSwitchPoint`, `ImportedCharacterRecord`
- `DailyBirdFoodModels.kt` — `BirdFoodStopCondition`, `BirdFoodTaskType`, `DaiBanGongWuOption`, `DaiBanGongWuEntry`, `TaDeChuanWenOption`, `BirdFoodConfig`
- `DailyMainline624Models.kt` — `Mainline624GameVariant`, `Mainline624Config`
- `DailyPlanGraphModels.kt` — `DailyPlanGraph`, `GraphLayoutNode`, `GraphNodeUiModel`, `GraphEdgeUiModel`, `GraphEdgeType`, `NodeDetailUiModel`, `NodeIncomingReference`
- `DailyTaskModels.kt` — `DailyTaskPlan`, `DailyTask`, `ModeOverrides`, `TaskOverride`, `TaskParams`, `ScreenshotStep`, `ROI`
- `Feedback.kt` — `issue_feedback`
- `HistoryAction.kt` — `HistoryAction`
- `ScriptModels.kt` — `InstructionType`, `DragonQiComparison`, `DragonQiCondition`, `encodeDragonQiCondition`, `decodeDragonQiCondition`, `DragonQiCondition`, `ScriptInstruction`, `InstructionJson`, `ScriptInstruction`, `InstructionJson`
- `StargazingModels.kt` — `StargazingConfig`
- `StrategyModel.kt` — `SupabaseRecord`, `MyUser`, `strategy_detail`, `strategy_favorite`, `strategy_comment`, `strategy_message`, `cloud_daily_script`, `cloud_daily_script_comment`, `cloud_daily_script_message`
- `StrategyPreviewData.kt` — `StrategyPreviewData`
- `TurnData.kt` — `TurnData`

## `model/dialogs/`  (5 个文件)

- `InstructionDialogs.kt` — `InstructionDialogs`
- `NoteDialogs.kt` — `NoteDialogs`
- `ServiceDialogs.kt` — `ServiceDialogs`
- `SettingsDialog.kt` — `SettingsDialog`
- `StyledDialogUi.kt` — `StyledDialogUi`

## `network/`  (2 个文件)

- `OcrManager.kt` — `OcrManager`
- `SupabaseRepository.kt` — `FavoriteState`, `StrategySavePayload`, `DailyScriptUploadTicket`, `DailyScriptDownloadTicket`, `CloudDailyScriptPublishPayload`, `HomeBadges`, `CloudGameAgentFateDisc`, `CloudGameAgent`, `CloudGachaPool`, `CloudGachaArchive`, `MyPublishedItems`, `MyMessageItems`, `SupabaseRepository`

## `tableocr/`  (11 个文件)

- `ActionComponentAnalyzer.kt` — `ActionComponentAnalyzer`
- `ActionParser.kt` — `ActionParser`
- `AssetCopier.kt` — `AssetCopier`
- `CellLayoutAnalyzer.kt` — `CellLayoutAnalyzer`
- `GridDetector.kt` — `GridDetector`
- `OcrScheduler.kt` — `OcrScheduler`
- `PaddleOcrNative.kt` — `PaddleOcrNative`
- `PaddleTextRecognizer.kt` — `PaddleTextResult`, `PaddleTextBlock`, `PaddleTextLine`, `PaddleTextElement`, `PaddleDetectedText`, `PaddleRecognizeByBoxesResult`, `PaddleTextRecognizer`
- `PostProcessor.kt` — `PostProcessor`
- `TableDetector.kt` — `TableDetector`
- `TableOcrModels.kt` — `CellBox`, `TableStructure`, `LineSegment`, `RowResult`, `TableOcrPreviewResult`, `ConfidenceLevel`, `ParseResult`

## `ui/`  (43 个文件)

- `AgentSelectionComponents.kt` — `SharedAgentPickerDialog`, `SharedTalentPickerDialog`, `AgentAvatar`, `TalentValueChip`, `buildTalentPreview`, `resolveTalentLabel`, `resolveTalentIdByLabel`, `buildSelectableAgentList`, `AgentPickerItem`, `TalentChoiceChip`
- `AutoSelectDialog.kt` — `AutoSelectDialog`
- `CharacterImportCalibrationDragHelper.kt` — `CharacterImportCalibrationDragHelper`
- `CharacterImportFragment.kt` — `CharacterImportFragment`
- `CharacterImportReviewActivity.kt` — `CharacterImportReviewActivity`, `EditField`, `EditTarget`, `CharacterImportReviewScreen`, `ExportHeaderEditor`, `ReviewCharacterColumn`, `RoleCell`, `StarCell`, `ValueCell`, `FateListCell`, `FateTextLine`, `RemarkCell`, `ReviewCellShell`, `StarIcons`, `StarPickerDialog`, `StarChoiceChip`, `TextEditDialog`
- `CloudDailyScriptActivity.kt` — `CloudDailyScriptActivity`
- `CloudDailyScriptListActivity.kt` — `CloudDailyScriptListActivity`
- `CloudDailyScriptListAdapter.kt` — `CloudDailyScriptListAdapter`
- `DailyBirdFoodFragment.kt` — `DailyBirdFoodFragment`, `BirdFoodUiState`, `DailyBirdFoodScreen`, `CheckOption`
- `DailyInventoryStitchFragment.kt` — `DailyInventoryStitchFragment`, `DailyInventoryStitchScreen`
- `DailyMainline624Fragment.kt` — `DailyMainline624Fragment`, `DailyMainline624UiState`, `DailyMainline624Screen`, `StoneHintCell`, `SpacerCell`, `StoneActionButton`
- `ExcludedAgentsDialog.kt` — `ExcludedAgentsDialog`
- `FaqActivity.kt` — `FaqItem`, `FaqActivity`, `FaqScreen`, `FaqEntry`
- `FeedbackAdminActivity.kt` — `FeedbackAdminActivity`, `FeedbackAdminScreen`, `FeedbackAdminRecordCard`
- `FeedbackCenterActivity.kt` — `FeedbackCenterActivity`, `FeedbackCenterScreen`, `FeedbackRecordCard`
- `FloatingUIManager.kt` — `FloatingUIManager`
- `GachaPoolDirectoryActivity.kt` — `GachaPoolDirectoryActivity`, `GachaPoolDirectoryScreen`, `PoolDirectoryTopBar`, `GachaArchiveConfirmDialog`, `PoolTopButton`, `PoolDirectoryIntro`, `ArchiveSummaryCard`, `ArchiveAgentSummaryCard`, `ArchiveAgentSummaryMetric`, `ArchiveSummaryMetric`, `PoolEntryCard`, `PoolEntryStats`, `PoolEntryStat`, `poolAspectRatio`, `GachaArchiveSwitchDialog`, `GachaArchiveCreateDialog`
- `GachaRecordActivity.kt` — `GachaRecordActivity`, `GachaMetric`, `GachaAgent`, `UpAgent`, `PrePull`, `CorrectionRecord`, `PullBadgeTone`, `GachaRecordScreen`, `GachaTopBar`, `GachaTopButton`, `GachaHero`, `GachaActionButton`, `GachaManualEntryDialog`, `GachaPityEditorDialog`, `GachaDeleteSecretDialog`, `GachaTabs`, `GachaOverview`, `GachaMetricCell`, `UpAgentRow`, `GachaPityStrip`, `GachaSecretRoster`, `SecretAgentCard`, `PullBadge`, `GachaCorrectionRecords`, `CorrectionRecordItem`, `RarityLabel`, `rarityColor`, `GachaEmptyHint`, `GachaSectionHeader`, `GachaAvatar`
- `GlobalSettingsActivity.kt` — `GlobalSettingsActivity`, `GlobalSettingsScreen`, `GlobalDelayField`, `parseGlobalDelayInput`
- `ImagePickerActivity.kt` — `ImagePickerActivity`
- `JobStationActivity.kt` — `JobStationActivity`
- `JobStationAssetRepository.kt` — `JobStationAssetRepository`
- `JobStationListActivity.kt` — `JobStationListActivity`
- `JobStationListAdapter.kt` — `JobStationListAdapter`
- `JobStationRemoteRepository.kt` — `JobStationApiModels`, `JobStationApiService`, `JobStationRemoteRepository`
- `LegacyFragmentHostActivity.kt` — `LegacyFragmentHostActivity`
- `LogAdapter.kt` — `LogAdapter`
- `MainActivity.kt` — `MainActivity`
- `MyFavoriteActivity.kt` — `MyFavoriteActivity`, `MyFavoriteScreen`, `FavoriteStrategyCard`, `buildStrategyAgentsText`
- `MyMessageActivity.kt` — `MessageEntry`, `MyMessageActivity`, `MyMessageScreen`, `CloudMessageCard`, `MessageCard`
- `MyPublishedActivity.kt` — `PublishedEntry`, `MyPublishedActivity`, `MyPublishedScreen`, `PublishedCloudScriptCard`, `PublishedStrategyCard`, `buildPublishedAgentsText`
- `MyStoneActivity.kt` — `MyStoneActivity`
- `OneKeyDailyActivity.kt` — `OneKeyDailyActivity`
- `RecordedDailyScriptViewerActivity.kt` — `RecordedDailyScriptViewerActivity`
- `RunLogActivity.kt` — `RunLogSection`, `RunLogActivity`, `RunLogScreen`, `sectionKey`, `RunLogEntry`, `buildColoredRunLogText`, `colorForRunLogLine`
- `ScriptLibraryActivity.kt` — `ScriptLibraryActivity`
- `SettingsActivity.kt` — `SettingsActivity`, `SettingsFormState`, `SettingsScreen`, `SpeedRadioRow`, `SettingsNumberField`, `SettingsDecimalField`, `SettingsTextField`, `SaveSettingsButton`
- `StargazingFragment.kt` — `StargazingFragment`, `StargazingUiState`, `StargazingScreen`
- `StrategyDetailActivity.kt` — `DetailItem`, `StrategyDetailActivity`
- `StrategyDetailImagesAdapter.kt` — `StrategyDetailImagesAdapter`
- `UploadCloudDailyScriptActivity.kt` — `UploadCloudDailyScriptActivity`
- `UploadStrategyActivity.kt` — `UploadTurnItem`, `UploadImportMode`, `UploadAgentMode`, `AgentSlotState`, `UploadStrategyUiState`, `UploadStrategyActivity`, `UploadStrategyScreen`, `BasicInfoSection`, `DelayFields`, `ScriptSection`, `ScriptTable`, `TableHeader`, `TableRow`, `AgentSection`, `AgentSlotCard`, `ImageSection`, `ImageUploadPanel`, `ActionSection`, `SegmentedButtons`, `SmallChoice`, `NumericTextField`, `yuanOutlinedTextFieldColors`, `TableCell`, `TitleText`, `BodyText`, `String`
- `XiuweiCalculatorActivity.kt` — `XiuweiAgentDraft`, `XiuweiCalculatorActivity`, `XiuweiCalculatorScreen`, `MaterialEditor`, `AgentEditor`, `JobSelector`, `ResultSection`, `StageMaterialAdder`, `decodeBitmap`, `calculateResult`, `createPreviewBitmap`, `Int`

## `ui/main/`  (10 个文件)

- `DailyScriptDebugIndex.kt` — `DailyScriptDebugNode`, `DailyScriptDebugIndex`, `DailyScriptTemplateNames`
- `DebugTabScreen.kt` — `DebugTabScreen`, `DebugCombatRoiDialog`, `DebugCombatRoiCanvas`, `DebugGlassPanel`, `DebugDropdownField`, `DebugScopeChip`, `DebugActionGrid`, `DebugMiniButton`, `DebugReplacementDialog`, `DebugReplacementCanvas`
- `DebugWorkbenchCoordinator.kt` — `DebugWorkbenchCoordinator`
- `HomeActionHandler.kt` — `HomeActionHandler`
- `HomeTabScreen.kt` — `HomeTabScreen`, `HomeEntryButton`, `ModeSection`, `FriendLinksSection`, `VersionInfo`, `FriendLinkCard`, `FriendLinkAssetIcon`, `SectionHeader`
- `JobTabScreen.kt` — `JobTabScreen`, `JobStationSourceCard`
- `MainShellActions.kt` — `HomeTabActions`, `JobTabActions`, `DebugTabActions`, `MineTabActions`
- `MainShellModels.kt` — `MainTab`, `HomeOverlayState`, `MineProfileState`, `DebugSelectionOption`, `DebugReplacementDialogState`, `DebugCombatRoiDialogState`, `DebugWorkbenchState`
- `MainShellScreen.kt` — `MainShellScreen`, `HomeTitle`, `SectionPageTitle`, `BottomNavItem`, `BottomActionBar`, `BottomNavButton`, `CompactBottomNavLabel`, `UnreadBadge`
- `MineTabScreen.kt` — `MineTabScreen`, `MineEntryItem`, `MineStage`, `MineProfilePanel`, `MineProfileActionChip`, `MineEntryRail`, `MineEntryButton`

## `ui/main/components/`  (2 个文件)

- `GufengDecorActionButton.kt` — `GufengDecorActionButton`, `CompactButtonLabel`
- `GufengFeatureCard.kt` — `GufengCardIcon`, `GufengFeatureCard`

## `ui/main/theme/`  (1 个文件)

- `MainShellTheme.kt` — `MainShellTheme`

## `ui/subpage/`  (9 个文件)

- `StoneStyleButton.kt` — `StoneStyleButton`, `StoneStyleChoiceButton`
- `SubpageCards.kt` — `SubpageSectionCard`, `SubpagePaperPanel`, `SubpageInfoStrip`, `SubpageBadge`
- `SubpageDialogs.kt` — `SubpageConfirmDialog`, `SubpageInputDialog`
- `SubpageFormFields.kt` — `SubpageFieldGroup`, `SubpageTextField`
- `SubpageListItems.kt` — `SubpageActionRow`, `SubpageToggleRow`, `SubpageChipRow`
- `SubpageOptionControls.kt` — `SubpageRadioOption`, `SubpageCheckOption`, `SubpageCircleIndicator`, `SubpageSquareIndicator`, `SubpageOptionRow`
- `SubpageScaffold.kt` — `SubpageScaffold`, `SubpageTopBar`, `SubpageBackButton`, `SubpageShapes`
- `SubpageStates.kt` — `SubpageEmptyState`, `SubpageLoadingState`, `SubpageErrorText`
- `SubpageThemeBridge.kt` — `SubpageThemeBridge`, `SubpageSurfaceColor`

## `ui/view/`  (1 个文件)

- `DailyPlanGraphView.kt` — `DailyPlanGraphView`

## `utils/`  (35 个文件)

- `AdminAccess.kt` — `isFeedbackAdminDevice`, `firstFeedbackImageUrl`
- `BattleFlowTestKeys.kt`（仅常量） — `BATTLE_FLOW_TEST_TASK_KEY`, `BATTLE_FLOW_START_BATTLE_OCR_DELAY_KEY`, `BATTLE_FLOW_FIRST_ACTION_DELAY_OPTION`
- `BirdFoodDebugScreenshotStore.kt` — `BirdFoodDebugScreenshotStore`
- `BirdFoodTestKeys.kt`（仅常量） — `BIRD_FOOD_NAV_TEST_TASK_KEY`
- `CharacterImportCalibrationStore.kt` — `CharacterImportCalibrationStore`
- `CharacterImportOverviewImageRenderer.kt` — `CharacterImportOverviewImageRenderer`
- `CloudDailyScriptReadStore.kt` — `CloudDailyScriptReadStore`
- `CloudGameAgentCache.kt` — `CloudGameAgentCache`
- `CloudScriptOverrideStore.kt` — `CloudScriptOverrideBundle`, `CloudScriptOverrideStore`
- `CombatDetectionRoiStore.kt` — `CombatDetectionRoiKey`, `CombatDetectionRoi`, `CombatDetectionRoiStore`
- `ConfigManager.kt` — `AppConfig`, `ConfigManager`
- `DailyGlobalDelayStore.kt` — `DailyGlobalDelayStore`
- `DailyScriptBundleZipStore.kt` — `PackedDailyScriptBundle`, `DailyScriptBundleZipStore`
- `DailyTaskTestKeys.kt`（仅常量） — `DAI_BAN_GONG_WU_START_BATTLE_DELAY_OPTION`, `MAINLINE_624_START_BATTLE_DELAY_OPTION`
- `DialogUtils.kt` — `DialogUtils`
- `ExceptionLogStore.kt` — `PersistedExceptionLogEntry`, `ExceptionLogStore`
- `GachaArchiveStore.kt` — `GachaGameVariant`, `GachaAgentRarity`, `GachaAgentRarityRegistry`, `GachaPoolDefinition`, `GachaPoolCatalog`, `GachaPoolCatalogRefreshResult`, `GachaPoolCatalogStore`, `GachaPoolProgress`, `GachaArchive`, `GachaArchiveSummary`, `GachaAgentFrequency`, `GachaArchiveMeta`, `GachaArchiveStore`
- `GachaPoolCoverStore.kt` — `GachaPoolCoverStore`
- `GameConstants.kt` — `GameConstants`
- `ImageExportUtils.kt` — `ImageExportUtils`
- `MyStoneStore.kt` — `MyStoneImageEntry`, `MyStoneCell`, `MyStoneRow`, `MyStoneRecord`, `MyStoneArchive`, `MyStoneArchiveMeta`, `MyStoneStore`
- `OcrRouteManager.kt` — `OcrRouteManager`
- `RunLogger.kt` — `RunLogger`
- `StartBattleShared.kt` — `StartBattleShared`
- `StoneLocalOcrSession.kt` — `StoneLocalOcrSession`
- `StoneOcrCoordinator.kt` — `StoneOcrMode`, `StoneOcrImportResult`, `StoneOcrCoordinator`
- `StoneOcrParser.kt` — `StoneStat`, `StoneOcrParser`
- `StonePaddleLocalRecognizer.kt` — `StonePaddleLocalRecognizer`
- `SupabaseTimeFormatter.kt` — `SupabaseTimeFormatter`
- `TemplateDelayOverrideStore.kt` — `TemplateDelayOverrideStore`
- `TemplateOverrideStore.kt` — `TemplateOverrideStore`
- `TraditionalModeStore.kt` — `TraditionalModeStore`
- `UserDailyScriptStore.kt` — `UserDailyScriptBundle`, `UserDailyScriptStore`
- `ViewExt.kt` — `EditText`, `EditText`, `EditText`
- `XiuweiCalculatorStore.kt` — `SavedXiuweiAgent`, `SavedXiuweiState`, `XiuweiCalculatorStore`
