package com.tayga16.s5landbox.data

/**
 * Plain records of what the native engine reported. Nothing here caches guest
 * state: every field is a copy made at the moment it was asked for, exactly like
 * the JSON it came from.
 */

data class Machine(
    val id: String,
    val name: String,
    val createdUnix: Long,
    val lastOpenedUnix: Long,
    val retiredTotal: Long,
    val ready: Boolean,
    val readiness: Int,
    val kernel: Boolean,
    val devicetree: Boolean,
    val rootfs: Boolean,
    val work: Boolean,
    val detail: String,
) {
    /** 0 = ready, 1 = needs the writable image, 2 = firmware incomplete. */
    val needsProvision: Boolean get() = readiness == 1
    val firmwareIncomplete: Boolean get() = readiness == 2
}

data class FirmwareState(
    val ok: Boolean,
    val kernelPresent: Boolean,
    val kernelSize: Long,
    val devicetreePresent: Boolean,
    val devicetreeSize: Long,
    val rootfsPresent: Boolean,
    val rootfsSize: Long,
    val importOk: Boolean,
    val importActive: Boolean,
    val source: String,
    val lastReport: String,
)

data class EngineStatus(
    val phase: String,
    val phaseCode: Int,
    val running: Boolean,
    val paused: Boolean,
    val machineReady: Boolean,
    val preparing: Boolean,
    val instanceId: String,
    val status: String,
    val mode: String,
    val note: String,
    val retired: Long,
    val rate: Double,
    val fps: Double,
    val prepareDone: Long,
    val prepareTotal: Long,
    val usb: Boolean,
    val audio: Boolean,
    val consolePending: Long,
) {
    val idle: Boolean get() = phase == "idle"
    val stopping: Boolean get() = phase == "stopping"
}

data class SnapshotInfo(
    val id: String,
    val createdUnix: Long,
    val retired: Long,
    val bytes: Long,
    val complete: Boolean,
    val directoryBytes: Long,
)

data class OptionRow(
    val name: String,
    val title: String,
    val detail: String,
    val default: Boolean,
    val group: Int,
    val implemented: Boolean,
)

data class OptionGroup(val title: String, val note: String)

data class OptionsCatalog(
    val groups: List<OptionGroup>,
    val options: List<OptionRow>,
)

data class BindResult(val ok: Boolean, val id: String, val detail: String)

// --------------------------------------------------------------- packages --

data class GuestPackage(
    val name: String,
    val version: String,
    val filename: String,
    val url: String,
    val size: Long,
    val sha256: String,
    val roles: Int,
) {
    val installs: Boolean get() = roles and Roles.INSTALL != 0
    val appsync: Boolean get() = roles and Roles.APPSYNC != 0
}

object Roles {
    const val INSTALL = 1
    const val FOUNDATION = 2
    const val APT_CACHE_TOOL = 4
    const val APPSYNC = 8
}

data class GuestManifest(
    val ok: Boolean,
    val busy: Boolean,
    val identity: String,
    val machineId: String,
    val machineOk: Boolean,
    val installed: Boolean,
    val workImageBytes: Long,
    val snapshots: Long,
    val totalBytes: Long,
    val appsyncBytes: Long,
    val packages: List<GuestPackage>,
    val appsync: GuestPackage?,
) {
    /** The base set the guest needs, in manifest order. */
    val installSet: List<GuestPackage> get() = packages
}

object InstallStatus {
    const val OK = 0
    const val ARGUMENT = 1
    const val TRANSACTION = 2
    const val SNAPSHOTS = 3
    const val STORAGE_NOT_CLEAN = 4
    const val PACKAGES = 5
    const val MANIFEST = 6
    const val PATH = 7
    const val ROOTFS = 8
    const val PUBLISH = 9
}

data class InstallResult(
    val ok: Boolean,
    val status: Int,
    val statusText: String,
    val detail: String,
    val alreadyInstalled: Boolean,
    val forcedPowerOff: Boolean,
    val storageUpgraded: Boolean,
    val cydiaPrivilegesRepaired: Boolean,
    val cydiaSourcesAdded: Boolean,
    val aptTrustInstalled: Boolean,
    val aptVerifierStaged: Boolean,
    val cydiaCacheStaged: Boolean,
    val filesystemRepaired: Boolean,
    val snapshots: Long,
) {
    val needsPackages: Boolean get() = !ok && status == InstallStatus.ARGUMENT
}

data class InstallProgress(
    val active: Boolean,
    val phase: Int,
    val done: Long,
    val total: Long,
) {
    val fraction: Float
        get() = if (total <= 0L) 0f else (done.toDouble() / total.toDouble())
            .coerceIn(0.0, 1.0).toFloat()
}

object InstallPhase {
    private val names = listOf(
        "recovering",
        "planning",
        "staging",
        "copying",
        "publishing",
        "complete",
    )

    fun name(index: Int): String = names.getOrElse(index) { "working" }
}


data class ImportOutcome(val ok: Boolean, val detail: String)

data class ImportProgress(
    val active: Boolean,
    val artefact: Int,
    val stage: Int,
    val done: Long,
    val total: Long,
) {
    val fraction: Float
        get() = if (total <= 0L) 0f else (done.toDouble() / total.toDouble())
            .coerceIn(0.0, 1.0).toFloat()
}

/** App-side button identifiers; these match VMButtonQueue.h by value. */
object Buttons {
    const val HOME = 0
    const val POWER = 1
    const val VOLUME_UP = 2
    const val VOLUME_DOWN = 3
    const val RINGER_SILENT = 4
}

object TouchPhase {
    const val BEGAN = 0
    const val MOVED = 1
    const val ENDED = 2
    const val CANCELLED = 3
}

object StopMode {
    const val PLAIN = 0
    const val CHECKPOINT = 1
    const val POWER_OFF = 2
}

object ImportStage {
    private val names = listOf(
        "opening the archive",
        "reading the manifest",
        "locating payloads",
        "extracting",
        "decrypting",
        "decompressing",
        "expanding the root filesystem",
        "verifying",
        "done",
    )

    fun name(index: Int): String = names.getOrElse(index) { "working" }
}
