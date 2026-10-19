package com.tayga16.s5landbox.data

import android.view.Surface
import com.tayga16.s5landbox.nativebridge.NativeEngine
import org.json.JSONObject

/**
 * Kotlin's view of one engine handle.
 *
 * The repository does not own threads and does not cache anything: it turns the
 * native JSON into records and hands calls straight through. Callers choose the
 * dispatcher — everything that can copy a firmware image or replay a snapshot
 * belongs on Dispatchers.IO, never on the main thread.
 */
class EngineRepository(private val handle: Long) {

    val available: Boolean get() = handle != 0L

    // ------------------------------------------------------------- firmware --

    fun firmware(): FirmwareState {
        val o = json(NativeEngine.nativeFirmwareJson(handle))
        val kernel = o.optJSONObject("kernel") ?: JSONObject()
        val tree = o.optJSONObject("devicetree") ?: JSONObject()
        val rootfs = o.optJSONObject("rootfs") ?: JSONObject()
        return FirmwareState(
            ok = o.optBoolean("ok", false),
            kernelPresent = kernel.optBoolean("present", false),
            kernelSize = kernel.optLong("size", 0L),
            devicetreePresent = tree.optBoolean("present", false),
            devicetreeSize = tree.optLong("size", 0L),
            rootfsPresent = rootfs.optBoolean("present", false),
            rootfsSize = rootfs.optLong("size", 0L),
            importOk = o.optBoolean("importOk", false),
            importActive = o.optBoolean("importActive", false),
            source = o.optString("source", ""),
            lastReport = o.optString("lastReport", ""),
        )
    }

    /**
     * Run the whole IPSW import. The descriptor stays the caller's; this reads
     * through pread and never closes it. Blocking — call on a background thread.
     */
    fun importIpsw(fd: Int, size: Long, skipStockChecksums: Boolean): ImportOutcome {
        val o = json(
            NativeEngine.nativeImportIpsw(
                handle,
                fd,
                size,
                skipStockChecksums,
            ),
        )
        return ImportOutcome(
            ok = o.optBoolean("ok", false),
            detail = o.optString("detail", ""),
        )
    }

    fun cancelImport() = NativeEngine.nativeImportCancel(handle)

    fun importProgress(): ImportProgress {
        val v = try {
            NativeEngine.nativeImportProgress(handle)
        } catch (_: Throwable) {
            LongArray(5)
        }
        return ImportProgress(
            active = v.getOrElse(0) { 0L } != 0L,
            artefact = v.getOrElse(1) { 0L }.toInt(),
            stage = v.getOrElse(2) { 0L }.toInt(),
            done = v.getOrElse(3) { 0L },
            total = v.getOrElse(4) { 0L },
        )
    }

    // ------------------------------------------------------------- machines --

    fun machines(): List<Machine> {
        val o = json(NativeEngine.nativeMachinesJson(handle))
        val array = o.optJSONArray("machines") ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val m = array.optJSONObject(i) ?: continue
                add(
                    Machine(
                        id = m.optString("id", ""),
                        name = m.optString("name", ""),
                        createdUnix = m.optLong("created", 0L),
                        lastOpenedUnix = m.optLong("lastOpened", 0L),
                        retiredTotal = m.optLong("retiredTotal", 0L),
                        ready = m.optBoolean("ready", false),
                        readiness = m.optInt("readyState", 2),
                        kernel = m.optBoolean("kernel", false),
                        devicetree = m.optBoolean("devicetree", false),
                        rootfs = m.optBoolean("rootfs", false),
                        work = m.optBoolean("work", false),
                        detail = m.optString("detail", ""),
                    ),
                )
            }
        }
    }

    fun runningMachineId(): String =
        json(NativeEngine.nativeMachinesJson(handle)).optString("runningId", "")

    fun createMachine(name: String, values: BooleanArray): BindResult =
        bind(NativeEngine.nativeMachineCreate(handle, name, values))

    fun renameMachine(id: String, name: String): BindResult =
        bind(NativeEngine.nativeMachineRename(handle, id, name))

    fun deleteMachine(id: String): BindResult =
        bind(NativeEngine.nativeMachineDelete(handle, id))

    fun options(): OptionsCatalog {
        val o = json(NativeEngine.nativeOptionsJson(handle))
        val groups = buildList {
            val array = o.optJSONArray("groups") ?: return@buildList
            for (i in 0 until array.length()) {
                val g = array.optJSONObject(i) ?: continue
                add(
                    OptionGroup(
                        title = g.optString("title", ""),
                        note = g.optString("note", ""),
                    ),
                )
            }
        }
        val rows = buildList {
            val array = o.optJSONArray("options") ?: return@buildList
            for (i in 0 until array.length()) {
                val r = array.optJSONObject(i) ?: continue
                add(
                    OptionRow(
                        name = r.optString("name", ""),
                        title = r.optString("title", ""),
                        detail = r.optString("detail", ""),
                        default = r.optBoolean("default", false),
                        group = r.optInt("group", 0),
                        implemented = r.optBoolean("implemented", true),
                    ),
                )
            }
        }
        return OptionsCatalog(groups, rows)
    }

    fun machineValues(id: String): BooleanArray {
        val o = json(NativeEngine.nativeMachineOptionsJson(handle, id))
        val array = o.optJSONArray("values") ?: return BooleanArray(0)
        return BooleanArray(array.length()) { array.optBoolean(it, false) }
    }

    fun setMachineValues(id: String, values: BooleanArray): BindResult =
        bind(NativeEngine.nativeMachineSetOptions(handle, id, values))

    // ------------------------------------------------------------- running --

    fun start(id: String?): BindResult =
        bind(NativeEngine.nativeStart(handle, id))

    fun stop(mode: Int) = NativeEngine.nativeStop(handle, mode)

    fun setPaused(paused: Boolean) = NativeEngine.nativeSetPaused(handle, paused)

    fun setInstructionCap(cap: Long) =
        NativeEngine.nativeSetInstructionCap(handle, cap)

    fun status(): EngineStatus {
        val o = json(NativeEngine.nativeStatusJson(handle))
        return EngineStatus(
            phase = o.optString("phase", "idle"),
            phaseCode = o.optInt("phaseCode", 0),
            running = o.optBoolean("running", false),
            paused = o.optBoolean("paused", false),
            machineReady = o.optBoolean("machineReady", false),
            preparing = o.optBoolean("preparing", false),
            instanceId = o.optString("instanceId", ""),
            status = o.optString("status", "idle"),
            mode = o.optString("mode", ""),
            note = o.optString("note", ""),
            retired = o.optLong("retired", 0L),
            rate = o.optDouble("rate", 0.0),
            fps = o.optDouble("fps", 0.0),
            prepareDone = o.optLong("prepareDone", 0L),
            prepareTotal = o.optLong("prepareTotal", 0L),
            usb = o.optBoolean("usb", false),
            audio = o.optBoolean("audio", false),
            consolePending = o.optLong("consolePending", 0L),
        )
    }

    fun consoleDrain(): String =
        NativeEngine.nativeConsoleDrain(handle)

    fun setSurface(surface: Surface?) =
        NativeEngine.nativeSetSurface(handle, surface)

    fun touch(x: Double, y: Double, width: Double, height: Double, phase: Int): Boolean =
        NativeEngine.nativeTouch(handle, x, y, width, height, phase)

    fun button(button: Int, pressed: Boolean): Boolean =
        NativeEngine.nativeButton(handle, button, pressed)

    fun buttonPressed(button: Int): Boolean =
        NativeEngine.nativeButtonPressed(handle, button)

    // ------------------------------------------------------------ snapshots --

    fun snapshots(id: String): List<SnapshotInfo> {
        val o = json(NativeEngine.nativeSnapshotsJson(handle, id))
        val array = o.optJSONArray("snapshots") ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val s = array.optJSONObject(i) ?: continue
                add(
                    SnapshotInfo(
                        id = s.optString("id", ""),
                        createdUnix = s.optLong("created", 0L),
                        retired = s.optLong("retired", 0L),
                        bytes = s.optLong("bytes", 0L),
                        complete = s.optBoolean("complete", false),
                        directoryBytes = s.optLong("directoryBytes", 0L),
                    ),
                )
            }
        }
    }

    fun takeSnapshot(id: String): BindResult =
        bind(NativeEngine.nativeSnapshotTake(handle, id))

    fun restoreSnapshot(id: String, snapshotId: String): BindResult =
        bind(NativeEngine.nativeSnapshotRestore(handle, id, snapshotId))

    fun deleteSnapshot(id: String, snapshotId: String): BindResult =
        bind(NativeEngine.nativeSnapshotDelete(handle, id, snapshotId))

    fun snapshotNeedsUnlock(id: String, snapshotId: String): Boolean =
        try {
            NativeEngine.nativeSnapshotNeedsUnlock(handle, id, snapshotId)
        } catch (_: Throwable) {
            false
        }

    fun snapshotsError(id: String): String =
        json(NativeEngine.nativeSnapshotsJson(handle, id)).optString("detail", "")

    // ---------------------------------------------------------------- audio --

    fun renderAudio(buffer: FloatArray, frames: Int): Int =
        try {
            NativeEngine.nativeRenderAudio(handle, buffer, frames)
        } catch (_: Throwable) {
            0
        }

    fun setMicrophone(enabled: Boolean) =
        NativeEngine.nativeSetMicrophone(handle, enabled)

    // ------------------------------------------------------------------ USB --

    fun usbReady(): Boolean =
        try {
            NativeEngine.nativeUsbReady(handle)
        } catch (_: Throwable) {
            false
        }

    fun usbOpen(service: String): BindResult =
        bind(NativeEngine.nativeUsbOpen(handle, service))

    // -------------------------------------------------------------- helpers --

    private fun bind(raw: String): BindResult {
        val o = json(raw)
        return BindResult(
            ok = o.optBoolean("ok", false),
            id = o.optString("id", ""),
            detail = o.optString("detail", ""),
        )
    }

    private fun json(raw: String?): JSONObject =
        try {
            if (raw.isNullOrEmpty()) JSONObject() else JSONObject(raw)
        } catch (_: Throwable) {
            JSONObject()
        }
}
