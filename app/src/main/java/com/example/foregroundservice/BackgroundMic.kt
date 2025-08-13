package com.example.foregroundservice

import android.app.Service
import android.content.Intent
import android.media.MediaRecorder
import android.os.IBinder
import android.os.Environment
import com.example.foregroundservice.AIname.AudioClassificationHelper
import java.io.IOException

class BackgroundMic : Service() {

    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private lateinit var audioHelper : AudioClassificationHelper


    override fun onCreate() {
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        audioHelper = AudioClassificationHelper(applicationContext)
        return START_STICKY
    }

    private fun startRecording() {
        val fileName = "${Environment.getExternalStorageDirectory().absolutePath}/audio_record.3gp"
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            setOutputFile(fileName)

            try {
                prepare()
            } catch (e: IOException) {
                e.printStackTrace()
            }

            start()
        }

        isRecording = true
    }

    private fun stopRecording() {
        mediaRecorder?.apply {
            stop()
            release()
        }

        isRecording = false
    }

    override fun onDestroy() {
        super.onDestroy()
        //stopRecording()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
