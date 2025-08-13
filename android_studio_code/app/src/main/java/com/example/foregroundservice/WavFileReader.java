package com.example.foregroundservice;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

import org.jcodec.common.DictionaryCompressor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class WavFileReader {
    private static final String TAG = "WavFileReader";

    public static int readWavFile(String filePath) {
        try {
            File file = new File(filePath);
            // Open the WAV file for reading
            FileInputStream inputStream = new FileInputStream(file);

            // Read WAV header to get audio format information
            byte[] header = new byte[44];
            int bytesRead = inputStream.read(header, 0, 44);

            if (bytesRead != 44) {
                Log.e(TAG, "Invalid WAV file format");
                return 1;
            }
            List<Integer> list = new ArrayList<>();
            // Parse header information
            for(int i=0;i<44;i=i+2){
                int audioFormat = byteArrayToInt(header, i, 2);
                list.add(audioFormat);
            }
            Log.d("trackk",filePath + " -  "+list.toString());

            int audioFormat = byteArrayToInt(header, 20, 2);
            int numChannels = byteArrayToInt(header, 22, 2);
            int sampleRate = byteArrayToInt(header, 24, 4);
            int bitsPerSample = byteArrayToInt(header, 34, 2);

            if (audioFormat != 1 || bitsPerSample != 16) {
                Log.e(TAG, "Unsupported WAV format (only PCM 16-bit supported)");
                return 1;
            }

            // Calculate the total number of samples
            int totalSamples = (int) (inputStream.getChannel().size() - 44) / 2;

            // Create a short array to hold audio data
            short[] audioBuffer = new short[totalSamples];

            // Read audio data into the buffer
            byte[] buffer = new byte[2];
            ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN); // Set byte order for little-endian WAV files

            int x=0;
            int dif = 800;
            ArrayList<Integer> integerList = new ArrayList<Integer>();
            ArrayList<Integer> average = new ArrayList<Integer>();
            int p;
            for (p = 0; p < totalSamples; p++) {
                if(p==dif){
                    integerList.add(x/800);
                    dif += 800;
                    x=0;
                }
                if (inputStream.read(buffer, 0, 2) != 2) {
                    Log.e("trackk", "Error reading audio data"+p);
                    return 1;
                }
                audioBuffer[p] = (short) Math.abs(byteBuffer.getShort(0));
                x+=audioBuffer[p];
            }

            int max_total=0;
            int target_index = 0;
            for(int i=0;i<20;i++){
                max_total+=integerList.get(i);
            }
            inputStream.close();
            int total=max_total;
            average.add(total);
            int integerList_size = integerList.size();
            for(int i=0;i<integerList_size-18;i++){
                if(i==integerList_size-20) break;
                total = total - integerList.get(i) + integerList.get(i+20);
                average.add(total);
                if(total>max_total){
                    max_total = total;
                    target_index = i+1;
                    Log.d("trackk","target index - "+target_index);
                }
            }
            if(target_index == 0){
                return 0;
            }
            Log.d("trackk","return float "+target_index);
            Log.d("trackk","return float - "+ (800*2*target_index));

            return (800*2*target_index);
        } catch (IOException e) {
            Log.e("trackk", "Error reading WAV file: " + e.getMessage());
            return 1;
        }
    }

    private static int byteArrayToInt(byte[] bytes, int offset, int length) {
        int value = 0;
        for (int i = 0; i < length; i++) {
            value |= (bytes[offset + i] & 0xFF) << (8 * i);
        }
        return value;
    }
}

