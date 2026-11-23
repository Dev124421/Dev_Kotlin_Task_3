package com.example.task_3

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import coil.load
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage

class SettingsActivity : AppCompatActivity() {

    private lateinit var btnBack: TextView
    private lateinit var ivSettingsAvatar: ImageView
    private lateinit var pbPfpUpload: ProgressBar
    private lateinit var btnChangePfp: TextView

    private lateinit var etSettingsName: EditText
    private lateinit var etSettingsUsername: EditText
    private lateinit var etSettingsBio: EditText
    private lateinit var etSettingsAge: EditText
    private lateinit var etSettingsGender: EditText
    private lateinit var etSettingsAddress: EditText
    private lateinit var btnSaveProfile: Button

    private lateinit var btnTerms: View
    private lateinit var btnPrivacy: View
    private lateinit var btnAbout: View
    private lateinit var btnLogout: View

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    // Image Picker for Profile Picture
    private val pfpPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            uploadProfilePicture(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        btnBack = findViewById(R.id.btnBack)
        ivSettingsAvatar = findViewById(R.id.ivSettingsAvatar)
        pbPfpUpload = findViewById(R.id.pbPfpUpload)
        btnChangePfp = findViewById(R.id.btnChangePfp)

        etSettingsName = findViewById(R.id.etSettingsName)
        etSettingsUsername = findViewById(R.id.etSettingsUsername)
        etSettingsBio = findViewById(R.id.etSettingsBio)
        etSettingsAge = findViewById(R.id.etSettingsAge)
        etSettingsGender = findViewById(R.id.etSettingsGender)
        etSettingsAddress = findViewById(R.id.etSettingsAddress)
        btnSaveProfile = findViewById(R.id.btnSaveProfile)

        btnTerms = findViewById(R.id.btnTerms)
        btnPrivacy = findViewById(R.id.btnPrivacy)
        btnAbout = findViewById(R.id.btnAbout)
        btnLogout = findViewById(R.id.btnLogout)

        btnBack.setOnClickListener {
            finish()
        }

        // Change Profile Picture
        btnChangePfp.setOnClickListener {
            pfpPickerLauncher.launch("image/*")
        }

        // Save Profile
        btnSaveProfile.setOnClickListener {
            saveUserSettings()
        }

        // Terms and Conditions Dialog
        btnTerms.setOnClickListener {
            showTermsDialog()
        }

        // Privacy Policy Dialog
        btnPrivacy.setOnClickListener {
            showPrivacyDialog()
        }

        // About Dialog
        btnAbout.setOnClickListener {
            showAboutDialog()
        }

        // Logout
        btnLogout.setOnClickListener {
            confirmLogout()
        }

        loadCurrentUserSettings()
    }

    private fun loadCurrentUserSettings() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    etSettingsName.setText(doc.getString("name") ?: "")
                    etSettingsUsername.setText(doc.getString("username") ?: "")
                    etSettingsBio.setText(doc.getString("bio") ?: "")
                    etSettingsAge.setText(doc.get("age")?.toString() ?: "")
                    etSettingsGender.setText(doc.getString("gender") ?: "")
                    etSettingsAddress.setText(doc.getString("address") ?: "")

                    val pfpUrl = doc.getString("photoUrl") ?: doc.getString("pfpUrl") ?: ""
                    if (pfpUrl.isNotEmpty()) {
                        ivSettingsAvatar.load(pfpUrl) {
                            crossfade(true)
                        }
                    }
                } else {
                    etSettingsName.setText(auth.currentUser?.displayName ?: "")
                    val derivedUsername = auth.currentUser?.email?.substringBefore("@") ?: ""
                    etSettingsUsername.setText(derivedUsername)
                }
            }
    }

    private fun uploadProfilePicture(imageUri: Uri) {
        val uid = auth.currentUser?.uid ?: return

        pbPfpUpload.visibility = View.VISIBLE
        btnChangePfp.isEnabled = false

        val storageRef = storage.reference.child("profile_pictures/$uid.jpg")
        storageRef.putFile(imageUri)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                    val urlString = downloadUrl.toString()

                    // Save to Firestore and Auth profile
                    db.collection("users").document(uid)
                        .set(mapOf("photoUrl" to urlString), SetOptions.merge())
                        .addOnCompleteListener {
                            pbPfpUpload.visibility = View.GONE
                            btnChangePfp.isEnabled = true
                            ivSettingsAvatar.load(urlString)
                            Toast.makeText(this, "Profile picture updated! 📸", Toast.LENGTH_SHORT).show()

                            val profileUpdates = UserProfileChangeRequest.Builder()
                                .setPhotoUri(downloadUrl)
                                .build()
                            auth.currentUser?.updateProfile(profileUpdates)
                        }
                }
            }
            .addOnFailureListener { e ->
                pbPfpUpload.visibility = View.GONE
                btnChangePfp.isEnabled = true
                Toast.makeText(this, "Upload failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveUserSettings() {
        val uid = auth.currentUser?.uid ?: return

        val name = etSettingsName.text.toString().trim()
        val username = etSettingsUsername.text.toString().trim().lowercase().replace(" ", "_")
        val bio = etSettingsBio.text.toString().trim()
        val age = etSettingsAge.text.toString().trim()
        val gender = etSettingsGender.text.toString().trim()
        val address = etSettingsAddress.text.toString().trim()

        if (name.isEmpty()) {
            etSettingsName.error = "Name cannot be empty"
            etSettingsName.requestFocus()
            return
        }

        btnSaveProfile.isEnabled = false
        btnSaveProfile.text = "Saving..."

        val updates = hashMapOf<String, Any>(
            "name" to name,
            "username" to username,
            "bio" to bio,
            "age" to age,
            "gender" to gender,
            "address" to address
        )

        db.collection("users").document(uid)
            .set(updates, SetOptions.merge())
            .addOnSuccessListener {
                btnSaveProfile.isEnabled = true
                btnSaveProfile.text = "Save Changes  ✓"

                // Update display name in Firebase Auth
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(name)
                    .build()
                auth.currentUser?.updateProfile(profileUpdates)

                Toast.makeText(this, "Account settings saved!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                btnSaveProfile.isEnabled = true
                btnSaveProfile.text = "Save Changes  ✓"
                Toast.makeText(this, "Error saving: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showTermsDialog() {
        val termsText = """
            Welcome to Inkora! By using this app, you agree to comply with our community terms.

            1. User Responsibilities
            You are responsible for safeguarding your account credentials and for all activities that occur under your account.

            2. Content Ownership & Rights
            You retain all rights to the stories, articles, and images you post on Inkora. By publishing, you grant Inkora a non-exclusive license to display your content to readers.

            3. Community Guidelines
            Respect fellow creators. Harassment, hate speech, spam, copyrighted material infringement, and malicious content are strictly prohibited.

            4. Account Termination
            Inkora reserves the right to suspend or remove accounts that violate these terms or community standards.

            5. Updates to Terms
            These terms may be updated periodically to reflect new features and community safety guidelines.
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Terms & Conditions")
            .setMessage(termsText)
            .setPositiveButton("I Agree", null)
            .show()
    }

    private fun showPrivacyDialog() {
        val privacyText = """
            Inkora takes your privacy seriously. Here is how your information is handled:

            1. Data Collection
            We collect your email address, profile information (name, username, bio), and stories you publish.

            2. Storage & Security
            All account data is securely stored and authenticated through encrypted industry-standard cloud database infrastructure with end-to-end access controls.

            3. Usage of Data
            Your information is used to personalize your feed, enable social engagement (likes and comments), and connect creators.

            4. No Third-Party Selling
            We do not sell, rent, or trade your personal data with third-party advertisers.

            5. Your Rights
            You have the right to update your profile information, edit or delete your stories, and request account deletion at any time.
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Privacy Policy")
            .setMessage(privacyText)
            .setPositiveButton("Got It", null)
            .show()
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("About Inkora 🪶")
            .setMessage("Inkora v1.0\n\nA modern social storytelling and blogging platform designed for creators, designers, and thinkers to share knowledge, stories, and ideas.\n\nCreate • Connect • Grow")
            .setPositiveButton("Close", null)
            .show()
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle("Log Out")
            .setMessage("Are you sure you want to log out of Inkora?")
            .setPositiveButton("Log Out") { _, _ ->
                auth.signOut()
                Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()

                val intent = Intent(this, MainActivity2::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
