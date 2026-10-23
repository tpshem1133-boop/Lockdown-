package com.lockdown.strictblocker

import android.app.Application
import com.lockdown.strictblocker.data.BlockRepository
import com.lockdown.strictblocker.engine.BlockEngine

class LockdownApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Warm the in-memory block-list snapshot for the engine services.
        BlockRepository.get(this)
        BlockEngine.refreshLaunchableCache(this)
    }
}
