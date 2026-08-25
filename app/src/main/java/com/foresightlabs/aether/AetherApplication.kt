package com.foresightlabs.aether

import android.app.Application
import com.foresightlabs.aether.data.telegram.TelegramClient

class AetherApplication : Application() {
    lateinit var telegram: TelegramClient
        private set

    override fun onCreate() {
        super.onCreate()
        telegram = TelegramClient(this)
        telegram.start()
    }
}
