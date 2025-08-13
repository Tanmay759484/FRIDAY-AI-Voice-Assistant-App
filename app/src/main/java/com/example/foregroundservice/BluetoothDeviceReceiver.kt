package com.example.foregroundservice

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.util.Log
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.content.getSystemService
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.foregroundservice.SharedData.bluetooth_connection_state

class BluetoothDeviceReceiver(private var callback: NumberCallback) : BroadcastReceiver() {
    //private var conn = con
    override fun onReceive(context: Context?, intent: Intent?) {

        val action = intent?.action
        //Log.d("trackk",action.toString())
        if (BluetoothDevice.ACTION_ACL_CONNECTED == action) {
            // Bluetooth device connected
            Log.d("trackk","bluetooth connected")
            bluetooth_connection_state = true
            //callback.onTTS("bueee")
            // Enable the Bluetooth microphone here
            //enableBluetoothMicrophone(contextt)
        } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED == action) {
            Log.d("trackk","bluetooth disconnected")
            bluetooth_connection_state = false
            // Bluetooth device disconnected
            // You can disable the Bluetooth microphone if needed
        }
    }
    fun enableBluetoothMicrophone(context: Context?) {
        val audioManager = context?.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_IN_CALL // Change the audio mode to in-call
        audioManager.isBluetoothScoOn = true // Enable Bluetooth SCO (audio input/output)
        audioManager.startBluetoothSco() // Start Bluetooth SCO connection
        //callback.onTTS("hiii")
    }

}
