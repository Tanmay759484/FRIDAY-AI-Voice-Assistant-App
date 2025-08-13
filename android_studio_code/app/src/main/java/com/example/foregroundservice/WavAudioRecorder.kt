package com.example.foregroundservice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavAudioRecorder(private val outputFile: File) {
    private val sampleRate = 16000
    private val audioSource = MediaRecorder.AudioSource.MIC
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
    @SuppressLint("MissingPermission")
    private val audioRecord = AudioRecord(audioSource, sampleRate, channelConfig, audioFormat, bufferSize)
    private var isRecording = false

    init {
        audioRecord.startRecording()
    }

    fun startRecording() {
        isRecording = true
        Thread(Runnable {
            val data = ByteArray(bufferSize)
            val outputStream = FileOutputStream(outputFile)

            while (isRecording) {
                val bytesRead = audioRecord.read(data, 0, bufferSize)
                if (bytesRead != AudioRecord.ERROR_INVALID_OPERATION) {
                    outputStream.write(data, 0, bytesRead)
                }
            }

            outputStream.close()
        }).start()
    }

    fun stopRecording() {
        isRecording = false
        audioRecord.stop()
        audioRecord.release()
        writeWavHeader()
    }

    private fun writeWavHeader() {
        val channels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val totalDataLen = outputFile.length() - 8
        val totalAudioLen = totalDataLen - 44

        val header = ByteBuffer.allocate(44)
        header.order(ByteOrder.LITTLE_ENDIAN)

        // RIFF header
        header.put("RIFF".toByteArray())
        header.putInt(totalDataLen.toInt())

        // Format chunk
        header.put("WAVEfmt ".toByteArray())
        header.putInt(16) // Subchunk1Size
        header.putShort(1.toShort()) // AudioFormat (PCM)
        header.putShort(channels.toShort()) // NumChannels
        header.putInt(sampleRate) // SampleRate
        header.putInt(byteRate) // ByteRate
        header.putShort((channels * bitsPerSample / 8).toShort()) // BlockAlign
        header.putShort(bitsPerSample.toShort()) // BitsPerSample

        // Data chunk
        header.put("data".toByteArray())
        header.putInt(totalAudioLen.toInt())

        try {
            val outputStream = FileOutputStream(outputFile, true)
            outputStream.write(header.array())
            outputStream.close()
            Log.d("trackk",outputFile.toString())
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
