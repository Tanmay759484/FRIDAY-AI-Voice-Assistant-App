package com.example.foregroundservice

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.database.Cursor
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.foregroundservice.STT.Stt
import com.example.foregroundservice.STT.SttListener
import com.example.foregroundservice.STT.wavClass
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.storage.FirebaseStorage
import com.google.gson.Gson
import org.vosk.android.SpeechService
import java.io.*
import java.util.Locale


class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    companion object {
        lateinit var stt: Stt
        lateinit var audioRecorder: wavClass
        lateinit var filepath: String
        lateinit var output_filepath: String
        lateinit var directory: String
        lateinit var tempFilepath: String
        lateinit var localFile: File
    }


    // shared pref set
    private lateinit var sharedPreferencesHelper: SharedPreferencesHelper

    private val PERMISSIONS_REQUEST_READ_CONTACTS = 100
    private val BLUETOOTH_PERMISSION_CODE = 200000
    private val PERMISSION_REQUEST_RECORD_AUDIO = 1
    private val ACCESS_FINE_LOCATION_CODE = 600
    private val POST_NOTIFICATIONS_CODE = 605
    private val CALL_PHONE_CODE = 503
    private val RC_SIGN_IN = 20
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var connection: AccessibilityServiceConnection
    private val REQUEST_CODE = 105
    private lateinit var fileinputstream: FileInputStream
    private lateinit var storage: FirebaseStorage
    private var AI_serial_no: Int = 5
    private var AI_name: String? = null
    private var input_fileName = "input_AIrecording"
    private var output_fileName = "AIname_recording"
    private var tempFile = "tempFile"
    private var isRecording = true
    private val handler = Handler()
    private lateinit var foregroundService: ForegroundService
    private val mediaButtonReceiver = MediaButtonReceiver()
    private val contactList: MutableList<Contact> = mutableListOf()
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var titleTextView : TextView
    private lateinit var toggleSwitch : SwitchCompat
    private lateinit var titleTextView2 : TextView
    private lateinit var toggleSwitch2 : SwitchCompat
    private lateinit var imageView_AI : ImageView

    private val handler1 = Handler(Looper.getMainLooper())
    private val CHECK_INTERVAL = 1000L // Check every 1 second
    private val TIMEOUT = 60000L // Stop after 1 minute (60,000 ms)

    private var elapsedTime = 0L // Tracks elapsed time

    private val checkServiceRunnable = object : Runnable {
        override fun run() {
            if (isAccessibilityServiceEnabled(this@MainActivity, MyAccessibilityService::class.java)) {
                // Accessibility Service is enabled, bring the app to the foreground
                bringAppToForeground()
                handler1.removeCallbacks(this) // Stop the periodic check
            } else if (elapsedTime >= TIMEOUT) {
                // Timeout reached, stop checking
                handler1.removeCallbacks(this)
                Toast.makeText(this@MainActivity, "Accessibility Service not enabled within 1 minute", Toast.LENGTH_SHORT).show()
            } else {
                // Increment elapsed time and check again
                elapsedTime += CHECK_INTERVAL
                handler1.postDelayed(this, CHECK_INTERVAL)
            }
        }
    }


    /** VOSK
     */

    private var speechService: SpeechService? = null

    private val toggleSwitchReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if(intent?.action == "com.example.UPDATE_TOGGLE_SWITCH_AI"){
                val newState = intent?.getBooleanExtra("new_toggle_state", false) ?: false
                toggleSwitch.isChecked = newState
            }
        }
    }


    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Register the BroadcastReceiver
        val intentFilter = IntentFilter("com.example.UPDATE_TOGGLE_SWITCH_AI")
        registerReceiver(toggleSwitchReceiver, intentFilter, RECEIVER_EXPORTED)

        Log.d("trackk","all permission not granted")

        //sharedpref init
        sharedPreferencesHelper = SharedPreferencesHelper(this)

        titleTextView= findViewById(R.id.titleTextView)
        toggleSwitch = findViewById(R.id.toggleSwitch)

        titleTextView2= findViewById(R.id.titleTextView2)
        toggleSwitch2 = findViewById(R.id.toggleSwitch2)
        imageView_AI = findViewById(R.id.imageView_AI)
        // Initialize the DrawerLayout and NavigationView
        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)

        // Set up the toolbar
        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)  // Set the Toolbar as the ActionBar

        // Set up the drawer toggle
        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Set up navigation view item click listener
        navView.setNavigationItemSelectedListener(this)

        // Get the user data from the intent or shared preferences
        val user_name = sharedPreferencesHelper.getString("user_user_id")
        val user_email = sharedPreferencesHelper.getString("user_user_email")

        // Update the navigation drawer header
        val headerView : View = navView.getHeaderView(0)
        val navUsername = headerView.findViewById<TextView>(R.id.nav_header_title)
        val navEmail = headerView.findViewById<TextView>(R.id.nav_header_subtitle)
        navUsername.text = user_name
        navEmail.text = user_email

        SharedData.mainActivity = this

        val liveText : TextView = findViewById(R.id.textView2)

        // Register the BroadcastReceiver dynamically in onResume

        //Log.d("trackk",contact_list.toString())
        val filter = IntentFilter(Intent.ACTION_MEDIA_BUTTON)
        registerReceiver(mediaButtonReceiver, filter, RECEIVER_NOT_EXPORTED)
        auth = FirebaseAuth.getInstance()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)
        //connection = AccessibilityServiceConnection()
        //val intent = Intent(this, MyAccessibilityService::class.java)
        //bindService(intent, connection, Context.BIND_AUTO_CREATE)
        /*if(sharedPreferencesHelper.getBoolean("mediaprojection_service_stat")){
            if(foregroundServiceRunning()) {
                stopForegroundService()
            }
            if(!norm_ForegroundServiceRunning()){
                startNorm_ForegroundService()
            }
        } else {
            if(!foregroundServiceRunning()) {
                startForegroundService()
            }
        }*/
        foregroundService = ForegroundService()
        //setup stt engine
        initSttEngine(liveText)
        AI_name = "Friday"
        directory =
            getExternalFilesDir(null).toString() // This will get the app-specific directory on external storage
        Log.d("trackk", directory)
        val create_folder = File("$directory/$AI_name")
        val create_folder3 = File("${directory}/Friday_model")
        localFile = File(create_folder3.path, "browserfft-speech.tflite")
        if(localFile.exists()){
            if(sharedPreferencesHelper.getBoolean("model_stat_not_avl")){
                SharedData.is_model_avl = false
            } else {
                SharedData.is_model_avl = true
            }
        } else {
            SharedData.is_model_avl = false
        }
        create_folder.mkdirs()
        directory = create_folder.path

        //filepath = directory + "/final_record.wav"
        //output_filepath = directory.toString() + "/output_final_record.wav"
        //val outputFile = File(directory, "recorded_audio.wav")
        //Log.d("trackk","outputFile.path" +outputFile.path)
        Log.d("trackk", "directory - " + directory.toString())
        //Log.d("trackk",wavClass(Environment.getExternalStorageDirectory().getPath()).toString())

        // Get an instance of FirebaseStorage
        // Get an instance of FirebaseStorage
        storage = FirebaseStorage.getInstance()


        val audioProcessor = AudioProcessor()
        //val wavFileReader = WavFileReader()
        /*// check for permission
        val permissionCheck =
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.RECORD_AUDIO)
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                PERMISSION_REQUEST_RECORD_AUDIO
            )
            return
        }*/

        findViewById<Button>(R.id.record_button).setOnClickListener {
            //audioProcessor.processAudio()

            if (isRecording) {
                isRecording = false
                filepath = "$directory/$input_fileName$AI_serial_no.wav"
                output_filepath = "$directory/$output_fileName$AI_serial_no.wav"
                tempFilepath = "$directory/$tempFile$AI_serial_no.raw"
                Log.d("trackk", filepath)
                Log.d("trackk", output_filepath)
                Log.d("trackk", tempFilepath)
                audioRecorder = wavClass(
                    directory,
                    (tempFile + AI_serial_no + ".raw"),
                    (input_fileName + AI_serial_no + ".wav")
                )
                audioRecorder.startRecording()
                delayWithRepetition(3, 1000)
                //audioRecorder.stopRecording()
                //AudioTrimmer.trimAudio(filepath,output_filepath,WavFileReader.readWavFile(filepath))
                //isRecording = true
            }

        }
        // background launch activity
        /**findViewById<Button>(R.id.background_button).setOnClickListener {
            val serviceIntent = Intent(this, BackgroundMic::class.java)
            startService(serviceIntent)
        } **/

        // screenshotFragment
        /**if (savedInstanceState == null) {
            val screenshotFragment = ScreenshotFragment()
            supportFragmentManager
                .beginTransaction()
                .add(R.id.container, screenshotFragment, "ScreenshotFragment")
                .commit()
        }**/

        //var accesisibility = MyAccessibilityService()
        requestContactsPermission()

        /*if (!foregroundServiceRunning()) {
            Log.d("trackk","in mainactivity oncreate forefround service start section")
            //setScreenshotPermission((Intent) data.clone());
            //service_intent.putExtra("resultCodeIntent", resultCode);
            //service_intent.putExtra("dataIntent", (Intent) screenshotPermission.clone());
            //startForegroundService()
            startProjection()
            // Inside your main activity or wherever you want to trigger the floating screen
        }*/

        toggleSwitch.thumbTintList = ContextCompat.getColorStateList(this, R.color.switch_thumb_color)
        toggleSwitch.trackTintList = ContextCompat.getColorStateList(this, R.color.switch_track_color)

        toggleSwitch2.thumbTintList = ContextCompat.getColorStateList(this, R.color.switch_thumb_color)
        toggleSwitch2.trackTintList = ContextCompat.getColorStateList(this, R.color.switch_track_color)

        toggleSwitch2.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!isAccessibilityServiceEnabled(this, MyAccessibilityService::class.java)) {
                    showEnableAccessibilityDialog(this)
                } else {
                    Toast.makeText(this, "Accessibility Service is already enabled", Toast.LENGTH_SHORT).show()
                    sharedPreferencesHelper.saveBoolean("isPhonepeFeaturesOn",true)
                }
                /*
                requestContactsPermission()
                Toast.makeText(this, "Switch ON", Toast.LENGTH_SHORT).show()*/
            } else {
                /*sharedPreferencesHelper.saveBoolean("isPhonepeFeaturesOn",false)
                stopForegroundService()
                if(!norm_ForegroundServiceRunning()){
                    startNorm_ForegroundService()
                }
                Toast.makeText(this, "Switch OFF", Toast.LENGTH_SHORT).show()*/
            }
        }

        // Handle toggle switch changes
        toggleSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                requestContactsPermission()
                Toast.makeText(this, "Switch ON", Toast.LENGTH_SHORT).show()
            } else {
                sharedPreferencesHelper.saveBoolean("Wake up voice activation",false)
                Toast.makeText(this, "Switch OFF", Toast.LENGTH_SHORT).show()
            }
        }

        imageView_AI.setOnClickListener {
            sendBroadcastMessage("hi")
        }




    }

    override fun onStart() {
        super.onStart()
        // Check if user is signed in (non-null) and update UI accordingly.
        //val currentUser = auth.currentUser.toString()
        //Log.d("trackk",currentUser)
        //updateUI(currentUser)
    }

    override fun onResume() {
        if (!isAccessibilityServiceEnabled(this, MyAccessibilityService::class.java)) {
            toggleSwitch2.isChecked = false
        } else{
            toggleSwitch2.isChecked = true
        }
        super.onResume()
        //numberCallback = foregroundService.callbackInterface
        if (foregroundServiceRunning()) {
            //startProjection()
            Log.d("trackk","on resume mainactivity")
            //sendBroadcastMessage("SR & SCO ON")
        }
        //Log.d("trackk","on resume mainactivity")
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_home -> {
                // Handle Home click
            }
            R.id.nav_profile -> {
                // Handle Profile click
            }
            R.id.nav_settings -> {
                startActivity(Intent(this, WakeUpWordActivity::class.java))
            }
            R.id.sign_out_button330 -> {
                sharedPreferencesHelper.saveBoolean("isPhonepeFeaturesOn",false)
                val intent = Intent(this,starting_page::class.java)
                intent.putExtra("Extra_msg",true)
                startActivity(intent)
            }
        }

        // Close the drawer
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }



    private fun initSttEngine(liveText : TextView) {
        stt = Stt(application, object : SttListener {
            override fun onSttLiveSpeechResult(liveSpeechResult: String) {
                Log.d(application.packageName, "Speech result - $liveSpeechResult")
                liveText.text = liveSpeechResult
            }

            override fun onSttFinalSpeechResult(speechResult: String) {
                Log.d(application.packageName, "Final speech result - $speechResult")
                liveText.text = speechResult
            }

            override fun onSttSpeechError(errMsg: String) {
                Log.d(application.packageName, "Speech error - $errMsg")
            }
        })
    }

    private fun startForegroundService() {
        val intent = Intent(this@MainActivity, ForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent) // Use startForegroundService for Android 8.0+
        } else {
            startService(intent) // Use startService for older versions
        }
    }

    private fun startNorm_ForegroundService() {

        val serviceIntent = Intent(this, Norm_ForegroundService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent) // Use startForegroundService for Android 8.0+
        } else {
            startService(serviceIntent) // Use startService for older versions
        }
    }

    private fun stopForegroundService() {
        if(foregroundServiceRunning()){
            val intent = Intent(this@MainActivity, ForegroundService::class.java)
            stopService(intent)
        }
    }
    private fun stopNorm_ForegroundService(){
        if(norm_ForegroundServiceRunning()){
            val intent = Intent(this@MainActivity, Norm_ForegroundService::class.java)
            stopService(intent)
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
        //finishAffinity()
        moveTaskToBack(true)
    }

    override fun onDestroy() {
        // Unregister the BroadcastReceiver to avoid memory leaks
        unregisterReceiver(toggleSwitchReceiver)
        unregisterReceiver(mediaButtonReceiver)
        super.onDestroy()
        Log.d("test", "onDestroy() called")
        //unbindService(connection)
        //stopForegroundService()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.d("trackk","in onrequest permission + ${requestCode}")
        if (requestCode == PERMISSION_REQUEST_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            }
            requestContactsPermission()
        }
        if (requestCode == PERMISSIONS_REQUEST_READ_CONTACTS) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
            }
            requestContactsPermission()
        }
        if (requestCode == BLUETOOTH_PERMISSION_CODE) {
            requestContactsPermission()
        }
        if(requestCode == ACCESS_FINE_LOCATION_CODE){
            Log.d("trackk","in onrequest ACCESS_FINE_LOCATION_CODE permission")
            requestContactsPermission()
        }
        if (requestCode == POST_NOTIFICATIONS_CODE){
            requestContactsPermission()
        }
        if(requestCode == CALL_PHONE_CODE) {
            requestContactsPermission()
        }
    }

    private fun delayWithRepetition(repetitions: Int, delayMillis: Long) {
        var currentRepetition = 0

        // Define a Runnable to be executed with a delay
        val delayRunnable = object : Runnable {
            override fun run() {
                // Your UI update code here
                //textView.text = "Delay ${currentRepetition + 1} of $repetitions"
                Log.d("trackk", "delay")

                currentRepetition++

                if (currentRepetition < repetitions) {
                    // Schedule the next repetition after the delay
                    handler.postDelayed(this, delayMillis)
                }
                if (currentRepetition == 3) {
                    audioRecorder.stopRecording()
                    AudioTrimmer.trimAudio(
                        filepath,
                        output_filepath,
                        WavFileReader.readWavFile(filepath)
                    )
                    val fileToDelete = File(filepath)
                    val fileToDelete1 = File(tempFilepath)
                    // Check if the file exists
                    if (fileToDelete.exists()) {
                        fileToDelete.delete()
                        fileToDelete1.delete()
                    }
                    val fileUri: Uri =
                        Uri.fromFile(File("$directory/$output_fileName$AI_serial_no.wav"))

                    // Specify the path to where you want to store the file in Firebase Storage
                    val storageRef =
                        storage.reference.child("uploaded_audio/${fileUri.lastPathSegment}")
                    // Upload the file to Firebase Storage
                    val uploadTask = storageRef.putFile(fileUri)
                    AI_serial_no++
                    isRecording = true
                }
            }
        }

        // Start the initial delay
        handler.postDelayed(delayRunnable, delayMillis)
    }

    fun readAssetFile(context: Context, assetFilePath: String): String {
        val assetManager: AssetManager = context.assets
        val stringBuilder = StringBuilder()

        try {
            val inputStream = assetManager.open(assetFilePath)
            val bufferedReader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))

            var line: String?
            while (bufferedReader.readLine().also { line = it } != null) {
                stringBuilder.append(line)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return stringBuilder.toString()
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

    fun norm_ForegroundServiceRunning() : Boolean {
        val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        for (service in activityManager.getRunningServices(Int.MAX_VALUE)) {
            if (Norm_ForegroundService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun startProjection() { //for request mediaprojection access
        val mProjectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mProjectionManager.createScreenCaptureIntent(), REQUEST_CODE)
    }

    private fun stopProjection() {
        startService(foregroundService.getStopIntent(this))
    }

    @SuppressLint("MissingSuperCall")
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                Log.d("trackk","on request screenshot accepted")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(foregroundService.getStartIntent(
                        this,
                        resultCode,
                        data)
                    )
                } else {
                    startService(foregroundService.getStartIntent(
                        this,
                        resultCode,
                        data)
                    )
                }
                toggleSwitch2.isChecked = true
            }else{
                sharedPreferencesHelper.saveBoolean("isPhonepeFeaturesOn",false)
                toggleSwitch2.isChecked = false
                showMediaProjectionPermissionDialog()
                if(!norm_ForegroundServiceRunning()){
                    startNorm_ForegroundService()
                }
                /*if (!sharedPreferencesHelper.getBoolean("hasFunctionRun")) {
                    showMediaProjectionPermissionDialog()
                } else {
                    if(!foregroundServiceRunning()){
                        val serviceIntent = Intent(this, ForegroundService::class.java)

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(serviceIntent) // Use startForegroundService for Android 8.0+
                        } else {
                            startService(serviceIntent) // Use startService for older versions
                        }
                    }
                }*/
            }
            if (sharedPreferencesHelper.getBoolean("Wake up voice activation")){
                if (localFile.exists()) {
                    toggleSwitch.isChecked = true
                }
            }
            if(toggleSwitch.isChecked){
                if (localFile.exists()){
                    toggleSwitch.isChecked = true
                    sharedPreferencesHelper.saveBoolean("Wake up voice activation",true)
                } else {
                    if(sharedPreferencesHelper.getBoolean("model_making_process_state")){
                        showModelMakingInProcessDialog()
                    } else{
                        if(!sharedPreferencesHelper.getBoolean("wake_activation")) {
                            showModelAddWakeActivityDialog()
                        }
                    }
                    toggleSwitch.isChecked = false
                    sharedPreferencesHelper.saveBoolean("Wake up voice activation",false)
                }
            }
        }
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                firebaseAuth(account.idToken)
            } catch (e: ApiException) {
                Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
            }
        }
        //val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        //startActivity(intent)
    }

    private fun firebaseAuth(idToken: String?) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    Log.d("trackk", "signInWithCredential:success")
                    val user = auth.currentUser
                    if (user != null) {
                        Log.d("trackk", user.displayName.toString())

                    }
                } else {
                    // If sign in fails, display a message to the user.
                    Log.w("trackk", "signInWithCredential:failure", task.exception)
                }
            }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun requestBluetoothPermission() {
        // Check if the device is running on Android 6.0 (Marshmallow) or higher
        // Request the permission
        Log.d(ForegroundService.TAG,"in bluetttoth connect permission")
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
            BLUETOOTH_PERMISSION_CODE
        )
    }
    fun sendBroadcastMessage(message: String) {
        // Create an Intent with the custom action and message
        val intent = Intent("com.example.yourapp.BROADCAST_ACTION_C")
        intent.putExtra("message", message)

        // Send the broadcast
        sendBroadcast(intent)
    }
    @RequiresApi(Build.VERSION_CODES.S)
    private fun requestContactsPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_CONTACTS),
                PERMISSIONS_REQUEST_READ_CONTACTS
            )
            toggleSwitch.isChecked = false
            sharedPreferencesHelper.saveBoolean("Wake up voice activation",false)
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                    BLUETOOTH_PERMISSION_CODE
                )
                toggleSwitch.isChecked = false
                sharedPreferencesHelper.saveBoolean("Wake up voice activation",false)
            } else {
                if(ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED){
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.RECORD_AUDIO),
                        PERMISSION_REQUEST_RECORD_AUDIO
                    )
                    toggleSwitch.isChecked = false
                    sharedPreferencesHelper.saveBoolean("Wake up voice activation",false)
                } else {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        Log.d("trackk", "ACCESS_FINE_LOCATION permission not granted")
                        Log.d("trackk",ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION).toString())

                        // Request permission if not granted
                        ActivityCompat.requestPermissions(
                            this,
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                            ACCESS_FINE_LOCATION_CODE
                        )
                        toggleSwitch.isChecked = false
                        sharedPreferencesHelper.saveBoolean("Wake up voice activation",false)
                    } else {
                        if (ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS),POST_NOTIFICATIONS_CODE)
                            toggleSwitch.isChecked = false
                            sharedPreferencesHelper.saveBoolean("Wake up voice activation",false)
                        } else {
                            if(ContextCompat.checkSelfPermission(this,Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), CALL_PHONE_CODE)
                                toggleSwitch.isChecked = false
                                sharedPreferencesHelper.saveBoolean("Wake up voice activation",false)
                            } else {
                                Log.d("trackk", "All permissions granted")
                                handleContacts()  // Your function to handle contacts
                                if(!foregroundServiceRunning()) {
                                    startForegroundService()
                                    //startProjection()  // Start the foreground service or other function
                                }
                                if(sharedPreferencesHelper.getBoolean("isPhonepeFeaturesOn")){
                                    /*stopNorm_ForegroundService()
                                    Log.d("trackk", "In main activity, starting foreground service")
                                    if(!foregroundServiceRunning()){
                                        startProjection()  // Start the foreground service or other function
                                    } else {
                                        toggleSwitch2.isChecked = true
                                    }*/
                                } else {
                                    /*if(!norm_ForegroundServiceRunning()){
                                        startNorm_ForegroundService()
                                    }*/
                                }
                                if (sharedPreferencesHelper.getBoolean("Wake up voice activation")){
                                    if (localFile.exists()) {
                                        toggleSwitch.isChecked = true
                                    }
                                }
                                if(toggleSwitch.isChecked){
                                    if (localFile.exists()){
                                        if(!sharedPreferencesHelper.getBoolean("Wake up voice activation")){
                                            sendBroadcastMessage("hi")
                                        }
                                        toggleSwitch.isChecked = true
                                        sharedPreferencesHelper.saveBoolean("Wake up voice activation",true)
                                    } else {
                                        if(sharedPreferencesHelper.getBoolean("model_making_process_state")){
                                            showModelMakingInProcessDialog()
                                        } else{
                                            if(!sharedPreferencesHelper.getBoolean("wake_activation")) {
                                                showModelAddWakeActivityDialog()
                                            }
                                        }
                                        toggleSwitch.isChecked = false
                                        sharedPreferencesHelper.saveBoolean("Wake up voice activation",false)
                                    }
                                }
                            }
                        }
                    }

                }
            }

        }
    }

    private fun loadContacts(): List<Contact> {
        val contacts = mutableListOf<Contact>()
        val contentResolver = contentResolver
        val cursor: Cursor? = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            null, null, null, null
        )

        cursor?.use {
            val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val name = it.getString(nameIndex).trim().replace("[^a-zA-Z0-9+/.(), ]".toRegex(), "")
                    .replace("...", "").replace("  ", " ").replace("   ", " ").trim().toLowerCase(
                        Locale.ROOT
                    )
                val number = it.getString(numberIndex).trim().replace("[^a-zA-Z0-9+/.(), ]".toRegex(), "")
                    .replace("...", "").replace("  ", " ").replace("   ", " ").replace("\\s".toRegex(), "").trim().toLowerCase(
                        Locale.ROOT
                    ).takeLast(10)
                contacts.add(Contact(name, number))
            }
        }
        return contacts
    }

    private fun handleContacts() {
        contactList.clear() // Clear the existing contacts
        contactList.addAll(loadContacts()) // Add the new contacts
        saveContactList(applicationContext, contactList)
        //Log.d("trackk - contact list",contactList.toString())
    }

    fun getStringFromSharedPreferences(context: Context, key: String): Map<String, Any>? {
        val sharedPref = context.getSharedPreferences("my__storage1344", Context.MODE_PRIVATE)
        val json = sharedPref.getString(key, null)
        if (json != null) {
            //Log.d(TAG,json)
        }
        return Gson().fromJson(json, Map::class.java) as? Map<String, Any>
    }

    fun saveStringToSharedPreferences(context: Context, key: String, value: Map<String, Any>) {
        val sharedPref = context.getSharedPreferences("my__storage1344", Context.MODE_PRIVATE)
        val json = Gson().toJson(value)
        with(sharedPref.edit()) {
            putString(key, json)
            apply()
        }
    }

    fun saveContactList(context: Context, contactList: List<Contact>) {
        val sharedPreferences: SharedPreferences = context.getSharedPreferences("MyPrefs_for_contact21", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val gson = Gson()
        val json = gson.toJson(contactList)
        editor.putString("contact_list", json)
        editor.apply()
    }

    private fun showModelMakingInProcessDialog() {
        // Create an AlertDialog builder
        val builder = AlertDialog.Builder(this)

        // Set dialog title and message
        builder.setTitle("Model making in Process")
        builder.setMessage("Sir, take few minutes to activate wake up activation features")

        // Set Discard button
        builder.setPositiveButton("Ok") { dialog, _ ->
            // Handle discard action
            dialog.dismiss()
        }

        // Show the dialog
        builder.setCancelable(true)
        val dialog = builder.create()
        dialog.show()
    }

    private fun showModelAddWakeActivityDialog() {
        // Create an AlertDialog builder
        val builder = AlertDialog.Builder(this)

        // Set dialog title and message
        builder.setTitle("Wake up voice not added")
        builder.setMessage("Sir, to unlock your Wake up voice activation fetures please add your AI name voice sample")

        // Set Discard button
        builder.setPositiveButton("Add") { dialog, _ ->
            // Handle discard action
            dialog.dismiss()
            val intent = Intent(this,WakeUpWordActivity::class.java)
            startActivity(intent)
        }

        // Show the dialog
        builder.setCancelable(true)
        val dialog = builder.create()
        dialog.show()
    }

    private fun showMediaProjectionPermissionDialog() {
        // Create an AlertDialog builder
        val builder = AlertDialog.Builder(this)

        // Set dialog title and message
        builder.setTitle("Need mediaprojection permission")
        builder.setMessage("Sir, Please allow mediaprojection permission for initial start, you can turn it off by click Phonepe transaction feauters toggle switch ")

        // Set Discard button
        builder.setPositiveButton("Ok") { dialog, _ ->
            // Handle discard action
            dialog.dismiss()
        }

        // Show the dialog
        builder.setCancelable(true)
        val dialog = builder.create()
        dialog.show()
    }

    fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<out AccessibilityService>): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false  // Return false if it's null

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals("${context.packageName}/${serviceClass.name}", ignoreCase = true)) {
                return true
            }
        }
        return false
    }


    fun showEnableAccessibilityDialog(context: Context) {
        AlertDialog.Builder(context)
            .setTitle("Enable Accessibility Service")
            .setMessage("To use this feature, please enable the accessibility service.")
            .setPositiveButton("Enable") { _, _ ->
                openAccessibilitySettingsAndMonitor(context)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun openAccessibilitySettingsAndMonitor(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)

        // Start monitoring the Accessibility Service status
        startCheckingAccessibilityService()
    }

    fun startCheckingAccessibilityService() {
        elapsedTime = 0L // Reset elapsed time
        handler.post(checkServiceRunnable)
    }

    fun bringAppToForeground() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
    }

}
