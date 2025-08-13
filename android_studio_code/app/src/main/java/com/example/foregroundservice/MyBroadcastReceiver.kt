package com.example.foregroundservice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class MyBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        // Handle the broadcast data here
        if (intent?.action == "YOUR_CUSTOM_ACTION") {
            val data = intent.getStringExtra("KEY_DATA")
            // Process the data as needed
        }
    }
}
