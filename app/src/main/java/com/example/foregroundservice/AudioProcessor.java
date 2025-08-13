package com.example.foregroundservice;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

public class AudioProcessor {
    private static final String TAG = "AudioProcessor";

    // Configuration for audio recording
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    // Desired length of the output audio segment in milliseconds
    private static final int DESIRED_LENGTH_MS = 1000; // 1 second

    public void processAudio() {
        // Calculate the buffer size for audio recording
        int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        short[] audioBuffer = new short[bufferSize / 2]; // 16-bit audio

        // Initialize and start recording
        AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize);
        audioRecord.startRecording();

        try {
            // Variables to track the loudest segment
            int x = 0;
            long maxRMS = 0;
            long currentRMS = 0;
            int bytesRead = 0;
            long startTime = System.currentTimeMillis();

            while (true) {
                bytesRead = audioRecord.read(audioBuffer, 0, audioBuffer.length);
                x++;
                // Calculate RMS value for the current audio segment
                for (int i = 0; i < bytesRead; i++) {
                    currentRMS += audioBuffer[i] * audioBuffer[i];
                }

                // Check if we have collected enough audio data for the desired length
                long currentTime = System.currentTimeMillis();
                if (currentTime - startTime >= DESIRED_LENGTH_MS) {
                    if (currentRMS > maxRMS) {
                        maxRMS = currentRMS;
                    }
                    break;
                }
            }


            // Process the loudest segment (maxRMS) as needed
            Log.d(TAG, "Loudest segment identified with RMS value: " + maxRMS);

        } catch (Exception e) {
            Log.e(TAG, "Error processing audio: " + e.getMessage());
        } finally {
            audioRecord.stop();
            audioRecord.release();
        }
    }
}