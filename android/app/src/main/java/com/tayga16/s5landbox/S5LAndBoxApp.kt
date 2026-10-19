package com.tayga16.s5landbox

import android.app.Application
import com.tayga16.s5landbox.data.EngineRepository
import com.tayga16.s5landbox.nativebridge.NativeEngine

/**
 * One process, one engine handle, one running machine — the same lifetime rule
 * the iOS app has. Storage lives in the app's external files directory so the
 * firmware and machine images are reachable over USB without any permission.
 */
class S5LAndBoxApp : Application() {

    lateinit var repository: EngineRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val root = getExternalFilesDir(null) ?: filesDir
        if (!root.exists()) root.mkdirs()
        repository = EngineRepository(NativeEngine.nativeCreate(root.absolutePath))
    }
}
