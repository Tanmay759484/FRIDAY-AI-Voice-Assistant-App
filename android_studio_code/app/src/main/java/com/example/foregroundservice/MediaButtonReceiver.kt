package com.example.foregroundservice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.KeyEvent

class MediaButtonReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d("trackk","media button - " + intent?.action.toString())
        if (intent?.action == Intent.ACTION_MEDIA_BUTTON) {
            Log.d("trackk","button click blueeee")
            val event = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
            if (event != null) {
                // Handle the key event
                handleMediaButtonEvent(event.keyCode)
            }
        }
    }

    private fun handleMediaButtonEvent(keyCode: Int) {
        // Handle the specific media button event (e.g., play, pause, next, previous)
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                // Handle play or play/pause button
                Log.d("trackk","10000")
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                // Handle pause button
                Log.d("trackk","10000")
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                // Handle next button
                Log.d("trackk","10000")
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                // Handle previous button
                Log.d("trackk","10000")
            }
            // Add more cases for other media button events as needed
        }
    }
}
