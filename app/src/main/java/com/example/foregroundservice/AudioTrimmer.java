package com.example.foregroundservice;

import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.AudioAttributes;
import android.media.AudioTrack;
import android.media.AudioManager;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class AudioTrimmer {
    private static final int SAMPLE_RATE = 16000; // Sample rate of the audio file (modify as needed)
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    public static void trimAudio(String inputFilePath, String outputFilePath, int startTrimbuffer) {
        int bufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        Log.d("trackk","audio trim buffer size" +String.valueOf(bufferSize));
        AudioTrack audioTrack = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AUDIO_FORMAT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_CONFIG)
                        .build())
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(bufferSize)
                .build();

        byte[] buffer = new byte[2];
        int bytesRead;
        int totalBytesRead = 0;

        try {
            FileInputStream fis = new FileInputStream(inputFilePath);
            FileOutputStream fos = new FileOutputStream(outputFilePath);

            // Skip audio data to the desired start time
            int bytesSkipped = 0;

            while ((bytesRead = fis.read(buffer)) != -1 && totalBytesRead < 44) {
                // Write to audio track to play audio in real-time (optional)
                audioTrack.write(buffer, 0, bytesRead);

                // Write to the output file to save trimmed audio
                fos.write(buffer, 0, bytesRead);

                totalBytesRead += bytesRead;
            }

            while (bytesSkipped < startTrimbuffer) {
                long bytesRemaining = startTrimbuffer - bytesSkipped;
                bytesRead = fis.read(buffer, 0, (int) Math.min(2, bytesRemaining));
                if (bytesRead == -1) {
                    break; // End of file reached
                }
                bytesSkipped += bytesRead;
            }

            //audioTrack.play();
            totalBytesRead = 0;

            while ((bytesRead = fis.read(buffer)) != -1 && totalBytesRead < 32000) {
                // Write to audio track to play audio in real-time (optional)
                if(totalBytesRead == 0){
                    Log.d("trackk",String.valueOf(bytesRead));
                }
                audioTrack.write(buffer, 0, bytesRead);

                // Write to the output file to save trimmed audio
                fos.write(buffer, 0, bytesRead);

                totalBytesRead += bytesRead;
            }

            audioTrack.stop();
            audioTrack.release();

            fis.close();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

