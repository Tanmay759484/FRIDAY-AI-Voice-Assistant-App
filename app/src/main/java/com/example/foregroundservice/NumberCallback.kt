package com.example.foregroundservice

interface NumberCallback {
    fun onRequeststart(result : String)
    fun onTTS(string: String)
    fun onTouchSuccess()
}