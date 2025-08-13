package com.example.foregroundservice

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import org.w3c.dom.Text

class phonpe_settings : AppCompatActivity() {
    private lateinit var upi_barcode : TextView
    private lateinit var imageView_upi_barcode : ImageView
    private lateinit var contact_details : TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_phonpe_settings)
        contact_details = findViewById(R.id.contact_details)
        upi_barcode = findViewById(R.id.upi_barcode)
        imageView_upi_barcode = findViewById(R.id.imageView_upi_barcode)
        upi_barcode.setOnClickListener {
            val upiLink = "upi://pay?pa=paytm.s141c02@pty&pn=Paytm"
            //openUPIPaymentLinkWithPhonePe(this, upiLink)
            //captureBarcode()
            val intent = Intent(this, Camera_activity::class.java)
            startActivity(intent)
        }

        imageView_upi_barcode.setOnClickListener {
            val intent = Intent(this, Camera_activity::class.java)
            startActivity(intent)
        }

        contact_details.setOnClickListener {
            val intent = Intent(this,UPI_contact_details::class.java)
            startActivity(intent)
        }
    }
    fun openUPIPaymentLinkWithPhonePe(context: Context, upiUri: String) {
        try {
            // Create the intent
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(upiUri)
                // Set the package name for PhonePe to restrict the intent to PhonePe
                setPackage("com.phonepe.app")
            }

            // Start the activity
            context.startActivity(intent)
        } catch (e: Exception) {
            // Handle case where PhonePe is not installed
            Toast.makeText(context, "PhonePe app not installed", Toast.LENGTH_SHORT).show()
        }
    }

    private val captureImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val bitmap = result.data?.extras?.get("data") as? Bitmap
            bitmap?.let { processBarcode(it) }
        }
    }

    private fun captureBarcode() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        captureImageLauncher.launch(intent)
    }

    private fun processBarcode(bitmap: Bitmap) {
        // Convert the Bitmap to InputImage
        val image = InputImage.fromBitmap(bitmap, 0)

        // Get the BarcodeScanner instance
        val scanner = BarcodeScanning.getClient()

        // Process the image
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    val rawValue = barcode.rawValue
                    //tvBarcodeResult.text = rawValue ?: "No barcode detected"
                    Log.d("trackk",rawValue ?: "No barcode detected")
                }
            }
            .addOnFailureListener { e ->
                //tvBarcodeResult.text = "Failed to detect barcode: ${e.message}"
                Log.d("trackk","Failed to detect barcode: ${e.message}")
            }
    }

}