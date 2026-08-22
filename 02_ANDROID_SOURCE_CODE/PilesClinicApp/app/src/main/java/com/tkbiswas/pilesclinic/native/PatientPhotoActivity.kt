package com.tkbiswas.pilesclinic.native

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.tkbiswas.pilesclinic.databinding.ActivityPatientPhotoBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Native rebuild -- Patient Photo. Search a patient by mobile, pick a gallery
 * image, downscale + JPEG-compress it, store as a data-URL in Supabase
 * patients.photo (+ mirror to followups). Downscaling keeps the base64 well
 * under the WebView's ~180k photo cap so the row stays small.
 */
class PatientPhotoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPatientPhotoBinding
    private val repository = PatientPhotoRepository()
    private var patient: PatientPhotoRepository.PatientRef? = null
    private var pendingPhotoData: String = ""
    private var cameraPhotoUri: Uri? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) processPickedImage(uri)
    }

    // TK APPROVED (2026-07-15): camera capture was missing here (only Gallery
    // worked) -- same proven pattern already used in RegistrationActivity,
    // feeding into the SAME existing processPickedImage() pipeline (nothing
    // duplicated, Gallery path untouched).
    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) openCamera()
            else Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show()
        }

    private val takePicture =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val uri = cameraPhotoUri
            if (success && uri != null) processPickedImage(uri)
        }

    private fun launchCameraWithPermission() {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) openCamera() else requestCameraPermission.launch(android.Manifest.permission.CAMERA)
    }

    private fun openCamera() {
        try {
            val dir = java.io.File(cacheDir, "images").apply { mkdirs() }
            val file = java.io.File(dir, "patient_${System.currentTimeMillis()}.jpg")
            val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            cameraPhotoUri = uri
            takePicture.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(this, "Camera could not open", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // TK-REPORTED (2026-07-27): this screen has no bottom bar, so until
        // now opening it never retried a save that was still stuck on this
        // phone. A staff member could sit here while a registration or a
        // payment stayed unsent. Same retry every other screen already does.
        try { BottomNav.retryStuckSaves(this) } catch (_: Throwable) { }
        binding = ActivityPatientPhotoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UppercaseInputUtil.applyToAll(binding.root)  // TK-REQUESTED GLOBAL RULE (2026-07-24): English text auto-CAPITAL, Password fields excluded automatically

        if (NativeSession.current(this) == null) { finish(); return }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnFind.setOnClickListener { findPatient() }
        // 🔒 TK-APPROVED (28.07.2026, ফটো-প্রুফে পাশ · খাতার সারি B47).
        // TK: *"ফটো যখন তোলা হয়ে গেছে, পুরনো ফটো আমি দেখছি — তারপরও তার নিচে
        // এরকম লেখা কেন থাকবে? Pick photo from gallery · Save photo।"*
        // এখন একটাই বোতাম, চাপলে Camera/Gallery-র পপ-আপ — Registration-এ
        // TK-এর পাশ করা হুবহু একই নিয়ম। btnPick মোছা হয়নি, শুধু লুকানো।
        binding.btnCamera.setOnClickListener { showPhotoSourceDialog() }
        // 🔒 TK-APPROVED (29.07.2026, ফটো-প্রুফে পাশ · খাতার সারি B64):
        // TK: *"অন্যান্য অ্যাপ্লিকেশনের ফটো জুম করা যায়, এখানে আমি কেন জুম
        // করতে পারছি না?"* — পিঞ্চ · দুবার ট্যাপ · টেনে সরানো, তিনটেই এখন চলে।
        // ⛔ কোনো বাইরের লাইব্রেরি যোগ করা হয়নি।
        ZoomableImageHelper.attach(binding.imgPhoto)
        binding.btnPick.visibility = View.GONE
        binding.btnSave.setOnClickListener { savePhoto() }
        // TK-REPORTED PATTERN (2026-07-18): same "still navy" oversight found
        // and fixed on the Draft/User-Photo screens — colored here too.
        // Text/click/enabled-logic unchanged.
        binding.btnFind.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#0F5C5C"))
        // ⛔ btnCamera-তে আর রং বসানো হয় না — ওটা এখন Registration-এর সবুজ পিল
        // (`bg_btn_photo_pill`), রং বসালে পিলটাই ঢাকা পড়ে যেত।
        binding.btnPick.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#1D6FE0"))
        binding.btnSave.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#0C9E33"))

        // Follow-up card photo triple-tap sends the selected patient's mobile.
        // Prefill and load that same patient automatically; manual search remains unchanged.
        val presetMobile = intent.getStringExtra("mobile").orEmpty().filter(Char::isDigit).takeLast(10)
        if (presetMobile.length == 10) {
            // TK-REQUESTED (2026-07-20): opened by 3-tapping a patient's photo,
            // so the patient is already known -- the mobile box + FIND PATIENT
            // step is pointless here. Hide them and jump straight to the photo
            // actions. (Opening this screen for a manual search still shows
            // them, since presetMobile is empty then.)
            binding.etMobile.setText(presetMobile)
            binding.etMobile.visibility = View.GONE
            binding.btnFind.visibility = View.GONE
            findPatient()
        }
    }

    private fun findPatient() {
        val digits = binding.etMobile.text.toString().filter { it.isDigit() }.takeLast(10)
        if (digits.length != 10) {
            Toast.makeText(this, "Enter a valid 10-digit mobile", Toast.LENGTH_SHORT).show()
            return
        }
        binding.progressLoad.visibility = View.GONE  // TK-REQUESTED (2026-07-20): spinner must NEVER spin anywhere; cache-first shows old data instantly, content appears when ready.
        lifecycleScope.launch {
            val found = withContext(Dispatchers.IO) { repository.findByMobile(digits) }
            binding.progressLoad.visibility = View.GONE
            if (found == null) {
                Toast.makeText(this@PatientPhotoActivity, "No patient found for this mobile", Toast.LENGTH_LONG).show()
                return@launch
            }
            patient = found
            pendingPhotoData = ""
            // ⛔ পুরনো এক-লাইনের লেখাটা মোছা হয়নি — শুধু লুকানো (নিচে নতুন কার্ড)।
            binding.tvPatient.text = PatientIdText.line(found.name, found.mobile, found.patientId)
            // 🔒 TK-APPROVED (29.07.2026, ফটো-প্রুফে পাশ · খাতার সারি B64):
            // নাম **মাঝখানে**, নিচে **বাঁয়ে মোবাইল**, **ডানে Patient ID**।
            val pMobile = found.mobile.filter { it.isDigit() }.takeLast(10)
            binding.tvPatientName.text = found.name.trim().ifBlank { pMobile }
            binding.tvPatientMobile.text = "\u260E  " + pMobile
            binding.tvPatientMobile.visibility = if (pMobile.isBlank()) View.GONE else View.VISIBLE
            val pId = found.patientId.trim()
            binding.tvPatientId.text = "\uD83C\uDD94  " + pId
            binding.tvPatientId.visibility = if (pId.isBlank()) View.GONE else View.VISIBLE
            binding.cardPatientInfo.visibility = View.VISIBLE
            binding.btnCamera.isEnabled = true
            binding.btnPick.isEnabled = true
            binding.btnSave.isEnabled = false
            binding.btnSave.visibility = View.GONE
            showExistingPhoto(found.photo)
            updatePhotoButtonText()
        }
    }

    private fun showExistingPhoto(photo: String) {
        val bmp = decodeDataUrl(photo)
        if (bmp != null) {
            binding.imgPhoto.setImageBitmap(bmp)
            binding.imgPhoto.visibility = View.VISIBLE
            // 🔒 খাতার সারি B450 (TK-নির্দেশ, 05.08.2026 — "সরিয়ে দিন,
            // সাবধানে") — জুমের ছোট মনে-করানো লেখাটা (tvZoomHint) সরানো
            // হয়েছে (XML থেকেই তুলে দেওয়া হয়েছে, তাই এখানে আর বাইন্ডিং-
            // রেফারেন্স নেই)। ⛔ জুম করার আসল সুবিধা (পিঞ্চ/ডাবল-ট্যাপ,
            // ZoomableImageHelper) অক্ষত — শুধু উপরে ভাসমান লেখাটা বাদ।
            binding.photoCard.visibility = View.VISIBLE
            ZoomableImageHelper.reset(binding.imgPhoto)
        } else {
            binding.imgPhoto.visibility = View.GONE
            binding.photoCard.visibility = View.GONE
        }
    }

    private fun processPickedImage(uri: Uri) {
        binding.progressLoad.visibility = View.GONE  // TK-REQUESTED (2026-07-20): spinner must NEVER spin anywhere; cache-first shows old data instantly, content appears when ready.
        lifecycleScope.launch {
            val dataUrl = withContext(Dispatchers.IO) { encodeResized(uri) }
            binding.progressLoad.visibility = View.GONE
            if (dataUrl == null) {
                Toast.makeText(this@PatientPhotoActivity, "Could not read image", Toast.LENGTH_SHORT).show()
                return@launch
            }
            pendingPhotoData = dataUrl
            showExistingPhoto(dataUrl)
            // খাতার সারি B47: নতুন ছবি এলে তবেই Save দেখা যায়।
            binding.btnSave.isEnabled = true
            binding.btnSave.visibility = View.VISIBLE
            updatePhotoButtonText()
        }
    }

    // TK-APPROVED (2026-07-26): this screen carries its OWN copy of the same
    // encoding logic PhotoUtils.encodeResized() has. Both were 400 / 70; both
    // are now 600 / 85 so the two screens can never produce different-sized
    // photos. Old photos are untouched -- only newly taken ones use this.
    private fun encodeResized(uri: Uri): String? {
        return try {
            val input = contentResolver.openInputStream(uri) ?: return null
            val original = BitmapFactory.decodeStream(input)
            input.close()
            if (original == null) return null
            val maxSide = 600
            val scale = maxSide.toFloat() / maxOf(original.width, original.height).toFloat()
            val bmp = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    original, (original.width * scale).toInt(), (original.height * scale).toInt(), true
                )
            } else original
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
            val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            "data:image/jpeg;base64,$b64"
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeDataUrl(dataUrl: String): Bitmap? {
        return try {
            val comma = dataUrl.indexOf(',')
            if (comma < 0) return null
            val bytes = Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * খাতার সারি B47 — ছবি থাকলে "📷 Change Photo", না থাকলে
     * "📷 Add Patient Photo"। Registration-এর হুবহু একই লেখা।
     */
    private fun updatePhotoButtonText() {
        val hasPhoto = binding.imgPhoto.visibility == View.VISIBLE
        binding.btnCamera.text = if (hasPhoto) "📷 Change Photo" else "📷 Add Patient Photo"
    }

    /**
     * ⛔ নতুন কিছু নয় — RegistrationActivity.showPhotoSourceDialog()-এর হুবহু
     * একই পপ-আপ (Camera / Gallery / Cancel), একই শব্দ, একই ক্রম।
     */
    private fun showPhotoSourceDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setCustomTitle(PremiumAlert.header(this, "Patient Photo"))
            .setItems(arrayOf("📷 Camera", "🖼️ Gallery")) { _, which ->
                if (which == 0) launchCameraWithPermission() else pickImage.launch("image/*")
            }
            .setNegativeButton("Cancel", null)
            .show().also { PremiumAlert.paint(it) }
    }

    private fun savePhoto() {
        val p = patient ?: return
        if (pendingPhotoData.isBlank()) return
        binding.progressLoad.visibility = View.GONE  // TK-REQUESTED (2026-07-20): spinner must NEVER spin anywhere; cache-first shows old data instantly, content appears when ready.
        binding.btnSave.isEnabled = false
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { repository.savePhoto(p, pendingPhotoData, applicationContext) }
            binding.progressLoad.visibility = View.GONE
            Toast.makeText(
                this@PatientPhotoActivity,
                if (ok) "Photo saved" else "Failed — check connection",
                Toast.LENGTH_SHORT
            ).show()
            binding.btnSave.isEnabled = !ok
            // সেভ হয়ে গেলে বোতামটা আবার সরে যায় — নিচে শুধু একটাই বোতাম থাকে।
            if (ok) {
                binding.btnSave.visibility = View.GONE
                updatePhotoButtonText()
            }
        }
    }
}
