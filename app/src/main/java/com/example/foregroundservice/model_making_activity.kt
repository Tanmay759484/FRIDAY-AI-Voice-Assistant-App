package com.example.foregroundservice

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class model_making_activity : AppCompatActivity() {
    private lateinit var button : Button
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_model_making)
        button = findViewById(R.id.button)
        button.setOnClickListener{
            val intent = Intent(this,WakeUpWordActivity::class.java)
            intent.putExtra("message_string","cancel")
            startActivity(intent)
        }
    }
}