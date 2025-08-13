package com.example.foregroundservice

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.io.IOException

class NetworkHelper {

    private val client = OkHttpClient()
    private val baseUrl = "http://13.232.75.140:5000/predict"


    fun sendRequest(text: String, callback: (String?) -> Unit) {
        // Prepare JSON body
        val json = JSONObject().apply {
            put("text", text)
        }

        val body = RequestBody.create(
            "application/json; charset=utf-8".toMediaType(),
            json.toString()
        )

        // Build the request
        val request = Request.Builder()
            .url(baseUrl)
            .post(body)
            .build()

        // Execute the request
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                callback(null)  // Pass null in case of failure
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseData = response.body?.string()
                    Log.d("trackk","failed aws - $responseData")
                    callback(responseData)  // Pass the response data to the callback
                } else {
                    callback(null)
                }
            }
        })
    }
}
