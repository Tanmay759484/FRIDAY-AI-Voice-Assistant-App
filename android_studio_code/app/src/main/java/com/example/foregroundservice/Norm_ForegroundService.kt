package com.example.foregroundservice


import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Insets
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioManager.OnAudioFocusChangeListener
import android.media.AudioRecord
import android.media.Image
import android.media.ImageReader
import android.media.ImageReader.OnImageAvailableListener
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcelable
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.KeyEvent
import android.view.OrientationEventListener
import android.view.WindowInsets
import android.view.WindowManager
import androidx.annotation.NonNull
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.example.foregroundservice.AIname.AudioClassificationHelper
import com.example.foregroundservice.SharedData.bluetooth_connection_state
import com.example.foregroundservice.SharedData.stt_state
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.FirebaseStorage
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit


open class Norm_ForegroundService : Service(),TextToSpeech.OnInitListener {
    companion object {
        lateinit var directory: String
        lateinit var model_dir: String
        lateinit var localFile: File
        var amt_change = false
        val token = "C645UTEVRZPACJQAA6DFUNUAGJGW2O7K"
        val sessionId = "your_unique_123session_id"
        var utteranceId = ""
        var isha : Int = 0
        val TAG: String = "trackk"
        var mHandler: Handler? = null
        var mImageReader: ImageReader? = null
        private var mMediaProjection: MediaProjection? = null
        private var mStoreDir: String? = null
        private lateinit var mDisplay: Display
        private var mVirtualDisplay: VirtualDisplay? = null
        private var mDensity = 0
        private var mWidth = 0
        private var mHeight = 0
        private var mRotation = 0
        private var mOrientationChangeCallback: OrientationChangeCallback? = null
        var IMAGES_PRODUCED = 0
        private var t :Float = 0F
        private var m :Float = 0F
        private lateinit var audioManager : AudioManager
        private lateinit var mBluetoothAdapter : BluetoothAdapter
        private  lateinit var mAudioManager : AudioManager
        private lateinit var mainActivity: MainActivity
        private val mLock = Any()
        private lateinit var latch: CountDownLatch
        private lateinit var screenSize : Pair<Int, Int>
        private lateinit var sharedPreferencesHelper: SharedPreferencesHelper
    }

    private lateinit var networkHelper: NetworkHelper
    private lateinit var auth: FirebaseAuth
    private var isBluetoothReceiverRegistered = false
    private var bluetoothDeviceReceiver: BluetoothDeviceReceiver? = null
    val witAiHelper: WitAiHelper = WitAiHelper(token)
    data class ResultData(val value : String)
    var recentList_in_input : MutableList<String> = mutableListOf()
    private val channel = Channel<ResultData>(Channel.UNLIMITED)
    private val firestoreHelper = FirestoreHelper()
    private var contactList_only : List<Contact> = listOf()
    private var contactList_transaction : List<Contact> = listOf()
    private var add_transaction : List<Contact> = listOf()
    private var mor_option_activated = false
    private var amt : Double = 0.0
    private var recent_amt : Double = 0.0
    private var recent_contact_list_sort_by_voice : List<Contact> = listOf()
    private var recent_payment_number : String = ""
    private var recent_payment_name : String = ""
    private var recent_wit_name : String = ""
    private var recent_processedWit_name : String = ""
    private var audioFocusRequest: AudioFocusRequest? = null
    private lateinit var databaseRef: DatabaseReference
    private var modelUpdateListener: ValueEventListener? = null
    private var storage = FirebaseStorage.getInstance()
    private var db = Firebase.firestore
    private var mBluetoothHeadset : BluetoothHeadset? = null
    private var mConnectedHeadset : BluetoothDevice? = null
    private var longDurationThread : Thread? = null
    private var audiohelp_start_or_not = true
    /**
    private val mediaButtonReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
    if (intent?.action == Intent.ACTION_MEDIA_BUTTON) {
    Log.d(TAG,"10000")
    val event = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
    if (event != null && event.action == KeyEvent.ACTION_DOWN) {
    // Handle the key event
    handleMediaButtonEvent(event.keyCode)
    }
    }
    }
    }
     **/

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
    /**
    var scoReceiver: BroadcastReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent) {
    val scoState = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1)
    if (scoState == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
    // Bluetooth SCO audio is connected
    } else if (scoState == AudioManager.SCO_AUDIO_STATE_DISCONNECTED) {
    // Bluetooth SCO audio is disconnected
    }
    }
    }
     **/


    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("trackk", intent?.action.toString())
            if(intent?.action == "com.example.yourapp.BROADCAST_ACTION"){
                val message = intent.getStringExtra("message")
                Log.d("TAG" , "In sendBroadcastMessage2 second time")
                Log.d("trackk",message.toString() + System.currentTimeMillis())
                if(bluetooth_connection_state){
                    Log.d(TAG,"double tap done")
                    connectBluetooth()
                    Thread.sleep(400)
                    if(!stt_state){
                        MainActivity.stt.startSpeechRecognition(callbackInterface)
                    }
                } else {
                    if(!stt_state){
                        MainActivity.stt.startSpeechRecognition(callbackInterface)
                    }
                }
            }
            if(intent?.action == "com.example.yourapp.BROADCAST_ACTION_C"){
                val message = intent.getStringExtra("message")
                //audioHelper.stopAudioClassification()
                if(bluetooth_connection_state){
                    Log.d(TAG,"double tap done")
                    connectBluetooth()
                    Thread.sleep(400)
                    if(!stt_state){
                        MainActivity.stt.startSpeechRecognition(callbackInterface)
                    }
                } else {
                    if(!stt_state){
                        MainActivity.stt.startSpeechRecognition(callbackInterface)
                    }
                }
            }
            if(intent?.action == "com.example.yourapp.BROADCAST_ACTION_model_listener"){
                val message = intent.getStringExtra("message")
                if(message == "add_listener"){
                    addModelStatListener()
                } else {
                    removeModelStatListener()
                }
            }
            if(intent?.action == "com.example.yourapp.BROADCAST_ACTION_start_audioclassification"){
                val message = intent.getStringExtra("message")
                if (sharedPreferencesHelper.getBoolean("Wake up voice activation")){
                    if(localFile.exists()){
                        if (audiohelp_start_or_not){
                            audioHelper.initClassifier()
                        } else {
                            Log.d(TAG, "model download in progress")
                        }
                    }
                }
            }
            if(intent?.action == "com.example.yourapp.BROADCAST_ACTION_stop_audioclassification"){
                val message = intent.getStringExtra("message")
                audioHelper.stopAudioClassification()
            }
        }

    }

    private var log : String = ""
    private lateinit var connection: AccessibilityServiceConnection
    private lateinit var myAccessibilityService : MyAccessibilityService
    private lateinit var tesseractOCR : TesseractOCR
    lateinit var wakeLock: WakeLock
    private var isDeviceLocked = false
    private var unlock_varriable = true
    var pass: Boolean = false
    private lateinit var kl : KeyguardManager.KeyguardLock
    private lateinit var km : KeyguardManager
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var textToSpeech : TextToSpeech
    private lateinit var audioRecorder: AudioRecord
    private var isaudioRecorderRegistered = false
    private val sampleRate = 44100 // Sample rate in Hz
    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )


    private val RESULT_CODE = "RESULT_CODE"
    private val DATA = "DATA"
    private val ACTION = "ACTION"
    private val START = "START"
    private val STOP = "STOP"
    private val SCREENCAP_NAME = "screencap"

    @OptIn(DelicateCoroutinesApi::class)
    private fun startIshaKriya(){
        longDurationThread = Thread{
            GlobalScope.launch {
                while (isha == 0) {
                    textToSpeech.speak(" Be ready,    Isha Kriya started", TextToSpeech.QUEUE_FLUSH, null, null)
                    delay(10000)
                    if(isha == 1) break
                    textToSpeech.speak(" Isha Kriya stage one started", TextToSpeech.QUEUE_FLUSH, null, null)
                    delay(600000)
                    if(isha == 1) break
                    textToSpeech.speak("Isha Kriya stage one completed", TextToSpeech.QUEUE_FLUSH, null, null)
                    delay(10000)
                    if(isha == 1) break
                    textToSpeech.speak("Isha Kriya stage two started",TextToSpeech.QUEUE_FLUSH,null,null)
                    delay(60000)
                    if(isha == 1) break
                    textToSpeech.speak("Isha Kriya stage two completed",TextToSpeech.QUEUE_FLUSH,null,null)
                    delay(10000)
                    if(isha == 1) break
                    textToSpeech.speak("Isha Kriya stage three started",TextToSpeech.QUEUE_FLUSH,null,null)
                    delay(300000)
                    if(isha == 1) break
                    textToSpeech.speak("Isha Kriya completed, Thank You",TextToSpeech.QUEUE_FLUSH,null,null)
                    isha = 1

                }
            }
        }
        longDurationThread?.start()
    }

    private fun stopIshaKriya(){
        isha=1
        longDurationThread = null
    }

    @OptIn(DelicateCoroutinesApi::class)
    val callbackInterface = object : NumberCallback {
        override fun onRequeststart(action: String) {
            Thread.sleep(500)
            audioManager.stopBluetoothSco()
            stt_state = false
            // Send a sample request to the Flask API
            Log.d(TAG, "aws action means norm_foreground - ${action}")
            networkHelper.sendRequest(action) { response ->
                if (response != null) {
                    Log.d(TAG, response)
                    // Parse JSON and use the response as needed
                } else {
                    Log.d(TAG, "Failed to get AWS response")
                }
            }
            enqueueResult(action)
            if (sharedPreferencesHelper.getBoolean("Wake up voice activation")){
                if(localFile.exists()){
                    if (audiohelp_start_or_not){
                        audioHelper.initClassifier()
                    } else {
                        Log.d(TAG, "model download in progress")
                    }
                }
            }
        }

        override fun onTTS(string: String) {
            GlobalScope.launch {
                delay(4000) // Replace with a coroutine-friendly delay
                val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

                // Set the audio mode to a suitable mode (e.g., MODE_NORMAL or MODE_IN_COMMUNICATION)
                audioManager.mode = AudioManager.MODE_IN_CALL

                // Enable Bluetooth SCO for audio input
                audioManager.isBluetoothScoOn = true

                // Start Bluetooth SCO connection
                audioManager.startBluetoothSco()

                // Delay to allow SCO connection to establish (adjust as needed)
                delay(500)

                // Notify the user
                textToSpeech.speak("Your Bluetooth device mic is connected", TextToSpeech.QUEUE_FLUSH, null, null)

                // Optional: If you want to play audio through the mobile phone speaker
                //audioManager.isSpeakerphoneOn = true
            }
        }



        override fun onTouchSuccess() {
            //connectBluetooth()
        }
    }

    private val audioBuffer = ShortArray(bufferSize)
    private lateinit var preRecordedNickname: ShortArray
    private lateinit var audioHelper : AudioClassificationHelper

    private val NOTIFICATION_CHANNEL_ID = "sttServiceChannel"

    @SuppressLint("UnspecifiedImmutableFlag")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        val connectedDevices: Set<BluetoothDevice>? = mBluetoothAdapter.bondedDevices
        connectedDevices?.forEach { device ->
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                Log.d(TAG, "bluetooth permission not granted")
            }
            if (device.bluetoothClass.deviceClass == BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES ||
                device.bluetoothClass.deviceClass == BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET) {
                bluetooth_connection_state = true
                Log.d(TAG + "bluetooth_connection_state", "bluetooth_connection_state - true")
            }
        }
        mAudioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (mAudioManager.isBluetoothScoAvailableOffCall)
        {
            Log.d(TAG,"in proxy")
            mBluetoothAdapter.getProfileProxy(this, mHeadsetProfileListener, BluetoothProfile.HEADSET)
        }

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager


        //val lkgmr = IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
        //registerReceiver(scoReceiver, lkgmr)

        //val sdfsd = IntentFilter(Intent.ACTION_MEDIA_BUTTON)
        //registerReceiver(mediaButtonReceiver, sdfsd, RECEIVER_NOT_EXPORTED)


        connection = AccessibilityServiceConnection()
        val intent1 = Intent(this, MyAccessibilityService::class.java)
        bindService(intent1, connection, Context.BIND_AUTO_CREATE)

        // Perform a one-time touch after some delay (adjust the delay as needed)
        //mainActivity = MainActivity()
        km = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        //Log.d("trackk", getSystemService(KEYGUARD_SERVICE).toString())
        kl = km.newKeyguardLock("MyKeyguardLock")
        val filter1 = IntentFilter()
        //filter1.addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
        bluetoothDeviceReceiver = BluetoothDeviceReceiver(callbackInterface)
        filter1.addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        registerReceiver(bluetoothDeviceReceiver, filter1)
        isBluetoothReceiverRegistered = true
        devicePolicyManager = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        //kl = km .newKeyguardLock("MyKeyguardLock");
        textToSpeech = TextToSpeech(this,this)

        Log.d("test","onStart" + intent!!.extras)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Foreground Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }

        val actIntent = Intent(this, MainActivity::class.java)
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this, 0, actIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("STT Service")
            .setContentText("Your voice is recognizing in the background")
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setOngoing(true)  // Mark as ongoing
            .setPriority(NotificationCompat.PRIORITY_HIGH)  // Set high priority to keep it at the top
            .setContentIntent(pendingIntent)
            .build()

// Set FLAG_NO_CLEAR to prevent the notification from being swiped away
        notification.flags = notification.flags or Notification.FLAG_NO_CLEAR

// Start the foreground service with the non-dismissible notification
        startForeground(1, notification)

        //sendUserNotification()
        MainActivity.stt.startSpeechRecognition(callbackInterface)
        //MainActivity.stt.startParallelProcessing()

        /**val recordedAudioFilePath = intent.getShortArrayExtra("recorded_audio_file")
        if (recordedAudioFilePath != null) {
        preRecordedNickname = recordedAudioFilePath
        }**/
        //preRecordedNickname = readAudioFromFile(recordedAudioFilePath)
        //startAudioRecording()
        //audioHelper = AudioClassificationHelper(this)
        //audioHelper.currentModel = AudioClassificationHelper.SPEECH_COMMAND_MODEL
        //audioHelper.initClassifier()



        //val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        //audioManager.mode = AudioManager.MODE_IN_CALL // Change the audio mode to in-call
        /**audioManager.isBluetoothScoOn = true // Enable Bluetooth SCO (audio input/output)
        audioManager.startBluetoothSco() // Start Bluetooth SCO connection
         **/


        myAccessibilityService = MyAccessibilityService()
        //var myA = MyAccessibilityService()
        // start projection
        val resultCode = intent.getIntExtra(RESULT_CODE, Activity.RESULT_CANCELED)
        val data = intent.getParcelableExtra<Intent>(DATA)
        if (data != null) {
            startProjection(resultCode, data)
        }

        //val mediaButtonReceiver = MediaButtonReceiver()

        return START_REDELIVER_INTENT
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }
    @SuppressLint("MissingPermission")
    private fun startAudioRecording() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        audioRecorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        isaudioRecorderRegistered = true

        audioRecorder.startRecording()

        Thread {
            while (true) {
                val bytesRead = audioRecorder.read(audioBuffer, 0, bufferSize)
                // Process the audio buffer, compare with the pre-recorded nickname,
                // and display response if matched
                if (isNicknameDetected(audioBuffer, bytesRead)) {
                    Log.d("trackk","detecteddddddddddd")
                    // Display the response
                    //showResponse("Hello!")
                }
            }
        }.start()
    }

    private fun isNicknameDetected(audioData: ShortArray, bytesRead: Int): Boolean {
        // Calculate the cross-correlation between audioData and preRecordedNickname
        val correlation = calculateCrossCorrelation(audioData, bytesRead, preRecordedNickname)

        // Set a correlation threshold (experiment with a suitable value)
        val correlationThreshold = 0.9

        return correlation >= correlationThreshold
    }

    private fun calculateCrossCorrelation(signal: ShortArray, signalLength: Int, reference: ShortArray): Double {
        var correlationSum = 0.0

        for (i in 0 until signalLength - reference.size) {
            var sum = 0.0
            for (j in reference.indices) {
                sum += signal[i + j].toDouble() * reference[j].toDouble()
            }
            correlationSum += sum / reference.size.toDouble()
        }

        return correlationSum
    }

    private fun readAudioFromFile(filePath: String?): ShortArray {
        val file = File(filePath)
        val inputStream = DataInputStream(FileInputStream(file))

        val audioData = mutableListOf<Short>()
        while (inputStream.available() > 0) {
            audioData.add(inputStream.readShort())
        }

        inputStream.close()
        return audioData.toShortArray()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        audioHelper.stopAudioClassification()
        val serviceIntent = Intent(this, Norm_ForegroundService::class.java)
        stopService(serviceIntent)
        SharedData.mainActivity?.finish()
        Log.d(TAG,"Foreground onTaskRemoved called")
    }

    override fun onDestroy() {

        //unregisterReceiver(mediaButtonReceiver)
        // Clean up the listener when the activity is destroyed
        removeModelStatListener()
        if (isBluetoothReceiverRegistered && bluetoothDeviceReceiver != null) {
            unregisterReceiver(bluetoothDeviceReceiver)
            isBluetoothReceiverRegistered = false
        }
        if(isaudioRecorderRegistered){
            audioRecorder.stop()
            audioRecorder.release()
            isaudioRecorderRegistered = false
        }
        unregisterReceiver(broadcastReceiver)
        unbindService(connection)
        Log.d(TAG,"Foreground ondestroy gone")
        super.onDestroy()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech.setLanguage(Locale("en","IN")) // Set the language to US English or another desired language.
            val voices = textToSpeech.voices
            var i =402
            // 23,92 - good girl-1,100 - good girl - 2,120,134 - slow girl,235,261,370 - good girl - 3,429 - good girl - 3
            Log.d("trackk", voices.size.toString())
            // 33 - good,
            textToSpeech.voice = voices.elementAt(33)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Handle the case where the language is not available or not supported.
            }
            textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    // Speaking started
                }

                override fun onDone(utteranceId: String?) {
                    // Speaking finished
                    releaseAudioFocus()
                    latch.countDown()  // Release the latch
                }

                override fun onError(utteranceId: String?) {
                    // Speaking error
                    releaseAudioFocus()
                    latch.countDown()  // Release the latch
                }
            })
        } else {
            // Handle initialization error.
        }
    }

    fun getStartIntent(context: Context?, resultCode: Int, data: Intent?): Intent {
        val intent = Intent(context, Norm_ForegroundService::class.java)
        intent.putExtra(ACTION, START)
        intent.putExtra(RESULT_CODE, resultCode)
        intent.putExtra(DATA, data)
        Log.d("trackk","for intent - ")
        return intent
    }

    fun getStopIntent(context: Context?): Intent? {
        val intent = Intent(context, Norm_ForegroundService::class.java)
        intent.putExtra(ACTION, STOP)
        return intent
    }

    private fun isStartCommand(intent: Intent): Boolean {
        return (intent.hasExtra(RESULT_CODE) && intent.hasExtra(DATA) && intent.hasExtra(ACTION) && intent.getStringExtra(ACTION) == START)
    }

    private fun isStopCommand(intent: Intent): Boolean {
        return intent.hasExtra(ACTION) && intent.getStringExtra(ACTION) == STOP
    }

    private fun getVirtualDisplayFlags(): Int {
        return DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
    }

    private fun takescreenshot() {
        var fos: FileOutputStream? = null
        var bitmap: Bitmap? = null
        Log.d("trackk", "enter image")
        try {
            mImageReader?.acquireLatestImage().use { image ->
                Log.d("trackk", "enter image1")
                if (image != null) {
                    Log.d("trackk", "enter image2")
                    val planes: Array<Image.Plane> = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val width = image.width
                    val height = image.height
                    val rowPadding = rowStride - pixelStride * width

                    // create bitmap
                    bitmap = Bitmap.createBitmap(
                        width + rowPadding / pixelStride,
                        height,
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap?.copyPixelsFromBuffer(buffer)

                    // crop the bitmap to the actual image size
                    bitmap = Bitmap.createBitmap(bitmap!!, 0, 0, width, height)

                    // Apply grayscale and thresholding
                    val preprocessedBitmap = applyThreshold(toGrayscale(bitmap!!))
                    //val preprocessedBitmap = toGrayscale(bitmap!!)

                    // write bitmap to a file
                    fos = FileOutputStream(mStoreDir + "/myscreen_" + IMAGES_PRODUCED + ".png")
                    preprocessedBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos!!)
                    //IMAGES_PRODUCED++
                    Log.e(TAG, "captured image: " + IMAGES_PRODUCED)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            fos?.let {
                try {
                    it.close()
                } catch (ioe: IOException) {
                    ioe.printStackTrace()
                }
            }
            bitmap?.recycle()
        }
    }

    // Convert to grayscale
    private fun toGrayscale(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val grayscaleBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (i in 0 until width) {
            for (j in 0 until height) {
                val p = bitmap.getPixel(i, j)
                val r = Color.red(p)
                val g = Color.green(p)
                val b = Color.blue(p)
                val gray = (r + g + b) / 3
                grayscaleBitmap.setPixel(i, j, Color.rgb(gray, gray, gray))
            }
        }
        return grayscaleBitmap
    }

    // Apply thresholding
    private fun applyThreshold(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val thresholdBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val threshold = 90

        for (i in 0 until width) {
            for (j in 0 until height) {
                val p = bitmap.getPixel(i, j)
                val gray = Color.red(p)
                if (gray < threshold) {
                    thresholdBitmap.setPixel(i, j, Color.BLACK)
                } else {
                    thresholdBitmap.setPixel(i, j, Color.WHITE)
                }
            }
        }
        return thresholdBitmap
    }



    private class ImageAvailableListener : OnImageAvailableListener {
        override fun onImageAvailable(reader: ImageReader) {
            var fos: FileOutputStream? = null
            var bitmap : Bitmap? = null
            try {
                mImageReader?.acquireLatestImage().use { image ->
                    if (image != null) {
                        val planes: Array<Image.Plane> = image.getPlanes()
                        val buffer = planes[0].buffer
                        val pixelStride = planes[0].pixelStride
                        val rowStride = planes[0].rowStride
                        val rowPadding: Int = rowStride - pixelStride * mWidth

                        // create bitmap
                        bitmap = Bitmap.createBitmap(
                            mWidth + rowPadding / pixelStride,
                            mHeight,
                            Bitmap.Config.ARGB_8888
                        )
                        bitmap?.copyPixelsFromBuffer(buffer)

                        // write bitmap to a file
                        fos =
                            FileOutputStream(mStoreDir + "/myscreen_" + IMAGES_PRODUCED + ".png")
                        bitmap?.compress(Bitmap.CompressFormat.JPEG, 100, fos!!)
                        IMAGES_PRODUCED++
                        Log.d(TAG, "captured image: " + IMAGES_PRODUCED)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (fos != null) {
                    try {
                        fos!!.close()
                    } catch (ioe: IOException) {
                        ioe.printStackTrace()
                    }
                }
                if (bitmap != null) {
                    bitmap!!.recycle()
                }
            }
        }
    }

    private class OrientationChangeCallback internal constructor(context: Context?) :
        OrientationEventListener(context) {
        override fun onOrientationChanged(orientation: Int) {
            val rotation: Int = mDisplay.getRotation()
            if (rotation != mRotation) {
                mRotation = rotation
                try {
                    // clean up
                    mVirtualDisplay?.release()
                    mImageReader?.setOnImageAvailableListener(null, null)

                    // re-create virtual display depending on device width / height
                    val foregroundService = Norm_ForegroundService()
                    foregroundService.createVirtualDisplay()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private class MediaProjectionStopCallback : MediaProjection.Callback() {
        override fun onStop() {
            Log.e(TAG, "stopping projection.")
            mHandler?.post(Runnable {
                mVirtualDisplay?.release()
                mImageReader?.setOnImageAvailableListener(null, null)
                mOrientationChangeCallback?.disable()
                mMediaProjection?.unregisterCallback(this@MediaProjectionStopCallback)
            })
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()

        Log.d(TAG,"in foreground oncreate")

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()
// Get the current user's UID
        audioHelper = AudioClassificationHelper(applicationContext)
        val currentUser = auth.currentUser
        val user_id = currentUser?.uid

        directory =
            getExternalFilesDir(null).toString()
        val create_folder3 = File("${directory}/Friday_model")
        create_folder3.mkdirs()
        model_dir = create_folder3.path
        // Define where to save the model file in local storage
        localFile = File(model_dir, "browserfft-speech.tflite")

        // Initialize Firebase Storage and Database
        storage = FirebaseStorage.getInstance()
        databaseRef = FirebaseDatabase.getInstance().getReference("/model_updates/${user_id}/")

        GlobalScope.launch {
            for(data in channel){
                result(data)
            }
        }
        sharedPreferencesHelper = SharedPreferencesHelper(this)
        // Update contact list2 based on new contacts in contact list1
        //updateContactList(sharedPreferencesHelper)
        contactList_only = sharedPreferencesHelper.getContacts("contact_list")
        add_transaction = sharedPreferencesHelper.getContacts("add_transaction") // for normal start need to check
        //syncContacts(firestoreHelper, sharedPreferencesHelper) // call whwn log in or out and search "i have two function in foreground service one collect data from firestore other add that data list with another list. i want to call second function when 1st function completely executed . kotlin android"
        // I want to know a details, of my android app client as app user log in from different Google account then , how i watch the contacts as per user?
        contactList_transaction = (contactList_only + add_transaction).distinct()
        Log.d(TAG + " add_transaction_final",add_transaction.toString())
        Log.d(TAG + " contactList_tra_final",contactList_transaction.toString())
        //identifyAndDeleteDuplicatesByName()

        mainActivity = MainActivity()
        tesseractOCR = TesseractOCR(this)
        val filter = IntentFilter()
        filter.addAction("com.example.yourapp.BROADCAST_ACTION")
        filter.addAction("com.example.yourapp.BROADCAST_ACTION_C")
        filter.addAction("com.example.yourapp.BROADCAST_ACTION_model_listener")
        filter.addAction("com.example.yourapp.BROADCAST_ACTION_start_audioclassification")
        filter.addAction("com.example.yourapp.BROADCAST_ACTION_stop_audioclassification")
        registerReceiver(broadcastReceiver,filter, RECEIVER_EXPORTED)
        // create store dir
        val externalFilesDir = getExternalFilesDir(null)
        if (externalFilesDir != null) {
            mStoreDir = externalFilesDir.absolutePath + "/screenshots/"
            Log.d("trackk", mStoreDir.toString())
            val storeDirectory = File(mStoreDir)
            if (!storeDirectory.exists()) {
                val success = storeDirectory.mkdirs()
                if (!success) {
                    Log.e(TAG, "failed to create file storage directory.")
                    stopSelf()
                }
            }
        } else {
            Log.e(TAG, "failed to create file storage directory, getExternalFilesDir is null.")
            stopSelf()
        }

        screenSize = getScreenSize(this)
        Log.d("trackk"," screensize = ${screenSize.first} + ${screenSize.second}")

        // start capture handling thread
        object : Thread() {
            override fun run() {
                Looper.prepare()
                mHandler = Handler()
                Looper.loop()
            }
        }.start()

        if(!SharedData.is_model_avl){
            addModelStatListener()
        }
        networkHelper = NetworkHelper()
    }

    private fun startProjection(resultCode: Int, data: Intent) {
        val mpManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        if (mMediaProjection == null) {
            mMediaProjection = mpManager.getMediaProjection(resultCode, data)
            if (mMediaProjection != null) {
                // display metrics
                mDensity = Resources.getSystem().displayMetrics.densityDpi
                Log.d("trackk","density - "  + mDensity.toString())
                val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
                mDisplay = windowManager.defaultDisplay

                // register media projection stop callback
                mMediaProjection!!.registerCallback(MediaProjectionStopCallback(), mHandler)

                // create virtual display depending on device width / height
                createVirtualDisplay()

                // register orientation change callback
                mOrientationChangeCallback = OrientationChangeCallback(this)
                if (mOrientationChangeCallback!!.canDetectOrientation()) {
                    mOrientationChangeCallback!!.enable()
                }
            }
        }
    }

    private fun stopProjection() {
        if (mHandler != null) {
            mHandler!!.post {
                mMediaProjection?.stop()
            }
        }
    }

    @SuppressLint("WrongConstant")
    fun createVirtualDisplay() {
        // get width and height
        mWidth = Resources.getSystem().displayMetrics.widthPixels
        //mWidth = getScreenWidth(Norm_ForegroundService.mainActivity)
        mHeight = Resources.getSystem().displayMetrics.heightPixels
        Log.d("trackk", "mwidth - $mWidth, mHeight - $mHeight")

        // start capture reader
        mImageReader = ImageReader.newInstance(720, 1600, PixelFormat.RGBA_8888, 2)
        mVirtualDisplay = mMediaProjection!!.createVirtualDisplay(
            SCREENCAP_NAME,
            720,
            1600,
            320,
            getVirtualDisplayFlags(),
            mImageReader!!.surface,
            null,
            mHandler
        )
        //mImageReader!!.setOnImageAvailableListener(ImageAvailableListener(), mHandler)
    }

    // Function to convert PNG file to Bitmap
    fun getPngBitmap(filePath: String): Bitmap? {
        val file = File(filePath)

        if (file.exists()) {
            // Decode the file into a Bitmap
            return BitmapFactory.decodeFile(filePath)
        } else {
            // Handle the case where the file doesn't exist
            return null
        }
    }

    fun getScreenSize(context: Context): Pair<Int, Int> {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)

        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        return Pair(screenWidth, screenHeight)
    }

    private fun getScreenWidth(@NonNull activity: Activity): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics: android.view.WindowMetrics = activity.windowManager.maximumWindowMetrics
            val insets: Insets = windowMetrics.windowInsets
                .getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
            windowMetrics.bounds.width() - insets.left - insets.right
        } else {
            val displayMetrics = DisplayMetrics()
            activity.windowManager.defaultDisplay.getMetrics(displayMetrics)
            displayMetrics.widthPixels
        }
    }

    private fun performOneTimeTouch() {
        val accessibilityIntent = Intent(this, MyAccessibilityService::class.java)
        bindService(accessibilityIntent, object : ServiceConnection, NumberCallback {
            @RequiresApi(Build.VERSION_CODES.N)
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                //val binder = service as myAccessibilityService.MyAccessibilityServiceBinder
                //val accessibilityService = binder.getService()
                //myAccessibilityService.accCallback = this
                // Replace 200.0F and 200F with your desired coordinates
                //myAccessibilityService.performTouch(605.0F, 742F)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                // Handle disconnection if needed
            }

            override fun onRequeststart(result: String) {
                TODO("Not yet implemented")
            }

            override fun onTTS(string: String) {
                TODO("Not yet implemented")
            }

            override fun onTouchSuccess() {
                stopSelf()
                TODO("Not yet implemented")
            }
        }, Context.BIND_AUTO_CREATE)
    }

    fun sendBroadcastMessage(t: Float, m : Float, holdDuration : Long) {
        // Create an Intent with the custom action and message
        val intent = Intent("YOUR_CUSTOM_ACTION")
        intent.putExtra("touch", true)
        intent.putExtra("t", t)
        intent.putExtra("m", m)
        intent.putExtra("holdTime", holdDuration)

        // Send the broadcast
        sendBroadcast(intent)
    }

    fun sendBroadcastMessage_for_back_button() {
        // Create an Intent with the custom action and message
        val intent = Intent("YOUR_CUSTOM_ACTION")
        intent.putExtra("holdTime", 100)
        intent.putExtra("back_button",true)

        // Send the broadcast
        sendBroadcast(intent)
    }

    fun simulate_HomeButton() {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    private fun touch(component : String){
        takescreenshot()
        val position = tesseractOCR.recognizeText(component, getPngBitmap(mStoreDir + "myscreen_0.png"))
        t = position.nwidth.toFloat()
        m = position.nheight.toFloat()
        Log.d("trackk","t - $t , m - $m")
        sendBroadcastMessage(t,m,100)
        //sendBroadcastMessage(600.1641F,1481.23211F)
        //Thread.sleep(3000)
    }

    private fun touch_by_id() : String {
        takescreenshot()
        val position = tesseractOCR.recognizeText_by_id(getPngBitmap(mStoreDir + "myscreen_0.png"))
        if(position == null){
            return "null"
        } else{
            return position
        }
    }

    private fun touch_special_for_upiID(){
        var map: MutableMap<String, Any> = mutableMapOf()
        getStringFromSharedPreferences(applicationContext, "phonepe_array")?.let {
            map = it as MutableMap<String, Any>
        }
        takescreenshot()
        val position = tesseractOCR.recognize_UPI_id(getPngBitmap(mStoreDir + "myscreen_0.png"))
        t = position.nwidth.toFloat()
        m = position.nheight.toFloat()
        if(position.nnew_upi){
            Log.d("trackk","t - $t , m - $m")
            map["new_upi_id"] = arrayOf(t,m)
            sendBroadcastMessage(t,m,100)
        } else{
            getStringFromSharedPreferences(applicationContext, "phonepe_array")?.let {
                Log.d(TAG,"get string from pref - $it")
                if(it["money_appVertical_distance"] != null){
                    val array = it["money_appVertical_distance"] as ArrayList<Float>
                    val d = (screenSize.first.div(2.0)).toFloat()
                    Log.d("trackk","t - $t , m - $m")
                    Log.d(TAG, " final storage data ${array[0]} , ${array[1]}, $d")
                    sendBroadcastMessage(d, (m.plus(2 * array[0])),100)
                } else{
                    Log.d(TAG, "nullllllllllllllllllllll")
                }
            }
        }

        getStringFromSharedPreferences(applicationContext, "phonepe_array")?.let {
            Log.d(TAG,"get string from pref - $it")
            if(it["money_appVertical_distance"] != null){
                val array = it["money_appVertical_distance"] as ArrayList<Float>
                val d = (screenSize.first.div(2.0)).toFloat()
                Log.d("trackk","t - $t , m - $m")
                Log.d(TAG, " final storage data ${array[0]} , ${array[1]}, $d")
                map["exist_contact_touch"] = arrayOf(d, (m.plus(2 * array[0])))
                saveStringToSharedPreferences(applicationContext, "phonepe_array", map)
                //sendBroadcastMessage(d, (m.plus(2 * array[0])),100)
            } else{
                Log.d(TAG, "nullllllllllllllllllllll")
            }
        }

        //sendBroadcastMessage(600.1641F,1481.23211F)
        //Thread.sleep(3000)
    }


    private fun touch_save(component : String, touch_position_name : String){
        var map: MutableMap<String, Any> = mutableMapOf()
        getStringFromSharedPreferences(applicationContext, "phonepe_array")?.let {
            map = it as MutableMap<String, Any>
        }
        takescreenshot()
        var position = tesseractOCR.recognizeText(component, getPngBitmap(mStoreDir + "myscreen_0.png"))
        t = position.nwidth.toFloat()
        m = position.nheight.toFloat()
        Log.d("trackk","t - $t , m - $m")
        //sendBroadcastMessage(t,m,100)
        map[touch_position_name] = arrayOf(t,m)
        saveStringToSharedPreferences(applicationContext, "phonepe_array", map)
        Log.d(TAG, map.toString())
        //sendBroadcastMessage(600.1641F,1481.23211F)
        //Thread.sleep(3000)
    }

    private fun search_box_delete_text(){
        var map: MutableMap<String, Any> = mutableMapOf()
        getStringFromSharedPreferences(applicationContext, "phonepe_array")?.let {
            map = it as MutableMap<String, Any>
        }
        getStringFromSharedPreferences(applicationContext, "phonepe_array")?.let {
            //Log.d(TAG, "get string from pref - $it")
            if (it["money_appVertical_distance"] != null) {
                val array = it["money_appVertical_distance"] as ArrayList<Float>
                val array1 = it["money_appHorizontal_distance"] as ArrayList<Float>
                val d = (screenSize.first.div(2.0)).toFloat()
                val d1 = (screenSize.first - array1[0])
                val array2 = it["search_box2"] as ArrayList<Float>
                val ss = (array2[1] as Double).toFloat()
                map["search_box_delete_text"] = arrayOf(d1,ss)
                //Log.d(TAG,"search_box_delete_text - $d1 ")
                //Log.d(TAG,"search_box_delete_text - $ss ")
                saveStringToSharedPreferences(applicationContext, "phonepe_array", map)
                //Log.d(TAG, "input position${d} and ${array2[1].plus(4 * array[0])}")
                //Log.d(TAG, "input cancel position ${d1} and ${array2[1]}")
                //sendBroadcastMessage(d1, (array2[1]), 100)
            }
        }
    }

    private fun touch_save_paste(touch_position_name : String){
        var map: MutableMap<String, Any> = mutableMapOf()
        getStringFromSharedPreferences(applicationContext, "phonepe_array")?.let {
            map = it as MutableMap<String, Any>
        }
        takescreenshot()
        val position = tesseractOCR.recognizeText_forpaste(getPngBitmap(mStoreDir + "myscreen_0.png"))
        t = position.nwidth.toFloat()
        m = position.nheight.toFloat()
        Log.d("trackk","t - $t , m - $m")
        sendBroadcastMessage(t,m,100)
        map[touch_position_name] = arrayOf(t,m)
        saveStringToSharedPreferences(applicationContext, "phonepe_array", map)
        Log.d(TAG, map.toString())
        //sendBroadcastMessage(600.1641F,1481.23211F)
        //Thread.sleep(3000)
    }

    private fun touch_new(string1 : String, string2 : String , string3 : String){
        takescreenshot()
        var position = tesseractOCR.recognize_three_Text(string1, string2, string3, getPngBitmap(mStoreDir + "myscreen_0.png"))
        t = position.nwidth.toFloat()
        m = position.nheight.toFloat()
        Log.d("trackk","t - $t , m - $m")
        sendBroadcastMessage(t,m,100)
        //sendBroadcastMessage(600.1641F,1481.23211F)
        //Thread.sleep(3000)
    }

    private fun touch_search(component : String) : Boolean{
        takescreenshot()
        var position = tesseractOCR.recognizeText(component, getPngBitmap(mStoreDir + "myscreen_0.png"))
        t = position.nwidth.toFloat()
        m = position.nheight.toFloat()
        Log.d("trackk","t - $t , m - $m")
        if(t==0F && m==0F){
            return false
        } else{
            return true
        }
        //sendBroadcastMessage(t,m,100)
        //sendBroadcastMessage(600.1641F,1481.23211F)
        //Thread.sleep(3000)
    }
    private fun longTouch(component : String){
        takescreenshot()
        var position = tesseractOCR.recognizeText(component, getPngBitmap(mStoreDir + "myscreen_0.png"))
        t = position.nwidth.toFloat()
        m = position.nheight.toFloat()
        Log.d("trackk","t - $t , m - $m")
        sendBroadcastMessage(t,m,1000)
        //sendBroadcastMessage(600.1641F,1481.23211F)
        Thread.sleep(3000)
    }

    private var mHeadsetProfileListener: BluetoothProfile.ServiceListener =
        object : BluetoothProfile.ServiceListener {
            /**
             * This method is never called, even when we closeProfileProxy on onPause.
             * When or will it ever be called???
             */
            override fun onServiceDisconnected(profile: Int) {
                if (ActivityCompat.checkSelfPermission(
                        applicationContext,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return
                }
                mBluetoothHeadset?.stopVoiceRecognition(mConnectedHeadset)
                //unregisterReceiver(mHeadsetBroadcastReceiver)
                mBluetoothHeadset = null
            }

            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                Log.d(TAG,"proxy connected")
                // mBluetoothHeadset is just a head set profile,
                // it does not represent a head set device.
                mBluetoothHeadset = proxy as BluetoothHeadset

                // If a head set is connected before this application starts,
                // ACTION_CONNECTION_STATE_CHANGED will not be broadcast.
                // So we need to check for already connected head set.
                val devices: List<BluetoothDevice> = mBluetoothHeadset!!.connectedDevices
                if (devices.size > 0) {
                    // Only one head set can be connected at a time,
                    // so the connected head set is at index 0.
                    mConnectedHeadset = devices[0]

                    // The audio should not yet be connected at this stage.
                    // But just to make sure we check.
                    if (ActivityCompat.checkSelfPermission(
                            applicationContext,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
                        Log.d(TAG,"return from proxy")
                        //return
                    }
                    val log: String = if (mBluetoothHeadset!!.isAudioConnected(mConnectedHeadset)) {
                        "Profile listener audio already connected" //$NON-NLS-1$
                    } else {
                        // The if statement is just for debug. So far startVoiceRecognition always
                        // returns true here. What can we do if it returns false? Perhaps the only
                        // sensible thing is to inform the user.
                        // Well actually, it only returns true if a call to stopVoiceRecognition is
                        // call somewhere after a call to startVoiceRecognition. Otherwise, if
                        // stopVoiceRecognition is never called, then when the application is restarted
                        // startVoiceRecognition always returns false whenever it is called.
                        if (mBluetoothHeadset!!.startVoiceRecognition(mConnectedHeadset)) {
                            "Profile listener startVoiceRecognition returns true" //$NON-NLS-1$
                        } else {
                            "Profile listener startVoiceRecognition returns false" //$NON-NLS-1$
                        }
                    }
                    Log.d(TAG, log)
                }

                // During the active life time of the app, a user may turn on and off the head set.
                // So register for broadcast of connection states.
                /*registerReceiver(
                    mHeadsetBroadcastReceiver,
                    IntentFilter(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
                )*/

                // Calling startVoiceRecognition does not result in immediate audio connection.
                // So register for broadcast of audio connection states. This broadcast will
                // only be sent if startVoiceRecognition returns true.
                val f = IntentFilter(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED)
                f.priority = Int.MAX_VALUE
                registerReceiver(mHeadsetBroadcastReceiver, f)
            }
        }

    protected var mHeadsetBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        @RequiresApi(Build.VERSION_CODES.S)
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            val state: Int
            val previousState = intent.getIntExtra(
                BluetoothHeadset.EXTRA_PREVIOUS_STATE,
                BluetoothHeadset.STATE_DISCONNECTED
            )
            if (action == BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED) {
                state = intent.getIntExtra(
                    BluetoothHeadset.EXTRA_STATE,
                    BluetoothHeadset.STATE_DISCONNECTED
                )
                if (state == BluetoothHeadset.STATE_CONNECTED) {
                    mConnectedHeadset = intent.getParcelableExtra<Parcelable>(BluetoothDevice.EXTRA_DEVICE) as BluetoothDevice

                    // Audio should not be connected yet but just to make sure.
                    if (ActivityCompat.checkSelfPermission(
                            applicationContext,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
                        //return
                    }
                    log = if (mBluetoothHeadset?.isAudioConnected(mConnectedHeadset) == true) {
                        "Headset connected audio already connected"
                    } else {

                        // Calling startVoiceRecognition always returns false here,
                        // that why a count down timer is implemented to call
                        // startVoiceRecognition in the onTick and onFinish.
                        if (mBluetoothHeadset?.startVoiceRecognition(mConnectedHeadset) == true) {
                            "Headset connected startVoiceRecognition returns true"
                        } else {
                            "Headset connected startVoiceRecognition returns false"
                        }
                    }
                } else if (state == BluetoothHeadset.STATE_DISCONNECTED) {
                    // Calling stopVoiceRecognition always returns false here
                    // as it should since the headset is no longer connected.
                    mConnectedHeadset = null
                }
            } else  // audio
            {
                state = intent.getIntExtra(
                    BluetoothHeadset.EXTRA_STATE,
                    BluetoothHeadset.STATE_AUDIO_DISCONNECTED
                )
                mBluetoothHeadset?.stopVoiceRecognition(mConnectedHeadset)
                if (state == BluetoothHeadset.STATE_AUDIO_CONNECTED) {
                    log = "Head set audio connected, cancel countdown timer"
                } else if (state == BluetoothHeadset.STATE_AUDIO_DISCONNECTED) {
                    // The headset audio is disconnected, but calling
                    // stopVoiceRecognition always returns true here.
                    val returnValue: Boolean = mBluetoothHeadset?.stopVoiceRecognition(mConnectedHeadset) == true
                    log = "Audio disconnected stopVoiceRecognition return $returnValue"
                }
            }
            log += """Action = $action State = $state previous state = $previousState"""
            Log.d(TAG, log)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun connectBluetooth() {
        GlobalScope.launch {
            //delay(400) // Replace with a coroutine-friendly delay

            // Set the audio mode to a suitable mode (e.g., MODE_NORMAL or MODE_IN_COMMUNICATION)
            //audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isBluetoothScoOn = true

            //Log.d(TAG, "in mode in call")
            // Enable Bluetooth SCO for audio input

            // Start Bluetooth SCO connection
            audioManager.startBluetoothSco()

            // Delay to allow SCO connection to establish (adjust as needed)
            //delay(500)

            // Notify the user
            //textToSpeech.speak("Your Bluetooth device mic is connected", TextToSpeech.QUEUE_FLUSH, null, null)
            // Optional: If you want to play audio through the mobile phone speaker

            //val sdfsd = IntentFilter(Intent.ACTION_MEDIA_BUTTON)
            //registerReceiver(mediaButtonReceiver, sdfsd, RECEIVER_NOT_EXPORTED)
        }
    }

    private fun fetchDataFromFirestore(screenshotPageName: String) {
        Log.d(TAG," enter fetch data from firestore")
        db.collection("extractedText")
            .get()
            .addOnSuccessListener { result ->
                var y = 0
                for (doc in result) {
                    y++
                    Log.d(TAG, "database document count $y")
                    saveStringToSharedPreferences(applicationContext,screenshotPageName,doc.data)
                }
            }
            .addOnFailureListener { exception ->
                Log.d(TAG, "Error getting documents.", exception)
            }
    }



    private suspend fun pollFirestoreForData(screenshotPageName: String) : Boolean {
        val pollingInterval = 1000L  // 2 seconds
        val timeout = 60000L  // 60 seconds
        val startTime = System.currentTimeMillis()
        var vall : Boolean = false

        while (System.currentTimeMillis() - startTime < timeout) {
            val result = fetchDataFromFirestore_new(screenshotPageName)
            if (result != null) {
                Log.d(TAG, "Data found: $result")
                saveStringToSharedPreferences(applicationContext, screenshotPageName, result)
                deleteDatabase()
                SharedData.is_database_delete = false
                val time_out = 40000L  // 60 seconds
                val start_Time = System.currentTimeMillis()

                while (System.currentTimeMillis() - start_Time < time_out) {
                    if(SharedData.is_database_delete){
                        vall = true
                        break
                    }
                    delay(pollingInterval)
                }
                if (System.currentTimeMillis() - start_Time >= time_out) {
                    textToSpeech.speak("database not deleted, Please check your internet connection",TextToSpeech.QUEUE_FLUSH,null,null)
                    // Handle the timeout case if needed
                }
                /**
                if(phonepe_calibration_home()){
                execute("later","phonepe_array")
                delay(2000)
                while (System.currentTimeMillis() - startTime < timeout) {
                if(SharedData.is_database_delete){
                uploadFile("Jiosaavn")
                break
                }
                delay(pollingInterval)
                }
                if (System.currentTimeMillis() - startTime >= timeout) {
                textToSpeech.speak("database not deleted, Please check your internet connection",TextToSpeech.QUEUE_FLUSH,null,null)
                // Handle the timeout case if needed
                }
                } else{
                phonepe_calibration_home_main()
                execute("to mobile","phonepe_array")
                delay(3000)
                uploadFile("Jiosaavn")
                }
                 **/
                break
            } else {
                Log.d(TAG, "Data not available yet, polling again...")
            }
            delay(pollingInterval)
        }

        if (System.currentTimeMillis() - startTime >= timeout) {
            // Handle the timeout case if needed
            vall = false
        }
        return vall
    }

    private suspend fun fetchDataFromFirestore_new(screenshotPageName: String): Map<String, Any>? {
        return try {
            /**val querySnapshot = db.collection("extractedText")
            .whereEqualTo("screenshotPageName", screenshotPageName)
            .get()
            .await()**/

            val querySnapshot = db.collection("extractedText")
                .get()
                .await()

            if (!querySnapshot.isEmpty) {
                querySnapshot.documents[0].data
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching data from Firestore", e)
            null
        }
    }

    fun saveStringToSharedPreferences(context: Context, key: String, value: Map<String, Any>) {
        val sharedPref = context.getSharedPreferences("my__storage1344", Context.MODE_PRIVATE)
        val json = Gson().toJson(value)
        with(sharedPref.edit()) {
            putString(key, json)
            apply()
        }
    }

    // Function to retrieve a string from SharedPreferences
    fun getStringFromSharedPreferences(context: Context, key: String): Map<String, Any>? {
        val sharedPref = context.getSharedPreferences("my__storage1344", Context.MODE_PRIVATE)
        val json = sharedPref.getString(key, null)
        if (json != null) {
            //Log.d(TAG,json)
        }
        return Gson().fromJson(json, Map::class.java) as? Map<String, Any>
    }

    private fun launchappByPackage(package_name : String) {
        val packageName = package_name
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
    }

    private fun uploadFile(screenshotPageName : String){
        takescreenshot()
        val stream = ByteArrayOutputStream()
        getPngBitmap(mStoreDir + "myscreen_0.png")?.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val data = stream.toByteArray()
        val path = "/users/pictures/" + "screen_photo" + ".png"
        val firememoReference = storage.getReference(path)
        val uploadTask = firememoReference.putBytes(data)
        uploadTask.addOnFailureListener {
            // Handle unsuccessful uploads
        }.addOnSuccessListener { taskSnapshot ->
            Log.d(TAG, "successfully photo uploaded")
            // taskSnapshot.metadata contains file metadata such as size, content-type, etc.
            //listenForProcessedData(screenshotPageName)
            //fetchDataFromFirestore(screenshotPageName)
            GlobalScope.launch {
                pollFirestoreForData(screenshotPageName)
            }
            //delay(5000)
            //fetchDataFromFirestore()
        }

        //var position = tesseractOCR.recognizeText(component, getPngBitmap(mStoreDir + "myscreen_0.png"))
        //t = position.nwidth.toFloat()
        //m = position.nheight.toFloat()
        //Log.d("trackk","t - $t , m - $m")
        //sendBroadcastMessage(t,m)
        //sendBroadcastMessage(600.1641F,1481.23211F)
        //Thread.sleep(3000)
    }

    private suspend fun uploadFile_new(screenshotPageName : String) : Boolean{
        var vall = false
        takescreenshot()
        val stream = ByteArrayOutputStream()
        getPngBitmap(mStoreDir + "myscreen_0.png")?.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val data = stream.toByteArray()
        val path = "/users/pictures/" + "screen_photo" + ".png"
        val firememoReference = storage.getReference(path)
        val uploadTask = firememoReference.putBytes(data)
        uploadTask.addOnFailureListener {
            // Handle unsuccessful uploads
        }.addOnSuccessListener { taskSnapshot ->
            Log.d(TAG, "successfully photo uploaded")
            // taskSnapshot.metadata contains file metadata such as size, content-type, etc.
            //listenForProcessedData(screenshotPageName)
            //fetchDataFromFirestore(screenshotPageName)
            GlobalScope.launch {
                vall = pollFirestoreForData(screenshotPageName)
            }
            //delay(5000)
            //fetchDataFromFirestore()
        }
        val pollingInterval = 1000L
        val time_out = 70000L  // 60 seconds
        val start_Time = System.currentTimeMillis()

        while (System.currentTimeMillis() - start_Time < time_out) {
            if(vall){
                break
            }
            delay(pollingInterval)
        }
        return vall

        //var position = tesseractOCR.recognizeText(component, getPngBitmap(mStoreDir + "myscreen_0.png"))
        //t = position.nwidth.toFloat()
        //m = position.nheight.toFloat()
        //Log.d("trackk","t - $t , m - $m")
        //sendBroadcastMessage(t,m)
        //sendBroadcastMessage(600.1641F,1481.23211F)
        //Thread.sleep(3000)
    }

    private fun deleteDatabase(){
        db.collection("extractedText")
            .get()
            .addOnSuccessListener { result ->
                if(result.size() == 1)
                {
                    for (doc in result) {
                        //saveStringToSharedPreferences(applicationContext,"phonepay",doc.data)
                        db.collection("extractedText").document(doc.id).delete().addOnSuccessListener {
                            Log.d(TAG, "Success delete document")
                            SharedData.is_database_delete = true
                        }.addOnFailureListener{e->

                        }
                    }
                }
            }
            .addOnFailureListener { exception ->
                Log.w(TAG, "Error getting documents.", exception)
            }
    }

    private fun execute(description : String, localStorageName : String){
        getStringFromSharedPreferences(applicationContext, localStorageName)?.let {
            Log.d(TAG,"get string from pref - ${it.toString()}")
            val array = it[description] as ArrayList<Float>
            Log.d(TAG, " final storage data ${array[0]} , ${array[1]}")
            sendBroadcastMessage(array[0], array[1],100)
        }
    }

    private fun longExecute(description : String, localStorageName : String){
        getStringFromSharedPreferences(applicationContext, localStorageName)?.let {
            Log.d(TAG,"get string from pref - $it")
            val array = it[description] as ArrayList<Float>
            Log.d(TAG, " final storage data long execute ${array[0]} , ${array[1]}")
            sendBroadcastMessage(array[0], array[1],1000)
        }
    }

    private fun myLibrary_calibration(){
        var map: MutableMap<String, Any> = mutableMapOf()
        getStringFromSharedPreferences(applicationContext, "Jiosaavn_array")?.let {
            map = it as MutableMap<String, Any>
        }
        val desMap = ArrayList<String>()
        getStringFromSharedPreferences(applicationContext,"Jiosaavn")?.let {
            val arrayValue = it["textAnnotations"] as? List<*>
            if (arrayValue != null) {
                var i = 0
                for (dp in arrayValue) {
                    val subMap = dp as Map<*, *>
                    if (i == 1) {
                        desMap.add(subMap["description"].toString().lowercase())
                    }
                    i = 1
                }
                var index = indexOfsidebysideString(desMap,"shuffle","all")
                Log.d(TAG,index.toString())
                var for_you = substractArray(subtractMaps(arrayValue, index),subtractMaps(arrayValue,(index + 1)))
                var w = (for_you[0]?.toFloat() ?: 0) as Float
                var z = ((for_you[1]?.toFloat() ?: 0) as Float)
                map["shuffle all"] = arrayOf(w,z)
                saveStringToSharedPreferences(applicationContext, "Jiosaavn_array", map)
            }
        }
    }

    private fun home_calibration(){
        val desMap = ArrayList<String>()
        val map = HashMap<String, Array<Float>>()
        getStringFromSharedPreferences(applicationContext,"Jiosaavn")?.let {
            //Log.d(TAG, it["textAnnotations"].toString()+ "list size")
            val arrayValue = it["textAnnotations"] as? List<*>
            if (arrayValue != null) {
                var i = 0
                for (dp in arrayValue) {
                    val subMap = dp as Map<*, *>
                    if (i == 1) {
                        desMap.add(subMap["description"].toString().lowercase())
                    }
                    i = 1
                }
                Log.d(TAG,desMap.toString())
                var index = indexOfsidebyside3String(desMap,"search","for","you")
                Log.d(TAG,index.toString())
                var for_you = subtractMaps(arrayValue,index)
                var w = (for_you[0]?.toFloat() ?: 0) as Float
                var z = ((for_you[1]?.toFloat() ?: 0) as Float)
                var d = w
                map["search"] = arrayOf(w,z)
                Log.d(TAG,w.toString() +  " search " +z.toString())
                for_you = substractArray(subtractMaps(arrayValue, (index+1)),subtractMaps(arrayValue,(index + 2)))
                w = (for_you[0]?.toFloat() ?: 0) as Float
                z = ((for_you[1]?.toFloat() ?: 0) as Float)
                map["for you"] = arrayOf(w,z)
                Log.d(TAG,w.toString() +  " for you " +z.toString())
                w -= ((w - d) * 2)
                map["home"] = arrayOf(w,z)
                index = indexOfsidebyside3String(desMap,"my","library","pro")
                Log.d(TAG,index.toString())
                for_you = substractArray(subtractMaps(arrayValue, index),subtractMaps(arrayValue,(index + 1)))
                w = (for_you[0]?.toFloat() ?: 0) as Float
                z = ((for_you[1]?.toFloat() ?: 0) as Float)
                map["my library"] = arrayOf(w,z)
                Log.d(TAG, map["my library"]?.get(0).toString() +" "+ map["my library"]?.get(0).toString())
                Log.d(TAG + "map", map.toString())
                saveStringToSharedPreferences(applicationContext, "Jiosaavn_array", map)
                //Log.d(TAG, for_you[0].toString() +", "+for_you[1].toString())
                //t = (for_you[0]?.toFloat() ?: 0) as Float
                //m = ((for_you[1]?.toFloat() ?: 0) as Float)



                //val index = indexOfsidebysideString(desMap,"b","nit")
                //val for_you = substractArray(subtractMaps(arrayValue, index),subtractMaps(arrayValue,(index + 1)))
                //Log.d(TAG," ${for_you[0].toString()} , ${for_you[1].toString()}")
                //t = (for_you[0]?.toFloat() ?: 0) as Float
                //m = ((for_you[1]?.toFloat() ?: 0) as Float)
                //Log.d("trackk","t - $t , m - $m")
                //sendBroadcastMessage(t,m)
            }
        }
    }

    private fun phonepe_calibration_home_main(){
        val desMap = ArrayList<String>()
        val map = HashMap<String, Array<Float>>()
        getStringFromSharedPreferences(applicationContext,"Jiosaavn")?.let {
            //Log.d(TAG, it["textAnnotations"].toString()+ "list size")
            val arrayValue = it["textAnnotations"] as? List<*>
            if (arrayValue != null) {
                var i = 0
                for (dp in arrayValue) {
                    val subMap = dp as Map<*, *>
                    if (i == 1) {
                        desMap.add(subMap["description"].toString().lowercase())
                    }
                    i = 1
                }
                Log.d(TAG,desMap.toString())
                var index = indexOfsidebysideString(desMap,"to","mobile")
                Log.d(TAG,index.toString())
                var for_you = substractArray(subtractMaps(arrayValue,index),subtractMaps(arrayValue,(index+1)))
                var w = (for_you[0]?.toFloat() ?: 0) as Float
                var z = ((for_you[1]?.toFloat() ?: 0) as Float)
                map["to mobile"] = arrayOf(w,z)

                index = indexOfsidebysideString(desMap,"to","bank")
                Log.d(TAG,index.toString())
                for_you = substractArray(subtractMaps(arrayValue,index),subtractMaps(arrayValue,(index+1)))
                w = (for_you[0]?.toFloat() ?: 0) as Float
                z = ((for_you[1]?.toFloat() ?: 0) as Float)
                map["to bank"] = arrayOf(w,z)

                index = indexOfsidebysideString(desMap,"to","self")
                Log.d(TAG,index.toString())
                for_you = substractArray(subtractMaps(arrayValue,index),subtractMaps(arrayValue,(index+1)))
                w = (for_you[0]?.toFloat() ?: 0) as Float
                z = ((for_you[1]?.toFloat() ?: 0) as Float)
                map["to self"] = arrayOf(w,z)

                index = indexOfsidebysideString(desMap,"check","bank")
                Log.d(TAG,index.toString())
                for_you = substractArray(subtractMaps(arrayValue,index),subtractMaps(arrayValue,(index+1)))
                w = (for_you[0]?.toFloat() ?: 0) as Float
                z = ((for_you[1]?.toFloat() ?: 0) as Float)
                map["check bank"] = arrayOf(w,z)
                saveStringToSharedPreferences(applicationContext, "phonepe_array", map)
                //Log.d(TAG, for_you[0].toString() +", "+for_you[1].toString())
                //t = (for_you[0]?.toFloat() ?: 0) as Float
                //m = ((for_you[1]?.toFloat() ?: 0) as Float)



                //val index = indexOfsidebysideString(desMap,"b","nit")
                //val for_you = substractArray(subtractMaps(arrayValue, index),subtractMaps(arrayValue,(index + 1)))
                //Log.d(TAG," ${for_you[0].toString()} , ${for_you[1].toString()}")
                //t = (for_you[0]?.toFloat() ?: 0) as Float
                //m = ((for_you[1]?.toFloat() ?: 0) as Float)
                //Log.d("trackk","t - $t , m - $m")
                //sendBroadcastMessage(t,m)
            }
        }
    }

    private fun phonepe_calibration_home() : Boolean{
        var return_state : Boolean = true
        val desMap = ArrayList<String>()
        val map = HashMap<String, Array<Float>>()
        getStringFromSharedPreferences(applicationContext,"Jiosaavn")?.let {
            //Log.d(TAG, it["textAnnotations"].toString()+ "list size")
            val arrayValue = it["textAnnotations"] as? List<*>
            if (arrayValue != null) {
                var i = 0
                for (dp in arrayValue) {
                    val subMap = dp as Map<*, *>
                    if (i == 1) {
                        desMap.add(subMap["description"].toString().lowercase())
                    }
                    i = 1
                }
                var index = indexOfsidebysideString(desMap,"later","download")
                Log.d(TAG,"later download index value - ${index.toString()}")
                if(index == -1){
                    return_state = false
                } else {
                    Log.d(TAG,index.toString() +"  later")
                    var for_you = subtractMaps(arrayValue,index)
                    var w = (for_you[0]?.toFloat() ?: 0) as Float
                    var z = ((for_you[1]?.toFloat() ?: 0) as Float)
                    map["later"] = arrayOf(w,z)
                    saveStringToSharedPreferences(applicationContext, "phonepe_array", map)
                    return_state = true
                }
            }
        }
        return return_state
    }

    private fun phonepe_calibration_toMobile() : Boolean{
        var return_state : Boolean = true
        val desMap = ArrayList<String>()
        var map: MutableMap<String, Any> = mutableMapOf()
        getStringFromSharedPreferences(applicationContext, "phonepe_array")?.let {
            map = it as MutableMap<String, Any>
        }
        getStringFromSharedPreferences(applicationContext,"Jiosaavn")?.let {
            //Log.d(TAG, it["textAnnotations"].toString()+ "list size")
            val arrayValue = it["textAnnotations"] as? List<*>
            if (arrayValue != null) {
                var i = 0
                for (dp in arrayValue) {
                    val subMap = dp as Map<*, *>
                    if (i == 1) {
                        desMap.add(subMap["description"].toString().lowercase())
                    }
                    i = 1
                }

                var index = indexOfsidebysideString(desMap,"send","money")
                if(index == -1){
                    return_state = false
                }else{
                    var for_you = subtractMaps(arrayValue,index+1)
                    var w = (for_you[0]?.toFloat() ?: 0) as Float
                    var z = ((for_you[1]?.toFloat() ?: 0) as Float)
                    val z0 = z
                    index = indexOfsidebysideString(desMap,"upi","app")
                    for_you = subtractMaps(arrayValue,index+1)
                    w = (for_you[0]?.toFloat() ?: 0) as Float
                    z = ((for_you[1]?.toFloat() ?: 0) as Float)
                    val d = (z-z0)
                    map["money_appVertical_distance"] = arrayOf(d,d)
                    for_you = subtractMaps(arrayValue,index)
                    val w1 = (for_you[0]?.toFloat() ?: 0) as Float
                    map["money_appHorizontal_distance"] = arrayOf((w-w1),(w-w1))
                    Log.d(TAG + "money_appHorizontal_distance", (w-w1).toString())
                    saveStringToSharedPreferences(applicationContext, "phonepe_array", map)
                }

                index = indexOfsidebyside3String(desMap,"new","payment","from")
                Log.d(TAG,"later download index value - ${index.toString()}")
                if(index == -1){
                    index = indexOfsidebysideString(desMap,"any","contact")
                    if(index == -1){
                        index = indexOfsidebysideString(desMap,"any","mobile")
                        if(index == -1){
                            return_state = false
                        }
                    }
                }
                if (index != -1){
                    Log.d(TAG,index.toString() +"  search")
                    var for_you = subtractMaps(arrayValue,index)
                    var w = (for_you[0]?.toFloat() ?: 0) as Float
                    var z = ((for_you[1]?.toFloat() ?: 0) as Float)
                    map["search_box"] = arrayOf(w,z)
                    saveStringToSharedPreferences(applicationContext, "phonepe_array", map)
                    return_state = true
                }else{
                    index = indexOfsidebysideString(desMap,"send","money")
                    if(index == -1){
                        return_state = false
                    }else{
                        var for_you = subtractMaps(arrayValue,index+1)
                        var w = (for_you[0]?.toFloat() ?: 0) as Float
                        var z = ((for_you[1]?.toFloat() ?: 0) as Float)
                        val z0 = z
                        index = indexOfsidebysideString(desMap,"upi","app")
                        for_you = subtractMaps(arrayValue,index+1)
                        w = (for_you[0]?.toFloat() ?: 0) as Float
                        z = ((for_you[1]?.toFloat() ?: 0) as Float)
                        val d = z-z0
                        z = (z + 2*d)
                        map["search_box"] = arrayOf(w,z)
                        saveStringToSharedPreferences(applicationContext, "phonepe_array", map)
                        return_state = true
                    }
                }
            }
        }
        return return_state
    }

    private fun phonepe_calibration_toMobile_search() : Boolean{
        var return_state : Boolean = true
        val desMap = ArrayList<String>()
        var map: MutableMap<String, Any> = mutableMapOf()
        getStringFromSharedPreferences(applicationContext, "phonepe_array")?.let {
            map = it as MutableMap<String, Any>
        }
        getStringFromSharedPreferences(applicationContext,"Jiosaavn")?.let {
            //Log.d(TAG, it["textAnnotations"].toString()+ "list size")
            val arrayValue = it["textAnnotations"] as? List<*>
            if (arrayValue != null) {
                var i = 0
                for (dp in arrayValue) {
                    val subMap = dp as Map<*, *>
                    if (i == 1) {
                        desMap.add(subMap["description"].toString().lowercase())
                    }
                    i = 1
                }
                var index = indexOfsidebysideString(desMap,"search","number")
                Log.d(TAG,"later download index value - ${index.toString()}")
                if(index == -1){
                    return_state = false
                }
                if (index != -1){
                    Log.d(TAG,index.toString() +"  search2")
                    var for_you = subtractMaps(arrayValue,index)
                    var w = (for_you[0]?.toFloat() ?: 0) as Float
                    var z = ((for_you[1]?.toFloat() ?: 0) as Float)
                    map["search_box2"] = arrayOf(w,z)
                    saveStringToSharedPreferences(applicationContext, "phonepe_array", map)
                    return_state = true
                }
            }
        }
        return return_state
    }

    private fun phonepe_calibration_enter_amount() : Boolean{
        var return_state : Boolean = true
        val desMap = ArrayList<String>()
        var map: MutableMap<String, Any> = mutableMapOf()
        getStringFromSharedPreferences(applicationContext, "phonepe_array")?.let {
            map = it as MutableMap<String, Any>
        }
        getStringFromSharedPreferences(applicationContext,"Jiosaavn")?.let {
            //Log.d(TAG, it["textAnnotations"].toString()+ "list size")
            val arrayValue = it["textAnnotations"] as? List<*>
            if (arrayValue != null) {
                var i = 0
                for (dp in arrayValue) {
                    val subMap = dp as Map<*, *>
                    if (i == 1) {
                        desMap.add(subMap["description"].toString().lowercase())
                    }
                    i = 1
                }
                var index = indexOfsidebysideString(desMap,"enter","amount")
                Log.d(TAG,"later download index value - ${index.toString()}")
                if(index == -1){
                    return_state = false
                }
                if (index != -1){
                    Log.d(TAG,index.toString() +"  search2")
                    var for_you = subtractMaps(arrayValue,index)
                    var w = (for_you[0]?.toFloat() ?: 0) as Float
                    var z = ((for_you[1]?.toFloat() ?: 0) as Float)
                    map["enter_amount"] = arrayOf(w,z)
                    saveStringToSharedPreferences(applicationContext, "phonepe_array", map)
                    return_state = true
                }
            }
        }
        return return_state
    }

    private fun phonepe_calibration_home_home() : Boolean{
        var return_state : Boolean = true
        val desMap = ArrayList<String>()
        var map: MutableMap<String, Any> = mutableMapOf()
        getStringFromSharedPreferences(applicationContext, "phonepe_array")?.let {
            map = it as MutableMap<String, Any>
        }
        getStringFromSharedPreferences(applicationContext,"Jiosaavn")?.let {
            //Log.d(TAG, it["textAnnotations"].toString()+ "list size")
            val arrayValue = it["textAnnotations"] as? List<*>
            if (arrayValue != null) {
                var i = 0
                for (dp in arrayValue) {
                    val subMap = dp as Map<*, *>
                    if (i == 1) {
                        desMap.add(subMap["description"].toString().lowercase())
                    }
                    i = 1
                }
                var index = indexOfsidebyside3String_contain(desMap,"home","loan","insurance")
                Log.d(TAG,"later download index value - ${index.toString()}")
                if(index == -1){
                    return_state = false
                }
                if (index != -1){
                    Log.d(TAG,index.toString() +"  search2")
                    var for_you = subtractMaps(arrayValue,index)
                    var w = (for_you[0]?.toFloat() ?: 0) as Float
                    var z = ((for_you[1]?.toFloat() ?: 0) as Float)
                    map["home_home"] = arrayOf(w,z)
                    saveStringToSharedPreferences(applicationContext, "phonepe_array", map)
                    return_state = true
                }
            }
        }
        return return_state
    }

    private fun phonepe_calibration_enter_amount_pay() : Boolean{
        var return_state : Boolean = true
        val desMap = ArrayList<String>()
        var map: MutableMap<String, Any> = mutableMapOf()
        getStringFromSharedPreferences(applicationContext, "phonepe_array")?.let {
            map = it as MutableMap<String, Any>
        }
        getStringFromSharedPreferences(applicationContext,"Jiosaavn")?.let {
            //Log.d(TAG, it["textAnnotations"].toString()+ "list size")
            val arrayValue = it["textAnnotations"] as? List<*>
            if (arrayValue != null) {
                var i = 0
                for (dp in arrayValue) {
                    val subMap = dp as Map<*, *>
                    if (i == 1) {
                        desMap.add(subMap["description"].toString().lowercase())
                    }
                    i = 1
                }
                var index = indexOfString(desMap,"pay")
                Log.d(TAG,"later download index value - ${index.toString()}")
                if(index == -1){
                    return_state = false
                }
                if (index != -1){
                    Log.d(TAG,index.toString() +"  search2")
                    var for_you = subtractMaps(arrayValue,index)
                    var w = (for_you[0]?.toFloat() ?: 0) as Float
                    var z = ((for_you[1]?.toFloat() ?: 0) as Float)
                    map["enter_amount_pay"] = arrayOf(w,z)
                    saveStringToSharedPreferences(applicationContext, "phonepe_array", map)
                    return_state = true
                }
            }
        }
        return return_state
    }

    private fun UPI_pin_position_cal() : Boolean{
        var map: MutableMap<String, Any> = mutableMapOf()
        getStringFromSharedPreferences(applicationContext, "phonepe_array")?.let {
            map = it as MutableMap<String, Any>
        }
        val screen_width = screenSize.first.toFloat()
        val screen_height = screenSize.second.toFloat()
        val pin1_width = screen_width.div(6.0)
        val pin1_height = (screen_height * 75).div(104.0)
        map["1"] = arrayOf(pin1_width,pin1_height)
        val pin9_width = (screen_width * 5).div(6.0)
        val pin9_height = (screen_height * 80).div(91.0)
        map["9"] = arrayOf(pin9_width,pin9_height)
        val width_gap = (pin9_width - pin1_width).div(2.0)
        val height_gap = (pin9_height - pin1_height).div(2.0)
        map["2"] = arrayOf(pin1_width.plus(width_gap),pin1_height)
        map["3"] = arrayOf(pin1_width.plus(width_gap * 2),pin1_height)
        map["4"] = arrayOf(pin1_width, pin1_height.plus(height_gap))
        map["5"] = arrayOf(pin1_width.plus(width_gap), pin1_height.plus(height_gap))
        map["6"] = arrayOf(pin1_width.plus(width_gap * 2), pin1_height.plus(height_gap))
        map["7"] = arrayOf(pin1_width, pin1_height.plus(height_gap * 2))
        map["8"] = arrayOf(pin1_width.plus(width_gap), pin1_height.plus(height_gap * 2))
        map["9"] = arrayOf(pin1_width.plus(width_gap * 2), pin1_height.plus(height_gap * 2))
        map["0"] = arrayOf(pin1_width.plus(width_gap), pin1_height.plus(height_gap * 3))
        map["pin_ok"] = arrayOf(pin1_width.plus(width_gap * 2), pin1_height.plus(height_gap * 3))
        saveStringToSharedPreferences(applicationContext, "phonepe_array", map)

        return true
    }

    private fun jioSaavnHome(){
        val desMap = ArrayList<String>()
        getStringFromSharedPreferences(applicationContext,"homeScreenJiosaavn")?.let {
            //Log.d(TAG, it["textAnnotations"].toString()+ "list size")
            val arrayValue = it["textAnnotations"] as? List<*>
            if (arrayValue != null) {
                var i = 0
                for (dp in arrayValue){
                    val subMap = dp as Map<*, *>
                    if(i==1){
                        desMap.add(subMap["description"].toString().lowercase())
                    }
                    i = 1
                }
                val index = indexOfsidebysideString(desMap,"for","you")
                val for_you = substractArray(subtractMaps(arrayValue, index),subtractMaps(arrayValue,(index + 1)))
                Log.d(TAG, for_you[0].toString() +", "+for_you[1].toString())
                t = (for_you[0]?.toFloat() ?: 0) as Float
                m = ((for_you[1]?.toFloat() ?: 0) as Float)
                //Log.d("trackk","t - $t , m - $m")
                //sendBroadcastMessage(t,m)
            }
        }
    }

    fun subtractMaps(list  : List<*>, indexValue : Int): Array<Double?> {
        val array = arrayOfNulls<Double>(2)
        val subMap = list[indexValue] as Map<*,*>
        val subsubMap = subMap["boundingPoly"] as Map<*,*>
        val verticesMap = subsubMap["vertices"] as List<*>
        val map1 = verticesMap[0] as Map<String, Double>
        Log.d(TAG,map1.toString())
        val map2 = verticesMap[2] as Map<String, Double>
        Log.d(TAG,map2.toString())

        // Iterate through the keys of map1
        var i = 0
        for ((key, value) in map1) {
            // Subtract the corresponding value from map2 if the key exists in map2
            array[i] = ((map2[key]?.minus(value))?.div(2))?.plus(value)
            i++
            // Store the result in the result map
            //result[key] = subtractedValue
        }
        return array
    }

    private fun substractArray(array1: Array<Double?>, array2: Array<Double?>) : Array<Double?> {
        val array = arrayOfNulls<Double>(2)
        if(array2[0]?.minus(array1[0]!!) == 0.0) {
            array[0] = array1[0]?.plus(array2[0]?.minus(array1[0]!!)!!)
        }else{
            array[0] = array1[0]?.plus((array2[0]?.minus(array1[0]!!)!!).div(2.0))
        }
        if(array2[1]?.minus(array1[1]!!) == 0.0) {
            array[1] = array1[1]?.plus(array2[1]?.minus(array1[1]!!)!!)
        }else{
            array[1] = array1[1]?.plus((array2[1]?.minus(array1[1]!!)!!).div(2.0))
        }
        return array
    }

    private fun indexOfsidebysideString(arraylist : ArrayList<String>,string1 : String,string2 : String) : Int{
        val specificIndex = arraylist.indexOf(string1)
        var i = -1
        if (specificIndex != -1) {
            // Iterate over the ArrayList starting from the index after the specific string
            for (y in 0 until arraylist.size) {
                if (arraylist[y] == string1) {
                    if(arraylist[y+1] == string2){
                        i = y + 1
                        break
                    }
                }
            }
        }
        return i
    }

    private fun indexOfString(arraylist: ArrayList<String>, string1: String) : Int{
        val specificIndex = arraylist.indexOf(string1)
        var i = -1
        if (specificIndex != -1) {
            // Iterate over the ArrayList starting from the index after the specific string
            for (y in 0 until arraylist.size) {
                if (arraylist[y] == string1) {
                    i = y + 1
                    break
                }
            }
        }
        return i
    }
    private fun indexOfsidebyside3String(arraylist : ArrayList<String>,string1 : String,string2 : String,string3 : String) : Int{
        val specificIndex = arraylist.indexOf(string1)
        var i = -1
        if (specificIndex != -1) {
            // Iterate over the ArrayList starting from the index after the specific string
            for (y in 0 until arraylist.size) {
                if (arraylist[y] == string1) {
                    if(arraylist[y+1] == string2){
                        if (arraylist[y+2] == string3){
                            i = y +1
                            break
                        }
                    }
                }
            }
        }
        return i
    }

    private fun indexOfsidebyside3String_contain(arraylist : ArrayList<String>,string1 : String,string2 : String,string3 : String) : Int{
        val specificIndex = arraylist.indexOf(string1)
        var i = -1
        if (specificIndex != -1) {
            // Iterate over the ArrayList starting from the index after the specific string
            for (y in 0 until arraylist.size) {
                if (arraylist[y] == string1) {
                    if(arraylist[y+1] == string2){
                        if (arraylist[y+2].contains(string3)){
                            i = y +1
                            break
                        }
                    }
                }
            }
        }
        return i
    }



    private fun open_specialOpen(){
        kl.reenableKeyguard()
        //devicePolicyManager.lockNow()
        try {
            Thread.sleep(500)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        if (unlock_varriable) {
            km = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
            Log.d("trackk", getSystemService(KEYGUARD_SERVICE).toString())
            kl = km.newKeyguardLock("MyKeyguardLock")
            kl.reenableKeyguard()
            Thread.sleep(500)
            val pm = applicationContext.getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.FULL_WAKE_LOCK
                        or PowerManager.ACQUIRE_CAUSES_WAKEUP
                        or PowerManager.ON_AFTER_RELEASE, "speech : my wake lock tag"
            )
            wakeLock.acquire(1000)
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
            kl.disableKeyguard()
            Thread.sleep(500)
        }else{
            textToSpeech.speak("your device is in special lock, first unlock your device by voice command",TextToSpeech.QUEUE_FLUSH,null,null)
        }
    }

    private fun searchbox_calibration(){
        var map: MutableMap<String, Any> = mutableMapOf()
        getStringFromSharedPreferences(applicationContext, "Jiosaavn_array")?.let {
            map = it as MutableMap<String, Any>
        }
        val desMap = ArrayList<String>()
        getStringFromSharedPreferences(applicationContext,"Jiosaavn")?.let {
            val arrayValue = it["textAnnotations"] as? List<*>
            if (arrayValue != null) {
                var i = 0
                for (dp in arrayValue) {
                    val subMap = dp as Map<*, *>
                    if (i == 1) {
                        desMap.add(subMap["description"].toString().lowercase())
                    }
                    i = 1
                }
                var index = indexOfsidebyside3String(desMap,"artists",",","and")
                Log.d(TAG,index.toString())
                var for_you = substractArray(subtractMaps(arrayValue, index),subtractMaps(arrayValue,(index + 1)))
                var w = (for_you[0]?.toFloat() ?: 0) as Float
                var z = ((for_you[1]?.toFloat() ?: 0) as Float)
                var c = z
                map["search box"] = arrayOf(w,z)
                index = indexOfsidebysideString(desMap,"see","all")
                for_you = subtractMaps(arrayValue,index)
                w = (for_you[0]?.toFloat() ?: 0) as Float
                map["clearSerachbox"] = arrayOf(w,z)
                index = indexOfsidebysideString(desMap,"recent","search")
                for_you = substractArray(subtractMaps(arrayValue, index),subtractMaps(arrayValue,(index + 1)))
                w = (for_you[0]?.toFloat() ?: 0) as Float
                z = ((for_you[1]?.toFloat() ?: 0) as Float)
                z = z+(z-c)
                map["search1stSong"] = arrayOf(w,z)
                saveStringToSharedPreferences(applicationContext, "Jiosaavn_array", map)
            }
        }
    }
    private fun copyTextToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("label", text)
        clipboard.setPrimaryClip(clip)
    }

    private fun isAppInstalled(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun redirectToPlayStore(packageName: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
            startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
            startActivity(intent)
        }
    }

    private fun phonepe_calibration(){
        open_specialOpen()
        Log.d(TAG, "1")
        deleteDatabase()
        SharedData.is_database_delete = false
        Log.d(TAG, "2")
        MyAccessibilityService.latch_phonepe_home = CountDownLatch(1)  // Initialize the latch
        launchappByPackage("com.phonepe.app")

        // Use coroutines to wait for the JioSaavn main activity to be detected with a manual timeout
        GlobalScope.launch(Dispatchers.Main) {
            var timeout = 10L  // Timeout in seconds
            var detected = waitForLatchWithTimeout(MyAccessibilityService.latch_phonepe_home!!, timeout, TimeUnit.SECONDS)
            if (detected) {
                var phonpe_current_state = MyAccessibilityService.phonepe_activity_state
                if (phonpe_current_state > 0) {
                    if (phonpe_current_state == 4){
                        sendBroadcastMessage_for_back_button()
                        delay(500)
                        sendBroadcastMessage_for_back_button()
                        delay(500)
                    }
                    sendBroadcastMessage_for_back_button()
                    delay(500)
                    sendBroadcastMessage_for_back_button()
                    delay(500)
                    sendBroadcastMessage_for_back_button()
                    delay(500)
                    sendBroadcastMessage_for_back_button()
                    delay(1000)
                    MyAccessibilityService.latch_phonepe_home = CountDownLatch(1)  // Initialize the latch
                    launchappByPackage("com.phonepe.app")
                    timeout = 10L  // Timeout in seconds
                    detected = waitForLatchWithTimeout(MyAccessibilityService.latch_phonepe_home!!, timeout, TimeUnit.SECONDS)
                    if (detected) {
                        Log.d(TAG, "3")
                        delay(3000)
                        takescreenshot()
                        delay(2000)
                        if(uploadFile_new("Jiosaavn")){
                            if (phonepe_calibration_home_home()){
                                touch_new("money","to","mobile")
                                //execute("to mobile","phonepe_array")
                                //launchPhonePePayment(applicationContext)
                                delay(4000)
                                if(uploadFile_new("Jiosaavn")){
                                    if (phonepe_calibration_toMobile()){
                                        execute("search_box","phonepe_array")
                                        delay(3000)
                                        if (uploadFile_new("Jiosaavn")){
                                            if(phonepe_calibration_toMobile_search()){
                                                copyTextToClipboard("9733774436@ybl")
                                                longExecute("search_box2","phonepe_array")
                                                delay(3000)
                                                touch_save_paste("search_box2paste")
                                                delay(2000)
                                                touch_special_for_upiID()
                                                delay(2000)
                                                if (uploadFile_new("Jiosaavn")){
                                                    if(phonepe_calibration_enter_amount()){
                                                        copyTextToClipboard("1")
                                                        longExecute("enter_amount","phonepe_array")
                                                        delay(3000)
                                                        touch_save_paste("enter_amount_paste")
                                                        delay(2000)
                                                        if (uploadFile_new("Jiosaavn")){
                                                            if(phonepe_calibration_enter_amount_pay()){
                                                                execute("enter_amount_pay","phonepe_array")
                                                                UPI_pin_position_cal()
                                                                delay(4000)
                                                                processPassword("9832")
                                                                delay(10000)
                                                                search_box_delete_text()
                                                                sendBroadcastMessage_for_back_button()
                                                                delay(500)
                                                                sendBroadcastMessage_for_back_button()
                                                                delay(500)
                                                                sendBroadcastMessage_for_back_button()
                                                                delay(500)
                                                                sendBroadcastMessage_for_back_button()
                                                                delay(500)
                                                                sendBroadcastMessage_for_back_button()
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }else{
                                    textToSpeech.speak("Please check your internet connection",TextToSpeech.QUEUE_FLUSH,null,null)
                                }
                            }
                        }
                    }
                }
            } else {
                textToSpeech.speak("Some intentionally touch done on screen or please check your network or please turn on accessibility service and then off and again on and check",TextToSpeech.QUEUE_ADD,null,null)
            }
        }
    }

    fun sendBroadcastMessage2(message: String) {
        // Create an Intent with the custom action and message
        /**Log.d("TAG" , "In sendBroadcastMessage2")
        val intent = Intent("com.example.yourapp.BROADCAST_ACTION")
        intent.putExtra("message", message)

        // Send the broadcast
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
         **/
        // Create an Intent with the custom action and message
        val intent = Intent("com.example.yourapp.BROADCAST_ACTION")
        intent.putExtra("message", message)

        // Send the broadcast
        applicationContext.sendBroadcast(intent)
    }

    fun sendBroadcastMessage_speechResult(message: String) {
        val intent = Intent("com.example.yourapp.BROADCAST_ACTION_speechResult")
        intent.putExtra("message", message)

        // Send the broadcast
        applicationContext.sendBroadcast(intent)
    }
    fun sendBroadcastMessage_wit_speechResult(message: String) {
        val intent = Intent("com.example.yourapp.BROADCAST_ACTION_wit_recipient_name")
        intent.putExtra("message", message)

        // Send the broadcast
        applicationContext.sendBroadcast(intent)
    }
    fun sendBroadcastMessage_device_speechResult(message: String) {
        val intent = Intent("com.example.yourapp.BROADCAST_ACTION_device_recipient_name")
        intent.putExtra("message", message)

        // Send the broadcast
        applicationContext.sendBroadcast(intent)
    }
    private suspend fun waitForLatchWithTimeout(latch: CountDownLatch, timeout: Long, unit: TimeUnit): Boolean {
        return withContext(Dispatchers.IO) {
            latch.await(timeout, unit)
        }
    }

    fun launchPhonePePayment(context: Context) {
        val upiUri = Uri.Builder()
            .scheme("upi")
            .authority("pay")
            .appendQueryParameter("pa", "9733774436@ybl") // Replace with the actual UPI ID
            .appendQueryParameter("pn", "Payee")      // Generic payee name
            .appendQueryParameter("tid", "txn123456") // Unique transaction ID (optional but recommended)
            .appendQueryParameter("tr", "txnref123")  // Transaction reference ID (optional but recommended)
            .appendQueryParameter("tn", "Payment")    // Transaction note
            .appendQueryParameter("am", "1")           // Amount (leave empty for user to enter)
            .appendQueryParameter("cu", "INR")        // Currency
            .build()

        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = upiUri
            setPackage("com.google.android.apps.nbu.paisa.user") // Specify the PhonePe package
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            //Toast.makeText(context, "PhonePe app is not installed", Toast.LENGTH_SHORT).show()
        }
    }

    suspend fun processDigit(digit: Char) {
        execute(digit.toString(),"phonepe_array")
        delay(1000)

        // Add your logic here to handle the digit
    }

    suspend fun processPassword(password: String) {
        //require(password.length == 6) { "Password must be 6 digits long" }
        for (digit in password) {
            processDigit(digit)
        }
        execute("pin_ok","phonepe_array")
    }

    private fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(audioAttributes)
                .setOnAudioFocusChangeListener { focusChange ->
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                        // Handle loss of audio focus if necessary
                        releaseAudioFocus()
                    }
                }
                .build()

            audioFocusRequest = focusRequest
            audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun releaseAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            audioManager.abandonAudioFocus(null)
        }
    }

    private fun speakText(resText: String, utteranceId: String) {
        if (requestAudioFocus()) {
            textToSpeech.speak(resText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        }
    }
    private fun splitString(input: String): List<String> {
        val delimiters = arrayOf(" ", "\n")
        return input.split(*delimiters)
            .filter { it.isNotEmpty() }
    }

    private fun findBestMatch(input: String, contacts: List<Contact>): List<Contact> {
        val inputWords = splitSentence(input)
        var check = mutableListOf<Contact>()
        var bestDistance = Int.MAX_VALUE
        var re_string : String = ""

        // Initial check for the first word
        if (inputWords.isNotEmpty()) {
            val firstWord = inputWords[0]
            for (contact in contacts) {
                val contactWords = splitSentence(contact.name.lowercase())
                for (contactWord in contactWords) {
                    val distance = levenshteinDistance(firstWord, contactWord)
                    if (distance < bestDistance) {
                        bestDistance = distance
                        check.clear()
                        re_string = ""
                        re_string = contactWord
                        check.add(contact)
                    } else if (distance == bestDistance && contact !in check) {
                        check.add(contact)
                    }
                }
            }
        }

        // Iterate over the remaining input words
        var fi_fi_string : String = ""
        for (i in 1 until inputWords.size) {
            val currentWord = inputWords[i]
            val nextCheck = mutableListOf<Contact>()
            var next_re_string : String = ""
            bestDistance = Int.MAX_VALUE

            for (contact in check) {
                val contactWords = splitSentence(contact.name.lowercase())
                for (contactWord in contactWords) {
                    val distance = levenshteinDistance(currentWord, contactWord)
                    if (distance < bestDistance) {
                        bestDistance = distance
                        nextCheck.clear()
                        next_re_string = ""
                        next_re_string = contactWord
                        nextCheck.add(contact)
                    } else if (distance == bestDistance && contact !in nextCheck) {
                        nextCheck.add(contact)
                    }
                }
            }
            check = nextCheck
            fi_fi_string = fi_fi_string + next_re_string + " "
        }
        fi_fi_string = re_string + " " + fi_fi_string
        check.add(Contact("fi_fi_fi_fi_fi_fi_fi_fi_fi_fi_fi_fi_fi_fi_fi_fi_fi_fi_fi_fi_string",fi_fi_string.trim()))
        return check
    }
    private fun findBestMatch_by_leven_only(input: String, contacts: List<Contact>): List<Contact> {
        var bestDistance = Int.MAX_VALUE
        val bestMatches = mutableListOf<Contact>()

        for (contact in contacts) {
            val distance = levenshteinDistance(input, contact.name.lowercase())
            if (distance < bestDistance) {
                bestDistance = distance
                bestMatches.clear() // Clear the list if a new best distance is found
                bestMatches.add(contact)
            } else if (distance == bestDistance) {
                bestMatches.add(contact) // Add to the list if the distance is equal to the best distance
            }
        }
        bestMatches.add(Contact("distnce", bestDistance.toString()))

        return bestMatches
    }

    fun splitSentence(sentence: String): List<String> {
        return sentence.split("\\s+".toRegex()).filter { it.isNotEmpty() }
    }

    private fun levenshteinDistance(lhs: String, rhs: String): Int {
        val lhsLength = lhs.length
        val rhsLength = rhs.length
        var cost = Array(lhsLength + 1) { it }
        var newCost = Array(lhsLength + 1) { 0 }

        for (i in 1..rhsLength) {
            newCost[0] = i

            for (j in 1..lhsLength) {
                val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1

                val costReplace = cost[j - 1] + match
                val costInsert = cost[j] + 1
                val costDelete = newCost[j - 1] + 1

                newCost[j] = minOf(costInsert, costDelete, costReplace)
            }

            val swap = cost
            cost = newCost
            newCost = swap
        }

        return cost[lhsLength]
    }

    private fun processString_for_recent_list(input: String): MutableList<String> {

        //val datePatternShort = Regex("\\b\\d{2}/\\d{2}\\b")
        //val datePatternLong = Regex("\\b\\d{2}/\\d{2}/\\d{2}\\b")
        val datePattern = Regex("\\b(\\d{4}\\s(am|pm)|(\\d{2}/\\d{2}/\\d{2}|\\d{2}/\\d{2}))\\b")
        val conditionPattern = Regex("(instantly|securely|failed)", RegexOption.IGNORE_CASE)
        val recentList = mutableListOf<String>()

        var previousIndex = 0
        var st_i  = 0

        datePattern.findAll(input).forEach { matchResult ->
            val start = matchResult.range.first
            val end = matchResult.range.last
            val date = matchResult.value

            Log.d("trackk - Found Date", date)
            if (st_i == 0) {
                // Extract the name before the date
                val name = input.substring(previousIndex, start).trim()
                Log.d("trackk - Name Before Date", name)
                previousIndex = end + 1

                if (name.isNotEmpty()) {
                    recentList.add(name)
                }
            } else {
                val conditionMatch = conditionPattern.find(input.substring(previousIndex))
                val conditionEnd = conditionMatch?.range?.last ?: start
                val nextName = input.substring(conditionEnd + 1 + previousIndex, start).trim()
                previousIndex = end + 1

                Log.d("trackk - Condition Match", conditionMatch?.value.toString())
                Log.d("trackk - Name After Condition", nextName)

                if (nextName.isNotEmpty()) {
                    recentList.add(nextName)
                }
            }
            st_i++
        }
        return recentList
    }

    private fun processString_allContact(input: String): MutableList<String> {
        val phonePattern = Regex("\\+?\\b\\w*\\d\\w*\\d\\w*\\d\\w*\\d\\w*\\d\\w*\\d\\w*\\b")
        val contactList = mutableListOf<String>()
        var previousIndex = 0

        phonePattern.findAll(input).forEach { matchResult ->
            val start = matchResult.range.first
            val end = matchResult.range.last
            val phone = matchResult.value

            Log.d("trackk - Found Phone Number", phone)

            // Extract the name before the phone number
            val name = input.substring(previousIndex, start).trim()
            Log.d("trackk - Name Before Phone", name)
            previousIndex = end + 1

            if (name.isNotEmpty()) {
                contactList.add(name)
            }
        }

        return contactList
    }

    private fun updateContactList(sharedPreferencesHelper: SharedPreferencesHelper) {
        val contactList1 = sharedPreferencesHelper.getContacts("contact_list")
        contactList_only = contactList1
        Log.d(TAG + "contactList_only",contactList_only.toString())
        val contactList2 = sharedPreferencesHelper.getContacts("contact_list_transaction")

        val newContacts = contactList1.filter { contact1 ->
            contactList2.none { contact2 -> contact2.number == contact1.number }
        }

        if (newContacts.isNotEmpty()) {
            val updatedContactList2 = contactList2.toMutableList()
            updatedContactList2.addAll(newContacts)
            sharedPreferencesHelper.saveContacts("contact_list_transaction", updatedContactList2)
            contactList_transaction = updatedContactList2
            Log.d(TAG + " contactList_tra",contactList_transaction.toString())
        }
    }

    private fun syncContacts(firestoreHelper: FirestoreHelper, sharedPreferencesHelper: SharedPreferencesHelper) {
        firestoreHelper.getContacts { firebaseContacts, firebaseError ->
            if (firebaseContacts != null) {
                val sharedPrefsContacts = sharedPreferencesHelper.getContacts("add_transaction")
                Log.d(TAG + " add_transaction in phone",sharedPrefsContacts.toString())

                // Find new contacts in SharedPreferences not in Firebase
                val newInSharedPrefs = sharedPrefsContacts.filter { contact ->
                    firebaseContacts.none { it.number == contact.number }
                }

                // Find new contacts in Firebase not in SharedPreferences
                val newInFirebase = firebaseContacts.filter { contact ->
                    sharedPrefsContacts.none { it.number == contact.number }
                }

                // Update Firebase with new contacts from SharedPreferences
                newInSharedPrefs.forEach { contact ->
                    firestoreHelper.saveContact(contact) { success, message ->
                        if (!success) {
                            Log.e("syncContacts", "Error saving contact to Firebase: $message")
                        }
                    }
                }

                // Update SharedPreferences with new contacts from Firebase
                if (newInFirebase.isNotEmpty()) {
                    val updatedContacts = sharedPrefsContacts.toMutableList()
                    updatedContacts.addAll(newInFirebase)
                    sharedPreferencesHelper.saveContacts("add_transaction",updatedContacts)
                    add_transaction = updatedContacts
                    Log.d(TAG + " add_transaction in fire",add_transaction.toString())
                }
            } else {
                Log.e("syncContacts", "Error getting contacts from Firebase: $firebaseError")
            }
        }
    }
    private fun identifyAndDeleteDuplicatesByName() {
        firestoreHelper.getdContacts { documents, error ->
            if (documents != null) {
                val seenNames = mutableSetOf<String>()
                val duplicates = mutableListOf<DocumentSnapshot>()

                for (document in documents) {
                    val name = document.toObject(Contact::class.java)?.number
                    if (name != null) {
                        if (!seenNames.add(name)) {
                            duplicates.add(document)
                        }
                    }
                }

                // Now delete the duplicates
                deleteDuplicates(duplicates)
            } else {
                Log.w("Firestore", "No documents found or error in fetching documents: $error")
            }
        }
    }

    private fun deleteDuplicates(duplicates: List<DocumentSnapshot>) {
        for (duplicate in duplicates) {
            firestoreHelper.db.collection("contacts").document(duplicate.id)
                .delete()
                .addOnSuccessListener { Log.d("Firestore", "DocumentSnapshot successfully deleted!") }
                .addOnFailureListener { e -> Log.w("Firestore", "Error deleting document", e) }
        }
    }

    fun enqueueResult(value: String) {
        GlobalScope.launch {
            channel.send(ResultData(value))
        }
    }

    private suspend fun result(data : ResultData) {
        var wait_vr = 0
        val action = data.value
        sendBroadcastMessage_speechResult(action)
        Log.d(TAG,action)
        utteranceId = UUID.randomUUID().toString()

        /**GlobalScope.launch {
        delay(2000)
        uploadFile("Jiosaavn")
        Log.d(TAG, "7")
        delay(20000)
        Log.d(TAG, "8")
        home_calibration()
        }
        getContactList(applicationContext)?.let { contactList ->
        if (contactList.isNotEmpty()) {
        //Log.d(TAG, contactList.last().number)
        findBestMatch("simanchal",contactList)?.let {
        Log.d(TAG, it.toString())
        //Log.d(TAG,it[0].number)
        Log.d(TAG, it.last().number)
        }
        } else {
        Log.d(TAG, "Contact list is empty")
        }
        }
        GlobalScope.launch {
        delay(2000)
        takescreenshot()
        delay(1000)
        //var position = tesseractOCR.recognizeText_contain("tor",getPngBitmap(mStoreDir + "myscreen_0.png"))
        //delay(1000)
        //Log.d(TAG, position.toString())
        //var position = tesseractOCR.recognizeText_contain("recents",getPngBitmap(mStoreDir + "myscreen_0.png"))
        //delay(1000)
        //Log.d(TAG, position.toString())
        //position = tesseractOCR.recognizeText_contain("contacts",getPngBitmap(mStoreDir + "myscreen_0.png"))
        //delay(1000)
        //Log.d(TAG, position.toString())
        val string =
        tesseractOCR.recognizeText_from_crop_photo(getPngBitmap(mStoreDir + "myscreen_0.png"))
        //val position = tesseractOCR.recognizeText_return_all(getPngBitmap(mStoreDir + "myscreen_0.png"))
        val cleanedText = string.nsstring2.replace("\n", " ")
        val cleanedLine = cleanedText.trim().replace("[^a-zA-Z0-9+/.(), ]".toRegex(), "")
        .replace("...", "").replace("rw LJ", "").trim().toLowerCase(
        Locale.ROOT
        )
        Log.d(TAG, " cleanned line " + cleanedLine)
        //val cleanF = cleanedLine.replace("\\b[a-zA-Z]\\b".toRegex(), "") // Remove single alphabetic characters


        //.replace("\\b[a-zA-Z]\\b".toRegex(), "") // Remove single alphabetic characters
        val (recentList, contactList) = processString(cleanedLine)

        // Debugging: Log the results
        recentList.forEach { Log.d("trackk - Recent List", it) }
        contactList.forEach { Log.d("trackk - Contact List", it) }
        getContactList(applicationContext)?.let { contactList ->
        if (contactList.isNotEmpty()) {
        Log.d(TAG, contactList[1].name)
        //findBestMatch_by_leven_only()
        findBestMatch_by_leven_only(recentList[0], contactList)?.let {
        Log.d(TAG + "fi_fi_result", it.name)
        //recipient_name_final = it.last().number
        latch = CountDownLatch(1)
        //val utteranceId = UUID.randomUUID().toString()
        speakText(
        "Sir, do you want to send ${amt.toInt()} rupees to ${it.name}",
        utteranceId
        )
        // Wait for the speech to finish
        try {
        latch.await()
        } catch (e: InterruptedException) {
        e.printStackTrace()
        }
        }
        }
        }
        }**/
        // Handle the callback action here
        //Log.d("trackk", "Received callback: $action" + System.currentTimeMillis())
        val trainingData = JSONArray().apply {
            put(JSONObject().apply {
                put("text", "send 300 rupees to tanmay")
                put("intent", "money_transfer")
                put("entities", JSONArray().apply {
                    put(JSONObject().apply {
                        put("entity", "money_Recipient:money_Recipient")
                        put("start", 19)
                        put("end", 25)
                        put("body", "tanmay")
                        put("entities", JSONArray())
                    })
                    put(JSONObject().apply {
                        put("entity", "wit"+"$"+"amount_of_money:amount_of_money")
                        put("start", 5)
                        put("end", 15)
                        put("body", "300 rupees")
                        put("entities", JSONArray())
                    })
                })
                put("traits", JSONArray())
            })
        }

        /**witAiHelper.trainModel(trainingData) { success, message ->
        if (success) {
        Log.d("TAG", "Success: $message")
        } else {
        Log.d("TAG", "Failure: $message")
        }
        }
        val entityName = "money_Recipient"
        witAiHelper.createEntity(entityName, token) { success, message ->
        if (success) {
        Log.d("TAG", "Success: $message")
        } else {
        Log.d("TAG", "Failure: $message")
        }
        }**/

        val contact = Contact(name = "John Doe", number = "+1234567890")
        Log.d(TAG+ " contactList_transaction size",contactList_transaction.size.toString())

        val contextMapp = mapOf("shape_color" to "red")
        witAiHelper.sendWitRequest(sessionId, contextMapp, action, token) { witResponse ->
            witResponse?.let {
                println("Final response: ${it.response?.text}")
                val res_text = it.response?.text
                if(res_text != null){
                    latch = CountDownLatch(1)
                    //val utteranceId = UUID.randomUUID().toString()
                    speakText(res_text,utteranceId)
                    // Wait for the speech to finish
                    try {
                        latch.await()
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                }
                if(it.action == "input - iubhs"){

                }

                val keywords = listOf("send_money", "more_option", "input","money_transfer")
                var containsKeyword = false
                if(it.action != null){
                    containsKeyword = keywords.any { keyword -> it.action.contains(keyword, ignoreCase = true) }
                }

                if(containsKeyword){
                    if(sharedPreferencesHelper.getBoolean("isPhonepeFeaturesOn")){
                        if(it.action == "send_money"){
                            GlobalScope.launch(Dispatchers.Main) {
                                /**if(amt_change){
                                open_specialOpen()
                                simulate_HomeButton()
                                Thread.sleep(1000)
                                //simulate_HomeButton()
                                MyAccessibilityService.latch_phonepe_home = CountDownLatch(1)  // Initialize the latch
                                launchappByPackage("com.phonepe.app")
                                val timeout = 10L  // Timeout in seconds
                                val detected = waitForLatchWithTimeout(
                                MyAccessibilityService.latch_phonepe_home!!,
                                timeout,
                                TimeUnit.SECONDS
                                )
                                if (detected) {
                                backTo_number_method()
                                delay(500)
                                } else {
                                speakText("Some intentionally touch done on screen or please check your network or please turn on accessibility service and then off and again on and check",
                                utteranceId)
                                }
                                }**/
                                if(mor_option_activated == false) {
                                    execute("search_box_delete_text","phonepe_array")
                                    copyTextToClipboard(recent_payment_number)
                                    delay(500)
                                    longExecute("search_box2","phonepe_array")
                                    delay(2000)
                                    execute("search_box2paste","phonepe_array")
                                    delay(2000)
                                    execute("exist_contact_touch","phonepe_array")
                                    delay(2000)
                                    copyTextToClipboard(amt.toInt().toString())
                                    longExecute("enter_amount","phonepe_array")
                                    delay(3000)
                                    execute("enter_amount_paste","phonepe_array")
                                    delay(1500)
                                }
                                delay(500)
                                execute("enter_amount_pay","phonepe_array")
                                delay(4000)
                                processPassword("9832")
                                delay(10000)
                                amt_change = false
                                wait_vr = 1
                            }


                            /**getStringFromSharedPreferences(applicationContext, "phonepe_array")?.let {
                            Log.d(TAG, "get string from pref - $it")
                            if (it["money_appVertical_distance"] != null) {
                            val array = it["money_appVertical_distance"] as ArrayList<Float>
                            val array1 = it["money_appHorizontal_distance"] as ArrayList<Float>
                            val d = (screenSize.first.div(2.0)).toFloat()
                            val d1 = (screenSize.first - array1[0])
                            val array2 = it["search_box2"] as ArrayList<Float>
                            Log.d(TAG, "input position${d} and ${array2[1].plus(4 * array[0])}")
                            Log.d(TAG, "input cancel position ${d1} and ${array2[1]}")
                            sendBroadcastMessage(d1, (array2[1]), 100)
                            copyTextToClipboard(recent_payment_number)
                            GlobalScope.launch(Dispatchers.Main) {
                            delay(500)
                            longExecute("search_box2","phonepe_array")
                            delay(2000)
                            execute("search_box2paste","phonepe_array")
                            delay(2000)
                            sendBroadcastMessage(d, (array2[1].plus(4 * array[0])), 100)
                            delay(2000)
                            copyTextToClipboard(amt.toInt().toString())
                            longExecute("enter_amount","phonepe_array")
                            delay(3000)
                            execute("enter_amount_paste","phonepe_array")
                            delay(2000)
                            execute("enter_amount_pay","phonepe_array")
                            delay(4000)
                            processPassword("9832")
                            delay(10000)
                            }

                            }
                            } **/
                        }

                        if(it.action == "more_option"){
                            if(recent_contact_list_sort_by_voice.size > 2){
                                GlobalScope.launch(Dispatchers.Main) {
                                    open_specialOpen()
                                    simulate_HomeButton()
                                    Thread.sleep(1000)
                                    //simulate_HomeButton()
                                    MyAccessibilityService.latch_phonepe_home = CountDownLatch(1)  // Initialize the latch
                                    launchappByPackage("com.phonepe.app")
                                    val timeout = 10L  // Timeout in seconds
                                    val detected = waitForLatchWithTimeout(
                                        MyAccessibilityService.latch_phonepe_home!!,
                                        timeout,
                                        TimeUnit.SECONDS
                                    )
                                    if (detected) {
                                        backTo_number_method()
                                        delay(500)
                                    } else {
                                        speakText("Some intentionally touch done on screen or please check your network or please turn on accessibility service and then off and again on and check",
                                            utteranceId)
                                    }

                                    if(recent_contact_list_sort_by_voice.size > 2){
                                        mor_option_activated = true
                                        sendBroadcastMessage_device_speechResult(recent_contact_list_sort_by_voice.joinToString(", "))
                                        recent_contact_list_sort_by_voice = recent_contact_list_sort_by_voice.filter { contact -> contact.number != recent_payment_number }
                                        phonepe_name_identification_for_moreOption(recent_contact_list_sort_by_voice,recent_processedWit_name)
                                    }

                                    //recent_processedWit_name
                                    //recent_wit_name
                                    //recent_contact_list_sort_by_voice
                                }
                            } else {
                                val contextMapp = mapOf("shape_color" to "red")
                                witAiHelper.sendWitRequest(sessionId, contextMapp, "silent", token) {
                                }
                                latch = CountDownLatch(1)
                                speakText("Sir, there are no contacts available similar with $recent_wit_name ",utteranceId)
                                // Wait for the speech to finish
                                try {
                                    latch.await()
                                } catch (e: InterruptedException) {
                                    e.printStackTrace()
                                }
                            }
                        }

                        if(it.action == "input"){
                            mor_option_activated = false
                            amt = 0.0
                            wait_vr = 0
                            val contextMap = witResponse.context_map.toMutableMap()
                            // Retrieve money recipient
                            var recipient_name = ""
                            if(contextMap["money_recipient"] != null){
                                val moneyRecipientEntity = contextMap["money_recipient"] as List<*>
                                val moneyRecipient = moneyRecipientEntity.get(0) as Map<String,Any>
                                recipient_name = moneyRecipient["value"].toString().lowercase()
                                recent_wit_name = recipient_name
                            }
                            var recipient_name_final= ""
                            var contact_list_sort_by_voice : List<Contact> = listOf()

                            if (contextMap.containsKey("amout_of_money_inNumber")) {
                                val amout_of_money_inNumber = contextMap["amout_of_money_inNumber"] as List<*>
                                val of_money_inNumber = amout_of_money_inNumber[0] as Map<String,Any>
                                val money_inNumber = of_money_inNumber["value"] as Double
                                amt = amt + money_inNumber
                                Log.d(TAG,"recipient_name - $money_inNumber")
                                Log.d(TAG,"recipient_name - ${amout_of_money_inNumber.size}")
                                if(amout_of_money_inNumber.size>1){
                                    Log.d(TAG,"recipient_name - $amout_of_money_inNumber")
                                    val of_money_inNumber1 = amout_of_money_inNumber[1] as Map<String,Any>
                                    val money_inNumber1 = of_money_inNumber1["value"] as Double
                                    amt = amt + money_inNumber1
                                    Log.d(TAG,"recipient_name - $money_inNumber1")
                                }
                            }
                            if (contextMap.containsKey("amout_of_money")) {
                                val amout_of_money = contextMap["amout_of_money"] as List<*>
                                //sendBroadcastMessage_device_speechResult(amout_of_money.joinToString())
                                val of_money = amout_of_money[0] as Map<String,Any>
                                val money_amount = of_money["value"] as Double
                                amt = amt + money_amount
                                Log.d(TAG,"recipient_name - $money_amount")
                            }
                            Log.d(TAG,"final_amount - $amt")

                            if((recipient_name.toString() == "")|| (recipient_name.toString().isEmpty())){
                                sendBroadcastMessage_wit_speechResult(recent_payment_name)
                                //amt = recent_amt
                                amt_change = true
                                latch = CountDownLatch(1)
                                speakText("Sir, do you want to send ${amt.toInt()} rupees to ${recent_payment_name}",utteranceId)
                                // Wait for the speech to finish
                                try {
                                    latch.await()
                                } catch (e: InterruptedException) {
                                    e.printStackTrace()
                                }
                                if(bluetooth_connection_state){
                                    connectBluetooth()
                                    sendBroadcastMessage2("hi")
                                    Log.d(TAG + "bluetooth_connection_state", bluetooth_connection_state.toString())
                                } else{
                                    sendBroadcastMessage2("hi")
                                }
                                //recent_payment_number = contact_list_sort_by_voice.first().number
                                //recent_payment_name = contact_list_sort_by_voice.first().name
                            } else if (amt == 0.0){
                                amt = recent_amt
                                sendBroadcastMessage_wit_speechResult(recipient_name.toString())
                                if (contactList_transaction.isNotEmpty()) {
                                    //Log.d(TAG, contactList[1].name)
                                    //findBestMatch_by_leven_only()
                                    findBestMatch(recipient_name,contactList_transaction)?.let {
                                        Log.d(TAG + "contact_list_sort_by_voice", it.toString())
                                        contact_list_sort_by_voice = it
                                        if(it.size == 2){
                                            recipient_name_final = it.first().name
                                        } else{
                                            val inputWords = splitSentence(recipient_name.toString())
                                            if(inputWords.size>1){
                                                val string = splitSentence(it.first().name)
                                                var j = 0
                                                for(i in 0 until inputWords.size){
                                                    if(j == 0){
                                                        recipient_name_final = string[i]
                                                    }else{
                                                        recipient_name_final =" " + string[i]
                                                    }
                                                    j++
                                                }
                                                recipient_name_final = string[0] + " " + string[1]
                                            }else{
                                                recipient_name_final = it.last().number
                                            }

                                        }
                                    }

                                } else {
                                    Log.d(TAG, "Contact list is empty")
                                }
                                Log.d(TAG,"recipient_name - $recipient_name")
                                //sendBroadcastMessage_device_speechResult(recipient_name_final)
                            } else {
                                sendBroadcastMessage_wit_speechResult(recipient_name.toString())
                                if (contactList_transaction.isNotEmpty()) {
                                    //Log.d(TAG, contactList[1].name)
                                    //findBestMatch_by_leven_only()
                                    findBestMatch(recipient_name,contactList_transaction)?.let {
                                        Log.d(TAG + "contact_list_sort_by_voice", it.toString())
                                        contact_list_sort_by_voice = it
                                        if(it.size == 2){
                                            recipient_name_final = it.first().name
                                        } else{
                                            val inputWords = splitSentence(recipient_name.toString())
                                            if(inputWords.size>1){
                                                val string = splitSentence(it.first().name)
                                                var j = 0
                                                for(i in 0 until inputWords.size){
                                                    if(j == 0){
                                                        recipient_name_final = string[i]
                                                    }else{
                                                        recipient_name_final =" " + string[i]
                                                    }
                                                    j++
                                                }
                                                recipient_name_final = string[0] + " " + string[1]
                                            }else{
                                                recipient_name_final = it.last().number
                                            }

                                        }
                                    }

                                } else {
                                    Log.d(TAG, "Contact list is empty")
                                }
                                Log.d(TAG,"recipient_name - $recipient_name")
                                //sendBroadcastMessage_device_speechResult(recipient_name_final)
                            }

                            contact_list_sort_by_voice = contact_list_sort_by_voice.map { contact ->
                                val cleanedNumber = contact.number.replace("\\s".toRegex(), "") // Remove all whitespace
                                val lastTenDigits = cleanedNumber.takeLast(10).filter { it.isDigit() } // Extract last 10 digits and remove non-digit characters
                                contact.copy(number = lastTenDigits)
                            }


                            recent_processedWit_name = recipient_name_final

                            recent_contact_list_sort_by_voice = contact_list_sort_by_voice

                            if(contact_list_sort_by_voice.size == 2){
                                latch = CountDownLatch(1)
                                speakText("Sir, do you want to send ${amt.toInt()} rupees to ${recipient_name_final}",utteranceId)
                                // Wait for the speech to finish
                                try {
                                    latch.await()
                                } catch (e: InterruptedException) {
                                    e.printStackTrace()
                                }
                                if(bluetooth_connection_state){
                                    connectBluetooth()
                                    sendBroadcastMessage2("hi")
                                    Log.d(TAG + "bluetooth_connection_state", bluetooth_connection_state.toString())
                                } else{
                                    sendBroadcastMessage2("hi")
                                }
                                recent_amt = amt
                                recent_payment_number = contact_list_sort_by_voice.first().number
                                recent_payment_name = contact_list_sort_by_voice.first().name
                            }
                            val phonePePackageName = "com.phonepe.app"
                            //launchappByPackage(phonePePackageName)
                            if (isAppInstalled(phonePePackageName)) {

                            } else {
                                Log.d(TAG,"redirect play")
                                // Handle the case where PhonePe is not installed
                                //redirectToPlayStore(phonePePackageName)
                            }
                            open_specialOpen()
                            simulate_HomeButton()
                            Thread.sleep(1000)
                            //simulate_HomeButton()
                            MyAccessibilityService.phonepe_activity_state = 0
                            MyAccessibilityService.latch_phonepe_home = CountDownLatch(1)  // Initialize the latch
                            launchappByPackage("com.phonepe.app")

                            // Use coroutines to wait for the JioSaavn main activity to be detected with a manual timeout
                            GlobalScope.launch(Dispatchers.Main) {
                                var timeout = 10L  // Timeout in seconds
                                var detected = waitForLatchWithTimeout(
                                    MyAccessibilityService.latch_phonepe_home!!,
                                    timeout,
                                    TimeUnit.SECONDS
                                )
                                if (detected) {
                                    backTo_number_method()
                                    delay(500)
                                    phonepe_name_identification(contact_list_sort_by_voice,recipient_name_final, utteranceId)
                                    /**Log.d(TAG, "delay start")
                                    getStringFromSharedPreferences(applicationContext, "phonepe_array")?.let {
                                    Log.d(TAG,"get string from pref - $it")
                                    if(it["money_appVertical_distance"] != null){
                                    val array = it["money_appVertical_distance"] as ArrayList<Float>
                                    val d = (screenSize.first.div(2.0)).toFloat()
                                    val array2 = it["search_box2"] as ArrayList<Float>
                                    Log.d(TAG,"input position${d} and ${array2[1].plus(4 * array[0])}")
                                    sendBroadcastMessage(d, (array2[1].plus(4 * array[0])),100)
                                    delay(5000)
                                    val d_height = (screenSize.second.div(20.0)).toFloat()
                                    sendBroadcastMessage(d,d_height,100)
                                    Log.d(TAG, "delay end")
                                    delay(2000)
                                    takescreenshot()
                                    var s  = ""
                                    delay(2000)
                                    val position = tesseractOCR.recognizeText_return_name(getPngBitmap(mStoreDir + "myscreen_0.png"))
                                    val words = splitString(position.nsstring1)
                                    if(words.size<4){
                                    s = position.nsstring2
                                    } else {
                                    var  wl = 0
                                    words.forEach {
                                    if (it.equals("manage", ignoreCase = true)) {
                                    if ((wl - 6) > 0) {
                                    for (i in (wl - 6) until wl) {
                                    s = s + words[i] + " "
                                    }
                                    } else {
                                    for (i in 0 until wl) {
                                    s = s + words[i] + " "                                }
                                    }
                                    }
                                    wl++
                                    }
                                    }
                                    Log.d(TAG, s)
                                    Log.d(TAG,"after s determined")
                                    contextMap = mutableMapOf("shape_color" to "red")
                                    // Add more key-value pairs
                                    contextMap["amout_of_money"] = amt.toString()
                                    witAiHelper.sendWitRequest(sessionId, contextMap, s, token) { witResponse ->
                                    witResponse?.let {
                                    println("Final response: ${it.response?.text}")
                                    val res_text = it.response?.text
                                    if(res_text != null){
                                    latch = CountDownLatch(1)
                                    //val utteranceId = UUID.randomUUID().toString()
                                    speakText(res_text,utteranceId)
                                    // Wait for the speech to finish
                                    try {
                                    latch.await()
                                    } catch (e: InterruptedException) {
                                    e.printStackTrace()
                                    }
                                    }
                                    }
                                    }
                                    //Log.d(TAG, touch_by_id())
                                    //touch_new("to","mobile")

                                    } else{
                                    Log.d(TAG, "nullllllllllllllllllllll")
                                    }
                                    }**/
                                } else {
                                    speakText("Some intentionally touch done on screen or please check your network or please turn on accessibility service and then off and again on and check",utteranceId)
                                }
                                wait_vr = 1
                            }
                            //phonepe_calibration()

                            //sendBroadcastMessage2("hi")
                        } else {
                            //audioHelper.initClassifier()
                        }
                        //audioHelper.initClassifier()
                        if(it.action == "money_transfer"){
                            val contextMap = witResponse.context_map

                            // Retrieve money recipient
                            val moneyRecipientEntity = contextMap["money_recipient"] as List<*>
                            val moneyRecipient = moneyRecipientEntity[0] as Map<String,Any>
                            val recipient_name = moneyRecipient["value"]
                            Log.d(TAG,"recipient_name - $recipient_name")
                            val phonePePackageName = "com.phonepe.app"
                            //launchappByPackage(phonePePackageName)
                            if (isAppInstalled(phonePePackageName)) {
                                open_specialOpen()
                                MyAccessibilityService.latch_phonepe_home = CountDownLatch(1)  // Initialize the latch
                                launchappByPackage("com.phonepe.app")

                                // Use coroutines to wait for the JioSaavn main activity to be detected with a manual timeout
                                GlobalScope.launch(Dispatchers.Main) {
                                    val timeout = 10L  // Timeout in seconds
                                    val detected = waitForLatchWithTimeout(MyAccessibilityService.latch_phonepe_home!!, timeout, TimeUnit.SECONDS)
                                    if (detected) {
                                        Log.d(TAG, "3")
                                        delay(4000)
                                        execute("to mobile","phonepe_array")
                                        delay(3000)
                                        copyTextToClipboard(recipient_name.toString())
                                        //longExecute("search box","Jiosaavn_array")
                                        //touch("later")
                                        /**open_specialOpen()
                                        Log.d(TAG, "1")
                                        deleteDatabase()
                                        Log.d(TAG, "2")
                                        launchappByPackage("com.jio.media.jiobeats")
                                        Log.d(TAG, "3")
                                        delay(5000)
                                        Log.d(TAG, "4")
                                        //touch("music")
                                        Log.d(TAG, "5")
                                        //delay(3000)
                                        Log.d(TAG, "6")
                                        uploadFile("Jiosaavn")
                                        Log.d(TAG, "7")
                                        delay(30000)
                                        Log.d(TAG, "8")
                                        home_calibration()
                                        Log.d(TAG, "9")
                                        execute("my library","Jiosaavn_array")
                                        delay(2000)
                                        uploadFile("Jiosaavn")
                                        delay(20000)
                                        myLibrary_calibration()
                                        execute("search","Jiosaavn_array")
                                        delay(2000)
                                        uploadFile("Jiosaavn")
                                        delay(20000)
                                        searchbox_calibration()
                                        execute("search box","Jiosaavn_array")
                                         **/
                                    } else {
                                        textToSpeech.speak("Some unintentionally touch done on screen or please check your network or please turn on accessibility service and then off and again turn on and check",TextToSpeech.QUEUE_FLUSH,null,null)
                                    }
                                }
                            } else {
                                Log.d(TAG,"redirect play")
                                // Handle the case where PhonePe is not installed
                                //redirectToPlayStore(phonePePackageName)
                                textToSpeech.speak("Sir, phonepe app not installed, redirect to play store", TextToSpeech.QUEUE_FLUSH, null, null)
                            }
                        }
                    } else {
                        speakText("Sir, at first you have to turn on Phonepe transaction feauters in homepage",
                            utteranceId)
                    }
                }
                if(it.action == "input" || it.action == "more_action" || it.action == "send_money"){

                }else {
                    wait_vr = 1
                }


                // Process the response here
            } ?: run {
                println("Failed to get a response")
                speakText("Failed to get a response", utteranceId)
                textToSpeech.speak("Failed to get a response", TextToSpeech.QUEUE_FLUSH, null, null)
                wait_vr = 1
            }
        }
        Log.d(TAG,"after wit")
        /**witAiHelper.getIntentEntitiesAndTraits(action) { intent, entities, traits, error ->
        if (error == null) {
        Log.d("WitAi", "Intent: $intent")
        Log.d("WitAi", "Entities: $entities")
        Log.d("WitAi", "Traits: $traits")
        } else {
        Log.e("WitAi", "Error: $error")
        }
        if(intent == "money_transfer"){
        // save a stored varriable of payment-platform
        val phonePePackageName = "com.phonepe.app"
        if (isAppInstalled(phonePePackageName)) {
        serviceScope.launch {
        //phonepe_calibration()
        }
        } else {
        // Handle the case where PhonePe is not installed
        redirectToPlayStore(phonePePackageName)
        }

        }
        }**/

        if(action.lowercase().contains("update contacts to model")){
            // for more than 1000 contatcts not save, so after 20 minutes we have to again start process
            witAiHelper.addContactsToWitAi(contactList_transaction){  success, message ->
                if (success) {
                    Log.d(TAG +" TAG", "Success: $message")
                } else {
                    Log.d(TAG + " TAG", "Failure: $message")
                }

            }
        }

        if(action.lowercase().contains("run my app")){
            /**
            val k = 1
            if(k == 0){
            Log.d(TAG, "test 1")
            delay(5000)
            Log.d(TAG,"test 2")
            delay(5000)
            if(k == 1){
            Log.d(TAG, "test 3")
            delay(5000)
            Log.d(TAG,"test 4")
            delay(5000)
            } else {
            Log.d(TAG, "test 5")
            delay(5000)
            Log.d(TAG,"test 6")
            delay(5000)
            }
            } else {
            Log.d(TAG,"test 7")
            delay(5000)
            }
            test()
            Log.d(TAG,"test 10") **/
            launchappByPackage("com.phonepe.app")
            delay(3000)
            //search_box_delete_text()
            MyAccessibilityService.latch_phonepe_home = CountDownLatch(1)  // Initialize the latch
            //launchappByPackage("com.phonepe.app")

            // Use coroutines to wait for the JioSaavn main activity to be detected with a manual timeout
            GlobalScope.launch(Dispatchers.Main) {
                var timeout = 10L  // Timeout in seconds
                var detected = waitForLatchWithTimeout(MyAccessibilityService.latch_phonepe_home!!, timeout, TimeUnit.SECONDS)
                if (detected) {
                    var phonpe_current_state = MyAccessibilityService.phonepe_activity_state
                    if (phonpe_current_state > 1) {
                        if (phonpe_current_state == 4){
                            sendBroadcastMessage_for_back_button()
                            delay(500)
                            sendBroadcastMessage_for_back_button()
                            delay(500)
                        }
                        sendBroadcastMessage_for_back_button()
                        delay(500)
                        sendBroadcastMessage_for_back_button()
                        delay(500)
                        sendBroadcastMessage_for_back_button()
                        delay(500)
                        sendBroadcastMessage_for_back_button()
                        delay(1000)
                        MyAccessibilityService.latch_phonepe_home = CountDownLatch(1)  // Initialize the latch
                        launchappByPackage("com.phonepe.app")
                        timeout = 10L  // Timeout in seconds
                        detected = waitForLatchWithTimeout(MyAccessibilityService.latch_phonepe_home!!, timeout, TimeUnit.SECONDS)
                        if (detected) {
                            delay(3000)
                            touch_new("money","to","mobile")
                        }
                    }
                } else {
                    //Log.d(TAG,"before end")
                }
            }
            //Log.d(TAG,"end")
        }

        if(action.lowercase().contains("go to phonepe")){
            val phonePePackageName = "com.phonepe.app"
            //launchappByPackage(phonePePackageName)
            if (isAppInstalled(phonePePackageName)) {
                phonepe_calibration()
            } else {
                Log.d(TAG,"redirect play")
                // Handle the case where PhonePe is not installed
                //redirectToPlayStore(phonePePackageName)
            }
        }

        if(action.contains("fetch data")){
            fetchDataFromFirestore("phonepay")
        }
        if(action.contains("search me a song")){
            GlobalScope.launch {
                deleteDatabase()
                open_specialOpen()
                launchappByPackage("com.jio.media.jiobeats")
                delay(5000)
                execute("search","Jiosaavn_array")
                delay(500)
                execute("search","Jiosaavn_array")
                delay(1000)
                copyTextToClipboard("one bottle down")
                longExecute("search box","Jiosaavn_array")
                delay(2000)
                touch("paste")
                delay(1000)
                execute("search1stSong","Jiosaavn_array")
                //delay(1000)
                //execute("clearSerachbox","Jiosaavn_array")

            }
        }
        if(action.contains("home calibration")){
            GlobalScope.launch {
                open_specialOpen()
                Log.d(TAG, "1")
                deleteDatabase()
                Log.d(TAG, "2")
                launchappByPackage("com.jio.media.jiobeats")
                Log.d(TAG, "3")
                delay(5000)
                Log.d(TAG, "4")
                //touch("music")
                Log.d(TAG, "5")
                //delay(3000)
                Log.d(TAG, "6")
                uploadFile("Jiosaavn")
                Log.d(TAG, "7")
                delay(30000)
                Log.d(TAG, "8")
                home_calibration()
                Log.d(TAG, "9")
                execute("my library","Jiosaavn_array")
                delay(2000)
                uploadFile("Jiosaavn")
                delay(20000)
                myLibrary_calibration()
                execute("search","Jiosaavn_array")
                delay(2000)
                uploadFile("Jiosaavn")
                delay(20000)
                searchbox_calibration()
                execute("search box","Jiosaavn_array")

            }
        }
        if(action.contains("calibrate jio app")){
            GlobalScope.launch {
                deleteDatabase()
                launchappByPackage("com.jio.media.jiobeats")
                delay(5000)
                touch("music")
                delay(5000)
                uploadFile("phonepay")
                //fetchDataFromFirestore()
            }

        }
        if(action.contains("print my data")){
            GlobalScope.launch {
                //uploadFile("Jiosaavn")
                touch("‘\n" +
                        "a")
            }

        }
        if(action.contains("play me a song")){
            GlobalScope.launch {
                open_specialOpen()
                launchappByPackage("com.jio.media.jiobeats")
                delay(5000)
                //home_calibration()
                jioSaavnHome()
                delay(1000)
                kl.reenableKeyguard()
            }

        }
        if(action.contains("play my favourite song")){
            GlobalScope.launch {
                open_specialOpen()
                launchappByPackage("com.jio.media.jiobeats")
                delay(5000)
                execute("my library","homeScreenJiosaavn_array")
                delay(5000)
                //execute("shuffle all","homeScreenJiosaavn_array")
                delay(1000)
                kl.reenableKeyguard()
            }

        }
        if(action.contains("launch music app")){
            launchappByPackage("com.example.foregroundservice")

        }
        if(action.contains("touch")){
            //Log.d("trackk","touchhhhhhhhhhhhh")
            GlobalScope.launch {
                launchappByPackage("com.jio.media.jiobeats")
                delay(5000)
                touch("music")
                //touch("me")
                //touch("favorite")
                //touch("shuffle")
            }

            //Log.d("trackk","position of resso - ${position.nwidth} + ${position.nheight} " )

        }
        if(action.contains("stop")){
            stopIshaKriya()
        }
        if (action.contains("morning meditation")){
            Log.d(TAG,"startIshaKriya")
            startIshaKriya()
            val serviceScope = CoroutineScope(Dispatchers.Default)
            serviceScope.launch {

            }
        }
        if(action.contains("full volume")){

            //audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION)
            audioManager.stopBluetoothSco()
            //val sdfsd = IntentFilter(Intent.ACTION_MEDIA_BUTTON)
            //registerReceiver(mediaButtonReceiver, sdfsd, RECEIVER_NOT_EXPORTED)
            //audioManager.setBluetoothScoOn(false)
        }
        if (action.contains("speaker on")){
            audioManager.isSpeakerphoneOn = true
        }
        if(action.contains("only audio")){
            val audioFocusChangeListener =
                OnAudioFocusChangeListener { focusChange ->
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_GAIN -> {}
                        AudioManager.AUDIOFOCUS_LOSS -> {}
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {}
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {}
                    }
                }
            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            val result: Int = audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )

            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                // Audio focus granted, proceed with audio playback
            } else {
                // Audio focus request failed
            }


        }
        if(action.contains("audio focus")){
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter != null && bluetoothAdapter.isEnabled) {
                // Bluetooth is enabled
                Log.d(TAG, "in bluetooth enabled")
                val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

                // Set audio mode to MODE_IN_COMMUNICATION for two-way communication

                // Set audio mode to MODE_IN_COMMUNICATION for two-way communication
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

                // Route audio to Bluetooth

                // Route audio to Bluetooth
                audioManager.isBluetoothScoOn = true
                audioManager.startBluetoothSco()
                audioManager.isSpeakerphoneOn = false // Disable speakerphone to use Bluetooth mic


            }

        }
        if(action.contains("bluetooth")){
            GlobalScope.launch {
                delay(400) // Replace with a coroutine-friendly delay

                // Set the audio mode to a suitable mode (e.g., MODE_NORMAL or MODE_IN_COMMUNICATION)
                //audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                audioManager.isBluetoothScoOn = true

                //Log.d(TAG, "in mode in call")
                // Enable Bluetooth SCO for audio input

                // Start Bluetooth SCO connection
                audioManager.startBluetoothSco()

                // Delay to allow SCO connection to establish (adjust as needed)
                delay(500)

                // Notify the user
                textToSpeech.speak("Your Bluetooth device mic is connected", TextToSpeech.QUEUE_FLUSH, null, null)
                // Optional: If you want to play audio through the mobile phone speaker

                //val sdfsd = IntentFilter(Intent.ACTION_MEDIA_BUTTON)
                //registerReceiver(mediaButtonReceiver, sdfsd, RECEIVER_NOT_EXPORTED)

            }
        }
        if(action.contains("dispatch")){
            //performOneTimeTouch()
            var position = tesseractOCR.recognizeText("me", getPngBitmap(mStoreDir + "myscreen_0.png"))
            t = position.nwidth.toFloat() * 605F/611F
            m = position.nheight.toFloat() * 742F/730F
            Log.d("trackk","t - $t , m - $m")
            //sendBroadcastMessage(t,m)

        }
        if(action.contains("screenshot")){
            takescreenshot()
        }
        if(action.contains("lock")){
            if (kl != null) {
                kl.reenableKeyguard()
            }
            devicePolicyManager.lockNow()
            Thread.sleep(1000)
            textToSpeech.speak("your device has been locked",TextToSpeech.QUEUE_FLUSH,null,null)
        }else if (action.contains("shutdown")){
            unlock_varriable = false
            pass = false
            //km = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
            kl.disableKeyguard()
            isDeviceLocked = km.isKeyguardLocked()
            Log.d("trackk", isDeviceLocked.toString())
            val isKeyguardEnabled: Boolean = km.isKeyguardSecure()
            Log.d("trackk", isKeyguardEnabled.toString())
            val pm = applicationContext.getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.FULL_WAKE_LOCK
                        or PowerManager.ACQUIRE_CAUSES_WAKEUP
                        or PowerManager.ON_AFTER_RELEASE, "speech : my wake lock tag"
            )
            wakeLock.acquire(1000)
            if (wakeLock.isHeld()) {
                wakeLock.release()
            }
            //kl = km.newKeyguardLock("MyKeyguardLock")
            if (kl != null) {
                kl.reenableKeyguard()
            }
            devicePolicyManager.lockNow()
            Thread.sleep(100)
            Log.d("trackk", "lock status$isDeviceLocked")
            Thread {
                do {
                    isDeviceLocked = km.isKeyguardLocked
                    if (!isDeviceLocked) {
                        devicePolicyManager.lockNow()
                    }
                } while (!pass)
            }.start()
            Thread.sleep(1000)
            textToSpeech.speak("your device now in special lock",TextToSpeech.QUEUE_FLUSH,null,null)
        }
        if(action.contains("password 13")){
            pass = true
            unlock_varriable = true
        }
        if(action.contains("open") || action.contains("password 13")){
            kl.reenableKeyguard()
            //devicePolicyManager.lockNow()
            try {
                Thread.sleep(500)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
            if (unlock_varriable) {
                km = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
                Log.d("trackk", getSystemService(KEYGUARD_SERVICE).toString())
                kl = km.newKeyguardLock("MyKeyguardLock")
                kl.reenableKeyguard()
                Thread.sleep(500)
                val pm = applicationContext.getSystemService(POWER_SERVICE) as PowerManager
                wakeLock = pm.newWakeLock(
                    PowerManager.FULL_WAKE_LOCK
                            or PowerManager.ACQUIRE_CAUSES_WAKEUP
                            or PowerManager.ON_AFTER_RELEASE, "speech : my wake lock tag"
                )
                wakeLock.acquire(1000)
                if (wakeLock.isHeld) {
                    wakeLock.release()
                }
                kl.disableKeyguard()
                Thread.sleep(500)
                textToSpeech.speak("your device has been unlocked",TextToSpeech.QUEUE_FLUSH,null,null)
            }else{
                textToSpeech.speak("your device is in special lock, first unlock your device by voice command",TextToSpeech.QUEUE_FLUSH,null,null)
            }
        }
        else {
            //Thread.sleep(1000)
            //textToSpeech.speak("ohh hellooo sir how can i help you",TextToSpeech.QUEUE_FLUSH,null,null)
        }
        Log.d(TAG,"end of the result")
        //MainActivity.stt.startSpeechRecognition()
        while (wait_vr == 0){
            withContext(Dispatchers.IO) {
                Thread.sleep(500)
            }
        }
    }

    private suspend fun phonepe_name_identification_for_moreOption(contact_list_sort_by_voice : List<Contact>,recipient_name_final : String){
        var brake_con = false
        var name_name = ""
        val list_size = recentList_in_input.size
        val sizz = contact_list_sort_by_voice.size - 1
        for (i in 0 until sizz){
            if(list_size > 1 && (list_size-1) > i){
                name_name = recentList_in_input[i + 1]
            } else {
                name_name = recent_processedWit_name
            }
            delay(500)
            findBestMatch_by_leven_only(name_name,recent_contact_list_sort_by_voice)?.let {
                copyTextToClipboard(it[0].number)
                longExecute("search_box2","phonepe_array")
                delay(2000)
                execute("search_box2paste","phonepe_array")
                delay(2000)
                //simulate_HomeButton()
                //if(first_time_activity_phonepe == 1){
                //}else {
                //   sendBroadcastMessage_for_back_button()
                //}
                execute("exist_contact_touch","phonepe_array")
                delay(2000)
                copyTextToClipboard(amt.toInt().toString())
                longExecute("enter_amount","phonepe_array")
                delay(2000)
                execute("enter_amount_paste","phonepe_array")
                delay(1500)
                takescreenshot()
                delay(1000)
                val bol_result =
                    tesseractOCR.recognize_three_two_forIdentifying_contactUPI("not","linked",getPngBitmap(mStoreDir + "myscreen_0.png"))
                if (bol_result){
                    recent_contact_list_sort_by_voice = recent_contact_list_sort_by_voice.filter { coontact -> coontact.number != it[0].number }
                    //sendBroadcastMessage_for_back_button()
                    //delay(2000)
                    //execute("search_box_delete_text","phonepe_array")
                    //delay(1000)
                    //contact_with_upiDetails.add(Contact_upi(it[0].number,false))
                } else{
                    recent_payment_number = it[0].number
                    recent_payment_name = it[0].name
                    //contact_with_upiDetails.add(Contact_upi(it[0].number,true))
                    brake_con = true
                }
            }
            if(brake_con){
                break
            }
            sendBroadcastMessage_for_back_button()
            delay(2000)
            takescreenshot()
            val bol_result =
                tesseractOCR.recognize_three_two_forIdentifying_contactUPI("send","money",getPngBitmap(mStoreDir + "myscreen_0.png"))
            if(bol_result){
                execute("search_box","phonepe_array")
            }
            delay(500)
            execute("search_box_delete_text","phonepe_array")
            delay(500)
            sendBroadcastMessage_for_back_button()
            delay(500)
        }
        if(recent_contact_list_sort_by_voice.size == 1){
            val contextMapp = mapOf("shape_color" to "red")
            witAiHelper.sendWitRequest(sessionId, contextMapp, "silent", token) {
            }
            latch = CountDownLatch(1)
            speakText("Sir, there are no contacts available similar with $recent_wit_name ",utteranceId)
            // Wait for the speech to finish
            try {
                latch.await()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }else{
            latch = CountDownLatch(1)
            speakText("Sir, do you want to send ${recent_amt.toInt()} rupees to ${recent_payment_name}",utteranceId)
            // Wait for the speech to finish
            try {
                latch.await()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
            if(bluetooth_connection_state){
                connectBluetooth()
                sendBroadcastMessage2("hi")
                Log.d(TAG + "bluetooth_connection_state", bluetooth_connection_state.toString())
            } else{
                sendBroadcastMessage2("hi")
            }
            //recent_contact_list_sort_by_voice = recent_contact_list_sort_by_voice.filter { coontact -> coontact.number != recent_payment_number }
        }
    }

    private suspend fun phonepe_name_identification(contact_list_sort_by_voice : List<Contact>,recipient_name_final : String, utteranceId : String){
        if(contact_list_sort_by_voice.size>2){
            copyTextToClipboard(recipient_name_final)
            longExecute("search_box2","phonepe_array")
            delay(2000)
            execute("search_box2paste","phonepe_array")
            delay(2000)
            //simulate_HomeButton()
            //if(first_time_activity_phonepe == 1){
            //}else {
            //    sendBroadcastMessage_for_back_button()
            //}
            delay(2000)
            takescreenshot()
            delay(1000)
            val string =
                tesseractOCR.recognizeText_from_crop_photo(getPngBitmap(mStoreDir + "myscreen_0.png"))
            //val position = tesseractOCR.recognizeText_return_all(getPngBitmap(mStoreDir + "myscreen_0.png"))
            val cleanedText = string.nsstring2.replace("\n", " ")
            val cleanedLine = cleanedText.trim().replace("[^a-zA-Z0-9+/.(), ]".toRegex(), "")
                .replace("...", "").replace("rw LJ", "").trim().toLowerCase(
                    Locale.ROOT
                )
            Log.d(TAG, " cleanned line " + cleanedLine)
            //val cleanF = cleanedLine.replace("\\b[a-zA-Z]\\b".toRegex(), "") // Remove single alphabetic characters
            val parts = cleanedLine.split("to cts")
            val leftPart = parts[0].trim()
            //val rightPart = if (parts.size > 1) parts[1].trim() else ""

            //Log.d("trackk - Left Part", leftPart)
            //Log.d("trackk - Right Part", rightPart)
            //.replace("\\b[a-zA-Z]\\b".toRegex(), "") // Remove single alphabetic characters
            recentList_in_input = mutableListOf()
            var recentList: MutableList<String>
            if(string.nsstring1){
                recentList = processString_for_recent_list(leftPart)
                Log.d(TAG + "recentList", recentList.toString())
            } else {
                //recentList = processString_allContact(rightPart)
                recentList = mutableListOf()
                Log.d(TAG + "contactList", recentList.toString())
            }
            // Debugging: Log the results
            if (contact_list_sort_by_voice.isNotEmpty()) {
                Log.d(TAG + "contact_list_sort_by_voice", contact_list_sort_by_voice.toString())
                //Log.d(TAG, contactList[1].name)
                //findBestMatch_by_leven_only()
                var it_name = ""
                if(string.nsstring1){
                    // notes
                    //sendBroadcastMessage_wit_speechResult("start")
                    var break_con = false
                    for(i in 0 until recentList.size) {
                        findBestMatch_by_leven_only(recentList[i],contact_list_sort_by_voice)?.let {
                            if(it.last().number.toInt()>5) {
                                //recent_payment_number = recipient_name_final
                                //it_name = recentList[0]
                                //recent_payment_name = it_name
                                //if(i == (recentList.size - 1)){
                                //execute("search_box_delete_text","phonepe_array")
                                // delay(500)
                                // phonepe_name_identification_for_moreOption(contact_list_sort_by_voice,recipient_name_final)
                                //}
                            } else {
                                // digit matching algorithm.
                                val input_s = splitSentence(recipient_name_final)
                                val recent_s = splitSentence(recentList[i])
                                // Create a mutable list to track unmatched strings from recent_s
                                val unmatchedRecent = recent_s.toMutableList()

                                // Variable to track if all input_s lengths have been matched
                                var allMatch = true

                                // Check if each string in input_s has a corresponding unmatched string in unmatchedRecent
                                for (inputStr in input_s) {
                                    val inputLength = inputStr.length
                                    val match = unmatchedRecent.find { it.length == inputLength }

                                    if (match != null) {
                                        // Remove the matched string from unmatchedRecent to prevent it from being reused
                                        unmatchedRecent.remove(match)
                                    } else {
                                        allMatch = false
                                        break
                                    }
                                }
                                if(allMatch == true){
                                    //recentList_in_input = recentList
                                    recentList_in_input.add(recentList[i])
                                    recent_payment_number = it[0].number
                                    it_name = it[0].name
                                    recent_payment_name = it_name
                                    //break_con = true
                                }
                            }
                            //sendBroadcastMessage_device_speechResult(it.last().number)
                        }
                    }
                    if(recentList_in_input.size == 0){
                        execute("search_box_delete_text","phonepe_array")
                        delay(500)
                        phonepe_name_identification_for_moreOption(contact_list_sort_by_voice,recipient_name_final)
                    } else {
                        latch = CountDownLatch(1)
                        //val utteranceId = UUID.randomUUID().toString()
                        speakText("Sir, do you want to send ${amt.toInt()} rupees to $it_name",utteranceId)

                        // Wait for the speech to finish
                        try {
                            withContext(Dispatchers.IO) {
                                latch.await()
                            }
                        } catch (e: InterruptedException) {
                            e.printStackTrace()
                        }
                        sendBroadcastMessage2("hi")
                    }
                } else{
                    /**findBestMatch_by_leven_only(recipient_name_final,contact_list_sort_by_voice)?.let {
                    recent_payment_number = it[0].number
                    it_name = it[0].name
                    recent_payment_name = it_name
                    }**/
                    execute("search_box_delete_text","phonepe_array")
                    delay(500)
                    phonepe_name_identification_for_moreOption(contact_list_sort_by_voice,recipient_name_final)
                }
                recent_amt = amt

            } else {
                Log.d(TAG, "contact_list_sort_by_voice is empty")
            }
        }
    }

    private suspend fun test(){
        Log.d(TAG, "test 8")
        delay(5000)
        Log.d(TAG,"test 9")
        delay(5000)
    }

    private suspend fun backTo_number_method(){
        var phonpe_current_state = MyAccessibilityService.phonepe_activity_state
        if (phonpe_current_state == 2){
            delay(2000)
            takescreenshot()
            val bol_result =
                tesseractOCR.recognize_three_two_forIdentifying_contactUPI("send","money",getPngBitmap(mStoreDir + "myscreen_0.png"))
            if(bol_result){
                execute("search_box","phonepe_array")
            }
            delay(500)
            execute("search_box_delete_text","phonepe_array")
            delay(500)
            sendBroadcastMessage_for_back_button()
        } else if (phonpe_current_state == 1){
            delay(2000)
            execute("home_home","phonepe_array")
            delay(2000)
            MyAccessibilityService.latch_phonepe_home = CountDownLatch(1)
            touch_new("money","to","mobile")
            var timeout = 10L  // Timeout in seconds
            var detected = waitForLatchWithTimeout(
                MyAccessibilityService.latch_phonepe_home!!,
                timeout,
                TimeUnit.SECONDS
            )
            if (detected) {
                phonpe_current_state = MyAccessibilityService.phonepe_activity_state
                if (phonpe_current_state == 2){

                } else {
                    sendBroadcastMessage_for_back_button()
                    delay(3000)
                    MyAccessibilityService.latch_phonepe_home = CountDownLatch(1)
                    touch_new("money","to","mobile")
                    var timeout = 10L  // Timeout in seconds
                    var detected = waitForLatchWithTimeout(
                        MyAccessibilityService.latch_phonepe_home!!,
                        timeout,
                        TimeUnit.SECONDS
                    )
                    if (detected) {
                        phonpe_current_state = MyAccessibilityService.phonepe_activity_state
                        if (phonpe_current_state == 2){
                        } else {
                            sendBroadcastMessage_for_back_button()
                            delay(3000)
                            touch_new("money","to","mobile")
                        }
                    }
                }
            } else {
                speakText("Some intentionally touch done on screen or please check your network or please turn on accessibility service and then off and again on and check",utteranceId)
            }
            delay(2000)
            execute("search_box","phonepe_array")
            delay(1000)
            sendBroadcastMessage_for_back_button()
        } else if (phonpe_current_state == 4){
            sendBroadcastMessage_for_back_button()
            delay(500)
            sendBroadcastMessage_for_back_button()
            delay(500)
            sendBroadcastMessage_for_back_button()
            delay(2000)
            takescreenshot()
            val bol_result =
                tesseractOCR.recognize_three_two_forIdentifying_contactUPI("send","money",getPngBitmap(mStoreDir + "myscreen_0.png"))
            if(bol_result){
                execute("search_box","phonepe_array")
                delay(500)
                sendBroadcastMessage_for_back_button()
            }
            execute("search_box_delete_text","phonepe_array")
            delay(500)
            execute("search_box_delete_text","phonepe_array")
            delay(500)
            sendBroadcastMessage_for_back_button()
        } else if (phonpe_current_state == 6) {
            sendBroadcastMessage_for_back_button()
            delay(1000)
            MyAccessibilityService.latch_phonepe_home = CountDownLatch(1)
            sendBroadcastMessage_for_back_button()
            //timeout = 10L  // Timeout in seconds
            val detected = waitForLatchWithTimeout(
                MyAccessibilityService.latch_phonepe_home!!,
                2L,
                TimeUnit.SECONDS
            )
            if (detected) {
                phonpe_current_state =
                    MyAccessibilityService.phonepe_activity_state
                if (phonpe_current_state == 2) {
                    takescreenshot()
                    val bol_result =
                        tesseractOCR.recognize_three_two_forIdentifying_contactUPI("send","money",getPngBitmap(mStoreDir + "myscreen_0.png"))
                    if(bol_result){
                        //speakText("sir, send money true",utteranceId)
                        execute("search_box","phonepe_array")
                    }
                    delay(500)
                    execute("search_box_delete_text","phonepe_array")
                    delay(500)
                    execute("search_box_delete_text","phonepe_array")
                    delay(500)
                    sendBroadcastMessage_for_back_button()
                }
            } else {
                touch_new("money","to","mobile")
                delay(2000)
                execute("search_box","phonepe_array")
                delay(500)
                sendBroadcastMessage_for_back_button()
            }
        } else if (phonpe_current_state == 3) {
            sendBroadcastMessage_for_back_button()
            delay(2000)
            takescreenshot()
            val bol_result =
                tesseractOCR.recognize_three_two_forIdentifying_contactUPI("send","money",getPngBitmap(mStoreDir + "myscreen_0.png"))
            if(bol_result){
                execute("search_box","phonepe_array")
                delay(500)
                sendBroadcastMessage_for_back_button()
            }
            delay(500)
            execute("search_box_delete_text","phonepe_array")
            delay(500)
            execute("search_box_delete_text","phonepe_array")
            delay(500)
            sendBroadcastMessage_for_back_button()
        } else if (phonpe_current_state == 5) {
            sendBroadcastMessage_for_back_button()
            delay(1500)
            execute("home_home","phonepe_array")
            delay(1500)
            touch_new("money","to","mobile")
            delay(2000)
            execute("search_box","phonepe_array")
            delay(500)
            sendBroadcastMessage_for_back_button()
        }
    }

    private fun addModelStatListener() {
        modelUpdateListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Check if the model_stat field exists and is true
                val modelStat = snapshot.child("model_stat").getValue(Boolean::class.java)
                if (modelStat == true) {
                    audioHelper.stopAudioClassification()
                    audiohelp_start_or_not = false
                    Log.d("Firebase", "Model updated, downloading the new model.")
                    // Get the model URL and download it
                    val modelUrl = snapshot.child("model_url").getValue(String::class.java)
                    if (modelUrl != null) {
                        downloadModel(modelUrl)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Database error: ${error.message}")
            }
        }

        // Attach the listener to the database reference
        modelUpdateListener?.let {
            databaseRef.addValueEventListener(it)
        }
    }

    // Download the model from Firebase Storage
    private fun downloadModel(modelUrl: String) {
        val modelRef = storage.getReference(modelUrl)

        modelRef.getFile(localFile)
            .addOnSuccessListener {
                sharedPreferencesHelper.saveBoolean("model_making_process_state",false)
                SharedData.is_model_avl = true
                sharedPreferencesHelper.saveBoolean("model_stat_not_avl",false)
                audiohelp_start_or_not = true
                audioHelper.initClassifier()
                Log.d("Firebase", "Model downloaded successfully.")
                sendUserNotification()
                // After download, you can stop the listener or update the UI
                removeModelStatListener()
                sendBroadcastUpdateToggleSwitch(true)
            }
            .addOnFailureListener { e ->
                Log.e("Firebase", "Failed to download model: ${e.message}")
            }
    }

    // Stop the listener after the model has been downloaded
    private fun removeModelStatListener() {
        modelUpdateListener?.let {
            databaseRef.removeEventListener(it)
            Log.d("Firebase", "Listener removed.")
        }
    }

    fun sendUserNotification() {
        Log.d(TAG,"send user notification")
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create an intent to open your app when the notification is tapped
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create a simple notification to notify the user
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.voice)
            .setContentTitle("Update")
            .setContentText("Sir new AI name model successfully updated!")
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)// High priority to make sure the user notices it
            .build()

        // Send the notification
        notificationManager.notify(2, notification)  // Use a different ID for additional notifications
    }

    private fun sendBroadcastUpdateToggleSwitch(newState: Boolean) {
        val intent = Intent("com.example.UPDATE_TOGGLE_SWITCH_AI")
        intent.putExtra("new_toggle_state", newState)
        sendBroadcast(intent)
    }


}