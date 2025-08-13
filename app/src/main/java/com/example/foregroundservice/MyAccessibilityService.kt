package com.example.foregroundservice

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.graphics.Rect
import android.os.*
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.concurrent.CountDownLatch
import kotlin.time.Duration

class MyAccessibilityService : AccessibilityService() {
    companion object {
        var latch_jiosaavn: CountDownLatch? = null
        var latch_phonepe_home: CountDownLatch? = null
        var phonepe_activity_state = 0
    }
    private var lo = true
    private var isreceiverRegistered = false
    private lateinit var db: FirebaseFirestore
    val TAG: String = "trackk"
    inner class MyAccessibilityServiceBinder : Binder() {
        fun getService(): MyAccessibilityService {
            return this@MyAccessibilityService
        }
    }

    private var z : Int = 1
    private var q : Int = 0
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Handle the broadcast data here
            if (intent?.action == "YOUR_CUSTOM_ACTION") {
                // Process the data as needed
                SharedData.xco = intent.getFloatExtra("t",0F)
                SharedData.yco = intent.getFloatExtra("m",0F)
                SharedData.touchPointDuration = intent.getLongExtra("holdTime",0)
                SharedData.back_button = intent.getBooleanExtra("back_button",false)
                SharedData.qco = intent.getBooleanExtra("touch",false)
                //x = intent.getFloatExtra("t",0F)
                // x = intent.getFloatExtra("m",0F)
                Log.d("trackk","q=1")
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val touchInterval = 500L // 5 seconds in milliseconds

    private val touchRunnable = object : Runnable {
        @RequiresApi(Build.VERSION_CODES.N)
        override fun run() {

            if(SharedData.get_recentList) {
                val rootNode = rootInActiveWindow
                rootNode?.let {
                    val recentContacts = getRecentContacts(rootNode)
                    if (recentContacts != null && recentContacts.isNotEmpty()) {
                        Log.d("RecentContacts", "Found recent contacts: $recentContacts")
                    } else {
                        Log.d("RecentContacts", "No recent contacts found.")
                    }
                    if (recentContacts != null) {
                        SharedData.get_recentList_list = recentContacts
                    } else {
                        SharedData.get_recentList_list = mutableListOf()
                    }
                }
                SharedData.get_recentList = false
            }

            if(SharedData.back_button){
                Log.d("trackk", "simulateBackButton  drht oi")
                simulateBackButton()
                SharedData.back_button = false
            }
            if(SharedData.back_button_state){
                Log.d("trackk", "simulateBackButton  drht oi")
                simulateBackButton()
                SharedData.back_button_state = false
            }

            if (SharedData.copy_text_stat){
                val rootNode = rootInActiveWindow
                rootNode?.let {
                    findAndSetText(it, SharedData.copy_text)
                }
                SharedData.copy_text_stat = false
            }

            if(SharedData.enter_pin){
                val rootNode = rootInActiveWindow ?: return
                // Example PIN
                //val pin = "983230"
                // Fill the PIN into the EditText
                findAndFillEditTextOnce(rootNode, SharedData.pin)
                findAndTouchNextAnyView(rootNode,"0")
                SharedData.enter_pin = false
            }
            if(SharedData.touchByText){
                val rootNode = rootInActiveWindow ?: return
                // Find and touch the TextView
                val isTouched = findAndTouchTextView_equal(rootNode, SharedData.touchByText_text)
                if (isTouched) {
                    Log.d(TAG, "Successfully touched the TextView with text ")
                } else {
                    Log.d(TAG, "Failed to touch the TextView with text ")
                }
                SharedData.touchByText = false
            }
            if(SharedData.touchByText_noParent){
                val rootNode = rootInActiveWindow ?: return
                // Find and touch the TextView
                val isTouched = findAndTouchTextView_equal_noParent(rootNode, SharedData.touchByText_text)
                if (isTouched) {
                    Log.d(TAG, "Successfully touched the TextView with text ")
                } else {
                    Log.d(TAG, "Failed to touch the TextView with text ")
                }
                SharedData.touchByText_noParent = false
            }
            if(SharedData.touchByText_contains){
                val rootNode = rootInActiveWindow ?: return
                // Find and touch the TextView
                val isTouched = findAndTouchTextView_contains(rootNode, SharedData.touchByText_text)
                if (isTouched) {
                    Log.d(TAG, "Successfully touched the TextView with text ")
                } else {
                    Log.d(TAG, "Failed to touch the TextView with text ")
                }
                SharedData.touchByText_contains = false
            }
            if(SharedData.touchBynextView){
                val rootNode = rootInActiveWindow ?: return
                // Find and touch the TextView
                val isTouched = findAndTouchTextViewForUPI(rootNode, SharedData.touchByText_nextView)
                if (isTouched) {
                    Log.d(TAG, "Successfully touched the TextView with text ")
                } else {
                    Log.d(TAG, "Failed to touch the TextView with text ")
                }
                SharedData.touchBynextView = false
            }
            if(SharedData.qco) {
                //Perform the touch event
                Log.d("trackk", "dispatchhhhhhhhhhhhhhhhhhhhhhhhhhhhh")
                Log.d(TAG, "dispatch coordinates are ${SharedData.xco} and ${SharedData.yco}")
                performTouch(SharedData.xco, SharedData.yco,SharedData.touchPointDuration)
                SharedData.qco = false
            }
            // Schedule the next touch event
            handler.postDelayed(this, touchInterval)
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun performTouch(x : Float, y : Float,holdDuration: Long){
        Log.d("trackk","touchhhhhhhhhhhhhhhhhhhhhhsdsfdsfe")
        val gestureBuilder = GestureDescription.Builder()

        // Specify the coordinates where you want to simulate the touch event
        //val startX = event.x
        //val startY = event.y

        // Create a StrokeDescription for the touch event
        val stroke = GestureDescription.StrokeDescription(
            Path().apply {
                moveTo(x, y)
            },
            0,
            holdDuration
        )

        // Add the StrokeDescription to the GestureDescription.Builder
        gestureBuilder.addStroke(stroke)

        // Build the GestureDescription
        val gesture = gestureBuilder.build()

        // Simulate the touch event
        //dispatchGesture(gesture, null, null)
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                //z=0
            }
        }, null)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onAccessibilityEvent(event: AccessibilityEvent) {

       /* // Find the EditText and set text programmatically
        val rootNode = rootInActiveWindow
        if (rootNode != null) {
            Log.d(TAG, "Root Node Hierarchy:")
            logAllNodes(rootNode)
        } else {
            Log.d(TAG, "Root node is null.")
        }*/

        /*val rootNode = rootInActiveWindow ?: return

        // Example PIN
        val pin = "983230"

        // Fill the PIN into the EditText
        findAndFillEditTextOnce(rootNode, pin)*/

        //Log.d(TAG, event.eventType.toString())

        /*// Traverse to find the currently focused element or the element that was interacted with
        val sourceNode = event.source ?: return

        // Get the bounds of the node
        val rect = Rect()
        sourceNode.getBoundsInScreen(rect)

        // The coordinates of the bounds of the node in the screen
        val xStart = rect.left
        val yStart = rect.top
        val xEnd = rect.right
        val yEnd = rect.bottom

        // If you want to estimate a touch position in the center of the element:
        val xCenter = (xStart + xEnd) / 2
        val yCenter = (yStart + yEnd) / 2*/

        //Log.d("AccessibilityService", "Touched node bounds: $rect")
        //Log.d("AccessibilityService", "Estimated touch position: ($xCenter, $yCenter)")
        //Log.d(TAG,it.className.toString() + " " + it.text.toString() + "   " + it.viewIdResourceName + " " + it.contentDescription)


        //phonepe_activity_state = 0
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            Log.d(TAG,"acc.clas - " + event.className.toString())
            //event.className?.toString()?.let { uploadEventString(it) }
            val className = event.className?.toString()
            // Check if this is the main activity of JioSaavn

            if (className == "com.phonepe.app.ui.activity.Navigator_MainActivity") {
                phonepe_activity_state = 1
                SharedData.input_accessi_activated = false
            } else if (className == "com.phonepe.app.chat.chatframework.ui.view.activity.Navigator_P2PMainActivity") {
                phonepe_activity_state = 2
            } else if (className == "com.phonepe.app.chat.chatframework.ui.view.activity.Navigator_ChatActivity"){
                phonepe_activity_state = 3
                SharedData.input_accessi_activated = false
            } else if (className == "org.npci.upi.security.pinactivitycomponent.GetCredential"){
                phonepe_activity_state = 4
            } else if (className == "org.chromium.chrome.browser.customtabs.CustomTabActivity"){
                phonepe_activity_state = 5
            } else if (className == "com.phonepe.app.v4.nativeapps.transaction.detail.ui.Navigator_TransactionDetailsActivity"){
                phonepe_activity_state = 6
            }

            if(phonepe_activity_state>0){
                latch_phonepe_home?.countDown()
            }

            if (className == "com.jio.media.jiobeats.HomeActivity") {
                // JioSaavn app is now in the foreground and main activity is loaded
                Log.d(TAG,"IN JIO MAIN")
                latch_jiosaavn?.countDown()  // Signal the latch
            }
            if(className == "android.inputmethodservice.SoftInputWindow") {
                SharedData.input_accessi_activated = true
            }
            SharedData.accessibility_changed = true
        }
        //Log.d("trackk", "onAccessibilityEvent count - ${q++}, ${event.eventType}, ${event.action}")
        if(z==1) {
            handler.postDelayed(touchRunnable,5000L)
            z=0
        }
        //if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED)

        //performTouch(605.0F, 742F)

        // Create a GestureDescription.Builder
    }

    private fun findAndSetText(nodeInfo: AccessibilityNodeInfo, text: String) {
        for (i in 0 until nodeInfo.childCount) {
            val child = nodeInfo.getChild(i)
            if (child != null) {
                if (child.className == "android.widget.EditText") {
                    val arguments = Bundle()
                    arguments.putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        text
                    )
                    child.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                } else {
                    findAndSetText(child, text)
                }
            }
        }
    }

    override fun onInterrupt() {
        //unregisterReceiver(receiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        if(isreceiverRegistered){
            unregisterReceiver(receiver)
            isreceiverRegistered = false
        }
        //unregisterReceiver(receiver)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onServiceConnected() {
        super.onServiceConnected()
        val filter = IntentFilter("YOUR_CUSTOM_ACTION")
        registerReceiver(receiver, filter, RECEIVER_EXPORTED)
        isreceiverRegistered = true
    }

    fun simulateBackButton() {
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun simulateHomeButton() {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }


    private fun uploadEventString(eventString: String) {
        // Create a new document with the event string
        val eventMap = hashMapOf(
            "eventString" to eventString,
            "timestamp" to System.currentTimeMillis()
        )

        // Add a new document with a generated ID
        db.collection("events")
            .add(eventMap)
            .addOnSuccessListener { documentReference ->
                Log.d(TAG, "DocumentSnapshot added with ID: ${documentReference.id}")
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Error adding document", e)
            }
    }
    override fun onCreate() {
        super.onCreate()
        // Initialize Firestore
        db = FirebaseFirestore.getInstance()
    }

    private fun logAllNodes(node: AccessibilityNodeInfo?, depth: Int = 0) {
        if (node == null) return

        // Print the current node's attributes
        val padding = "  ".repeat(depth) // Indentation for visualizing the hierarchy
        Log.d(TAG, "$padding ClassName: ${node.className}")
        Log.d(TAG, "$padding Text: ${node.text}")
        Log.d(TAG, "$padding ViewIdResourceName: ${node.viewIdResourceName}")
        Log.d(TAG, "$padding ContentDescription: ${node.contentDescription}")
        Log.d(TAG, "$padding BoundsInScreen: ${Rect().apply { node.getBoundsInScreen(this) }}")

        // Recursively print all child nodes
        for (i in 0 until node.childCount) {
            logAllNodes(node.getChild(i), depth + 1)
        }
    }

    private fun findAndFillEditTextOnce(rootNode: AccessibilityNodeInfo, pin: String) {
        // Locate the TextView with specific text
        val targetTextView = findTextViewByText(rootNode, "ENTER 6-DIGIT UPI PIN")
        targetTextView?.let {
            // Locate the first EditText after the TextView
            val editTextNode = findNextEditText(it)
            editTextNode?.let { editText ->
                // Focus on the EditText
                editText.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                // Fill the EditText with each digit of the PIN one by one
                for (digit in pin) {
                    val arguments = Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, editText.text?.toString() + digit)
                    }
                    editText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                    Thread.sleep(200) // Add a small delay between inputs (adjust as necessary)
                }
                Log.d(TAG, "PIN entered successfully.")
            } ?: run {
                Log.d(TAG, "No EditText found after the TextView.")
            }
        } ?: run {
            Log.d(TAG, "TextView with specified text not found.")
        }
    }

    // Finds the TextView with the specified text
    private fun findTextViewByText(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (node == null) return null

        // Check if the node is a TextView with the specified text
        if (node.className == "android.widget.TextView" && node.text?.toString()?.equals(text, ignoreCase = true) == true) {
            return node
        }

        // Recursively search in child nodes
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val result = findTextViewByText(child, text)
            if (result != null) {
                return result
            }
        }
        return null
    }

    // Finds the first EditText node after the specified TextView
    private fun findNextEditText(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null

        // Get the parent node
        val parent = node.parent ?: return null

        // Iterate through the parent's children
        for (i in 0 until parent.childCount) {
            val child = parent.getChild(i)
            // Check if this is the target TextView
            if (child == node) {
                // Look for the next EditText
                for (j in i + 1 until parent.childCount) {
                    val sibling = parent.getChild(j)
                    if (sibling?.className == "android.widget.EditText") {
                        return sibling
                    }
                }
            }
        }
        return null
    }

    private fun findAndTouchTextView_contains(rootNode: AccessibilityNodeInfo, text: String): Boolean {
        // Locate the TextView with the specified text
        val targetTextView = findTextViewByText_contains(rootNode, text)
        targetTextView?.let {
            // Perform a click action on the TextView
            val parent = it.parent
            val isClicked = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (isClicked) {
                Log.d(TAG, "TextView with text '$text' clicked successfully.")
                return true

            } else {
                Log.d(TAG, "Failed to click on TextView with text '$text'.")
            }
        } ?: run {
            Log.d(TAG, "TextView with text '$text' not found.")
        }
        return false
    }

    private fun findAndTouchTextView_equal_noParent(rootNode: AccessibilityNodeInfo, text: String): Boolean {
        // Locate the TextView with the specified text
        val targetTextView = findTextViewByText1(rootNode, text)
        targetTextView?.let {
            // Perform a click action on the TextView
            val parent = it
            val isClicked = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (isClicked) {
                Log.d(TAG, "TextView with text '$text' clicked successfully.")
                return true

            } else {
                Log.d(TAG, "Failed to click on TextView with text '$text'.")
            }
        } ?: run {
            Log.d(TAG, "TextView with text '$text' not found.")
        }
        return false
    }

    private fun findAndTouchTextView_equal(rootNode: AccessibilityNodeInfo, text: String): Boolean {
        // Locate the TextView with the specified text
        val targetTextView = findTextViewByText1(rootNode, text)
        targetTextView?.let {
            // Perform a click action on the TextView
            val parent = it.parent
            val isClicked = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (isClicked) {
                Log.d(TAG, "TextView with text '$text' clicked successfully.")
                return true

            } else {
                Log.d(TAG, "Failed to click on TextView with text '$text'.")
            }
        } ?: run {
            Log.d(TAG, "TextView with text '$text' not found.")
        }
        return false
    }

    // Finds the TextView with the specified text
    private fun findTextViewByText1(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if(node.text != null){

        }

        // Check if the node is a TextView with the specified text
        if (node.className == "android.widget.TextView" && node.text?.toString()?.equals(text, ignoreCase = true) == true) {
            Log.d(TAG + " fak",node.text.toString().lowercase())
            return node
        }



        // Recursively search in child nodes
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val result = findTextViewByText1(child, text)
            if (result != null) {
                return result
            }
        }
        return null
    }

    private fun findTextViewByText_contains(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if(node.text != null){

        }

        // Check if the node is a TextView with the specified text
        if (node.className == "android.widget.TextView" && node.text?.toString()?.contains(text, ignoreCase = true) == true) {
            Log.d(TAG + " fak",node.text.toString().lowercase())
            return node
        }



        // Recursively search in child nodes
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val result = findTextViewByText_contains(child, text)
            if (result != null) {
                return result
            }
        }
        return null
    }


    private fun findAndTouchTextViewForUPI(rootNode: AccessibilityNodeInfo, text: String): Boolean {
        // Locate the TextView with the specified text
        val targetTextView = findTextViewByText5(rootNode, text)
        targetTextView?.let { firstMatch ->
            // Find the next possible TextView after the first match
            val nextTextView = findNextTextView(firstMatch)
            nextTextView?.let {
                // Perform a click action on the parent of the next TextView
                val parent = it.parent
                val isClicked = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (isClicked) {
                    Log.d(TAG, "Next TextView clicked successfully.")
                    return true
                } else {
                    Log.d(TAG, "Failed to click on the next TextView.")
                }
            } ?: run {
                Log.d(TAG, "Next TextView after '$text' not found.")
            }
        } ?: run {
            Log.d(TAG, "TextView with text '$text' not found.")
        }
        return false
    }

    private fun findAndTouchNextAnyView(rootNode: AccessibilityNodeInfo, text: String): Boolean {
        // Locate the TextView with the specified text
        val targetTextView = findTextViewByText5(rootNode, text)
        targetTextView?.let { firstMatch ->
            // Find the next possible TextView after the first match
            val nextTextView = findNextAnyView(firstMatch)
            nextTextView?.let {
                // Perform a click action on the parent of the next TextView
                val parent = it
                val isClicked = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (isClicked) {
                    Log.d(TAG, "Next TextView clicked successfully.")
                    return true
                } else {
                    Log.d(TAG, "Failed to click on the next TextView.")
                }
            } ?: run {
                Log.d(TAG, "Next TextView after '$text' not found.")
            }
        } ?: run {
            Log.d(TAG, "TextView with text '$text' not found.")
        }
        return false
    }

    // Finds the TextView with the specified text
    private fun findTextViewByText5(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (node == null) return null

        // Check if the node is a TextView with the specified text
        if (node.className == "android.widget.TextView" && node.text?.toString()?.equals(text, ignoreCase = true) == true) {
            Log.d(TAG, "Found TextView with text: ${node.text}")
            return node
        }

        // Recursively search in child nodes
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val result = findTextViewByText5(child, text)
            if (result != null) {
                return result
            }
        }
        return null
    }

    // Finds the next possible TextView after the specified node
    private fun findNextTextView(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // Check sibling nodes for the next TextView
        val parent = node.parent ?: return null
        val childCount = parent.childCount
        var foundCurrentNode = false

        for (i in 0 until childCount) {
            val sibling = parent.getChild(i)

            if (sibling == node) {
                foundCurrentNode = true
                continue
            }

            if (foundCurrentNode && sibling != null) {
                // Recursively search for a TextView in the sibling node
                val nextTextView = searchForTextViewInHierarchy(sibling)
                if (nextTextView != null) {
                    return nextTextView
                }
            }
        }
        return null
    }

    private fun findNextAnyView(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // Check sibling nodes for the next TextView
        val parent = node.parent ?: return null
        val childCount = parent.childCount
        var foundCurrentNode = false

        for (i in 0 until childCount) {
            val sibling = parent.getChild(i)

            if (sibling == node) {
                foundCurrentNode = true
                continue
            }

            if (foundCurrentNode && sibling != null) {
                // Recursively search for a TextView in the sibling node
                return  sibling
            }
        }
        return null
    }

    // Recursively searches for a TextView in a given node hierarchy
    private fun searchForTextViewInHierarchy(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null

        // Check if the node is a TextView
        if (node.className == "android.widget.TextView") {
            Log.d(TAG, "Found next TextView: ${node.text}")
            return node
        }

        // Search in child nodes
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val result = searchForTextViewInHierarchy(child)
            if (result != null) {
                return result
            }
        }
        return null
    }

    private fun getRecentContacts(rootNode: AccessibilityNodeInfo): List<String>? {
        // Find the "Recents" TextView node
        val recentsNode = findTextViewByText(rootNode, "Recents")
        recentsNode?.let {
            // Locate the RecyclerView that is the parent of Recents' ViewGroup
            val recyclerViewNode = findParentRecyclerView(it)
            recyclerViewNode?.let { recyclerView ->
                // Collect contact names from RecyclerView until "All Contacts" is found
                return collectContactsFromRecyclerView(recyclerView)
            }
        }
        // If "Recents" or RecyclerView isn't found, return null
        return null
    }

    // Helper function to find the parent RecyclerView of a node
    private fun findParentRecyclerView(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var current = node?.parent
        while (current != null) {
            if (current.className == "androidx.recyclerview.widget.RecyclerView") {
                return current
            }
            current = current.parent
        }
        return null
    }

    // Helper function to collect contact names from a RecyclerView
    private fun collectContactsFromRecyclerView(recyclerViewNode: AccessibilityNodeInfo?): List<String> {
        val contactNames = mutableListOf<String>()
        if (recyclerViewNode == null) return contactNames

        var foundAllContacts = false

        // Iterate through the children of the RecyclerView
        for (i in 0 until recyclerViewNode.childCount) {
            val child = recyclerViewNode.getChild(i)

            // Check if the child is a ViewGroup containing a TextView with "All Contacts"
            val allContactsNode = findTextViewByText(child, "All Contacts")
            if (allContactsNode != null) {
                foundAllContacts = true
                break
            }

            // If not "All Contacts", collect contact names from the ViewGroup
            val contactName = extractContactNameFromViewGroup(child)
            if (contactName != null) {
                contactNames.add(contactName)
            }
        }

        return if (foundAllContacts) contactNames else emptyList()
    }

    // Helper function to extract a contact name from a ViewGroup
    private fun extractContactNameFromViewGroup(viewGroupNode: AccessibilityNodeInfo?): String? {
        if (viewGroupNode == null) return null

        // Search for a TextView within the ViewGroup that contains the contact name
        for (i in 0 until viewGroupNode.childCount) {
            val child = viewGroupNode.getChild(i)
            if (child.className == "android.widget.TextView" && !child.text.isNullOrEmpty()) {
                return child.text.toString().trim().replace("[^a-zA-Z0-9+/.(), ]".toRegex(), "")
                    .replace("...", "").trim().toLowerCase(
                        Locale.ROOT
                    )
            }
        }
        return null
    }




}