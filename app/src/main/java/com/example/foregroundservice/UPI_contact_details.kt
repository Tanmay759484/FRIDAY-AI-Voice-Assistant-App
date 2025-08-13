package com.example.foregroundservice

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.concurrent.thread

class UPI_contact_details : AppCompatActivity() {
    private lateinit var recycler_view: RecyclerView
    private lateinit var audioAdapter: AudioAdapter
    private lateinit var sharedPreferencesHelper: SharedPreferencesHelper
    private lateinit var upi_barcode : TextView
    private lateinit var imageView_upi_barcode : ImageView
    private lateinit var extraData_string : String
    private var extraData_bool = false

    private var upi_contacts : MutableList<Contact> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_upi_contact_details)

        upi_barcode = findViewById(R.id.upi_barcode)
        imageView_upi_barcode = findViewById(R.id.imageView_upi_barcode)
        sharedPreferencesHelper = SharedPreferencesHelper(this)
        upi_contacts =
            sharedPreferencesHelper.getContacts("upi_contacts").toMutableList()
        recycler_view = findViewById(R.id.recycler_view)
        audioAdapter = AudioAdapter(this, upi_contacts)
        recycler_view.layoutManager = LinearLayoutManager(this)
        recycler_view.adapter = audioAdapter

        upi_barcode.setOnClickListener {
            val upiLink = "upi://pay?pa=paytm.s141c02@pty"
            //val upiLink = "upi://pay?pa=paytm.s141c02@pty&pn=Paytm&am=10&cu=INR&tn=Payment for services"
            openUPIPaymentLinkWithPhonePe(this, upiLink)
            //openUPIPaymentLink(this,upiLink)
            //captureBarcode()
            /*val intent = Intent(this, Camera_activity::class.java)
            startActivity(intent)*/
        }

        imageView_upi_barcode.setOnClickListener {
            val intent = Intent(this, Camera_activity::class.java)
            startActivity(intent)
        }

        extraData_bool = intent.getBooleanExtra("from_camera",false)
        extraData_string = intent.getStringExtra("string_from_camera").toString()

        if (intent.getBooleanExtra("from_camera", false)) {
            Handler(Looper.getMainLooper()).postDelayed({
                if (extraData_bool){
                    showPopupWindow()
                }
            }, 100) // Delay of 100ms
        }
    }

    override fun onResume() {
        super.onResume()
    }

    private fun showPopupWindow() {
        if (isFinishing || isDestroyed) {
            Log.e("PopupWindow", "Activity is not in a valid state to show the popup.")
            return
        }

        // Inflate the popup layout
        val inflater = LayoutInflater.from(this)
        val popupView = inflater.inflate(R.layout.save_upi_linear_activity, null)

        // Create the PopupWindow
        val popupWindow = PopupWindow(
            popupView,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true // Focusable
        )

        // Set up EditText and buttons in the popup
        val editText = popupView.findViewById<EditText>(R.id.popup_edittext)
        val icon1 = popupView.findViewById<ImageView>(R.id.icon1)
        val icon2 = popupView.findViewById<ImageView>(R.id.icon2)

        icon1.setOnClickListener {
            val enteredText = editText.text.toString()
            sharedPreferencesHelper.saveContacts_withNewValues("upi_contacts", Contact(enteredText, extraData_string))
            upi_contacts = sharedPreferencesHelper.getContacts("upi_contacts").toMutableList()
            val intent = Intent("com.example.yourapp.BROADCAST_ACTION_upi_contacts_updation")
            intent.putExtra("message", "")

            // Send the broadcast
            sendBroadcast(intent)
            audioAdapter.updateContacts(upi_contacts)
            popupWindow.dismiss()
        }

        icon2.setOnClickListener {
            popupWindow.dismiss()
        }

        popupWindow.showAtLocation(window.decorView.rootView, Gravity.CENTER, 0, 0)
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

    fun openUPIPaymentLink(context: Context, upiLink: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(upiLink)
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            Toast.makeText(context, "No UPI app found", Toast.LENGTH_SHORT).show()
        }
    }



    class AudioAdapter(private val context: Context, private var contact_list : MutableList<Contact>) : RecyclerView.Adapter<AudioAdapter.AudioViewHolder>() {

        private lateinit var sharedPreferencesHelper : SharedPreferencesHelper


        inner class AudioViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val record_contact: ImageView = itemView.findViewById(R.id.record_contact)
            val contact_name: EditText = itemView.findViewById(R.id.contact_name)
            val delete_button: ImageView = itemView.findViewById(R.id.delete_button)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AudioAdapter.AudioViewHolder {
            sharedPreferencesHelper = SharedPreferencesHelper(context)
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_contact_row, parent, false)
            return AudioViewHolder(view)
        }

        override fun onBindViewHolder(holder: AudioViewHolder, position: Int) {
            val con_list = contact_list[position]
            holder.contact_name.setText(con_list.name)

            holder.record_contact.setOnClickListener {

            }

            holder.delete_button.setOnClickListener {

            }
        }

        override fun getItemCount(): Int {
            return contact_list.size
        }

        // Method to update the dataset
        fun updateContacts(newContacts: List<Contact>) {
            contact_list.clear()
            contact_list.addAll(newContacts)
            notifyDataSetChanged()
        }
    }
}