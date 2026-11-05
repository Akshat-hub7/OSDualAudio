package com.dualaudio.streamer

import android.app.Application
import android.util.Log
import com.dualaudio.streamer.service.NotificationHelper

class DualAudioApp : Application() {

    companion object {
        private const val TAG = "DualAudioApp"
    }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannel(this)
        Log.i(TAG, "DualCast Audio application initialized")
    }
}

