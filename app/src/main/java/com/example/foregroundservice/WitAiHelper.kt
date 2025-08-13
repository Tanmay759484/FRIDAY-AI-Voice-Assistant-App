package com.example.foregroundservice

import com.google.gson.Gson
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

data class WitRequest(
    val type: String,
    val message: String
)

data class WitResponse(
    val context_map: Map<String, Any>,
    val response: ResponseText?,
    val action: String?,
    val expects_input: Boolean?,
    val is_final: Boolean?,
    val type: String
)

data class ResponseText(
    val text: String
)

class WitAiHelper(private val token: String) {

    private val client = OkHttpClient()
    private val gson = Gson()
    fun sendWitRequest(sessionId: String, contextMap: Map<String, Any>, userQuery: String, token: String, callback: (WitResponse?) -> Unit) {
        val url = "https://api.wit.ai/event?v=20240304&session_id=$sessionId"
        val requestBody = RequestBody.create(
            "application/json; charset=utf-8".toMediaTypeOrNull(),
            gson.toJson(WitRequest("message", userQuery))
        )
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        callback(null)
                        throw IOException("Unexpected code $response")
                    }
                    val responseData = response.body?.string()
                    val witResponse = gson.fromJson(responseData, WitResponse::class.java)
                    callback(witResponse)
                }
            }
        })
    }


    fun getIntentEntitiesAndTraits(text: String, callback: (String?, MutableList<String>?, MutableList<String>?, String?) -> Unit) {
        val url = "https://api.wit.ai/message?v=20220622&q=$text"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(null, null, null, e.message)
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.let {
                    try {
                        val jsonResponse = JSONObject(it.string())

                        // Extracting intent
                        val intent = if (jsonResponse.has("intents")) {
                            val intents = jsonResponse.getJSONArray("intents")
                            if (intents.length() > 0) intents.getJSONObject(0).getString("name") else "No intent found"
                        } else {
                            "No intent found"
                        }

                        // Extracting entities
                        val entities = if (jsonResponse.has("entities")) {
                            val entitiesJson = jsonResponse.getJSONObject("entities")
                            val entityList = mutableListOf<String>()
                            entitiesJson.keys().forEach { key ->
                                val entityArray = entitiesJson.getJSONArray(key)
                                for (i in 0 until entityArray.length()) {
                                    val entity = entityArray.getJSONObject(i)
                                    entityList.add("${entity.getString("name")}: ${entity.getString("value")}")
                                }
                            }
                            entityList
                        } else {
                            mutableListOf("No entities found")
                        }

                        // Extracting traits
                        val traits = if (jsonResponse.has("traits")) {
                            val traitsJson = jsonResponse.getJSONObject("traits")
                            val traitList = mutableListOf<String>()
                            traitsJson.keys().forEach { key ->
                                val traitArray = traitsJson.getJSONArray(key)
                                for (i in 0 until traitArray.length()) {
                                    val trait = traitArray.getJSONObject(i)
                                    traitList.add(trait.getString("name"))
                                }
                            }
                            traitList
                        } else {
                            mutableListOf("No traits found")
                        }

                        callback(intent, entities, traits, null)
                    } catch (e: Exception) {
                        callback(null, null, null, e.message)
                    }
                }
            }
        })
    }

    fun trainModel(trainingData: JSONArray, callback: (Boolean, String) -> Unit) {
        val url = "https://api.wit.ai/utterances?v=20240304"
        val mediaType = "application/json".toMediaTypeOrNull()
        val body = RequestBody.create("application/json; charset=utf-8".toMediaTypeOrNull(), trainingData.toString())

        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false, e.message ?: "Unknown error")
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                if (response.isSuccessful) {
                    callback(true, "Training data sent successfully.")
                } else {
                    callback(false, "Error: ${response.message}\n$responseBody")
                }
            }
        })
    }

    fun createEntity(entityName: String, token: String, callback: (Boolean, String) -> Unit) {
        val client = OkHttpClient()
        val url = "https://api.wit.ai/entities?v=20240304"
        val mediaType = "application/json".toMediaTypeOrNull()
        val body = JSONObject().apply {
            put("name", entityName)
            put("roles", JSONArray())
        }.toString()

        val request = Request.Builder()
            .url(url)
            .post(RequestBody.create(mediaType, body))
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false, e.message ?: "Unknown error")
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                if (response.isSuccessful) {
                    callback(true, "Entity created successfully.")
                } else {
                    callback(false, "Error: ${response.message}\n$responseBody")
                }
            }
        })
    }
    fun addContactsToWitAi(contacts: List<Contact>, callback: (Boolean, String) -> Unit) {
        GlobalScope.launch {
            contacts.forEachIndexed { index, contact ->
                // Delay to respect rate limits
                if (index > 0) delay(1000) // 1 second delay

                val jsonObject = JSONObject().apply {
                    put("keyword", contact.name)
                    put("synonyms", JSONArray().apply {
                        put(contact.name)
                    })
                }

                val requestBody = RequestBody.create(
                    "application/json; charset=utf-8".toMediaTypeOrNull(),
                    jsonObject.toString()
                )

                val request = Request.Builder()
                    .url("https://api.wit.ai/entities/money_Recipient/keywords?v=20240304")
                    .header("Authorization", "Bearer $token")
                    .post(requestBody)
                    .build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        e.printStackTrace()
                        callback(false, "Failed to add contact: ${contact.name}")
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.use {
                            if (response.isSuccessful) {
                                callback(true, "Successfully added contact: ${contact.name}")
                            } else {
                                callback(false, "Failed to add contact: ${contact.name}")
                            }
                        }
                    }
                })
            }
        }
    }
}
