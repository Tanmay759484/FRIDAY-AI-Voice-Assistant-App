package com.example.foregroundservice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.transition.Visibility
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class AI_nameSetting_activity : AppCompatActivity() {

    // for firebase realtime database
    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth

    // shared pref set
    private lateinit var sharedPreferencesHelper: SharedPreferencesHelper

    private lateinit var submit_button : Button
    private lateinit var AInameEdit_text : EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_ai_name_setting)
        submit_button = findViewById(R.id.submitButton)
        AInameEdit_text = findViewById(R.id.nameEditText)
        submit_button.visibility = View.GONE

        sharedPreferencesHelper = SharedPreferencesHelper(this)

        submit_button.setOnClickListener {
            val name = AInameEdit_text.text.toString()
            if(name.trim().isNotEmpty()){
                saveStringToFirebase(name)
            } else {
                Toast.makeText(this,"Please enter your AI name sir",Toast.LENGTH_SHORT).show()
            }
        }

        // Get the current user's UID
        auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser
        val userId = currentUser?.uid
        // Initialize Firebase Realtime Database with user-specific path
        if (userId != null) {
            database = FirebaseDatabase.getInstance().getReference("MyAppData").child(userId)
        }
        fetchStringFromFirebase()
    }

    private fun fetchStringFromFirebase() {
        val key = "AI_assistance"
        database.child(key).get().addOnSuccessListener { dataSnapshot ->
            if (dataSnapshot.exists()) {
                val retrievedData = dataSnapshot.getValue(String::class.java)

                // shared pref save
                sharedPreferencesHelper.saveString("user_AI_name",retrievedData.toString())

                //inputText.setText(retrievedData)
                submit_button.setText(retrievedData)
                Toast.makeText(this, "AI assistance name found - $retrievedData", Toast.LENGTH_SHORT).show()
                fetchStringAndroidIDFromFirebase()
            } else {
                submit_button.visibility = View.VISIBLE
                Toast.makeText(this, "No data found for this user.", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to fetch Boolean.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchStringAndroidIDFromFirebase() {
        val key = "AI_Android_ID"
        database.child(key).get().addOnSuccessListener { dataSnapshot ->
            if (dataSnapshot.exists()) {
                val retrievedData = dataSnapshot.getValue(String::class.java)
                val android_id = getDeviceId(this)
                //sharedPreferencesHelper.saveString("AI_Android_ID",android_id)
                Toast.makeText(this, "same device found - $retrievedData", Toast.LENGTH_SHORT).show()
                if(android_id == retrievedData){
                    fetch_wakeAI_boolStatFromFirebase()
                } else {
                    sharedPreferencesHelper.saveBoolean("is_AI_Android_ID_mismatched",true)
                    val keyaa = "wake_activation"
                    sharedPreferencesHelper.saveBoolean(keyaa,false)
                    val intent = Intent(this,WakeUpWordActivity::class.java)
                    intent.putExtra("new_stat",true)
                    startActivity(intent)
                    Toast.makeText(this, "wake up voice features not activated.", Toast.LENGTH_SHORT).show()
                }
            } else {
                val keyaa = "wake_activation"
                sharedPreferencesHelper.saveBoolean(keyaa,false)
                val intent = Intent(this,WakeUpWordActivity::class.java)
                intent.putExtra("new_stat",true)
                startActivity(intent)
                Toast.makeText(this, "wake up voice features not activated.", Toast.LENGTH_SHORT).show()
                Toast.makeText(this, "No data found for this user.", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to fetch Boolean.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveStringToFirebase(data: String) {
        val key = "AI_assistance"
        database.child(key).setValue(data).addOnCompleteListener { task ->
            if (task.isSuccessful) {

                // shared pref save
                sharedPreferencesHelper.saveString("user_AI_name",data)

                Toast.makeText(this, "Data saved to Firebase!", Toast.LENGTH_SHORT).show()

                fetchStringAndroidIDFromFirebase()
            } else {
                Toast.makeText(this, "Failed to save data.", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to fetch Boolean.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetch_wakeAI_boolStatFromFirebase() {
        val key = "wake_activation"
        database.child(key).get().addOnSuccessListener { dataSnapshot ->
            if (dataSnapshot.exists()) {
                val retrievedData = dataSnapshot.getValue(Boolean::class.java) ?: true
                sharedPreferencesHelper.saveBoolean(key,retrievedData)
                //inputText.setText(retrievedData)
                //submit_button.setText(retrievedData)
                Toast.makeText(this, "AI wake word settings name found - $retrievedData", Toast.LENGTH_SHORT).show()
                val intent = Intent(this,WakeUpWordActivity::class.java)
                intent.putExtra("new_stat",true)
                startActivity(intent)
            } else {
                sharedPreferencesHelper.saveBoolean(key,false)
                val intent = Intent(this,WakeUpWordActivity::class.java)
                intent.putExtra("new_stat",true)
                startActivity(intent)
                Toast.makeText(this, "No data found for this user.", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to fetch Boolean.", Toast.LENGTH_SHORT).show()
        }
    }

    // Function to save string data to Firebase
    private fun save_wakeAI_boolStatFromFirebase(data: Boolean) {
        val key = "wake_activation"
        database.child(key).setValue(data).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Toast.makeText(this, "Data saved to Firebase!", Toast.LENGTH_SHORT).show()

                val intent = Intent(this,WakeUpWordActivity::class.java)
                intent.putExtra("new_stat",true)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Failed to save data.", Toast.LENGTH_SHORT).show()
            }
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
            Toast.makeText(this, "Failed to fetch Boolean.", Toast.LENGTH_SHORT).show()
        }
    }

    fun getDeviceId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }
}