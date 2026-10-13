# Static ownership guard, not a substitute for UIKit build/device testing.
if(NOT DEFINED APP_DIR)
    message(FATAL_ERROR "APP_DIR is required")
endif()
file(READ "${APP_DIR}/Sources/VMRuntimeSettingsViewController.m" runtime)
file(READ "${APP_DIR}/Sources/VMSettingsViewController.m" app)
file(READ "${APP_DIR}/Sources/EmulatorViewController.m" emulator)
file(READ "${APP_DIR}/Sources/VMInstanceListViewController.m" machines)

foreach(forbidden "sharedSettings" "NSUserDefaults" "resetToDefaults"
        "VMFirmwareImportViewController" "VMGuestInstallViewController"
        "chooseGraphicsMode" "VMJitProbe")
    string(FIND "${runtime}" "${forbidden}" found)
    if(NOT found EQUAL -1)
        message(FATAL_ERROR "Runtime settings acquired app-wide control: ${forbidden}")
    endif()
endforeach()
foreach(required "Machine Settings" "runtimeDelegate" "snapshotsDirectory"
        "setRuntimePausesInBackground:" "setRuntimeMicrophoneEnabled:"
        "setRuntimeInlineConsole:" "setRuntimeInstructionCap:"
        "performAfterDismiss:" "UIAlertActionStyleCancel"
        "VMRuntimeActionForcePowerOff" "runtimeCanForcePowerOff"
        "s5lbox.machine-settings.force-power-off")
    string(FIND "${runtime}" "${required}" found)
    if(found EQUAL -1)
        message(FATAL_ERROR "Runtime settings lost its scoped control: ${required}")
    endif()
endforeach()
foreach(forbidden "VMGeneralRowSnapshots" "VMSnapshotListViewController")
    string(FIND "${app}" "${forbidden}" found)
    if(NOT found EQUAL -1)
        message(FATAL_ERROR "App settings acquired a machine-only row: ${forbidden}")
    endif()
endforeach()
string(FIND "${emulator}" "[[VMSettingsViewController alloc] init]" old_controller)
string(FIND "${emulator}" "[[VMRuntimeSettingsViewController alloc] init]" current_controller)
string(FIND "${machines}" "[[VMSettingsViewController alloc] init]" app_controller)
if(NOT old_controller EQUAL -1 OR current_controller EQUAL -1 OR app_controller EQUAL -1)
    message(FATAL_ERROR "Machines and emulator must use different settings controllers")
endif()
foreach(required "[_engine setInstructionCap:_sessionInstructionCap]"
        "_shuttingDown ||\n                         _sessionPausesInBackground"
        "BOOL inlineConsole = _sessionInlineConsole"
        "[_engine forcePowerOffWithCompletion:"
        "self.guestShutdownCompletion = nil; /* NEVER continue jailbreak after force-off */"
        "!_savingCheckpoint && !_restarting && !_forcePoweringOff;")
    string(FIND "${emulator}" "${required}" found)
    if(found EQUAL -1)
        message(FATAL_ERROR "Engine no longer consumes session-owned settings: ${required}")
    endif()
endforeach()
message(STATUS "App defaults and current-machine settings ownership passed")

# Keep the everyday machine page free of duplicate Pause/microphone/background
# switches. Only Controls owns Pause; session preferences belong to Advanced.
foreach(forbidden "pauseChanged:" "s5lbox.machine-settings.pause" "@\"Pause guest\"")
    string(FIND "${runtime}" "${forbidden}" found)
    if(NOT found EQUAL -1)
        message(FATAL_ERROR "Machine settings duplicated the Controls Pause action: ${forbidden}")
    endif()
endforeach()
foreach(required "const VMRuntimeSection sections[] = { VMRuntimeSectionApps, VMRuntimeSectionSnapshots,\n        VMRuntimeSectionPower, VMRuntimeSectionAdvanced };"
        "return section == 0 ? VMRuntimeSectionSession : VMRuntimeSectionDeveloper;"
        "case VMRuntimeSectionSession: return 2;"
        "advanced.advanced = YES;" "advanced.runtimeDelegate = self.runtimeDelegate;"
        "advanced.showsDeveloperControls = self.showsDeveloperControls;"
        "s5lbox.machine-settings.open-advanced")
    string(FIND "${runtime}" "${required}" found)
    if(found EQUAL -1)
        message(FATAL_ERROR "Machine settings lost its Advanced-only session preferences: ${required}")
    endif()
endforeach()
file(READ "${APP_DIR}/Sources/VMDeviceControlsViewController.m" controls)
string(FIND "${controls}" "setRuntimePaused:" controls_pause)
if(controls_pause EQUAL -1)
    message(FATAL_ERROR "Controls must retain Pause/Resume")
endif()

# UIKit positions the navigation title. Mutating it from the guest's layout
# resets its animated origin and makes the machine name jump during startup.
foreach(forbidden "_machineHeading.frame =" "_machineHeading.bounds =" "_machineHeading.center =")
    string(FIND "${emulator}" "${forbidden}" found)
    if(NOT found EQUAL -1)
        message(FATAL_ERROR "Emulator layout must not reposition its navigation title: ${forbidden}")
    endif()
endforeach()
foreach(required "@interface VMMachineHeadingView : UIView"
        "- (CGSize)intrinsicContentSize" "- (CGSize)sizeThatFits:(CGSize)size"
        "[titleContent.leadingAnchor constraintEqualToAnchor:_machineHeading.leadingAnchor]"
        "[titleContent.trailingAnchor constraintEqualToAnchor:_machineHeading.trailingAnchor]"
        "_machineTitle.lineBreakMode = NSLineBreakByTruncatingTail;"
        "traitCollectionDidChange:" "[self updateMachineHeadingFonts];")
    string(FIND "${emulator}" "${required}" found)
    if(found EQUAL -1)
        message(FATAL_ERROR "Navigation title lost its self-sizing or accessibility behavior: ${required}")
    endif()
endforeach()

file(READ "${APP_DIR}/Sources/VMEngine.m" engine)
file(READ "${APP_DIR}/Sources/AppDelegate.m" delegate)
foreach(required "setMicrophoneEnabled:[VMSettings sharedSettings].guestMicrophoneEnabled")
    string(FIND "${engine}" "${required}" found)
    if(found EQUAL -1)
        message(FATAL_ERROR "New sessions must inherit the microphone default")
    endif()
endforeach()
foreach(required "!VMSettings.sharedSettings.guestMicrophoneEnabled"
        "AVAudioSessionRecordPermissionUndetermined" "requestRecordPermission:")
    string(FIND "${delegate}" "${required}" found)
    if(found EQUAL -1)
        message(FATAL_ERROR "Initial microphone permission lost its opt-out/authorization gate")
    endif()
endforeach()
string(FIND "${app}" "[rows addObject:@(VMGeneralRowGuestMicrophone)]" microphone_row)
if(microphone_row EQUAL -1)
    message(FATAL_ERROR "Advanced settings lost the guest microphone default")
endif()

# Creation uses the app default once; no size picker or preference-driven boot
# resizing may be introduced on the normal New Machine path.
file(READ "${APP_DIR}/Sources/VMInstanceStore.m" store)
foreach(required "[rows addObject:@(VMGeneralRowDiskSize)]"
        "Storage for new machines" "s5lbox.settings.new-machine-storage"
        "controller->_settings.newMachineDiskSizeGiB = choice.unsignedIntegerValue;")
    string(FIND "${app}" "${required}" found)
    if(found EQUAL -1)
        message(FATAL_ERROR "Advanced settings lost its new-machine storage choice: ${required}")
    endif()
endforeach()
string(FIND "${machines}" "alertControllerWithTitle:@\"Disk Size\"" old_disk_picker)
string(FIND "${machines}" "createInstanceNamed:name error:&err" default_creation)
string(FIND "${store}" "diskSizeGiB:[VMSettings sharedSettings].newMachineDiskSizeGiB error:error" recorded_default)
if(NOT old_disk_picker EQUAL -1 OR default_creation EQUAL -1 OR recorded_default EQUAL -1)
    message(FATAL_ERROR "New Machine must record the storage default without a size picker")
endif()
string(FIND "${app}" "[rows addObject:@(VMGeneralRowPauseInBackground)]" background_row)
string(FIND "${app}" "VMDiagnosticsRowPauseInBackground" old_background_row)
if(background_row EQUAL -1 OR NOT old_background_row EQUAL -1)
    message(FATAL_ERROR "Background pause must be available in Advanced without Developer Mode")
endif()

file(READ "${APP_DIR}/Sources/VMGuestInstallViewController.m" jailbreak)
string(FIND "${jailbreak}" "_headline.textAlignment = NSTextAlignmentCenter;" centered_install_stage)
if(centered_install_stage EQUAL -1)
    message(FATAL_ERROR "Guest preparation headings must be centered above progress")
endif()
foreach(required "_includePackages = _packageFlow || [VMSettings sharedSettings].packagesWithJailbreak;"
        "if (!_includePackages) return YES;"
        "_openPackagesWhenReady = _packageFlow;")
    string(FIND "${jailbreak}" "${required}" found)
    if(found EQUAL -1)
        message(FATAL_ERROR "Jailbreak lost its optional Packages policy: ${required}")
    endif()
endforeach()
foreach(forbidden "offerPackagesIfVisible" "_packageOfferPending"
        "actionWithTitle:@\"Open Packages\"" "actionWithTitle:@\"Open iPhone OS\"")
    string(FIND "${jailbreak}" "${forbidden}" found)
    if(NOT found EQUAL -1)
        message(FATAL_ERROR "Jailbreak must return to the machine without a completion chooser: ${forbidden}")
    endif()
endforeach()
# Consume completion before calling the owner, and only from the visible,
# foreground screen. Notifications and viewDidAppear must not open it twice.
string(FIND "${jailbreak}" "if (!_completionPending || !self.view.window || self.presentedViewController ||" completion_guard)
string(FIND "${jailbreak}" "UIApplication.sharedApplication.applicationState != UIApplicationStateActive ||" active_guard)
string(FIND "${jailbreak}" "self.navigationController.topViewController != self) return;" top_guard)
string(FIND "${jailbreak}" "_completionPending = NO;\n    _openPackagesWhenReady = _packageFlow;\n    void (^ready)(void) = [self.readyHandler copy];" completion_consumed)
string(FIND "${jailbreak}" "void (^ready)(void) = [self.readyHandler copy];\n    if (ready) ready();" direct_handoff)
if(completion_guard EQUAL -1 OR active_guard EQUAL -1 OR top_guard EQUAL -1
        OR completion_consumed EQUAL -1 OR direct_handoff EQUAL -1)
    message(FATAL_ERROR "Jailbreak completion must perform a guarded, one-shot handoff")
endif()
file(READ "${APP_DIR}/Sources/VMPackageManagerViewController.m" packages)
string(FIND "${packages}" "Row(@\"S5LBox Packages\"" packages_banner)
if(NOT packages_banner EQUAL -1)
    message(FATAL_ERROR "Packages home must not duplicate its title in a branded banner")
endif()
# First-start readiness is per machine, not the choice of screen opened after
# jailbreaking. Manual entry, closing the sheet and relaunching must preserve it.
foreach(required "_finishingSetup=[VMPackageBridge awaitingFirstConnectionForInstance:identifier];"
        "[VMPackageBridge noteSuccessfulConnectionForInstance:self->_instanceID];"
        "self->_lastConnectionError=error;"
        "_lastConnectionError.localizedDescription"
        "if (self.setupRequest && _needsSetup)")
    string(FIND "${packages}" "${required}" found)
    if(found EQUAL -1)
        message(FATAL_ERROR "Packages lost its independent first-start connection state: ${required}")
    endif()
endforeach()
string(FIND "${emulator}" "finishingSetup:self.openPackagesWhenRunning" coupled_setup_navigation)
string(FIND "${packages}" "_needsSetup || _connectionTimedOut" timeout_reinstall)
if(NOT coupled_setup_navigation EQUAL -1 OR NOT timeout_reinstall EQUAL -1)
    message(FATAL_ERROR "Connection readiness must not depend on navigation or imply a reinstall on timeout")
endif()
file(READ "${APP_DIR}/Sources/VMPackageBridge.m" package_bridge)
string(REGEX MATCHALL "AwaitFirstConnection\\(identifier\\)" first_connection_markers "${package_bridge}")
list(LENGTH first_connection_markers first_connection_marker_count)
if(NOT first_connection_marker_count EQUAL 2)
    message(FATAL_ERROR "Both successful helper-preparation paths must await their first live connection")
endif()
string(FIND "${package_bridge}" "choose Repair package setup" misleading_repair_hint)
if(NOT misleading_repair_hint EQUAL -1)
    message(FATAL_ERROR "A transient connection error must not recommend reinstalling the helper")
endif()
string(FIND "${jailbreak}" "vm_guest_install_build_after_force_off(" force_builder)
string(FIND "${app}" "Force Off & Jailbreak" force_choice)
string(FIND "${machines}" "[self_ prepareGuestInstall:install" boot_to_shutdown)
if(force_builder EQUAL -1 OR force_choice EQUAL -1 OR NOT boot_to_shutdown EQUAL -1)
    message(FATAL_ERROR "Jailbreak must use explicit stopped-disk preflight, not boot-to-shutdown")
endif()
