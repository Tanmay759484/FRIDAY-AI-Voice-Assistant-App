package com.example.foregroundservice

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken


class SharedPreferencesHelper(context: Context) {

    private val sharedPreferences = context.getSharedPreferences("MyPrefs_for_contact21", Context.MODE_PRIVATE)

    fun getContacts(key: String): List<Contact> {
        val contactsJson = sharedPreferences.getString(key, "[]")
        val listType = object : TypeToken<List<Contact>>() {}.type
        return Gson().fromJson(contactsJson, listType)
    }

    fun saveContacts(key: String, contacts: List<Contact>) {
        val editor = sharedPreferences.edit()
        val contactsJson = Gson().toJson(contacts)
        editor.putString(key, contactsJson)
        editor.apply()
    }

    fun saveContacts_withNewValues(key: String, newContact: Contact) {
        val editor = sharedPreferences.edit()

        // Step 1: Extract the old value (if it exists)
        val oldContactsJson = sharedPreferences.getString(key, null)
        val oldContacts: MutableList<Contact> = if (oldContactsJson != null) {
            // Convert JSON string back to a list of Contact
            val type = object : TypeToken<List<Contact>>() {}.type
            Gson().fromJson(oldContactsJson, type)
        } else {
            // If no old value exists, initialize an empty list
            mutableListOf()
        }

        // Step 2: Add the new contact to the old list
        oldContacts.add(newContact)

        // Step 3: Save the updated list back to SharedPreferences
        val updatedContactsJson = Gson().toJson(oldContacts)
        editor.putString(key, updatedContactsJson)
        editor.apply()
    }



    fun saveString(key : String, details : String){
        val editor = sharedPreferences.edit()
        editor.putString(key, details)
        editor.apply()
    }
    fun getString(key: String) : String? {
        return sharedPreferences.getString(key,"")
    }
    fun saveBoolean(key: String, value: Boolean) {
        val editor = sharedPreferences.edit()
        editor.putBoolean(key, value)
        editor.apply()  // Or editor.commit() for synchronous saving
    }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        return sharedPreferences.getBoolean(key, defaultValue)
    }

    fun saveInt(key: String, int : Int){
        val editor = sharedPreferences.edit()
        editor.putInt(key, int)
        editor.apply()
    }

    fun getInt(key: String) : Int {
        return sharedPreferences.getInt(key,0)
    }

}
