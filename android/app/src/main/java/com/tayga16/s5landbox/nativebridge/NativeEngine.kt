package com.tayga16.s5landbox.nativebridge

import android.view.Surface

/**
 * The JNI surface of the portable emulator.
 *
 * Every long-running call is synchronous and must be made off the main thread;
 * the only exceptions are the queueing calls (touch, button, pause, stop) and
 * the JSON getters, which take the engine mutex and return.
 *
 * The class name and package are part of the ABI: jni_bridge.c exports
 * Java_com_tayga16_s5landbox_nativebridge_NativeEngine_* and renaming either
 * breaks the link at run time rather than at build time.
 */
object NativeEngine {
    init {
        System.loadLibrary("s5landbox")
    }

    external fun nativeCreate(root: String): Long
    external fun nativeDestroy(handle: Long)

    // Firmware.
    external fun nativeImportIpsw(
        handle: Long,
        fd: Int,
        size: Long,
        skipStockChecksums: Boolean,
    ): String
    external fun nativeImportCancel(handle: Long)

    /** [active, artefact, stage, done, total]. */
    external fun nativeImportProgress(handle: Long): LongArray
    external fun nativeFirmwareJson(handle: Long): String

    // Machines.
    external fun nativeMachinesJson(handle: Long): String
    external fun nativeMachineCreate(
        handle: Long,
        name: String,
        options: BooleanArray,
    ): String
    external fun nativeMachineRename(handle: Long, id: String, name: String): String
    external fun nativeMachineDelete(handle: Long, id: String): String
    external fun nativeOptionsJson(handle: Long): String
    external fun nativeMachineOptionsJson(handle: Long, id: String): String
    external fun nativeMachineSetOptions(
        handle: Long,
        id: String,
        options: BooleanArray,
    ): String

    // Execution.
    external fun nativeStart(handle: Long, id: String?): String
    external fun nativeStop(handle: Long, mode: Int)
    external fun nativeSetPaused(handle: Long, paused: Boolean)
    external fun nativeSetInstructionCap(handle: Long, cap: Long)
    external fun nativeStatusJson(handle: Long): String
    external fun nativeConsoleDrain(handle: Long): String

    // Display and input.
    external fun nativeSetSurface(handle: Long, surface: Surface?)
    external fun nativeTouch(
        handle: Long,
        x: Double,
        y: Double,
        viewWidth: Double,
        viewHeight: Double,
        phase: Int,
    ): Boolean
    external fun nativeButton(handle: Long, button: Int, pressed: Boolean): Boolean
    external fun nativeButtonPressed(handle: Long, button: Int): Boolean

    // Snapshots.
    external fun nativeSnapshotsJson(handle: Long, id: String): String
    external fun nativeSnapshotTake(handle: Long, id: String): String
    external fun nativeSnapshotRestore(
        handle: Long,
        id: String,
        snapshotId: String,
    ): String
    external fun nativeSnapshotDelete(
        handle: Long,
        id: String,
        snapshotId: String,
    ): String
    external fun nativeSnapshotNeedsUnlock(
        handle: Long,
        id: String,
        snapshotId: String,
    ): Boolean

    // Audio.
    external fun nativeRenderAudio(handle: Long, buffer: FloatArray, frames: Int): Int
    external fun nativeSetMicrophone(handle: Long, enabled: Boolean)

    // USB services (transport lands in a later stage).
    external fun nativeUsbReady(handle: Long): Boolean
    external fun nativeUsbOpen(handle: Long, service: String): String
}
