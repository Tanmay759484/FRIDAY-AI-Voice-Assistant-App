package com.example.foregroundservice

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.widget.Toolbar
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.foregroundservice.STT.Stt
import com.example.foregroundservice.STT.wavClass
import com.example.foregroundservice.SharedData.saveDiscardDialog_stat
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.checkerframework.checker.units.qual.A
import org.jcodec.common.DictionaryCompressor
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import kotlin.concurrent.thread
import kotlin.random.Random

class WakeUpWordActivity : AppCompatActivity() {

    companion object {
        lateinit var audioRecorder: wavClass
        lateinit var filepath: String
        lateinit var output_filepath: String
        lateinit var directory: String
        lateinit var model_dir: String
        lateinit var directory_main: String
        lateinit var directory_main_final : String
        lateinit var tempFilepath: String
    }

    // for record and save wake word
    private var AI_serial_no : Int = 0
    private var current_AI_serial_no : Int = 0
    private var AI_name: String? = null
    private var input_fileName = "input_AIrecording"
    private var output_fileName = "AIname_recording"
    private var tempFile = "tempFile"
    private val PERMISSION_REQUEST_RECORD_AUDIO = 1
    private var isRecording = true
    private var record_comp = false
    private var wake_activation_stat = true
    private var is_AI_Android_ID_mismatched = false
    private val handler = Handler()
    private var model_making_process_state = false

    private var mediaPlayer: MediaPlayer? = null

    // for Blur/Dim Background Overlay and framelayout
    private lateinit var bottomImage: ImageView
    private lateinit var recordButton: ImageView
    private lateinit var recordingInfo: TextView
    private lateinit var overlayView: View
    private lateinit var overlayView1: View
    private lateinit var recordingOverlay: View
    private lateinit var record_again_button : Button
    private lateinit var save_button : Button

    private lateinit var description_title : TextView
    private lateinit var add_des : TextView
    private lateinit var bottom_update_button : Button

    // initiate firebase varriable
    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth

    // shared pref set
    private lateinit var sharedPreferencesHelper: SharedPreferencesHelper

    //set up the recycleview
    private lateinit var recyclerView: RecyclerView
    private lateinit var audioAdapter: AudioAdapter
    private var audioFiles : MutableList<String> = mutableListOf()// Sample audio file names

    private lateinit var bottomSkip_button : Button
    private lateinit var save_icon : ImageView
    private lateinit var person_name_input : EditText
    private lateinit var countdownView : TextView
    private lateinit var recording_info_text : TextView
    private lateinit var recording_info2 : TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("trackk","wakeupword activity oncreate start")
        enableEdgeToEdge()
        setContentView(R.layout.activity_wake_up_word)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()
        // Get the current user's UID
        val currentUser = auth.currentUser
        val userId = currentUser?.uid
        // Initialize Firebase Realtime Database with user-specific path
        if (userId != null) {
            database = FirebaseDatabase.getInstance().getReference("MyAppData").child(userId)
        }

        // for record and save wake word
        AI_name = "Friday_wake_temp"
        directory =
            getExternalFilesDir(null).toString() // This will get the app-specific directory on external storage
        val create_folder = File("${directory}/$AI_name")
        val create_folder1 = File("${directory}/Friday_wake_main")
        val create_folder2 = File("${directory}/Friday_wake_main_final")
        SharedData.directory = directory
        create_folder.mkdirs()
        create_folder1.mkdirs()
        create_folder2.mkdirs()
        val create_folder3 = File("${directory}/Friday_model")
        create_folder3.mkdirs()
        model_dir = create_folder3.path
        directory = create_folder.path
        directory_main = create_folder1.path
        directory_main_final = create_folder2.path
        AI_serial_no = Random.nextInt(1, Int.MAX_VALUE)
        current_AI_serial_no = AI_serial_no

        if(hasWavFiles(directory_main_final)){
            deleteAllFilesInFolder(directory_main)
            copyAllFiles(directory_main_final, directory_main)
        }

        bottomSkip_button = findViewById(R.id.bottomSkip_button)

        sharedPreferencesHelper = SharedPreferencesHelper(this)
        wake_activation_stat = sharedPreferencesHelper.getBoolean("wake_activation")
        is_AI_Android_ID_mismatched = sharedPreferencesHelper.getBoolean("is_AI_Android_ID_mismatched")
        model_making_process_state = sharedPreferencesHelper.getBoolean("model_making_process_state")
        val extrac_string = intent.getStringExtra("message_string")
        if(extrac_string == "cancel"){
            if (userId != null) {

                /*sendBroadcastMessage_for_start_listener("remove_listeners")
                delete_UserWakeInfoToFirebase(userId)
                save_model_statFromFirebase(true)*/
            }
        }

        val extraData_msg = intent.getBooleanExtra("new_stat",false)
        bottomSkip_button.visibility = View.GONE
        if(model_making_process_state){
            showModelMakingInProcessDialog()
        }
        if(extraData_msg){
            deleteAllFilesInFolder(directory_main)
            deleteAllFilesInFolder(directory)
            deleteAllFilesInFolder(directory_main_final)
            deleteAllFilesInFolder(model_dir)
            SharedData.is_model_avl = false
            bottomSkip_button.visibility = View.VISIBLE
            if(is_AI_Android_ID_mismatched){
                showDeviceChangeDialog()
            }
            if(wake_activation_stat){
                bottomSkip_button.visibility = View.GONE
                if (userId != null) {
                    downloadWavFilesFromFirebase(userId, directory_main_final) { success ->
                        if (success) {
                            copyAllFiles(directory_main_final, directory_main)
                            val intent = Intent(this,MainActivity::class.java)
                            startActivity(intent)
                            Toast.makeText(this, "All files downloaded successfully", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this, "Failed to download one or more files", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }

        // Initialize obverlay and framelayout Views
        bottomImage = findViewById(R.id.bottom_image)
        recordButton = findViewById(R.id.record_button)
        recordingInfo = findViewById(R.id.recording_info)
        overlayView = findViewById(R.id.overlay_view)
        overlayView1 = findViewById(R.id.overlay_view1)
        recordingOverlay = findViewById(R.id.recording_overlay)

        description_title = findViewById(R.id.description_title)
        countdownView = findViewById(R.id.countdown_view)
        record_again_button = findViewById(R.id.record_again_button)
        recording_info_text = findViewById(R.id.recording_info_text)
        save_button = findViewById(R.id.save_button)
        add_des = findViewById(R.id.add_des)
        recording_info2 = findViewById(R.id.recording_info2)

        bottom_update_button = findViewById(R.id.bottom_update_button)

        bottom_update_button.setOnClickListener {
            bottom_update_button.visibility = View.GONE
            upload_File()
        }

        // Extract .wav files
        audioFiles = (create_folder1.listFiles { file ->
            file.extension == "wav"
        }?.map { it.name } ?: listOf()).toMutableList()  // Get the file names or return an empty list if none are found
        var size_lit_recy = audioFiles.size
        "$size_lit_recy/10".also { recording_info2.text = it }
        if(size_lit_recy == 10){
            bottomImage.visibility = View.GONE
            add_des.visibility = View.GONE
            if(wake_activation_stat){
                bottom_update_button.visibility = View.GONE
            } else {
                bottom_update_button.visibility = View.VISIBLE
            }
        } else {
            bottomImage.visibility = View.VISIBLE
            add_des.visibility = View.VISIBLE
        }

        // Set the status bar color programmatically
        window.statusBarColor = resources.getColor(R.color.wake_statusBar)  // Replace with your color

        add_des.setOnClickListener{
            showRecordingUI()
        }

        save_button.setOnClickListener {

            audioFiles = (create_folder1.listFiles { file ->
                file.extension == "wav"
            }?.map { it.name } ?: listOf()).toMutableList()
            audioAdapter.addAudioFiles(audioFiles)
            size_lit_recy = audioFiles.size
            "$size_lit_recy/10".also { recording_info2.text = it }
            current_AI_serial_no = 0
            if(size_lit_recy == 10){
                bottomImage.visibility = View.GONE
                add_des.visibility = View.GONE
                bottom_update_button.visibility = View.VISIBLE
                hideRecordingUI()
            } else {
                bottomImage.visibility = View.VISIBLE
                add_des.visibility = View.VISIBLE
            }
            record_comp = false
            recordButton.setImageResource(R.drawable.voice)
            recording_info_text.text = getString(R.string.tap_to_say)
            record_again_button.visibility = View.GONE
            save_button.visibility = View.GONE
        }

        record_again_button.setOnClickListener {
            val audioFile_h = File("$directory_main/$output_fileName$current_AI_serial_no.wav")
            if (audioFile_h.exists()) {
                val isDeleted = audioFile_h.delete()  // Delete the file
            }
            record_comp = false
            current_AI_serial_no = 0
            recordButton.setImageResource(R.drawable.voice)
            recording_info_text.text = getString(R.string.tap_to_say)
            record_again_button.visibility = View.GONE
            save_button.visibility = View.GONE
        }

        // Set OnClickListener to show the recording overlay
        bottomImage.setOnClickListener {
            showRecordingUI()
        }

        recordButton.setOnClickListener {

            if(record_comp) {
                val audioFile_h = File("$directory_main/$output_fileName$current_AI_serial_no.wav")

                if(audioFile_h.exists()){
                    // Handle play/pause logic
                    if (mediaPlayer != null && mediaPlayer!!.isPlaying) {
                        mediaPlayer?.stop()
                        mediaPlayer?.release()
                        mediaPlayer = null
                    } else {
                        mediaPlayer = MediaPlayer().apply {
                            setDataSource(audioFile_h.absolutePath)
                            prepare()
                            start()
                        }
                    }
                }
            } else{
                if (isRecording) {
                    isRecording = false
                    sendBroadcastMessage_for_stop_audioclassification("hi")
                    filepath = "$directory/$input_fileName$AI_serial_no.wav"
                    output_filepath = "$directory_main/$output_fileName$AI_serial_no.wav"
                    tempFilepath = "$directory/$tempFile$AI_serial_no.raw"
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

        }

        // Handle touches outside the recording button (on the overlay)
        overlayView.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                hideRecordingUI()  // Hide the recording UI and return to normal

                // Call performClick to ensure accessibility compatibility
                v.performClick()
                true
            } else {
                false
            }
        }

        // set up the recycleview
        recyclerView = findViewById(R.id.recycler_view)
        audioAdapter = AudioAdapter(this, audioFiles, bottomImage, add_des, bottom_update_button, recording_info2)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = audioAdapter

        save_icon = findViewById(R.id.save_icon)
        person_name_input = findViewById(R.id.person_name_input)

        person_name_input.setText(sharedPreferencesHelper.getString("user_AI_name"))

        bottomSkip_button.setOnClickListener {
            val intent = Intent(this,MainActivity::class.java)
            startActivity(intent)
        }

        save_icon.setOnClickListener {
            val name = person_name_input.text.toString()
            if(name.trim().isNotEmpty()){
                saveStringToFirebase(name)
            } else {
                Toast.makeText(this,"Please enter your AI name sir", Toast.LENGTH_SHORT).show()
            }
        }

        countTotalUserEntries()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("trackk","wakeupactivity ondestroy called")
    }

    override fun onStart() {
        super.onStart()
        listenForNewUserEntries()
    }

    @SuppressLint("MissingSuperCall")
    @Deprecated("This method has been deprecated in favor of using the\n      {@link OnBackPressedDispatcher} via {@link #getOnBackPressedDispatcher()}.\n      The OnBackPressedDispatcher controls how back button events are dispatched\n      to one or more {@link OnBackPressedCallback} objects.")
    override fun onBackPressed() {
        if(overlayView.visibility == View.VISIBLE)
        {
            hideRecordingUI()
        } else {
            if(saveDiscardDialog_stat){
                showSaveDiscardDialog()
            } else {
                val intent = Intent(this,MainActivity::class.java)
                startActivity(intent)
            }
        }
    }
    // Function to save string data to Firebase
    private fun saveStringToFirebase(data: String) {
        val key = "AI_assistance"
        database.child(key).setValue(data).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                // shared pref save
                sharedPreferencesHelper.saveString("user_AI_name",data)

                Toast.makeText(this, "Data saved to Firebase!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to save data.", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to fetch Boolean.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRecordingUI() {
        // Show the overlay and recording UI
        overlayView1.visibility = View.VISIBLE
        description_title.visibility = View.GONE
        overlayView.visibility = View.VISIBLE
        recordingOverlay.visibility = View.VISIBLE

        // Optionally, update the recording info text
        //recordingInfo.text = "Recording... 3 seconds left"
    }

    // Hide the recording overlay and restore the background
    private fun hideRecordingUI() {
        val audioFile_h = File("$directory_main/$output_fileName$current_AI_serial_no.wav")
        if (audioFile_h.exists()) {
            val isDeleted = audioFile_h.delete()  // Delete the file
        }
        recordButton.setImageResource(R.drawable.voice)
        recording_info_text.text = getString(R.string.tap_to_say)
        record_again_button.visibility = View.GONE
        save_button.visibility = View.GONE
        record_comp = false
        current_AI_serial_no = 0
        description_title.visibility = View.VISIBLE
        overlayView1.visibility = View.GONE
        overlayView.visibility = View.GONE
        recordingOverlay.visibility = View.GONE
    }

    private fun delayWithRepetition(repetitions: Int, delayMillis: Long) {
        countdownView.visibility = View.VISIBLE
        var currentRepetition = 0
        countdownView.text = buildString {
            append(currentRepetition.toString())
            append(" sec")
        }

        // Define a Runnable to be executed with a delay
        val delayRunnable = object : Runnable {
            override fun run() {
                // Your UI update code here
                //textView.text = "Delay ${currentRepetition + 1} of $repetitions"
                Log.d("trackk", "delay")

                currentRepetition++
                countdownView.text = buildString {
                    append(currentRepetition.toString())
                    append(" sec")
                }

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
                    /*val fileToDelete = File(filepath)
                    val fileToDelete1 = File(tempFilepath)
                    // Check if the file exists
                    if (fileToDelete.exists()) {
                        fileToDelete.delete()
                        fileToDelete1.delete()
                    }*/

                    deleteAllFilesInFolder(directory)

                    val fileUri: Uri =
                        Uri.fromFile(File("${directory}/$output_fileName$AI_serial_no.wav"))

                    // Specify the path to where you want to store the file in Firebase Storage
                    /*val storageRef =
                        storage.reference.child("uploaded_audio/${fileUri.lastPathSegment}")*/
                    // Upload the file to Firebase Storage
                    //val uploadTask = storageRef.putFile(fileUri)
                    isRecording = true
                    record_comp = true
                    countdownView.visibility = View.GONE
                    recordButton.setImageResource(R.drawable.play)
                    recording_info_text.text = getString(R.string.play_again)
                    save_button.visibility = View.VISIBLE
                    record_again_button.visibility = View.VISIBLE
                    current_AI_serial_no = AI_serial_no
                    if(overlayView.visibility == View.GONE) {
                        val audioFile_h = File("$directory_main/$output_fileName$AI_serial_no.wav")
                        if(audioFile_h.exists()){
                            val isDeleted = audioFile_h.delete()
                        }
                    }

                    val audioFile_h = File("$directory_main/$output_fileName$current_AI_serial_no.wav")
                    if(audioFile_h.exists()){
                        // Handle play/pause logic
                        if (mediaPlayer != null && mediaPlayer!!.isPlaying) {
                            mediaPlayer?.stop()
                            mediaPlayer?.release()
                            mediaPlayer = null
                        } else {
                            mediaPlayer = MediaPlayer().apply {
                                setDataSource(audioFile_h.absolutePath)
                                prepare()
                                start()
                            }
                        }
                    }
                    AI_serial_no = Random.nextInt(1, Int.MAX_VALUE)
                    sendBroadcastMessage_for_start_audioclassification("hi")
                }
            }
        }

        // Start the initial delay
        handler.postDelayed(delayRunnable, delayMillis)
    }

    fun deleteAllFilesInFolder(folderPath: String) {
        val folder = File(folderPath)

        if (folder.exists() && folder.isDirectory) {
            val files = folder.listFiles()
            if (files != null) {
                for (file in files) {
                    if (file.isFile) {
                        file.delete()  // Deletes individual file
                    }
                }
            }
        }
    }

    private fun save_wakeAI_boolStatFromFirebase(data: Boolean) {
        val key = "wake_activation"
        database.child(key).setValue(data).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                sharedPreferencesHelper.saveBoolean("wake_activation",true)
                Toast.makeText(this, "Data saved to Firebase!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to save data.", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to fetch Boolean.", Toast.LENGTH_SHORT).show()
        }
    }
    private fun save_model_statFromFirebase(data: Boolean, userId: String) {
        val key = "model_stat"
        val database_model = FirebaseDatabase.getInstance().getReference("model_updates").child(userId)
        database_model.child(key).setValue(data).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                sharedPreferencesHelper.saveBoolean("model_stat_not_avl",true)
                SharedData.is_model_avl = false
                Toast.makeText(this, "Data saved to Firebase!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to save data.", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to fetch Boolean.", Toast.LENGTH_SHORT).show()
        }
    }
    private fun saveUserWakeInfoToFirebase(userId: String) {
        val databaseRef = FirebaseDatabase.getInstance().reference.child("user_wake_info")

        // Create the data to be stored
        val userData = mapOf(
            "user_id" to userId,
            "timestamp" to System.currentTimeMillis() / 1000  // Store the timestamp in seconds
        )

        // Save the data under the user's ID
        databaseRef.child(userId).setValue(userData)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Data successfully saved
                    Toast.makeText(this, "User wake info saved successfully.", Toast.LENGTH_SHORT).show()
                } else {
                    // Handle the failure
                    Toast.makeText(this, "Failed to save data.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { exception ->
                // Handle any errors
                Log.d("trackk","Error ffffffff: ${exception.message}")
                Toast.makeText(this, "Error: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun delete_UserWakeInfoToFirebase(userId: String) {
        val databaseRef = FirebaseDatabase.getInstance().reference.child("user_wake_info")

        // Check if the user entry already exists
        databaseRef.child(userId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    // Entry with userId exists, delete it
                    databaseRef.child(userId).removeValue()
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                Log.d("Firebase", "Existing entry for user $userId deleted successfully.")
                            } else {
                                Log.e("Firebase", "Failed to delete existing entry for user $userId.")
                            }
                        }
                        .addOnFailureListener { exception ->
                            Log.e("Firebase", "Error deleting entry: ${exception.message}")
                        }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle the error
                Log.e("Firebase", "Error checking user data: ${error.message}")
            }
        })
    }

    private fun fetch_wakeAI_boolStatFromFirebase() {
        val key = "wake_activation"
        database.child(key).get().addOnSuccessListener { dataSnapshot ->
            if (dataSnapshot.exists()) {
                val retrievedData = dataSnapshot.getValue(Boolean::class.java) ?: false
                Toast.makeText(this, "AI wake word settings name found - $retrievedData", Toast.LENGTH_SHORT).show()
                wake_activation_stat = retrievedData
            } else {
                Toast.makeText(this, "No data found for this user.", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to fetch Boolean.", Toast.LENGTH_SHORT).show()
        }
    }
    fun getWavFilesFromDirectory(directoryPath: String): List<File> {
        val directory = File(directoryPath)
        return if (directory.exists() && directory.isDirectory) {
            directory.listFiles { file ->
                file.extension == "wav"
            }?.toList() ?: emptyList()
        } else {
            emptyList()
        }
    }
    private fun uploadWavFilesToFirebase(userId: String, wavFiles: List<File>, onComplete: (Boolean) -> Unit) {
        val storage = FirebaseStorage.getInstance()
        val storageRef = storage.reference.child("users/$userId/wav_files")

        var allFilesUploaded = true
        var filesUploadedCount = 0

        if (wavFiles.isEmpty()) {
            onComplete(true)  // No files to upload, so we consider it as success.
            return
        }

        for (file in wavFiles) {
            val fileUri = Uri.fromFile(file)
            val fileRef = storageRef.child(file.name)

            fileRef.putFile(fileUri)
                .addOnSuccessListener {
                    // File uploaded successfully
                    fileRef.downloadUrl.addOnSuccessListener { uri ->
                        // Optionally, save metadata (download URL) here if needed.
                        val downloadUrl = uri.toString()
                        //saveFileMetadataToDatabase(userId, file.name, downloadUrl)

                        filesUploadedCount++

                        // Check if all files have been processed
                        if (filesUploadedCount == wavFiles.size) {
                            onComplete(allFilesUploaded)  // All files processed, return the result
                        }
                    }
                }
                .addOnFailureListener {
                    // Handle unsuccessful upload
                    //Toast.makeText(this, "Failed to upload ${file.name}", Toast.LENGTH_SHORT).show()
                    allFilesUploaded = false  // Mark as failure

                    filesUploadedCount++
                    // Check if all files have been processed
                    if (filesUploadedCount == wavFiles.size) {
                        onComplete(allFilesUploaded)  // All files processed, return the result
                    }
                }
        }
    }

    private fun downloadWavFilesFromFirebase(userId: String, destinationPath: String, onComplete: (Boolean) -> Unit) {
        val storage = FirebaseStorage.getInstance()
        val storageRef = storage.reference.child("users/$userId/wav_files")

        storageRef.listAll().addOnSuccessListener { listResult ->
            val totalFiles = listResult.items.size
            var filesDownloadedCount = 0
            var allFilesDownloaded = true

            if (totalFiles == 0) {
                onComplete(true) // No files to download
                return@addOnSuccessListener
            }

            for (fileRef in listResult.items) {
                val localFile = File("$destinationPath/${fileRef.name}")

                fileRef.getFile(localFile)
                    .addOnSuccessListener {
                        // File downloaded successfully
                        filesDownloadedCount++
                        // Check if all files have been processed
                        if (filesDownloadedCount == totalFiles) {
                            onComplete(allFilesDownloaded) // All files downloaded
                        }
                    }
                    .addOnFailureListener {
                        // Handle unsuccessful download
                        allFilesDownloaded = false
                        filesDownloadedCount++
                        // Check if all files have been processed
                        if (filesDownloadedCount == totalFiles) {
                            onComplete(allFilesDownloaded) // One or more downloads failed
                        }
                    }
            }
        }.addOnFailureListener {
            // Handle failure to list files
            onComplete(false)
        }
    }



    private fun saveFileMetadataToDatabase(userId: String, fileName: String, downloadUrl: String) {
        val database = FirebaseDatabase.getInstance().reference
        val userFilesRef = database.child("users").child(userId).child("files")

        val fileMetadata = mapOf(
            "fileName" to fileName,
            "downloadUrl" to downloadUrl
        )

        userFilesRef.push().setValue(fileMetadata)
    }
    private fun upload_File(){
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val userId = currentUser.uid
            val wavFiles = getWavFilesFromDirectory(directory_main)

            if (isNetworkAvailable(this)) {
                deleteWavFilesFolder(userId) { isFolderDeleted ->
                    if (isFolderDeleted) {
                        // If folder was deleted successfully, proceed to upload files
                        uploadWavFilesToFirebase(userId, wavFiles) { success ->
                            if (success) {
                                sharedPreferencesHelper.saveBoolean("model_making_process_state",true)
                                save_model_statFromFirebase(false,userId)
                                saveUserWakeInfoToFirebase(userId)
                                val android_id = getDeviceId(this)
                                saveStringAndroidIDtoFirebase(android_id)
                                saveDiscardDialog_stat = false
                                deleteAllFilesInFolder(directory_main_final)
                                copyAllFiles(directory_main, directory_main_final)
                                save_wakeAI_boolStatFromFirebase(true)
                                sendBroadcastMessage_for_start_listener("add_listener")
                                Toast.makeText(this, "All files uploaded successfully", Toast.LENGTH_SHORT).show()
                                val intent = Intent(this,model_making_activity::class.java)
                                startActivity(intent)
                            } else {
                                bottom_update_button.visibility = View.VISIBLE
                                Toast.makeText(this, "One or more files failed to upload", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        // If folder deletion failed, show error
                        Toast.makeText(this, "Failed to delete the folder. Upload cancelled.", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                // No internet connection
                Toast.makeText(this, "No internet connection. Please try again later.", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Handle user not logged in
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
        }
    }

    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
            return when {
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> false
            }
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION")
            return networkInfo.isConnected
        }
    }

    private fun deleteWavFilesFolder(userId: String, onComplete: (Boolean) -> Unit) {
        val storage = FirebaseStorage.getInstance()
        val storageRef = storage.reference.child("users/$userId/wav_files")

        storageRef.listAll().addOnSuccessListener { listResult ->
            val totalFiles = listResult.items.size
            var filesDeletedCount = 0
            var deletionSuccess = true

            if (totalFiles == 0) {
                // If no files are in the folder, we consider it clean and call onComplete(true)
                onComplete(true)
                return@addOnSuccessListener
            }

            // Iterate over all items (files) and delete them
            for (fileRef in listResult.items) {
                fileRef.delete().addOnSuccessListener {
                    filesDeletedCount++
                    // If all files are deleted, notify the callback
                    if (filesDeletedCount == totalFiles && deletionSuccess) {
                        onComplete(true)  // All files deleted successfully
                    }
                }.addOnFailureListener {
                    // If a deletion fails, mark as failure
                    deletionSuccess = false
                    filesDeletedCount++
                    if (filesDeletedCount == totalFiles) {
                        onComplete(false)  // One or more deletions failed
                    }
                }
            }
        }.addOnFailureListener {
            // If listing files failed, call onComplete(false)
            onComplete(false)
        }
    }

    private fun showSaveDiscardDialog() {
        // Create an AlertDialog builder
        val builder = AlertDialog.Builder(this)

        // Set dialog title and message
        builder.setTitle("Unsaved Changes")
        builder.setMessage("You have unsaved changes. Do you want to modify or discard them?")

        // Set Modify button
        builder.setNegativeButton("Modify") { dialog, _ ->
            // Handle save action
            dialog.dismiss()
            //super.onBackPressed()  // Go back after saving
        }

        // Set Discard button
        builder.setPositiveButton("Discard") { dialog, _ ->
            saveDiscardDialog_stat = false
            deleteAllFilesInFolder(directory_main)
            copyAllFiles(directory_main_final, directory_main)
            // Handle discard action
            dialog.dismiss()
            val intent = Intent(this,MainActivity::class.java)
            startActivity(intent)  // Go back without saving
        }

        // Show the dialog
        builder.setCancelable(true)
        val dialog = builder.create()
        dialog.show()
    }

    private fun showDeviceChangeDialog() {
        // Create an AlertDialog builder
        val builder = AlertDialog.Builder(this)

        // Set dialog title and message
        builder.setTitle("Android Device Changed")
        builder.setMessage("Sir, you log in to a different device, you have to set up your wake word activation features again")

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

    fun copyAllFiles(sourceDirPath: String, destDirPath: String) {
        val sourceDir = File(sourceDirPath)
        val destDir = File(destDirPath)

        if (!sourceDir.exists() || !sourceDir.isDirectory) {
            Toast.makeText(this, "Source directory does not exist!", Toast.LENGTH_SHORT).show()
            return
        }

        // Ensure the destination directory exists
        if (!destDir.exists()) {
            destDir.mkdirs()
        }

        val files = sourceDir.listFiles()
        if (files != null) {
            for (file in files) {
                if (file.isFile) {
                    // Copy each file
                    val destFile = File(destDir, file.name)
                    copyFile(file, destFile)
                }
            }
        }
    }

    // Function to copy individual file
    fun copyFile(sourceFile: File, destFile: File) {
        try {
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    // Function to save string data to Firebase
    private fun saveStringAndroidIDtoFirebase(data: String) {
        val key = "AI_Android_ID"
        database.child(key).setValue(data).addOnCompleteListener { task ->
            if (task.isSuccessful) {

                Toast.makeText(this, "Data saved to Firebase!", Toast.LENGTH_SHORT).show()

            } else {
                Toast.makeText(this, "Failed to save data.", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to fetch data.", Toast.LENGTH_SHORT).show()
        }
    }

    fun getDeviceId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }


    //for admin code
    private fun listenForNewUserEntries() {
        val databaseRef = FirebaseDatabase.getInstance().reference.child("user_wake_info")

        // Add a ChildEventListener to listen for new entries
        databaseRef.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(dataSnapshot: DataSnapshot, previousChildName: String?) {
                // A new child has been added, count the total number of entries
                countTotalUserEntries()
            }

            override fun onChildChanged(dataSnapshot: DataSnapshot, previousChildName: String?) {
                // Handle child changed if necessary
            }

            override fun onChildRemoved(dataSnapshot: DataSnapshot) {
                // Handle child removed if necessary
                countTotalUserEntries()
            }

            override fun onChildMoved(dataSnapshot: DataSnapshot, previousChildName: String?) {
                // Handle child moved if necessary
            }

            override fun onCancelled(databaseError: DatabaseError) {
                // Handle any errors
                Log.e("Firebase", "Failed to listen for new entries", databaseError.toException())
            }
        })
    }

    private fun countTotalUserEntries() {
        val databaseRef = FirebaseDatabase.getInstance().reference.child("user_wake_info")

        // Get the total number of children (users) under the user_wake_info node
        databaseRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val totalUsers = dataSnapshot.childrenCount

                // Notify admin about the total number of users
                notifyAdminOfUserEntry(totalUsers)
            }

            override fun onCancelled(databaseError: DatabaseError) {
                // Handle any errors
                Log.e("Firebase", "Failed to count user entries", databaseError.toException())
            }
        })
    }

    private fun notifyAdminOfUserEntry(totalUsers: Long) {
        // Show a toast with the total number of users
        Toast.makeText(this, "Total number of users: $totalUsers", Toast.LENGTH_LONG).show()

        // Optionally, send a local notification
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = 3
        val channelId = "user_entry_channel"

        // Create a notification channel for Android Oreo and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = "User Entries"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, channelName, importance)
            notificationManager.createNotificationChannel(channel)
        }

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.common_google_signin_btn_icon_light)
            .setContentTitle("New User Entry")
            .setContentText("Total users: $totalUsers")
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    //for admin code

    fun hasWavFiles(directoryPath: String): Boolean {
        val directory = File(directoryPath)

        // Check if the directory exists and is indeed a directory
        if (directory.exists() && directory.isDirectory) {
            // List all files and filter for .wav files
            val wavFiles = directory.listFiles { _, name -> name.endsWith(".wav", ignoreCase = true) }

            // Return true if any .wav files are found
            if (!wavFiles.isNullOrEmpty()) {
                return true
            }
        }

        // Return false if no .wav files are found or directory doesn't exist
        return false
    }

    fun view_update_function(){
        audioFiles = (File(directory_main).listFiles { file ->
            file.extension == "wav"
        }?.map { it.name } ?: listOf()).toMutableList()
        audioAdapter.addAudioFiles(audioFiles)
    }

    fun sendBroadcastMessage_for_start_listener(message: String) {
        //Log.d("trackk","in floateee speech")
        // Create an Intent with the custom action and message
        val intent = Intent("com.example.yourapp.BROADCAST_ACTION_model_listener")
        intent.putExtra("message", message)

        // Send the broadcast
        sendBroadcast(intent)
    }

    fun sendBroadcastMessage_for_stop_audioclassification(message: String) {
        //Log.d("trackk","in floateee speech")
        // Create an Intent with the custom action and message
        val intent = Intent("com.example.yourapp.BROADCAST_ACTION_stop_audioclassification")
        intent.putExtra("message", message)

        // Send the broadcast
        sendBroadcast(intent)
    }
    fun sendBroadcastMessage_for_start_audioclassification(message: String) {
        //Log.d("trackk","in floateee speech")
        // Create an Intent with the custom action and message
        val intent = Intent("com.example.yourapp.BROADCAST_ACTION_start_audioclassification")
        intent.putExtra("message", message)

        // Send the broadcast
        sendBroadcast(intent)
    }







}

class AudioAdapter(private val context : Context, private var audioList: MutableList<String>, private val bottomImage: View,
                   private val add_des: View, private val bottom_update_button : View, private val recording_info2 : TextView) : RecyclerView.Adapter<AudioAdapter.AudioViewHolder>() {
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var sharedPreferencesHelper : SharedPreferencesHelper
    private lateinit var directory : String
    //lateinit var directory_main: String
    // A flag to track if an item is being deleted to prevent multiple rapid deletions
    private var isDeleting = false
    private lateinit var audioManager: AudioManager
    private var focusRequest : AudioFocusRequest? = null

    fun addAudioFiles(newAudioFiles: MutableList<String>) {
        // Clear the current list
        this.audioList.clear()

        // Add new items to the list
        this.audioList.addAll(newAudioFiles)

        // Notify the adapter that the dataset has changed
        notifyDataSetChanged()
    }


    inner class AudioViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val playPauseButton: ImageView = itemView.findViewById(R.id.play_pause_button)
        val audioFileName: TextView = itemView.findViewById(R.id.audio_file_name)
        val deleteButton: ImageView = itemView.findViewById(R.id.delete_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AudioViewHolder {
        sharedPreferencesHelper = SharedPreferencesHelper(context)
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_audio_row, parent, false)
        return AudioViewHolder(view)
    }

    override fun onBindViewHolder(holder: AudioViewHolder, position: Int) {
        val audioFile = audioList[position]
        holder.audioFileName.text = audioFile

        // Set click listeners for each button
        holder.playPauseButton.setOnClickListener {
            // Handle play/pause logic
            directory = SharedData.directory
            val audioFile_h = File("$directory/Friday_wake_main/$audioFile")

            if(audioFile_h.exists()){
                // Handle play/pause logic
                if (mediaPlayer != null && mediaPlayer!!.isPlaying) {
                    stopAndReleaseMediaPlayer()  // Stop playing if already playing
                } else {
                    requestAudioFocusAndPlay(audioFile_h)  // Request audio focus and play audio
                }
            }
        }

        holder.deleteButton.setOnClickListener {
            if (!isDeleting) {
                isDeleting = true  // Set the flag to true to indicate deletion in progress

                // Disable all delete buttons to avoid rapid clicks on other items
                holder.deleteButton.isClickable = false

                // Handle delete logic
                directory = SharedData.directory
                val audioFile_h = File("$directory/Friday_wake_main/$audioFile")

                if (audioFile_h.exists()) {
                    val isDeleted = audioFile_h.delete()  // Delete the file
                    if (isDeleted) {
                        if (sharedPreferencesHelper.getBoolean("wake_activation")) {
                            saveDiscardDialog_stat = true
                        }

                        // Remove the item from the list and notify the adapter
                        if (position < audioList.size) {  // Ensure the position is valid
                            audioList.removeAt(position)
                            // Notify on the main thread to ensure proper UI update
                            notifyItemRemoved(position)
                            notifyItemRangeChanged(position, audioList.size)

                            // Update UI elements
                            val size_lit_recy = audioList.size
                            "$size_lit_recy/10".also { recording_info2.text = it }
                            bottomImage.visibility = View.VISIBLE
                            add_des.visibility = View.VISIBLE
                            bottom_update_button.visibility = View.GONE
                        }
                    }
                } else {
                    Log.e("FileDelete", "File not found: $audioFile")
                    val audioFiles = (File(WakeUpWordActivity.directory_main).listFiles { file ->
                        file.extension == "wav"
                    }?.map { it.name } ?: listOf()).toMutableList()
                    addAudioFiles(audioFiles)
                }

                // Re-enable the delete button and allow further actions
                holder.deleteButton.postDelayed({
                    holder.deleteButton.isClickable = true
                    isDeleting = false  // Allow further deletions
                }, 300)  // Delay for 300ms to prevent rapid deletions
            }
        }


    }

    override fun getItemCount(): Int {
        return audioList.size
    }

    private fun requestAudioFocusAndPlay(audioFile: File) {
        val focusRequestResult: Int

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // For Android O and above
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener { focusChange ->
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                        stopAndReleaseMediaPlayer()  // Stop when audio focus is lost
                    }
                }
                .build()

            focusRequestResult = audioManager.requestAudioFocus(focusRequest!!)
        } else {
            // For older Android versions
            focusRequestResult = audioManager.requestAudioFocus(
                { focusChange ->
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                        stopAndReleaseMediaPlayer()  // Stop when audio focus is lost
                    }
                },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }

        if (focusRequestResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            playAudio(audioFile)
        } else {
            // Failed to gain audio focus
            Toast.makeText(context, "Failed to gain audio focus", Toast.LENGTH_SHORT).show()
        }
    }

    private fun playAudio(audioFile: File) {
        mediaPlayer = MediaPlayer().apply {
            setDataSource(audioFile.absolutePath)
            prepare()
            start()
        }

        mediaPlayer?.setOnCompletionListener {
            stopAndReleaseMediaPlayer()
        }
    }

    private fun stopAndReleaseMediaPlayer() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null

        // Abandon audio focus when done
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let {
                audioManager.abandonAudioFocusRequest(it)
            }
        } else {
            audioManager.abandonAudioFocus(null)
        }
    }
}
