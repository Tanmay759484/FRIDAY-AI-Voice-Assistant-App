package com.example.foregroundservice

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.*
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import java.lang.ref.WeakReference
import java.util.*
import kotlin.collections.ArrayList
import kotlin.concurrent.thread
import android.media.AudioManager

import android.os.Build
import android.os.Messenger


/**
 * Read only purpose
 * TODO delete this later
 */

open class BGService : Service(), RecognitionListener {
    companion object {
        var MSG_RECOGNIZER_START_LISTENING = 1
        val MSG_RECOGNIZER_CANCEL = 2
        private var speech: SpeechRecognizer? = null
        private var recognizerIntent: Intent? = null


    }

    protected var mAudioManager: AudioManager? = null
    protected var mIsListening = false

    /**
     * Speech related vars
     */

    private var counter: Int = 0
    private var textListCounter = 0
    private var listCounter = ArrayList<Int>()
    private var twoSecLap: Long = System.currentTimeMillis() / 1000 - 2 // two seconds before

    private fun resetSpeechRecognizer() {
        if (speech != null) speech!!.destroy()
        speech = SpeechRecognizer.createSpeechRecognizer(this)
        Log.i(
            "test",
            "isRecognitionAvailable: " + (speech == null).toString() + " -- " + SpeechRecognizer.isRecognitionAvailable(
                this
            )
        )
        if (SpeechRecognizer.isRecognitionAvailable(this)) speech!!.setRecognitionListener(this)
    }
    fun setRecogniserIntent() {

        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        recognizerIntent!!.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        recognizerIntent!!.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        recognizerIntent!!.putExtra(
            RecognizerIntent.EXTRA_CALLING_PACKAGE,
            applicationContext.packageName
        )
        recognizerIntent!!.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        recognizerIntent!!.putExtra(
            RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
            100
        )
        recognizerIntent!!.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

    }

    val CHANNEL_ID = "bg_notification"
    val CHANNEL_NAME = "ServiceNotification"
    val NOTIFICATION_ID = 0
    private lateinit var notificationManager: NotificationManager
    // Notification channel for android > 8

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                lightColor = Color.BLUE
                enableLights(true)
            }

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)

        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("test", "onCreate()")
        resetSpeechRecognizer()
        setRecogniserIntent()
        speech!!.startListening(recognizerIntent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        //call only once
        createNotificationChannel()

        val clickIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = TaskStackBuilder.create(this).run {
            addNextIntentWithParentStack(clickIntent)
            getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Notification Title")
            .setContentText("Notification Description")
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = NotificationManagerCompat.from(this)

        notificationManager.notify(NOTIFICATION_ID, notification)

        mAudioManager = this.getSystemService(Context.AUDIO_SERVICE) as AudioManager


//        thread {
//            while (true){
//                Thread.sleep(1000)
//                Log.d("test","Service is running....")
//            }
//        }
        return START_STICKY
    }

    protected val mServerMessenger = Messenger(IncomingHandler(this))

    protected class IncomingHandler internal constructor(target: BGService) : Handler() {
        private val mtarget: WeakReference<BGService> = WeakReference<BGService>(target)
        private fun resetSpeechRecognizer() {
            if (speech != null) {
                speech!!.stopListening()
                speech!!.cancel()
                speech!!.destroy()
            }
            speech = SpeechRecognizer.createSpeechRecognizer(mtarget.get()!!.applicationContext)
            Log.i(
                "test",
                "isRecognitionAvailable in service: " + (speech == null).toString() + " -- " + SpeechRecognizer.isRecognitionAvailable(
                    mtarget.get()!!.applicationContext
                )
            )
            if (SpeechRecognizer.isRecognitionAvailable(mtarget.get()!!.applicationContext)) speech!!.setRecognitionListener(mtarget.get())
        }
        fun setRecogniserIntent() {
            recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            recognizerIntent!!.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            recognizerIntent!!.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            recognizerIntent!!.putExtra(
                RecognizerIntent.EXTRA_CALLING_PACKAGE,
                mtarget.get()!!.applicationContext.packageName
            )
            Log.d("test", mtarget.get()!!.applicationContext.packageName)
            recognizerIntent!!.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            recognizerIntent!!.putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                100
            )
            recognizerIntent!!.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)



        }
        override fun handleMessage(msg: Message) {
            val target: BGService? = mtarget.get()
            when (msg.what) {
                MSG_RECOGNIZER_START_LISTENING -> {
                    // turn off beep sound

//                    target!!.mAudioManager!!.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                    target!!.mAudioManager!!.adjustStreamVolume(
                        AudioManager.STREAM_SYSTEM,
                        AudioManager.ADJUST_MUTE,
                        0
                    )
                    Log.d("test", "heyya")
                    if (!target.mIsListening) {
                        Log.d("test", "heyyb")
                        resetSpeechRecognizer()
                        setRecogniserIntent()
                        speech!!.startListening(recognizerIntent)
                        target.mIsListening = true
                        //Log.d(TAG, "message start listening"); //$NON-NLS-1$
                    }
                }
                MSG_RECOGNIZER_CANCEL -> {
                    speech!!.cancel()
                    target!!.mIsListening = false
                }
            }
        }

    }

    override fun onBind(p0: Intent?): IBinder? {
        return mServerMessenger.binder
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("test", "Service stopped")
        if (speech != null) {
            speech!!.stopListening()
            speech!!.cancel()
            speech!!.destroy()
        }
        speech = null
    }

    override fun onReadyForSpeech(p0: Bundle?) {

    }

    override fun onBeginningOfSpeech() {
        twoSecLap = System.currentTimeMillis() / 1000 - 2 // two seconds before speaking

    }

    override fun onRmsChanged(p0: Float) {
    }

    override fun onBufferReceived(p0: ByteArray?) {
    }

    override fun onEndOfSpeech() {
        textListCounter = 0
        counter = 0
        Log.d("test", "onEndofSpeech()")
//        resetSpeechRecognizer()
        mIsListening = false
        val msg = Message()
        msg.what = MSG_RECOGNIZER_START_LISTENING
        mServerMessenger.send(msg)
//        if(speech != null){
//            Log.d("hey","speech not null")
////            speech!!.stopListening()
////            speech!!.cancel()
//            speech!!.destroy()
////            resetSpeechRecognizer()
//            setRecogniserIntent()
//            speech!!.startListening(recognizerIntent)
//        }
    }

    override fun onError(p0: Int) {
        Log.d("test","error " + getErrorText(p0))
        speech!!.stopListening()
        speech!!.cancel()
        speech!!.destroy()
        resetSpeechRecognizer()
        mIsListening = false
        val msg = Message()
        msg.what = MSG_RECOGNIZER_START_LISTENING
        mServerMessenger.send(msg)
    }

    fun getErrorText(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No match"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RecognitionService busy"
            SpeechRecognizer.ERROR_SERVER -> "error from server"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
            else -> "Didn't understand, please try again."
        }
    }

    override fun onResults(p0: Bundle?) {
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val result = partialResults!!.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)!![0]
        val trimmed = result.trim {
            it <= ' '
        }
        val partialResultSize =
            if (trimmed.isEmpty()) 0 else trimmed.split("\\s+".toRegex()).toTypedArray().size

        Log.d(
            "partial",
            partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)!![0]
        )
        Log.d("partial", "$partialResultSize $textListCounter")

        counter = partialResultSize - textListCounter
        if (counter > 0) {
            calSpeechRate(counter)
//            Log.i("test", "onPartialResults before -> "  + counter)
            Log.d(
                "partial in ",
                partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)!![0]
            )
            textListCounter = partialResultSize
            Log.i("partial", "onPartialResults after -> " + counter)

        }
    }

    private fun calSpeechRate(counter: Int) {
        listCounter.add(counter)

        val curTimeInSec = System.currentTimeMillis() / 1000
//        Log.d("partial rate", "$twoSecLap $curTimeInSec")

        val timeGap = 1L
        val highWordRate = 2
        val lowWordRate = 2

        if (curTimeInSec - twoSecLap >= timeGap) {
            //two seconds covered, time to check next 2 seconds word rate
            when {
                listCounter.size > highWordRate -> {
                    //user speaking fastly
//                    rateTV.text = " ${rateTV.text} \n You are very fast"
                }
                listCounter.size < lowWordRate -> {
//                    rateTV.text = " ${rateTV.text} \n You are quite slow"
                }
                else -> {
//                    rateTV.text = " ${rateTV.text} \n You are moderate"
                }
            }
            //default state
            twoSecLap = curTimeInSec
            listCounter = ArrayList()
        }
    }

    override fun onEvent(p0: Int, p1: Bundle?) {
    }
}