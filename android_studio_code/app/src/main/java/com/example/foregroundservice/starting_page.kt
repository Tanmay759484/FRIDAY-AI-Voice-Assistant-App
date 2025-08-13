package com.example.foregroundservice

import android.app.ActivityManager
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.View
import android.view.animation.AlphaAnimation
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch

class starting_page : AppCompatActivity(),TextToSpeech.OnInitListener {

    // for speak function
    private lateinit var textToSpeech : TextToSpeech
    private lateinit var latch: CountDownLatch
    private lateinit var audioManager : AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    var utteranceId = ""
    var extra_mmsg = false

    //google sign in
    private lateinit var auth: FirebaseAuth
    private val RC_SIGN_IN = 20
    private lateinit var googleSignInClient: GoogleSignInClient

    // for firebase realtime database
    private lateinit var database: DatabaseReference

    // shared pref set
    private lateinit var sharedPreferencesHelper: SharedPreferencesHelper

    private lateinit var sign_in_button_s1 : Button
    private lateinit var sign_out_button_s1 : Button
    private lateinit var startButton : Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //actionBar?.hide()
        setContentView(R.layout.activity_starting_page)

        //sharedprfes init
        sharedPreferencesHelper = SharedPreferencesHelper(this)

        //google sign in
        auth = FirebaseAuth.getInstance()
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)



        val welcomeTextView: TextView = findViewById(R.id.welcomeTextView)
        val aiNameTextView: TextView = findViewById(R.id.aiNameTextView)
        val subtitleTextView: TextView = findViewById(R.id.subtitleTextView)
        startButton = findViewById(R.id.let_start)
        sign_in_button_s1  = findViewById(R.id.sign_in_button_s1)
        sign_out_button_s1  = findViewById(R.id.sign_out_button_s1)

        // for speak function
        textToSpeech = TextToSpeech(this,this)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        utteranceId = UUID.randomUUID().toString()


        // Create animation
        val fadeIn = AlphaAnimation(0.0f, 1.0f)
        fadeIn.duration = 2000

        // Start animations
        welcomeTextView.startAnimation(fadeIn)
        welcomeTextView.alpha = 1.0f

        aiNameTextView.startAnimation(fadeIn)
        aiNameTextView.alpha = 1.0f

        subtitleTextView.startAnimation(fadeIn)
        subtitleTextView.alpha = 1.0f

        startButton.startAnimation(fadeIn)
        startButton.alpha = 1.0f

        sign_in_button_s1.startAnimation(fadeIn)
        sign_in_button_s1.alpha = 1.0f
        sign_in_button_s1.visibility = View.GONE

        sign_out_button_s1.startAnimation(fadeIn)
        sign_out_button_s1.alpha = 1.0f
        sign_out_button_s1.visibility = View.GONE

        //google sign out
        extra_mmsg = intent.getBooleanExtra("Extra_msg",false)
        if(extra_mmsg){
            val serviceIntent = Intent(this, ForegroundService::class.java)
            stopService(serviceIntent)
            val serviceIntentot = Intent(this, Norm_ForegroundService::class.java)
            stopService(serviceIntentot)
            SharedData.mainActivity?.finish()
            sign_out()
        }

        // Set click listener for the button
        startButton.setOnClickListener {
            // Navigate to the main activity (replace MainActivity with your main activity class)
            start_act()
        }
        sign_in_button_s1.setOnClickListener {
            val intentauth = googleSignInClient.signInIntent
            startActivityForResult(intentauth, RC_SIGN_IN)
        }
        sign_out_button_s1.setOnClickListener {
            sign_out()
        }
        if(isUserSignedIn()){
            if(foregroundServiceRunning()){
                // Navigate to the main activity (replace MainActivity with your main activity class)
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        //start_act()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("trackk","on destroy called from startring page")
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
    // check if sign in
    fun isUserSignedIn(): Boolean {
        return FirebaseAuth.getInstance().currentUser != null
    }

    // for speak function
    private fun speakText(resText: String, utteranceId: String) {
        if (requestAudioFocus()) {
            textToSpeech.speak(resText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        }
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
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            //val result = textToSpeech.setLanguage(Locale("en","IN")) // Set the language to US English or another desired language.
            val voices = textToSpeech.voices
            // 23,92 - good girl-1,100 - good girl - 2,120,134 - slow girl,235,261,370 - good girl - 3,429 - good girl - 3
            Log.d("trackk", voices.size.toString())
            // 33 - good,
            val voiceNameToSet = "en-us-x-sfg-network" // Replace with your desired voice name
            if (voices != null) {
                // Find the voice by name
                val selectedVoice = voices.find { voice ->
                    voice.name == voiceNameToSet
                }

                if (selectedVoice != null) {
                    textToSpeech.voice = selectedVoice
                    Log.d("TTS", "Voice set to: ${selectedVoice.name}, Locale: ${selectedVoice.locale}")
                } else {
                    Log.e("TTS", "Voice with name $voiceNameToSet not found!")
                }
            } else {
                Log.e("TTS", "No voices available!")
            }
            /*textToSpeech.voice = voices.elementAt(33)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Handle the case where the language is not available or not supported.
            }*/
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
    private fun releaseAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            audioManager.abandonAudioFocus(null)
        }
    }
    // for speak function end

    //starting activity management
    private fun start_act(){
        if(isUserSignedIn()){
            if(foregroundServiceRunning()){
                // Navigate to the main activity (replace MainActivity with your main activity class)
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish()
            } else {
                //speak sir need media player access, data will remain with best ever privacy and security
                // activate get started button text
                speakText("WELCOME SIR",utteranceId)
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish()
            }
        } else {
            // speak sir please sign in to start and activate sign in with google button
            sign_in_button_s1.visibility = View.VISIBLE
            startButton.visibility = View.GONE
            speakText("sir please sign in to start",utteranceId)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                firebaseAuth(account.idToken)
                sharedPreferencesHelper.saveString("user_user_email",account.email.toString())
            } catch (e: ApiException) {
                Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    //google sign in
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
                        sign_in_button_s1.visibility = View.GONE
                        startButton.visibility = View.VISIBLE
                        latch = CountDownLatch(1)
                        sharedPreferencesHelper.saveString("user_user_id",user.displayName.toString())
                        speakText("Sir,sign in successfully done with named ${user.displayName.toString()} ", utteranceId)
                        try {
                            latch.await()
                        } catch (e: InterruptedException) {
                            e.printStackTrace()
                        }

                        val intent = Intent(this,AI_nameSetting_activity::class.java)
                        startActivity(intent)
                        //start_act()
                    }
                } else {
                    // If sign in fails, display a message to the user.
                    Log.w("trackk", "signInWithCredential:failure", task.exception)
                    speakText("Sir,not able to sign in please check your internet connection ", utteranceId)
                }
            }
    }

    // google sign out
    private fun sign_out(){
        auth.signOut()
        googleSignInClient.signOut().addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                // Firebase user should be null after auth.signOut()
                if (auth.currentUser == null) {
                    // Sign-out from Firebase and Google Sign-In was successful
                    startButton.visibility = View.GONE
                    sign_in_button_s1.visibility = View.VISIBLE
                    Log.d("SignOut", "User successfully signed out from Firebase and Google.")

                    // Optional: Revoke access to the Google account
                    googleSignInClient.revokeAccess().addOnCompleteListener(this) { revokeTask ->
                        if (revokeTask.isSuccessful) {
                            Log.d("SignOut", "Google account access successfully revoked.")
                        } else {
                            Log.e("SignOut", "Failed to revoke Google account access.")
                        }
                        speakText("Sir, signing out, pleasure to work with you ", utteranceId)
                    }
                } else {
                    Log.e("SignOut", "Sign-out from Firebase failed, user is still signed in.")
                    speakText("Sir,not able to sign out please check your internet connection ", utteranceId)
                    sign_out_button_s1.visibility = View.VISIBLE
                }
            } else {
                Log.e("SignOut", "Sign-out from Google failed.")
                speakText("Sir,not able to sign out please check your internet connection ", utteranceId)
                sign_out_button_s1.visibility = View.VISIBLE
            }
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        //finishAffinity()
        moveTaskToBack(true)
    }
}
