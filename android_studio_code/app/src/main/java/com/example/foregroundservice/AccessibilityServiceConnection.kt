package com.example.foregroundservice

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder

class AccessibilityServiceConnection : ServiceConnection {
    private var accessibilityService: MyAccessibilityService? = null

    fun getAccessibilityService(): MyAccessibilityService? {
        return accessibilityService
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        //val binder = service as MyAccessibilityService.MyAccessibilityServiceBinder
        //accessibilityService = binder.getService()
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        accessibilityService = null
    }
}
