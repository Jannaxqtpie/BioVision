package com.surendramaran.yolov8tflite

import ScoreDatabaseHelper3
import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.speech.tts.TextToSpeech
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class FullScreenImageActivity2 : AppCompatActivity() {

    private lateinit var overlayView: OverlayView
    private lateinit var imageView: ImageView
    private lateinit var btnCheckAnswers: Button
    private var boundingBoxes: MutableList<BoundingBox> = mutableListOf()
    private var correctLabels: MutableMap<BoundingBox, String> = mutableMapOf()
    private lateinit var btnRecordScore: Button
    private lateinit var btnClearScores: Button
    private lateinit var btnClearAnswers: Button
    private lateinit var scoreDatabase: ScoreDatabaseHelper3
    private lateinit var textToSpeech: TextToSpeech

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_full_screen_image2)

        overlayView = findViewById(R.id.overlayView)
        imageView = findViewById(R.id.savedImageView)
        btnCheckAnswers = findViewById(R.id.btnCheckAnswers)
        btnRecordScore = findViewById(R.id.btnRecordScore)
        btnClearScores = findViewById(R.id.btnClearScores)
        btnClearAnswers = findViewById(R.id.btnClearAnswers)

        val imageUriString = intent.getStringExtra("image_uri")
        if (imageUriString != null) {
            val imageUri = Uri.parse(imageUriString)
            imageView.setImageURI(imageUri)
        }

        boundingBoxes = intent.getParcelableArrayListExtra<BoundingBox>("bounding_boxes")
            ?.map {
                correctLabels[it] = it.clsName
                it.copy(clsName = "")
            }
            ?.toMutableList() ?: mutableListOf()

        overlayView.setShowLabels(true)
        overlayView.setResults(boundingBoxes)

        overlayView.setOnBoundingBoxClickListener { boundingBox ->
            showLabelInputDialog2(boundingBox)
        }

        btnCheckAnswers.setOnClickListener { checkAnswers2() }
        scoreDatabase = ScoreDatabaseHelper3(this)
        btnRecordScore.setOnClickListener { recordScore2() }
        btnClearScores.setOnClickListener { clearScores2() }
        btnClearAnswers.setOnClickListener { clearBoundingBoxAnswers() }
    }

    private fun showLabelInputDialog2(boundingBox: BoundingBox) {
        val editText = EditText(this).apply {
            hint = "Enter label"
            setText(boundingBox.clsName) // Pre-fill with existing label
            setTextColor(Color.BLACK)
            setPadding(50, 30, 50, 30)
        }

        val dialog = MaterialAlertDialogBuilder(this, R.style.CustomAlertDialogTheme)
            .setTitle("Enter Organ Label ✍️")
            .setView(editText)
            .setPositiveButton("Enter") { _, _ ->
                val newLabel = editText.text.toString().trim()
                if (newLabel.isNotEmpty()) {
                    updateBoundingBoxLabel(boundingBox, newLabel)
                    overlayView.updateTextColor(boundingBox, Color.WHITE) // Reset color to white when re-answering
                    overlayView.invalidate()
                } else {
                    Toast.makeText(this, "Label cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()

        applyDialogStyle(dialog)
        dialog.show()

        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val langResult = textToSpeech.setLanguage(Locale.getDefault())
                if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Toast.makeText(this, "TTS Language not supported!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateBoundingBoxLabel(boundingBox: BoundingBox, newLabel: String) {
        val index = boundingBoxes.indexOf(boundingBox)
        if (index != -1) {
            boundingBoxes[index] = boundingBox.copy(clsName = newLabel)
            overlayView.setResults(boundingBoxes)
        }
    }

    private fun checkAnswers2() {
        var correctCount = 0
        val total = boundingBoxes.size
        val correctAnswers = mutableListOf<String>()
        val incorrectAnswers = mutableListOf<String>()

        for (box in boundingBoxes) {
            val correctLabel = correctLabels[box] ?: ""
            if (box.clsName.isNotBlank() && box.clsName.equals(correctLabel, ignoreCase = false)) {
                correctCount++
                correctAnswers.add("✅ ${box.clsName}")
                overlayView.updateTextColor(box, Color.WHITE) // Reset correct answers to white
            } else {
                incorrectAnswers.add("❌ ${box.clsName} ($correctLabel)")
                overlayView.updateTextColor(box, Color.RED) // Change incorrect answers to red
            }
        }

        overlayView.invalidate() // Refresh overlay to show new colors

        val scoreText = "Score: $correctCount / $total"
        val passOrFail = if (correctCount >= total / 2) "Pass Congrats" else "Fail Try Again"

        // Apply white background to scoreText and passOrFail
        val spannableScoreText = SpannableString("$scoreText ($passOrFail)\n\n").apply {
            setSpan(BackgroundColorSpan(Color.WHITE), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        // Apply white background to "✔ Correct:" and "❌ Incorrect:"
        val correctLabelText = SpannableString("✔ Correct:\n").apply {
            setSpan(BackgroundColorSpan(Color.WHITE), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        val incorrectLabelText = SpannableString("❌ Incorrect:\n").apply {
            setSpan(BackgroundColorSpan(Color.WHITE), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        val speechText = "Your score is $correctCount out of $total. You $passOrFail."
        textToSpeech.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, null)

        val details = SpannableStringBuilder().apply {
            append(spannableScoreText) // Add formatted score text
            if (correctAnswers.isNotEmpty()) {
                append(correctLabelText)
                append(correctAnswers.joinToString("\n") + "\n\n") // Append answers without background color
            }
            if (incorrectAnswers.isNotEmpty()) {
                append(incorrectLabelText)
                append(incorrectAnswers.joinToString("\n")) // Append answers without background color
            }
        }

        val dialog = MaterialAlertDialogBuilder(this, R.style.CustomAlertDialogTheme)
            .setTitle("Results 🎯")
            .setMessage(details)
            .setPositiveButton("OK", null)
            .create()

        applyDialogStyle(dialog)
        dialog.show()

        askForUserName2(correctCount, total)
    }

    private fun askForUserName2(correct: Int, total: Int) {
        val editText = EditText(this).apply {
            hint = "Enter your name"
            setPadding(50, 30, 50, 30)
        }

        val dialog = MaterialAlertDialogBuilder(this, R.style.CustomAlertDialogTheme)
            .setTitle("Enter Your Name ✍️")
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->
                val userName = editText.text.toString().trim()
                if (userName.isNotEmpty()) {
                    saveScore2(userName, correct, total)
                } else {
                    Toast.makeText(this, "Name cannot be empty!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()

        applyDialogStyle(dialog)
        dialog.show()
    }

    private fun saveScore2(userName: String, correct: Int, total: Int) {
        val score = "$correct/$total"
        scoreDatabase.insertScore(userName, score)

        val dialog = MaterialAlertDialogBuilder(this, R.style.CustomAlertDialogTheme)
            .setTitle("Success 🎉")
            .setMessage("Your score has been recorded successfully.")
            .setPositiveButton("OK", null)
            .create()

        applyDialogStyle(dialog)
        dialog.show()
    }


    private fun recordScore2() {
        val scoresList = scoreDatabase.getAllScores()

        val scoresText = if (scoresList.isEmpty()) {
            "No recorded scores yet."
        } else {
            scoresList.joinToString("\n\n")
        }

        val dialog = MaterialAlertDialogBuilder(this, R.style.CustomAlertDialogTheme)
            .setTitle("Recorded Scores 📜")
            .setMessage(scoresText)
            .setPositiveButton("OK", null)
            .create()

        applyDialogStyle(dialog)
        dialog.show()
    }



    private fun clearScores2() {
        val dialog = MaterialAlertDialogBuilder(this, R.style.CustomAlertDialogTheme)
            .setTitle("Clear All Scores ⚠️")
            .setMessage("Are you sure you want to delete all recorded scores? This action cannot be undone.")
            .setPositiveButton("Yes") { _, _ ->
                scoreDatabase.clearAllScores()
                Toast.makeText(this, "All scores cleared!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .create()

        applyDialogStyle(dialog)
        dialog.show()
    }



    private fun clearBoundingBoxAnswers() {
        if (boundingBoxes.isNotEmpty()) {
            boundingBoxes = boundingBoxes.map { it.copy(clsName = "") }.toMutableList()
            overlayView.setResults(boundingBoxes)
            Toast.makeText(this, "All answers cleared!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "No answers to clear!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyDialogStyle(dialog: androidx.appcompat.app.AlertDialog) {
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            attributes?.windowAnimations = R.transition.anim
        }

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
                setTextColor(Color.BLACK)
                setBackgroundColor(Color.WHITE)
                setPadding(20, 10, 20, 10)
                textSize = 14f
            }

            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.BLACK)
                setPadding(20, 10, 20, 10)
                textSize = 12f
            }

            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.apply {
                setTextColor(Color.DKGRAY)
                setBackgroundColor(Color.LTGRAY)
                setPadding(20, 10, 20, 10)
                textSize = 14f
            }
        }
    }

}
