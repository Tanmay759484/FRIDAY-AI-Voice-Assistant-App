package com.example.foregroundservice

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.animation.AlphaAnimation
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity2 : AppCompatActivity() {

    private lateinit var liveDataTextView : TextView
    private lateinit var liveDataTextView1 : TextView
    private lateinit var liveDataTextView2 : TextView
    private var updateJob : Job? = null

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("trackk", intent?.action.toString())
            if(intent?.action == "com.example.yourapp.BROADCAST_ACTION_speechResult"){
                val message = intent.getStringExtra("message")
                liveDataTextView.text = message
                Log.d("TAG" , "In sendBroadcastMessage2 second time")
            }
            if(intent?.action == "com.example.yourapp.BROADCAST_ACTION_wit_recipient_name"){
                val message = intent.getStringExtra("message")
                liveDataTextView2.text = message
                Log.d("TAG" , "In sendBroadcastMessage2 second time")
            }
            if(intent?.action == "com.example.yourapp.BROADCAST_ACTION_device_recipient_name"){
                val message = intent.getStringExtra("message")
                liveDataTextView1.text = message
                Log.d("TAG" , "In sendBroadcastMessage2 second time")
            }

        }

    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main2)

        val filter = IntentFilter()
        filter.addAction("com.example.yourapp.BROADCAST_ACTION_speechResult")
        filter.addAction("com.example.yourapp.BROADCAST_ACTION_wit_recipient_name")
        filter.addAction("com.example.yourapp.BROADCAST_ACTION_device_recipient_name")
        registerReceiver(broadcastReceiver,filter, RECEIVER_NOT_EXPORTED)

        liveDataTextView= findViewById(R.id.liveDataTextView)
        liveDataTextView2= findViewById(R.id.liveDataTextView2)
        liveDataTextView1= findViewById(R.id.liveDataTextView1)

        //liveDataTextView.text = "result"
        //liveDataTextView2.text = "2"
        //liveDataTextView1.text = "1"


        if (foregroundServiceRunning()) {
            Log.d("trackk","on resume mainactivity")
            sendBroadcastMessage("SR & SCO ON")
        }
        val welcomeTextView: TextView = findViewById(R.id.welcomeTextView)
        val aiNameTextView: TextView = findViewById(R.id.aiNameTextView)
        val subtitleTextView: TextView = findViewById(R.id.subtitleTextView)

        // Create animation
        val fadeIn = AlphaAnimation(0.0f, 1.0f)
        fadeIn.duration = 100

        // Start animations
        welcomeTextView.startAnimation(fadeIn)
        welcomeTextView.alpha = 1.0f

        aiNameTextView.startAnimation(fadeIn)
        aiNameTextView.alpha = 1.0f

        subtitleTextView.startAnimation(fadeIn)
        subtitleTextView.alpha = 1.0f

        //startUpdatingParameter()

        //onBackPressed()
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
        sendBroadcast(intent)
    }

    @Deprecated("This method has been deprecated in favor of using the\n      {@link OnBackPressedDispatcher} via {@link #getOnBackPressedDispatcher()}.\n      The OnBackPressedDispatcher controls how back button events are dispatched\n      to one or more {@link OnBackPressedCallback} objects.")
    override fun onBackPressed() {
        super.onBackPressed()
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(broadcastReceiver)
    }

    private fun startUpdatingParameter() {
        updateJob = CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                withContext(Dispatchers.Main) {
                    liveDataTextView.text = SharedData.speechResult
                    Log.d("trackk" +" hey",SharedData.speechResult)
                }
                delay(500) // Update every second
            }
        }
    }
}
