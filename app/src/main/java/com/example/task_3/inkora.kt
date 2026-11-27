package com.example.task_3

import android.app.Application
import com.cloudinary.android.MediaManager

class inkora : Application() {
    override fun onCreate() {
        super.onCreate()
        MediaManager.init(this, mapOf("cloud_name" to CloudinaryHelper.CLOUD_NAME))
    }
}
