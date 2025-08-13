/*
 * Copyright 2022 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.foregroundservice.AIname

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.foregroundservice.ForegroundService
import com.example.foregroundservice.MainActivity
import com.example.foregroundservice.NumberCallback
import com.example.foregroundservice.NumberHelperCallback
import com.example.foregroundservice.SharedData.stt_state
import com.example.foregroundservice.SharedPreferencesHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
//import org.tensorflow.lite.examples.audio.fragments.AudioClassificationListener
import org.tensorflow.lite.support.audio.TensorAudio
import org.tensorflow.lite.task.audio.classifier.AudioClassifier
import org.tensorflow.lite.task.core.BaseOptions
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

@RequiresApi(Build.VERSION_CODES.M)
class AudioClassificationHelper(
  val context: Context,
  //private val callback: NumberHelperCallback,
  //val listener: AudioClassificationListener,
  var currentModel: String = SPEECH_COMMAND_MODEL,
  var classificationThreshold: Float = DISPLAY_THRESHOLD,
  var overlap: Float = DEFAULT_OVERLAP_VALUE,
  var numOfResults: Int = DEFAULT_NUM_OF_RESULTS,
  var currentDelegate: Int = 0,
  var numThreads: Int = 2
) {

    var i : Int = 1
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main)
    private lateinit var classifier: AudioClassifier
    private lateinit var tensorAudio: TensorAudio
    private lateinit var recorder: AudioRecord
    private lateinit var executor: ScheduledThreadPoolExecutor
    private lateinit var sharedPreferencesHelper: SharedPreferencesHelper

    @RequiresApi(Build.VERSION_CODES.M)
    private val classifyRunnable = Runnable {
        classifyAudio()
    }

    init {
        //initClassifier()
    }

    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.M)
    fun initClassifier() {
        sharedPreferencesHelper = SharedPreferencesHelper(context)
        Log.d("trackk","initClassifier")
        // Set general detection options, e.g. number of used threads
        val baseOptionsBuilder = BaseOptions.builder()
            .setNumThreads(numThreads)

        // Use the specified hardware for running the model. Default to CPU.
        // Possible to also use a GPU delegate, but this requires that the classifier be created
        // on the same thread that is using the classifier, which is outside of the scope of this
        // sample's design.
        when (currentDelegate) {
            DELEGATE_CPU -> {
                // Default
            }
            DELEGATE_NNAPI -> {
                baseOptionsBuilder.useNnapi()
            }
        }
        // Define the model path from the local directory
        val directory =
            context.getExternalFilesDir(null).toString()  // Your app's internal storage directory
        val modelFile = File(directory, "Friday_model/browserfft-speech.tflite")
        // Usage: Copy model from Friday_model to internal storage
        /*val destinationFileName = "browserfft-speech.tflite"
        val copiedFile = copyFileToInternalStorage(modelFile.path, destinationFileName)
        val modelPath = File(context.filesDir, "browserfft-speech.tflite")*/


        // Open the ParcelFileDescriptor for the model file
        //val parcelFileDescriptor = ParcelFileDescriptor.open(modelPath, ParcelFileDescriptor.MODE_READ_ONLY)
        // Configures a set of parameters for the classifier and what results will be returned.
        val options = AudioClassifier.AudioClassifierOptions.builder()
            .setScoreThreshold(classificationThreshold)
            .setMaxResults(numOfResults)
            .setBaseOptions(baseOptionsBuilder.build())
            .build()

        if(modelFile.exists()){
            val modelBuffer = loadModelMappedByteBuffer(modelFile)
            try {
                // Create the classifier and required supporting objects
                //classifier = AudioClassifier.createFromFileAndOptions(context, modelPath.path, options)
                classifier = AudioClassifier.createFromBufferAndOptions(modelBuffer, options)
                tensorAudio = classifier.createInputTensorAudio()
                recorder = classifier.createAudioRecord()
                val audioSource = recorder.audioSource
                val sampleRate = recorder.sampleRate
                val channelConfig = recorder.channelConfiguration
                val audioFormat = recorder.audioFormat
                val bufferSize = recorder.bufferSizeInFrames
                recorder = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, bufferSize)
                startAudioClassification()
            } catch (e: IllegalStateException) {
                //listener.onError( "Audio Classifier failed to initialize. See error logs for details" )

                Log.e("AudioClassification", "TFLite failed to load with error: " + e.message)
            }
        }

    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun startAudioClassification() {
        if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            return
        }

        recorder.startRecording()
        executor = ScheduledThreadPoolExecutor(1)

        // Each model will expect a specific audio recording length. This formula calculates that
        // length using the input buffer size and tensor format sample rate.
        // For example, YAMNET expects 0.975 second length recordings.
        // This needs to be in milliseconds to avoid the required Long value dropping decimals.
        val lengthInMilliSeconds = ((classifier.requiredInputBufferSize * 1.0f) /
                classifier.requiredTensorAudioFormat.sampleRate) * 1000

        val interval = (lengthInMilliSeconds * (1 - overlap)).toLong()

        executor.scheduleAtFixedRate(
            classifyRunnable,
            0,
            interval,
            TimeUnit.MILLISECONDS)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun classifyAudio() {
        //Log.d("trackk","while")
        tensorAudio.load(recorder)
        var inferenceTime = SystemClock.uptimeMillis()
        val output = classifier.classify(tensorAudio)
        inferenceTime = SystemClock.uptimeMillis() - inferenceTime
        val outputtt : String = output[0].categories.toString()
//        Log.d("trackk",inferenceTime.toString())
//        Log.d("trackk",output[0].categories.toString())
        if(outputtt.lowercase().contains("vigor")){
           Log.d("trackk", "friday - $inferenceTime - " +System.currentTimeMillis() )

            stopAudioClassification()
            //callback.onhelperRequest("go back to foreground from audio classification")
            if (sharedPreferencesHelper.getBoolean("Wake up voice activation")){
                sendBroadcastMessage("hi")
            }
        }
        //listener.onResult(output[0].categories, inferenceTime)
    }

    fun stopAudioClassification() {
        if(this::recorder.isInitialized) {
            recorder.stop()
            executor.shutdownNow()
        }
    }

    fun sendBroadcastMessage(message: String) {
        // Create an Intent with the custom action and message
        val intent = Intent("com.example.yourapp.BROADCAST_ACTION")
        intent.putExtra("message", message)

        // Send the broadcast
        context.sendBroadcast(intent)
    }

    fun copyFileToInternalStorage(sourceFilePath: String, destinationFileName: String): File? {
        try {
            val sourceFile = File(sourceFilePath)
            val destFile = File(context.filesDir, destinationFileName)

            if (sourceFile.exists()) {
                FileInputStream(sourceFile).use { inputStream ->
                    FileOutputStream(destFile).use { outputStream ->
                        val buffer = ByteArray(1024)
                        var length: Int
                        while (inputStream.read(buffer).also { length = it } > 0) {
                            outputStream.write(buffer, 0, length)
                        }
                    }
                }
                Log.d("FileCopy", "File copied to internal storage at: ${destFile.path}")
                return destFile
            } else {
                Log.e("FileCopy", "Source file does not exist: $sourceFilePath")
            }
        } catch (e: IOException) {
            Log.e("FileCopy", "Error copying file: ${e.message}")
        }
        return null
    }

    fun loadModelMappedByteBuffer(modelFile: File): MappedByteBuffer? {
        try {
            FileInputStream(modelFile).use { inputStream ->
                val fileChannel = inputStream.channel
                return fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, modelFile.length())
            }
        } catch (e: Exception) {
            Log.e("AudioClassifier", "Error loading model: ${e.message}")
            e.printStackTrace()
        }
        return null
    }

    companion object {
        const val DELEGATE_CPU = 0
        const val DELEGATE_NNAPI = 1
        const val DISPLAY_THRESHOLD = 0.99f
        const val DEFAULT_NUM_OF_RESULTS = 1
        const val DEFAULT_OVERLAP_VALUE = 0.50f
        const val SPEECH_COMMAND_MODEL = "browserfft-speech.tflite"
    }
}
