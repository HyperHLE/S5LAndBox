package com.tayga16.s5landbox.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * The Android counterpart of the iOS package downloader.
 *
 * It only ever moves bytes: the pinned manifest supplies each URL, exact byte
 * count and SHA-256, and a file that fails either check never reaches the
 * builder. Downloads land in a cache directory whose name carries the
 * manifest identity, so one pinned set never reuses another's files.
 */
class GuestPackageDownloader(private val context: Context) {

    data class Progress(
        val packageIndex: Int,
        val packageCount: Int,
        val packageName: String,
        val packageDone: Long,
        val packageTotal: Long,
        val overallDone: Long,
        val overallTotal: Long,
    ) {
        val fraction: Float
            get() = if (overallTotal <= 0L) 0f else
                (overallDone.toDouble() / overallTotal.toDouble())
                    .coerceIn(0.0, 1.0).toFloat()
    }

    fun cacheDirectory(identity: String): File {
        val leaf = identity.take(16).ifEmpty { "unknown" }
        return File(context.cacheDir, "GuestPackages-$leaf")
    }

    /**
     * Download and verify every package into [directory]. Files already
     * present with the exact size and digest are kept. Blocking — call from
     * Dispatchers.IO, which this function also enforces for its own body.
     */
    suspend fun downloadAll(
        packages: List<GuestPackage>,
        identity: String,
        onProgress: (Progress) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        if (packages.isEmpty()) throw IOException("The package manifest is empty.")
        val directory = cacheDirectory(identity)
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("The package cache directory could not be created.")
        }
        val total = packages.sumOf { it.size }
        var overallDone = 0L
        var lastReport = 0L

        for ((index, packageInfo) in packages.withIndex()) {
            val target = File(directory, packageInfo.filename)
            if (!(target.isFile &&
                    target.length() == packageInfo.size &&
                    sha256Of(target) == packageInfo.sha256)
            ) {
                if (target.exists()) target.delete()
                val partial = File(directory, packageInfo.filename + ".partial")
                downloadOne(packageInfo, partial) { done ->
                    val now = System.currentTimeMillis()
                    if (now - lastReport >= 150L || done == packageInfo.size) {
                        lastReport = now
                        onProgress(
                            Progress(
                                packageIndex = index,
                                packageCount = packages.size,
                                packageName = packageInfo.name,
                                packageDone = done,
                                packageTotal = packageInfo.size,
                                overallDone = overallDone + done,
                                overallTotal = total,
                            ),
                        )
                    }
                }
                if (!partial.renameTo(target)) {
                    partial.delete()
                    throw IOException("${packageInfo.name} could not be moved into the cache.")
                }
            }
            overallDone += packageInfo.size
            onProgress(
                Progress(
                    packageIndex = index,
                    packageCount = packages.size,
                    packageName = packageInfo.name,
                    packageDone = packageInfo.size,
                    packageTotal = packageInfo.size,
                    overallDone = overallDone,
                    overallTotal = total,
                ),
            )
        }
        directory
    }

    private fun downloadOne(
        packageInfo: GuestPackage,
        partial: File,
        onBytes: (Long) -> Unit,
    ) {
        val connection = openHttps(packageInfo.url)
        try {
            val expectedLength = connection.contentLengthLong
            if (expectedLength >= 0L && expectedLength != packageInfo.size) {
                throw IOException(
                    "The server offers ${expectedLength} bytes for ${packageInfo.name}, " +
                        "the manifest pins ${packageInfo.size}.",
                )
            }
            val digest = MessageDigest.getInstance("SHA-256")
            var done = 0L
            FileOutputStream(partial).use { output ->
                connection.inputStream.use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        done += read
                        if (done > packageInfo.size) {
                            throw IOException(
                                "${packageInfo.name} is larger than the pinned ${packageInfo.size} bytes.",
                            )
                        }
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                        onBytes(done)
                    }
                }
                output.flush()
                output.fd.sync()
            }
            if (done != packageInfo.size) {
                partial.delete()
                throw IOException(
                    "${packageInfo.name} downloaded $done bytes, the manifest pins ${packageInfo.size}.",
                )
            }
            val actual = hex(digest.digest())
            if (!actual.equals(packageInfo.sha256, ignoreCase = true)) {
                partial.delete()
                throw IOException(
                    "${packageInfo.name} failed its SHA-256 check and was discarded.",
                )
            }
        } catch (failure: Throwable) {
            partial.delete()
            throw failure
        } finally {
            connection.disconnect()
        }
    }

    /** HTTPS only, at most five redirects; the pinned hosts never redirect. */
    private fun openHttps(address: String): HttpURLConnection {
        var current = URL(address)
        repeat(6) {
            if (!current.protocol.equals("https", ignoreCase = true)) {
                throw IOException("Refusing to download from a non-HTTPS address.")
            }
            val connection = current.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 90_000
            connection.readTimeout = 600_000
            connection.setRequestProperty("User-Agent", "S5LBox/1")
            connection.setRequestProperty("Accept", "*/*")
            val code = connection.responseCode
            if (code in 300..399) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location.isNullOrEmpty()) {
                    throw IOException("The package server answered a redirect without a location.")
                }
                current = URL(current, location)
                return@repeat
            }
            if (code != HttpURLConnection.HTTP_OK) {
                connection.disconnect()
                throw IOException("The package server answered HTTP $code for ${current.host}.")
            }
            return connection
        }
        throw IOException("The package server redirected too many times.")
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return hex(digest.digest())
    }

    private fun hex(bytes: ByteArray): String {
        val digits = "0123456789abcdef"
        val out = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val value = bytes[i].toInt() and 0xff
            out[i * 2] = digits[value ushr 4]
            out[i * 2 + 1] = digits[value and 0x0f]
        }
        return String(out)
    }
}
