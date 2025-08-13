package com.example.foregroundservice;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.Log;

import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public class TesseractOCR {
    private static final String TAG = "trackk";
    private TessBaseAPI mTess;

    public TesseractOCR(Context context) {
        // The "tessdata" directory is inside the "files" folder.
        String dataPath = context.getApplicationContext().getFilesDir().getPath();
        //TessBaseAPI tessBaseAPI = new TessBaseAPI();
        //String dataPath = Environment.getExternalStorageDirectory().getPath() + "/data/user/0/com.google.cloud.android.speech/files/tessdata";
        String subfolderName = "tessdata";

// Concatenate the subfolder name to the base path
        String subfolderPath = dataPath + File.separator + subfolderName;
        Log.d("trackk",subfolderPath);

// Create the subfolder directory if it doesn't exist
        File subfolder = new File(subfolderPath);
        if (!subfolder.exists()) {
            if (subfolder.mkdirs()) {
                // Successfully created the subfolder
            } else {
                // Failed to create the subfolder
            }
        }

        // Check if the trained data file "eng.traineddata" exists in the "tessdata" folder.
        // If not, copy it from the assets.
        String trainedDataPath = subfolderPath + "/eng.traineddata";
        File trainedDataFile = new File(trainedDataPath);
        //copyTrainedDataToInternalStorage(context, trainedDataPath);
        if (!trainedDataFile.exists()) {
            copyTrainedDataToInternalStorage(context, trainedDataPath);
        }

        mTess = new TessBaseAPI();
        mTess.init(dataPath, "eng");
        //mTess.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789");
    }

    private void copyTrainedDataToInternalStorage(Context context, String trainedDataPath) {
        try {
            InputStream inputStream = context.getAssets().open("tessdata/eng.traineddata");
            OutputStream outputStream = new FileOutputStream(trainedDataPath);

            byte[] buffer = new byte[1024];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }

            outputStream.flush();
            outputStream.close();
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Position recognizeText(String searchText, Bitmap bitmap) {
        int l = 0,t = 0;
        mTess.setImage(bitmap);
        List<Rect> wordBoxes = mTess.getWords().getBoxRects();
        //for (Rect box : wordBoxes) {
            //Log.d(TAG, "Position: Left = " + box.left + ", Top = " + box.top
            //       + ", Right = " + box.right + ", Bottom = " + box.bottom + ", Text = " + recognizeTextFromBox(bitmap, box));
        //}
        for (Rect box : wordBoxes) {
            String word =recognizeTextFromBox(bitmap, box);
            Log.d(TAG,word.toLowerCase());

            if (word.toLowerCase().equals(searchText)) {
                // The user-provided text is found within this bounding box
                l = box.left;
                int r = box.right;
                t = box.top;
                int b = box.bottom;

                l = l + (r-l)/2;
                t = t + (b-t)/2;
                Log.d(TAG,String.valueOf(l));
                Log.d(TAG,String.valueOf(t));
                break;
            }
        }
        return new Position(l,t);
    }

    public Position4 recognizeText_from_crop_photo(Bitmap bitmap) {
        boolean res_stat = false;
        StringBuilder cf = new StringBuilder();
        mTess.setImage(bitmap);
        List<Rect> wordBoxes = mTess.getWords().getBoxRects();
        for (Rect box : wordBoxes) {
            String word = recognizeTextFromBox(bitmap, box);
            if (word.toLowerCase().equals("recents")) {
                res_stat = true;
                //int width = bitmap.getWidth() - position.getNwidth();
                //int height = bitmap.getHeight() - position.getNheight();
                int width = bitmap.getWidth() - 145;
                int height = bitmap.getHeight() - 242;

                // Crop the bitmap
                Bitmap croppedBitmap = Bitmap.createBitmap(bitmap, 145, 242, width, height);
                Log.d(TAG, "bitmap_res_size = " + croppedBitmap.getWidth()+" ,"+croppedBitmap.getHeight());
                mTess.setImage(croppedBitmap);
                List<Rect> wordBoxess = mTess.getWords().getBoxRects();
                //for (Rect box : wordBoxes) {
                //Log.d(TAG, "Position: Left = " + box.left + ", Top = " + box.top
                //       + ", Right = " + box.right + ", Bottom = " + box.bottom + ", Text = " + recognizeTextFromBox(bitmap, box));
                //}
                for (Rect boxs : wordBoxess) {
                    String words = recognizeTextFromBox(croppedBitmap, boxs);
                    if (words != null && !words.trim().isEmpty()) {
                        cf.append(words).append(" ");
                    }
                    Log.d(TAG,words.toLowerCase());
                }
                break;
            }
            if (word.toLowerCase().contains("contacts")) {
                res_stat = false;
                int width = bitmap.getWidth() - 145;
                int height = bitmap.getHeight() - 122;

                // Crop the bitmap
                Bitmap croppedBitmap = Bitmap.createBitmap(bitmap, 145, 122, width, height);
                Log.d(TAG, "bitmap_con_size = " + croppedBitmap.getWidth()+" ,"+croppedBitmap.getHeight());
                mTess.setImage(croppedBitmap);
                List<Rect> wordBoxess = mTess.getWords().getBoxRects();
                //for (Rect box : wordBoxes) {
                //Log.d(TAG, "Position: Left = " + box.left + ", Top = " + box.top
                //       + ", Right = " + box.right + ", Bottom = " + box.bottom + ", Text = " + recognizeTextFromBox(bitmap, box));
                //}
                for (Rect boxs : wordBoxess) {
                    String words = recognizeTextFromBox(croppedBitmap, boxs);
                    if (words != null && !words.trim().isEmpty()) {
                        cf.append(words).append(" ");
                    }
                    Log.d(TAG,words.toLowerCase());
                }
                break;
            }

        }

        return new Position4(res_stat,cf.toString().trim());
    }

    public Position recognizeText_contain(String searchText, Bitmap bitmap) {
        int l = 0,t = 0;
        mTess.setImage(bitmap);
        List<Rect> wordBoxes = mTess.getWords().getBoxRects();
        //for (Rect box : wordBoxes) {
        //Log.d(TAG, "Position: Left = " + box.left + ", Top = " + box.top
        //       + ", Right = " + box.right + ", Bottom = " + box.bottom + ", Text = " + recognizeTextFromBox(bitmap, box));
        //}
        for (Rect box : wordBoxes) {
            String word =recognizeTextFromBox(bitmap, box);
            Log.d(TAG,word.toLowerCase());

            if (word.toLowerCase().contains(searchText)) {
                // The user-provided text is found within this bounding box
                l = box.left;
                int r = box.right;
                t = box.top;
                int b = box.bottom;

                l = l + (r-l)/2;
                t = t + (b-t)/2;
                Log.d(TAG,String.valueOf(l));
                Log.d(TAG,String.valueOf(t));
                break;
            }
        }
        return new Position(l,t);
    }

    public String recognizeText_return_all(Bitmap bitmap) {
        StringBuilder cf = new StringBuilder();
        mTess.setImage(bitmap);
        List<Rect> wordBoxes = mTess.getWords().getBoxRects();
        //for (Rect box : wordBoxes) {
        //Log.d(TAG, "Position: Left = " + box.left + ", Top = " + box.top
        //       + ", Right = " + box.right + ", Bottom = " + box.bottom + ", Text = " + recognizeTextFromBox(bitmap, box));
        //}
        for (Rect box : wordBoxes) {
            String word = recognizeTextFromBox(bitmap, box);
            if (word != null && !word.trim().isEmpty()) {
                cf.append(word).append(" ");
            }
            Log.d(TAG,word.toLowerCase());
        }
        return cf.toString().trim();
    }

    public Position3 recognizeText_return_name(Bitmap bitmap) {
        Log.d(TAG, "start image processing in tesseract");
        int l = 0;
        String s1 = "",s2 = "";
        mTess.setImage(bitmap);
        List<Rect> wordBoxes = mTess.getWords().getBoxRects();
        //for (Rect box : wordBoxes) {
        //Log.d(TAG, "Position: Left = " + box.left + ", Top = " + box.top
        //       + ", Right = " + box.right + ", Bottom = " + box.bottom + ", Text = " + recognizeTextFromBox(bitmap, box));
        //}
        for (Rect box : wordBoxes) {
            String word =recognizeTextFromBox(bitmap, box);
            Log.d(TAG,word.toLowerCase());

            if (word.toLowerCase().contains("manage")) {
                s1 = word;
                    if((l-6) > 0){
                        for(int i = l-6;i<l;i++){
                            s2 =s2 +recognizeTextFromBox(bitmap, wordBoxes.get(i)) + " ";
                        }
                    } else {
                        for(int i = 0;i<l;i++){
                            s2 =s2 +recognizeTextFromBox(bitmap, wordBoxes.get(i)) + " ";
                        }
                    }

                // The user-provided text is found within this bounding box
                break;
            }
            l++;
        }
        return new Position3(s1,s2);
    }

    public String recognizeText_by_id(Bitmap bitmap) {
        String qw = null;
        int l = 0,t = 0;
        mTess.setImage(bitmap);
        List<Rect> wordBoxes = mTess.getWords().getBoxRects();
        //for (Rect box : wordBoxes) {
        //Log.d(TAG, "Position: Left = " + box.left + ", Top = " + box.top
        //     + ", Right = " + box.right + ", Bottom = " + box.bottom + ", Text = " + recognizeTextFromBox(bitmap, box));
        //}
        int i = 0,ll = 0, ee = 0,ii=0;
        boolean h = false;

        for (Rect boxx : wordBoxes) {
            String word =recognizeTextFromBox(bitmap, boxx);
            Log.d(TAG,word.toLowerCase());

            if (word.toLowerCase().equals("ll")) {
                ll = i;
            }

            if (h) {
                if (word.toLowerCase().equals("é")) {
                    ee = i;
                    if(ii != (ll+1)){
                        for (int s = (ll+1); s <= (ii-1); s++) {
                            qw = recognizeTextFromBox(bitmap, wordBoxes.get(s));
                        }
                    }
                    //break;
                }


            }
            if (word.toLowerCase().equals("i")) {
                ii = i;
                h = true;

            }
            i++;
        }
        return qw;
    }

    public Position recognizeText_forpaste(Bitmap bitmap) {
        int l = 0,t = 0;
        mTess.setImage(bitmap);
        List<Rect> wordBoxes = mTess.getWords().getBoxRects();
        //for (Rect box : wordBoxes) {
        //Log.d(TAG, "Position: Left = " + box.left + ", Top = " + box.top
        //     + ", Right = " + box.right + ", Bottom = " + box.bottom + ", Text = " + recognizeTextFromBox(bitmap, box));
        //}
        for (Rect box : wordBoxes) {
            String word =recognizeTextFromBox(bitmap, box);
            Log.d(TAG,word.toLowerCase());

            if (word.equalsIgnoreCase("paste") || word.equalsIgnoreCase("poste")) {
                // The user-provided text is found within this bounding box
                l = box.left;
                int r = box.right;
                t = box.top;
                int b = box.bottom;

                l = l + (r-l)/2;
                t = t + (b-t)/2;
                Log.d(TAG,String.valueOf(l));
                Log.d(TAG,String.valueOf(t));
                break;
            }
        }
        return new Position(l,t);
    }

    public boolean recognize_three_Text_forIdentifying_contactUPI(String searchText1, String searchText2, String searchText3, Bitmap bitmap) {
        boolean result = false;
        int l = 0,t = 0;
        mTess.setImage(bitmap);
        List<Rect> wordBoxes = mTess.getWords().getBoxRects();
        //for (Rect box : wordBoxes) {
        //Log.d(TAG, "Position: Left = " + box.left + ", Top = " + box.top
        //     + ", Right = " + box.right + ", Bottom = " + box.bottom + ", Text = " + recognizeTextFromBox(bitmap, box));
        //}
        boolean statt = false,sta2 = false;
        int i = 0;
        int a =0;
        for (Rect box : wordBoxes) {
            i++;
            String word =recognizeTextFromBox(bitmap, box);
            Log.d(TAG,word.toLowerCase());
            if (word.toLowerCase().equals(searchText1)) {
                statt = true;
                a = i;
            }
            if (statt && i==a+1 && word.toLowerCase().equals(searchText2)) {
                sta2 = true;
                statt = false;
                a = i;
            }
            if(sta2 && i==a+1 && word.toLowerCase().equals(searchText3)){
                // The user-provided text is found within this bounding box
                result = true;
                break;
            }
        }
        return result;
    }

    public boolean recognize_three_two_forIdentifying_contactUPI(String searchText1, String searchText2, Bitmap bitmap) {
        boolean result = false;
        mTess.setImage(bitmap);
        List<Rect> wordBoxes = mTess.getWords().getBoxRects();
        //for (Rect box : wordBoxes) {
        //Log.d(TAG, "Position: Left = " + box.left + ", Top = " + box.top
        //     + ", Right = " + box.right + ", Bottom = " + box.bottom + ", Text = " + recognizeTextFromBox(bitmap, box));
        //}
        boolean statt = false;
        int i = 0;
        int a =0;
        for (Rect box : wordBoxes) {
            i++;
            String word =recognizeTextFromBox(bitmap, box);
            Log.d(TAG,word.toLowerCase());
            if (word.toLowerCase().equals(searchText1)) {
                statt = true;
                a = i;
            }
            if (statt && i==a+1 && word.toLowerCase().equals(searchText2)) {
                result = true;
                break;
            }
        }
        return result;
    }

    public Position recognize_three_Text(String searchText1, String searchText2, String searchText3, Bitmap bitmap) {
        int l = 0,t = 0;
        mTess.setImage(bitmap);
        List<Rect> wordBoxes = mTess.getWords().getBoxRects();
        //for (Rect box : wordBoxes) {
        //Log.d(TAG, "Position: Left = " + box.left + ", Top = " + box.top
        //     + ", Right = " + box.right + ", Bottom = " + box.bottom + ", Text = " + recognizeTextFromBox(bitmap, box));
        //}
        boolean statt = false,sta2 = false;
        int i = 0;
        int a =0;
        for (Rect box : wordBoxes) {
            i++;
            String word =recognizeTextFromBox(bitmap, box);
            Log.d(TAG,word.toLowerCase());
            if (word.toLowerCase().equals(searchText1)) {
                statt = true;
                a = i;
            }
            if (statt && i==a+1 && word.toLowerCase().equals(searchText2)) {
                sta2 = true;
                statt = false;
                a = i;
            }
            if(sta2 && i==a+1 && word.toLowerCase().equals(searchText3)){
                // The user-provided text is found within this bounding box
                l = box.left;
                int r = box.right;
                t = box.top;
                int b = box.bottom;

                l = l + (r-l)/2;
                t = t + (b-t)/2;
                Log.d(TAG,String.valueOf(l));
                Log.d(TAG,String.valueOf(t));
                break;
            }
        }
        return new Position(l,t);
    }

    public Position1 recognize_UPI_id(Bitmap bitmap) {
        int l = 0,t = 0;
        mTess.setImage(bitmap);
        List<Rect> wordBoxes = mTess.getWords().getBoxRects();
        //for (Rect box : wordBoxes) {
        //Log.d(TAG, "Position: Left = " + box.left + ", Top = " + box.top
        //     + ", Right = " + box.right + ", Bottom = " + box.bottom + ", Text = " + recognizeTextFromBox(bitmap, box));
        //}
        boolean statt = false;
        boolean upi_stat = false;
        boolean one_time = true;
        int i = 0;
        int a =0;
        int l1 = 0,t1 = 0;
        for (Rect box : wordBoxes) {
            i++;
            String word =recognizeTextFromBox(bitmap, box);
            Log.d(TAG,word.toLowerCase());
            if (word.equalsIgnoreCase("recents")){
                l = box.left;
                int r = box.right;
                t = box.top;
                int b = box.bottom;

                l = l + (r-l)/2;
                t = t + (b-t)/2;
                l1 = l;
                t1 = t;
                
            }
            if (word.equalsIgnoreCase("upi id")){
                l = box.left;
                int r = box.right;
                t = box.top;
                int b = box.bottom;

                l = l + (r-l)/2;
                t = t + (b-t)/2;
            }
            if (word.equalsIgnoreCase("new")) {
                statt = true;
                a = i;
            }
            if(statt && i==a+1 && word.equalsIgnoreCase("upi id")){
                // The user-provided text is found within this bounding box
                l = box.left;
                int r = box.right;
                t = box.top;
                int b = box.bottom;

                l = l + (r-l)/2;
                t = t + (b-t)/2;
                Log.d(TAG,String.valueOf(l));
                Log.d(TAG,String.valueOf(t));
                upi_stat = true;
                break;
            }
        }
        return new Position1(l,t,l1,t1,upi_stat);
    }

    public void stop() {
        mTess.stop();
    }
    public String recognizeTextFromBox(Bitmap bitmap, Rect box) {
        // Crop the bitmap to the specified bounding box
        Bitmap croppedBitmap = Bitmap.createBitmap(bitmap, box.left, box.top, box.width(), box.height());

        // Set the cropped image for OCR
        mTess.setImage(croppedBitmap);

        // Get the recognized text from the cropped image
        String recognizedText = mTess.getUTF8Text();

        //Log.d(TAG, "Recognized Text from Box: " + recognizedText);

        mTess.clear();
        return recognizedText;
    }

}

