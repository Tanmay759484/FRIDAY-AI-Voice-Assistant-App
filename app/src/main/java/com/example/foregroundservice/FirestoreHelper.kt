package com.example.foregroundservice

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QueryDocumentSnapshot

class FirestoreHelper {

    val db = FirebaseFirestore.getInstance()

    fun getContacts(callback: (List<Contact>?, String?) -> Unit) {
        db.collection("contacts")
            .get()
            .addOnSuccessListener { result ->
                val contacts = result.map { document ->
                    document.toObject(Contact::class.java)
                }
                callback(contacts, null)
            }
            .addOnFailureListener { e ->
                callback(null, "Error getting contacts: ${e.message}")
            }
    }

    fun getdContacts(callback: (List<DocumentSnapshot>?, String?) -> Unit) {
        db.collection("contacts")
            .get()
            .addOnSuccessListener { result ->
                callback(result.documents, null)
            }
            .addOnFailureListener { e ->
                callback(null, "Error getting contacts: ${e.message}")
            }
    }

    fun saveContact(contact: Contact, callback: (Boolean, String) -> Unit) {
        db.collection("contacts")
            .add(contact)
            .addOnSuccessListener { documentReference ->
                callback(true, "Contact added with ID: ${documentReference.id}")
                Log.d("trackk add",contact.name +" "+ contact.number)
            }
            .addOnFailureListener { e ->
                callback(false, "Error adding contact: ${e.message}")
            }
    }
}
