package com.example.foregroundservice

import android.app.ActivityManager
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.foregroundservice.R

class FloatingActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_floating)
        // Load the FloatingFragment into the activity
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, FloatingFragment())
            .commit()
    }

    override fun onResume() {
        super.onResume()
        if (foregroundServiceRunning()) {
            Log.d("trackk","on resume mainactivity")
            sendBroadcastMessage("SR & SCO ON")
        }
        val filter = Intent(this, MainActivity::class.java)
        startActivity(filter) // if the application no yet started then it will start that

    }

    fun foregroundServiceRunning(): Boolean {
        val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        for (service in activityManager.getRunningServices(Int.MAX_VALUE)) {
            if (ForegroundService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }

    fun sendBroadcastMessage(message: String) {
        Log.d("trackk","in floateee speech")
        // Create an Intent with the custom action and message
        val intent = Intent("com.example.yourapp.BROADCAST_ACTION_C")
        intent.putExtra("message", message)

        // Send the broadcast
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }
}