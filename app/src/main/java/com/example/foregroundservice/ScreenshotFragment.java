package com.example.foregroundservice;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class ScreenshotFragment extends Fragment {

    private static final int REQUEST_SCREENSHOT = 101;
    private static final String SCREENSHOT_FILE_NAME = "screenshot.png";

    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;
    private ImageReader imageReader;
    private ImageView imageView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_screenshot, container, false);
        imageView = view.findViewById(R.id.imageView3);
        mediaProjectionManager = (MediaProjectionManager) requireActivity().getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        // Trigger the screenshot capture (e.g., on a button click)
        view.findViewById(R.id.captureButton).setOnClickListener(v -> requestScreenCapturePermission());

        return view;
    }

    private void requestScreenCapturePermission() {
        startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), REQUEST_SCREENSHOT);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SCREENSHOT && resultCode == Activity.RESULT_OK) {
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);
            startScreenCapture();
        }
    }

    private void startScreenCapture() {
        Log.d("trackk", String.valueOf(getView().getWidth()));
        Log.d("trackk", String.valueOf(getView().getHeight()));
        imageReader = ImageReader.newInstance(
                getView().getWidth(),
                getView().getHeight(),
                PixelFormat.RGBA_8888, // ImageFormat.RGB_565 or ImageFormat.RGBA_8888
                2 // Max Images
        );
        Image image = imageReader.acquireLatestImage();
        Log.d("trackk", String.valueOf(image));

        mediaProjection.createVirtualDisplay(
                "Screenshot",
                getView().getWidth(),
                getView().getHeight(),
                getResources().getDisplayMetrics().densityDpi,
                0,
                imageReader.getSurface(),
                null,
                null
        );

        // Delay capturing the screenshot to allow time for the virtual display to be created
        new Handler(Looper.getMainLooper()).postDelayed(() -> captureScreenshot(), 1000);
    }

    private void captureScreenshot() {
        Image image = imageReader.acquireLatestImage();
        Log.d("trackk", String.valueOf(image.getFormat()));
        if (image != null) {
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            Log.d("trackk",buffer.toString());
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            Log.d("trackk", Arrays.toString(bytes));
            Log.d("trackk", String.valueOf(bytes.length));

// Convert the byte array to a Bitmap
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bitmap != null) {
                saveBitmapToFile(bitmap);
                // Display the Bitmap in the ImageView
                // imageView.setImageBitmap(bitmap);
            } else {
                Log.e("Error", "Bitmap decoding failed.");
            }

// Display the Bitmap in the ImageView
            //imageView.setImageBitmap(bitmap);

// Don't forget to close the acquired Image
            image.close();
            /**
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * width;
            bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(buffer);
             **/

            /**
            // Save the screenshot to a file
            File screenshotFile = new File(requireActivity().getExternalFilesDir(null), SCREENSHOT_FILE_NAME);
            try (FileOutputStream outputStream = new FileOutputStream(screenshotFile)) {
                outputStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }**/

            // Release the image
            //image.close();
        }
    }
    private void saveBitmapToFile(Bitmap bitmap) {
        // Define the directory and filename for the saved image
        String directory = Environment.getExternalStorageDirectory().getPath() + "/YourAppDirectoryName/";
        String fileName = "imageer.png";

        // Create the directory if it doesn't exist
        File directoryFile = new File(directory);
        if (!directoryFile.exists()) {
            //directoryFile.mkdirs();
        }
        //Log.d("trackk",directoryFile.getAbsolutePath());

        // Save the Bitmap as an image file
        File file = new File("/storage/emulated/0/Android/data/com.example.foregroundservice/files", fileName);
        //Log.d("trackk",file.getAbsolutePath());
        try {
            FileOutputStream outputStream = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            outputStream.flush();
            outputStream.close();
        } catch (Exception e) {
            Log.e("ImageProcessingActivity", "Error saving image: " + e.getMessage());
        }
    }
}
