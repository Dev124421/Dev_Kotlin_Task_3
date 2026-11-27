package com.example.task_3

import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

object CloudinaryHelper {

    const val CLOUD_NAME = "pokc22nv"

    // Must match an Unsigned Upload Preset in your Cloudinary Dashboard
    // (Settings -> Upload -> Upload presets -> Signing Mode: Unsigned).
    const val UPLOAD_PRESET = "inkora_unsigned"

    private val mainHandler = Handler(Looper.getMainLooper())

    fun uploadImage(
        imageUri: Uri,
        folder: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (UPLOAD_PRESET.isBlank()) {
            mainHandler.post {
                onError("Cloudinary upload preset is not configured.")
            }
            return
        }

        MediaManager.get()
            .upload(imageUri)
            .unsigned(UPLOAD_PRESET)
            .option("folder", folder)
            .callback(object : UploadCallback {
                override fun onStart(requestId: String) {}

                override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {}

                override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                    val rawUrl = (resultData["secure_url"] as? String)
                        ?: (resultData["url"] as? String)
                    val secureUrl = rawUrl?.replaceFirst("http://", "https://")
                    mainHandler.post {
                        if (!secureUrl.isNullOrBlank()) {
                            onSuccess(secureUrl)
                        } else {
                            onError("Cloudinary did not return an image URL.")
                        }
                    }
                }

                override fun onError(requestId: String, error: ErrorInfo) {
                    mainHandler.post {
                        onError(error.description ?: "Image upload failed.")
                    }
                }

                override fun onReschedule(requestId: String, error: ErrorInfo) {
                    mainHandler.post {
                        onError(error.description ?: "Image upload rescheduled due to network issue.")
                    }
                }
            })
            .dispatch()
    }

    /**
     * Saves the Cloudinary profile picture URL to Firestore (users/{uid}),
     * updates the FirebaseAuth profile photo URI, and propagates authorPhotoUrl
     * to the user's existing posts in the blogs collection.
     */
    fun saveProfilePhotoToFirebase(
        uid: String,
        imageUrl: String,
        auth: FirebaseAuth,
        db: FirebaseFirestore,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection("users").document(uid)
            .set(mapOf("photoUrl" to imageUrl), SetOptions.merge())
            .addOnSuccessListener {
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setPhotoUri(Uri.parse(imageUrl))
                    .build()
                auth.currentUser?.updateProfile(profileUpdates)

                // Keep authorPhotoUrl up to date on existing blog posts by this user
                db.collection("blogs")
                    .whereEqualTo("authorId", uid)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        if (!snapshot.isEmpty) {
                            val batch = db.batch()
                            for (doc in snapshot.documents) {
                                batch.update(doc.reference, "authorPhotoUrl", imageUrl)
                            }
                            batch.commit()
                        }
                    }

                onSuccess()
            }
            .addOnFailureListener { e ->
                onFailure(e)
            }
    }

    /**
     * Saves the Cloudinary profile cover/banner URL to Firestore (users/{uid}.coverPhotoUrl).
     */
    fun saveCoverPhotoToFirebase(
        uid: String,
        imageUrl: String,
        db: FirebaseFirestore,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection("users").document(uid)
            .set(mapOf("coverPhotoUrl" to imageUrl), SetOptions.merge())
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e) }
    }
}
