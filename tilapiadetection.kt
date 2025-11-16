package com.surendramaran.yolov8tflite

import android.Manifest
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.surendramaran.yolov8tflite.Constants.LABELS_PATH_FISH
import com.surendramaran.yolov8tflite.Constants.MODEL_PATH_FISH
import com.surendramaran.yolov8tflite.databinding.ActivityTilapiadetectionBinding
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.widget.ScrollView
import android.widget.LinearLayout
import android.hardware.display.DisplayManager
import android.view.Display
import android.content.Context
import android.graphics.Canvas
import android.graphics.text.LineBreaker
import android.net.Uri
import android.os.Build
import android.speech.tts.TextToSpeech
import android.view.Gravity
import com.google.android.material.button.MaterialButton
import java.util.Locale


class tilapiadetection : AppCompatActivity(), Detector.DetectorListener {
    private lateinit var binding: ActivityTilapiadetectionBinding
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var detector: Detector
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var textToSpeech: TextToSpeech



    private val detectedLabels: MutableList<String> = mutableListOf()



    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.all { it.value }) {
                startCamera()
            } else {
                Toast.makeText(this, "Permissions not granted", Toast.LENGTH_SHORT).show()
            }
        }
    private lateinit var noObjectDetectedTextView: TextView // Declare the TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTilapiadetectionBinding.inflate(layoutInflater)
        setContentView(binding.root)



        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
            }
        }


        noObjectDetectedTextView = binding.noObjectDetected // Initialize the TextView

        detector = Detector(baseContext, Constants.MODEL_PATH_FISH, Constants.LABELS_PATH_FISH, this)
        detector.setup()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.buttonCapture.setOnClickListener {
            captureScreen()
        }

        binding.overlay.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val x = event.x
                val y = event.y
                handleTouchOnBoundingBox(x, y)
            }
            true
        }

        val flashButton: MaterialButton = findViewById(R.id.button_flash)
        var isFlashOn = false

        flashButton.setOnClickListener {
            isFlashOn = !isFlashOn
            val cameraControl = camera?.cameraControl
            if (cameraControl != null) {
                cameraControl.enableTorch(isFlashOn)
                flashButton.setIconResource(
                    if (isFlashOn) R.drawable.bottom_btn4 else R.drawable.bottom_btn4
                )
            } else {
                Toast.makeText(this, "Camera not initialized", Toast.LENGTH_SHORT).show()
            }
        }

        binding.buttonSaveAndView.setOnClickListener {
            captureScreenWithoutLabels()
        }

        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val langResult = textToSpeech.setLanguage(Locale.getDefault())
                if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "Language is not supported or missing data")
                }
            } else {
                Log.e(TAG, "TextToSpeech initialization failed")
            }
        }


    }





    private val labelDefinitions = mapOf(
        "heart" to "keeps the blood flowing through the body. The blood carries waste products from the cells to the kidneys and liver for removal, and it carries oxygen and nutrients that have been digested to the cells of different organs (Parenti, et,al, 2024).",
        "liver" to "It acts as a storage space for fats and carbohydrates and aids in digestion by secreting enzymes that break down fats. The liver also plays a part in the excretion of nitrogen (waste) and is crucial for the breakdown of old blood cells and maintaining appropriate blood chemistry (Parenti, et,al, 2024).",
        "gallbladder" to "The gallbladder stores and concentrates bile from the liver, releasing it into the first section of the small intestine, the duodenum, where it aids in breaking down and absorbing fats from food (IQWiG, 2021).",
        "spleen" to "One of the main peripheral lymphoid organs in fish is the spleen. The fish spleen enables immunocompetent cells' close interaction and antigen processing and entrapment (Zapata, 2024).",
        "intestine" to "The length of the gut varies greatly depending on the nutrition of the fish. The primary function of the gut is to absorb nutrients into the bloodstream (Parenti, et,al, 2024). ",
        "gills" to "Fish breathe through their gills, which are highly vascularized and give them their vivid red color chemistry (Parenti, et,al, 2024). ",
        "stomach" to "Fish stomachs differ significantly based on what they eat. It is a straightforward tube or pouches with a muscular wall and a glandular lining seen in the majority of predatory fishes. There, food is mostly broken down and expelled from the stomach in liquid form chemistry (Parenti, et,al, 2024).",
        "swimbladder" to "Fish are able to preserve energy by maintaining neutral buoyancy (suspending) in the water because of this hollow, gas-filled balancing organ (The Internal and External Anatomy of Florida Fish, 2023)."
    )




    private val labelComparisons = mapOf(
        "heart" to Pair(
            "The heart pumps blood throughout the frog's body, supplying oxygen to tissues and organs while transporting carbon dioxide and other waste products to excretory organs.",
            "Both tilapia and humans have hearts that circulate blood throughout the body, ensuring the transport of nutrients and oxygen to tissues. In tilapia, the heart circulates blood through gills for oxygen exchange before distributing it throughout the body (Parenti et al., 2024). Similarly, in humans, the heart pumps oxygenated blood from the lungs to various organs via the circulatory system (Human anatomy, 2023). While their circulatory systems are both vital for nutrient transport, tilapia's reliance on gills for respiration sets it apart from the human circulatory system, which depends on lungs for oxygenation."
        ),
        "liver" to Pair(
            "The liver helps filter and remove toxins from the bloodstream. It plays a role in converting nutrients from food into usable forms, such as converting excess glucose into glycogen for storage.",
            "In both tilapia and humans, the liver is a crucial organ for detoxification and metabolic processes. The tilapia liver helps break down fats and stores nutrients, and it also plays a role in the excretion of nitrogenous waste (Parenti et al., 2024). Similarly, the human liver performs detoxification, produces bile for digestion, and stores nutrients like glycogen (Human anatomy, 2023). While both livers share basic functions like detoxification and nutrient storage, the tilapia liver is more specialized for maintaining the balance of nitrogen waste in an aquatic environment."
        ),
        "gills" to Pair(
            "The lungs facilitate the exchange of gases, allowing the frog to take in oxygen from the air and expel carbon dioxide.",
            "Tilapia possess gills for oxygen exchange, which are highly vascularized and adapted for aquatic life (Parenti et al., 2024). Humans, on the other hand, rely on lungs for gas exchange, where oxygen is absorbed from the air and carbon dioxide is expelled (Human anatomy, 2023). The function of both organs is to provide oxygen to the body, but gills are specifically adapted to extract oxygen from water, while lungs are designed for air-based respiration.."
        ),
        "stomach" to Pair(
            "The stomach secretes gastric juices, which contain enzymes and acids that break down food into a semi-liquid form called chyme before it moves to the small intestine.",
            "The stomach in both tilapia and humans functions as a digestive organ where food is broken down. In tilapia, the stomach is relatively simple and specialized for the digestion of liquid-based food (Parenti et al., 2024). In contrast, the human stomach is more complex, designed to handle solid food and mechanically break it down through peristalsis and enzymatic activity (Human anatomy, 2023). Both stomachs contribute to the digestive process, but they are adapted to different dietary needs and food types."
        ),
        "intestine" to Pair(
            "The small intestine is the primary site for digestion of food and absorption of nutrients, including carbohydrates, proteins, and fats, into the bloodstream.",
            "Both tilapia and humans have intestines that absorb nutrients from digested food. The length and structure of the intestines in tilapia are influenced by its diet, which is typically shorter due to its carnivorous nature (Parenti et al., 2024). In contrast, humans have a much longer intestine, which allows for more extensive nutrient absorption from a varied diet (Human anatomy, 2023). The structure of the intestines in both species reflects their respective dietary needs and metabolic demands."
        ),
        "gallbladder" to Pair(
            "The gallbladder stores bile produced by the liver until it is needed for digestion, particularly in the breakdown of fats.",
            "The gallbladder in both tilapia and humans stores bile produced by the liver to aid in digestion, especially the breakdown of fats. In tilapia, the gallbladder stores bile in a simpler form compared to humans, as their diet is less complex (Parenti et al., 2024; IQWiG, 2021). Humans have a more specialized gallbladder function, concentrating bile before releasing it into the duodenum to assist with the digestion of fatty foods (Human anatomy, 2023; IQWiG, 2021)."
        ),
        "swimbladder" to Pair(
            "The gallbladder stores bile produced by the liver until it is needed for digestion, particularly in the breakdown of fats.",
            "The swimbladder is a unique feature of tilapia that helps them maintain neutral buoyancy in water, allowing them to float and conserve energy (The Internal and External Anatomy of Florida Fish, 2023). This organ is not present in humans, as humans do not require buoyancy for movement. Instead, humans rely on their skeletal and muscular systems for mobility on land."
        ),
        "spleen" to Pair(
            "The spleen filters and removes old or damaged red blood cells from the bloodstream. It plays a role in the immune response by producing lymphocytes (a type of white blood cell) that help fight infections.",
            "In tilapia, the spleen plays a role in immune function, aiding in the processing of antigens and the interaction of immune cells (Zapata, 2024). Similarly, the human spleen is involved in recycling old red blood cells and supporting immune function by hosting lymphocytes, which help fight infections (Human anatomy, 2023). Both species have spleens involved in immune responses, but the tilapia spleen has additional functions related to antigen processing and immune cell interaction in a fish's immune system."
        )
    )


    private val labelComparisonsfrogchicken = mapOf(
        "heart" to Pair(
            "The heart pumps blood throughout the frog's body, supplying oxygen to tissues and organs while transporting carbon dioxide and other waste products to excretory organs.",
            "In frogs, the heart consists of three chambers—two atria and one ventricle—resulting in the mixing of oxygenated and deoxygenated blood. This structure is less efficient in delivering oxygen to the body compared to the two-chambered heart of tilapia, which circulates oxygenated blood from the gills to the body (NSF, n.d.; Parenti & Weitzman, 2024). "
        ),
        "liver" to Pair(
            "The liver helps filter and remove toxins from the bloodstream. It plays a role in converting nutrients from food into usable forms, such as converting excess glucose into glycogen for storage.",
            "Similarly, while the frog's liver stores bile and aids in digestion, the tilapia’s liver performs additional functions such as storing fats and breaking down old blood cells, contributing to nitrogen excretion and maintaining blood chemistry (Gall, n.d.; Parenti & Weitzman, 2024)."
        ),
        "lungs" to Pair(
            "The lungs facilitate the exchange of gases, allowing the frog to take in oxygen from the air and expel carbon dioxide.",
            "The frog’s lungs are adapted for cutaneous respiration, allowing it to breathe through its skin in addition to using its lungs. In contrast, tilapia relies on its highly vascularized gills to extract oxygen from water, making it suited to an aquatic environment where lungs are not as efficient (Libretexts, 2022; Parenti & Weitzman, 2024)."
        ),
        "stomach" to Pair(
            "The stomach secretes gastric juices, which contain enzymes and acids that break down food into a semi-liquid form called chyme before it moves to the small intestine.",
            "When it comes to digestion, frogs have a stomach that stores food and initiates digestion, while tilapia has a more complex stomach structure suited to its diet, breaking down food into a liquid state before absorption (Gall, n.d.; Parenti & Weitzman, 2024)."
        ),
        "intestine" to Pair(
            "The small intestine is the primary site for digestion of food and absorption of nutrients, including carbohydrates, proteins, and fats, into the bloodstream.",
            "In terms of intestines, both species rely on these organs for nutrient absorption, though the frog’s small intestine is primarily dedicated to digestion and nutrient uptake, while the tilapia’s intestines have evolved to vary in length based on its diet, optimizing nutrient absorption (Gall, n.d.; Parenti & Weitzman, 2024). The large intestine in both animals functions to store undigested material, but in tilapia, it also plays a significant role in excreting waste material after nutrient absorption, reflecting its aquatic environment (Gall, n.d.; Parenti & Weitzman, 2024)."
        ),
        "spleen" to Pair(
            "The spleen filters and removes old or damaged red blood cells from the bloodstream. It plays a role in the immune response by producing lymphocytes (a type of white blood cell) that help fight infections.",
            "The frog’s spleen, which plays a role in blood cell production and storage, contrasts with the tilapia's spleen, which is involved in immune responses and antigen processing, highlighting different evolutionary adaptations (Gall, n.d.; Zapata, 2024)."
        ),
        "gallbladder" to Pair(
            "The spleen filters and removes old or damaged red blood cells from the bloodstream. It plays a role in the immune response by producing lymphocytes (a type of white blood cell) that help fight infections.",
            "Both animals have gallbladders that store bile produced by the liver; however, in frogs, the gallbladder’s main function is to aid digestion by releasing bile into the intestine, whereas tilapia's bile is involved in digesting fats and aiding nutrient absorption (Lindenmeyer, 2022; IQWiG, 2021)."
        )
    )

    private val labelComparisonsfrogtilapia = mapOf(
        "heart" to Pair(
            "The heart pumps blood throughout the frog's body, supplying oxygen to tissues and organs while transporting carbon dioxide and other waste products to excretory organs.",
            "The tilapia has a two-chambered heart, consisting of one atrium and one ventricle. Blood flows through the gills for oxygenation, which is less efficient for oxygen delivery compared to the more complex circulatory systems of land animals (Parenti et al., 2024). In contrast, the chicken's heart has four chambers—two atria and two ventricles—ensuring the full separation of oxygenated and deoxygenated blood, optimizing oxygen transport to support its high metabolic demands, essential for activities like running and short-distance flying (5 Answers from Research Papers, n.d.)."
        ),
        "liver" to Pair(
            "The liver helps filter and remove toxins from the bloodstream. It plays a role in converting nutrients from food into usable forms, such as converting excess glucose into glycogen for storage.",
            "The chicken's liver plays a crucial role in detoxification, storing vitamins, minerals, and regulating energy reserves by processing fats and carbohydrates (Chicken Liver, n.d.). The tilapia’s liver, while also involved in energy storage, is primarily focused on aiding digestion and waste excretion (Parenti et al., 2024)."
        ),
        "gills" to Pair(
            "The lungs facilitate the exchange of gases, allowing the frog to take in oxygen from the air and expel carbon dioxide.",
            "The chicken’s lungs are small and attached to the ribs, relying on air sacs to aid in the exchange of gases (Small and Backyard Poultry, n.d.). Conversely, tilapia, like other fish, use gills for oxygen extraction from water, with highly vascularized gill filaments facilitating efficient oxygen uptake (Parenti et al., 2024)."
        ),
        "stomach" to Pair(
            "The stomach secretes gastric juices, which contain enzymes and acids that break down food into a semi-liquid form called chyme before it moves to the small intestine.",
            "The chicken's gizzard serves as a mechanical stomach that grinds food to aid in digestion (Poultry Hub Australia, 2020). In contrast, the tilapia has a stomach that breaks down food chemically, helping with nutrient absorption (Parenti et al., 2024)."
        ),
        "intestine" to Pair(
            "The small intestine is the primary site for digestion of food and absorption of nutrients, including carbohydrates, proteins, and fats, into the bloodstream.",
            "The chicken's large intestine primarily handles water reabsorption, ensuring efficient digestion of nutrients (Poultry Hub Australia, 2020). In tilapia, the intestine serves as the site for nutrient absorption, with its structure varying depending on the species' diet (Parenti et al., 2024)."
        ),
        "intestine" to Pair(
            "The small intestine is the primary site for digestion of food and absorption of nutrients, including carbohydrates, proteins, and fats, into the bloodstream.",
            "The small intestine in chickens is the primary site for nutrient absorption, where digestive enzymes break down food into absorbable molecules, providing essential nutrients such as proteins, fats, and carbohydrates (Poultry Hub Australia, 2020). Similarly, in tilapia, the small intestine plays a crucial role in digestion and absorption, where nutrients from the food are absorbed after being broken down. However, the structure of the tilapia’s small intestine may vary depending on its diet, reflecting adaptations to the specific types of food it consumes (Parenti et al., 2024)."
        ),
        "gallbladder" to Pair(
            "The small intestine is the primary site for digestion of food and absorption of nutrients, including carbohydrates, proteins, and fats, into the bloodstream.",
            "The chicken’s gallbladder stores bile produced by the liver and aids in the digestion of fats (Poultry Hub Australia, 2020). Similarly, the tilapia's gallbladder stores and concentrates bile, releasing it as needed for digestion (IQWiG, 2021)."
        ),
        "spleen" to Pair(
            "The spleen filters and removes old or damaged red blood cells from the bloodstream. It plays a role in the immune response by producing lymphocytes (a type of white blood cell) that help fight infections.",
            "In chickens, the spleen is crucial for immune function, helping to filter blood and fight infections (Vali et al., 2023). The tilapia’s spleen similarly plays an immune role, interacting with immune cells and processing antigens (Zapata, 2024)."
        )
    )
    // updated handlebound for the text to speech
    private fun handleTouchOnBoundingBox(x: Float, y: Float) {
        val clickedBoundingBox = binding.overlay.handleTouch(x, y)

        if (clickedBoundingBox != null) {
            val labelName = clickedBoundingBox.clsName
            val normalizedLabelKey = labelName.lowercase().trim().replace(" ", "_").replace(" ", "-")

            Log.d(TAG, "Normalized Label Key: $normalizedLabelKey")

            val definition = labelDefinitions[normalizedLabelKey]
            val humanComparison = labelComparisons[normalizedLabelKey]?.second
            val chickenComparison = labelComparisonsfrogchicken[normalizedLabelKey]?.second
            val tilapiaComparison = labelComparisonsfrogtilapia[normalizedLabelKey]?.second

            // Speak out the label name first
            textToSpeech.speak(labelName, TextToSpeech.QUEUE_FLUSH, null, null)


            // If a definition exists, speak it after the label
            if (definition != null) {
                val formattedLabel = labelName.replaceFirstChar { it.uppercase() }
                val speechText = "$formattedLabel: $definition"

                textToSpeech.speak(speechText, TextToSpeech.QUEUE_ADD, null, null)

                showLabelDefinition(formattedLabel, definition, humanComparison, chickenComparison, tilapiaComparison)
            } else {
                Toast.makeText(this, "No definition found for: $labelName", Toast.LENGTH_SHORT).show()
            }

            binding.overlay.highlightBoundingBox(clickedBoundingBox)
        }


    }
    // speaktext
    private fun speakText(text: String) {
        if (text.isNotEmpty()) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    private fun showLabelDefinition(
        label: String,
        definition: String,
        humanComparison: String? = null,
        chickenComparison: String? = null,
        tilapiaComparison: String? = null
    ) {

        val dialogBuilder = androidx.appcompat.app.AlertDialog.Builder(this)


        val titleTextView = TextView(this).apply {
            text = label
            textSize = 20f
            setTextColor(ContextCompat.getColor(this@tilapiadetection,R.color.blue))
            setPadding(16, 16, 16, 16)
            gravity = Gravity.CENTER

        }
        dialogBuilder.setCustomTitle(titleTextView)


        val marginInDp = 10
        val marginInPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, marginInDp.toFloat(), resources.displayMetrics
        ).toInt()


        val scrollView = ScrollView(this)
        val linearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }


        val definitionTextView = TextView(this).apply {
            text = definition
            setTextColor(ContextCompat.getColor(this@tilapiadetection, android.R.color.black))
            textSize = 16f
            setLineSpacing(0f, 1.2f) // Adjust spacing for better readability
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                justificationMode = LineBreaker.JUSTIFICATION_MODE_INTER_WORD
            }
        }
        linearLayout.addView(definitionTextView)


        if (humanComparison != null) {
            val humanButton = Button(this).apply {
                text = "Compare with Human"
                setTextColor(ContextCompat.getColor(this@tilapiadetection, android.R.color.white))
                setBackgroundColor(ContextCompat.getColor(this@tilapiadetection, R.color.blue))
                setPadding(32, 16, 32, 16)

                setOnClickListener {
                    showHumanComparison(label, humanComparison)
                    speakText(humanComparison) // 🔊 Speak the comparison result
                }
            }
            val humanButtonLayoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            humanButtonLayoutParams.setMargins(0, marginInPx, 0, 0)  // Set 10dp margin at the top
            linearLayout.addView(humanButton, humanButtonLayoutParams)
        }


        if (chickenComparison != null) {
            val chickenButton = Button(this).apply {
                text = "Compare with Frog"
                setTextColor(ContextCompat.getColor(this@tilapiadetection, android.R.color.white))
                setBackgroundColor(ContextCompat.getColor(this@tilapiadetection, R.color.blue))
                setPadding(32, 16, 32, 16)  // Adjust padding here

                setOnClickListener {
                    showChickenComparison(label, chickenComparison)
                    speakText(chickenComparison) // 🔊 Speak the comparison result
                }

            }

            val chickenButtonLayoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            chickenButtonLayoutParams.setMargins(0, marginInPx, 0, 0)  // Set 10dp margin at the top
            linearLayout.addView(chickenButton, chickenButtonLayoutParams)
        }


        if (tilapiaComparison != null) {
            val tilapiaButton = Button(this).apply {
                text = "Compare with Chicken"
                setTextColor(ContextCompat.getColor(this@tilapiadetection, android.R.color.white))
                setBackgroundColor(ContextCompat.getColor(this@tilapiadetection, R.color.blue))
                setPadding(32, 16, 32, 16)  // Adjust padding here

                setOnClickListener {
                    showTilapiaComparison(label, tilapiaComparison)
                    speakText(tilapiaComparison) // 🔊 Speak the comparison result
                }

            }

            val tilapiaButtonLayoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            tilapiaButtonLayoutParams.setMargins(0, marginInPx, 0, 0)  // Set 10dp margin at the top
            linearLayout.addView(tilapiaButton, tilapiaButtonLayoutParams)
        }


        scrollView.addView(linearLayout)
        dialogBuilder.setView(scrollView)

        dialogBuilder.setPositiveButton("OK") { dialog, _ ->
            textToSpeech.stop()
            dialog.dismiss()
        }

        val alertDialog = dialogBuilder.create()


        alertDialog.show()


        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            setBackgroundColor(ContextCompat.getColor(this@tilapiadetection,R.color.blue )) // Set background to white
            setTextColor(ContextCompat.getColor(this@tilapiadetection, android.R.color.white)) // Set text color to black
        }


        alertDialog.window?.decorView?.setBackgroundColor(ContextCompat.getColor(this, R.color.white))
    }



    private fun showChickenComparison(label: String, chickenComparison: String) {
        val comparisonDialogBuilder = androidx.appcompat.app.AlertDialog.Builder(this)


        val titleTextView = TextView(this).apply {
            text = "$label Comparison with Frog"
            textSize = 20f
            setTextColor(ContextCompat.getColor(this@tilapiadetection,R.color.blue))
            setPadding(16, 16, 16, 16)
            gravity = Gravity.CENTER
        }
        comparisonDialogBuilder.setCustomTitle(titleTextView)

        comparisonDialogBuilder.setMessage(chickenComparison)
        comparisonDialogBuilder.setPositiveButton("OK") { dialog, _ ->
            textToSpeech.stop()  // Stop any ongoing speech
            dialog.dismiss()
        }
        val comparisonAlertDialog = comparisonDialogBuilder.create()
        comparisonAlertDialog.show()

        val comparisonMessageView = comparisonAlertDialog.findViewById<TextView>(android.R.id.message)
        comparisonMessageView?.apply {
            setTextColor(ContextCompat.getColor(this@tilapiadetection, android.R.color.black))
            setLineSpacing(0f, 1.0f) // Adjust spacing for better readability
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                justificationMode = LineBreaker.JUSTIFICATION_MODE_INTER_WORD
            }
            setTextColor(ContextCompat.getColor(this@tilapiadetection, android.R.color.white))
            setBackgroundColor(ContextCompat.getColor(this@tilapiadetection,R.color.blue)) // Use blue color
        }


        comparisonAlertDialog.window?.decorView?.setBackgroundColor(ContextCompat.getColor(this, R.color.white))


        comparisonAlertDialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            setBackgroundColor(ContextCompat.getColor(this@tilapiadetection,R.color.blue))
            setTextColor(ContextCompat.getColor(this@tilapiadetection, android.R.color.white))
        }
    }

    private fun showHumanComparison(label: String, humanComparison: String) {
        val comparisonDialogBuilder = androidx.appcompat.app.AlertDialog.Builder(this)


        val titleTextView = TextView(this).apply {
            text = "$label Comparison with Human"
            textSize = 20f
            setTextColor(ContextCompat.getColor(this@tilapiadetection, R.color.blue))
            setPadding(16, 16, 16, 16)
            gravity = Gravity.CENTER
        }
        comparisonDialogBuilder.setCustomTitle(titleTextView)

        comparisonDialogBuilder.setMessage(humanComparison)
        comparisonDialogBuilder.setPositiveButton("OK") { dialog, _ ->
            textToSpeech.stop()  // Stop any ongoing speech
            dialog.dismiss()
        }

        val comparisonAlertDialog = comparisonDialogBuilder.create()
        comparisonAlertDialog.show()

        val comparisonMessageView = comparisonAlertDialog.findViewById<TextView>(android.R.id.message)
        comparisonMessageView?.apply {
            setTextColor(ContextCompat.getColor(this@tilapiadetection, android.R.color.black))
            setLineSpacing(0f, 1.0f) // Adjust spacing for better readability
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                justificationMode = LineBreaker.JUSTIFICATION_MODE_INTER_WORD
            }
            setTextColor(ContextCompat.getColor(this@tilapiadetection, android.R.color.white))
            setBackgroundColor(ContextCompat.getColor(this@tilapiadetection,R.color.blue)) // Use blue color
        }



        comparisonAlertDialog.window?.decorView?.setBackgroundColor(ContextCompat.getColor(this, R.color.white))


        comparisonAlertDialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            setBackgroundColor(ContextCompat.getColor(this@tilapiadetection, R.color.blue))
            setTextColor(ContextCompat.getColor(this@tilapiadetection, android.R.color.white))
        }
    }

    private fun showTilapiaComparison(label: String, tilapiaComparison: String) {
        val comparisonDialogBuilder = androidx.appcompat.app.AlertDialog.Builder(this)


        val titleTextView = TextView(this).apply {
            text = "$label Comparison with Chicken"
            textSize = 20f
            setTextColor(ContextCompat.getColor(this@tilapiadetection, R.color.blue))
            setPadding(16, 16, 16, 16)
            gravity = Gravity.CENTER
        }
        comparisonDialogBuilder.setCustomTitle(titleTextView)

        comparisonDialogBuilder.setMessage(tilapiaComparison)
        comparisonDialogBuilder.setPositiveButton("OK") { dialog, _ ->
            textToSpeech.stop()  // Stop any ongoing speech
            dialog.dismiss()
        }
        val comparisonAlertDialog = comparisonDialogBuilder.create()
        comparisonAlertDialog.show()



        val comparisonMessageView = comparisonAlertDialog.findViewById<TextView>(android.R.id.message)
        comparisonMessageView?.apply {
            setTextColor(ContextCompat.getColor(this@tilapiadetection, android.R.color.black))
            setLineSpacing(0f, 1.0f) // Adjust spacing for better readability
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                justificationMode = LineBreaker.JUSTIFICATION_MODE_INTER_WORD
            }
            setTextColor(ContextCompat.getColor(this@tilapiadetection, android.R.color.white))
            setBackgroundColor(ContextCompat.getColor(this@tilapiadetection,R.color.blue)) // Use blue color
        }



        comparisonAlertDialog.window?.decorView?.setBackgroundColor(ContextCompat.getColor(this, R.color.white))


        comparisonAlertDialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            setBackgroundColor(ContextCompat.getColor(this@tilapiadetection,R.color.blue))
            setTextColor(ContextCompat.getColor(this@tilapiadetection, android.R.color.white))
        }
    }


    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }


    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: throw IllegalStateException("Camera initialization failed.")
        val rotation = binding.viewFinder.display.rotation

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(rotation)
            .build()

        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetRotation(binding.viewFinder.display.rotation)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()

        imageAnalyzer?.setAnalyzer(cameraExecutor) { imageProxy ->
            val bitmapBuffer = Bitmap.createBitmap(imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888)
            imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }


            val rotatedBitmap = rotateBitmap(bitmapBuffer, imageProxy.imageInfo.rotationDegrees)


            detector.detect(rotatedBitmap)

            imageProxy.close()
        }

        cameraProvider.unbindAll()
        try {
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
            preview?.setSurfaceProvider(binding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }


    private fun rotateBitmap(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        val matrix = Matrix().apply {
            postRotate(rotationDegrees.toFloat())
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }


    private fun captureScreen() {

        val bitmapBuffer = binding.viewFinder.bitmap ?: return


        val rotatedBitmap = rotateBitmap(bitmapBuffer, binding.viewFinder.display.rotation)


        val overlayBitmap = getOverlayBitmap()


        val combinedBitmap = combineBitmapWithOverlay(rotatedBitmap, overlayBitmap)


        saveBitmapToGallery(combinedBitmap)
    }

    private fun captureScreenWithoutLabels() {
        val bitmapBuffer = binding.viewFinder.bitmap ?: return
        val rotatedBitmap = rotateBitmap(bitmapBuffer, binding.viewFinder.display.rotation)

        val overlayBitmap = getOverlayWithoutLabels() // Get bounding box only
        val combinedBitmap = combineBitmapWithOverlay(rotatedBitmap, overlayBitmap)

        val imageUri = saveBitmapToGallery(combinedBitmap)

        // Open new activity with the saved image
        val intent = Intent(this, FullScreenImageActivity3::class.java).apply {
            putExtra("image_uri", imageUri.toString())
            putParcelableArrayListExtra("bounding_boxes", ArrayList(binding.overlay.getBoundingBoxes()))


        }
        startActivity(intent)
    }

    private fun getOverlayWithoutLabels(): Bitmap {
        val overlayBitmap = Bitmap.createBitmap(binding.overlay.width, binding.overlay.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(overlayBitmap)

        // Disable label display when drawing bounding boxes
        binding.overlay.setShowLabels(true) // Make sure labels are not shown
        binding.overlay.drawBoundingBoxesOnly(canvas) // Draw bounding boxes only

        return overlayBitmap
    }




    private fun getOverlayBitmap(): Bitmap {
        val overlayBitmap = Bitmap.createBitmap(binding.overlay.width, binding.overlay.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(overlayBitmap)
        binding.overlay.draw(canvas)
        return overlayBitmap
    }



    private fun combineBitmapWithOverlay(cameraBitmap: Bitmap, overlayBitmap: Bitmap): Bitmap {
        val combinedBitmap = Bitmap.createBitmap(cameraBitmap.width, cameraBitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(combinedBitmap)


        canvas.drawBitmap(cameraBitmap, 0f, 0f, null)


        canvas.drawBitmap(overlayBitmap, 0f, 0f, null)

        return combinedBitmap
    }


    private fun saveBitmapToGallery(bitmap: Bitmap): Uri? {
        val filename = "captured_image_${System.currentTimeMillis()}.png"
        var fos: OutputStream? = null
        var imageUri: Uri? = null

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                fos = imageUri?.let { contentResolver.openOutputStream(it) }

                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                imageUri?.let { contentResolver.update(it, contentValues, null, null) }
            } else {
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val imageFile = File(imagesDir, filename)
                fos = FileOutputStream(imageFile)
                imageUri = Uri.fromFile(imageFile)
            }

            fos?.use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving image: ${e.message}")
        } finally {
            fos?.close()
        }
        return imageUri
    }


    override fun onDestroy() {
        super.onDestroy()
        textToSpeech.stop()
        textToSpeech.shutdown()
        detector.clear()
        cameraExecutor.shutdown()
    }

    override fun onResume() {
        super.onResume()
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
        }

        // Initialize TextToSpeech
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val langResult = textToSpeech.setLanguage(Locale.getDefault())
                if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "Language not supported or missing data.")
                }
            } else {
                Log.e(TAG, "TextToSpeech initialization failed.")
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 1) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Storage Permission Granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Storage Permission Denied. Screenshots may not save.", Toast.LENGTH_LONG).show()
            }
        }
    }


    override fun onEmptyDetect() {
        runOnUiThread {

            binding.overlay.clearResults()


            detectedLabels.clear()


            binding.overlay.invalidate()


            binding.noObjectDetected.visibility = View.VISIBLE
        }
    }

    // updated on detect for the text to speech
    override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
        runOnUiThread {
            binding.inferenceTime.text = "${inferenceTime}ms"
            binding.overlay.apply {
                setResults(boundingBoxes)
                invalidate()
            }

            detectedLabels.clear()
            detectedLabels.addAll(boundingBoxes.map { it.clsName })

            binding.noObjectDetected.visibility = View.GONE
        }
    }




    override fun onBackPressed() {
        try {
            Log.d("MyActivity", "Back button pressed")



            val intent = Intent(this, DrawerNav::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)


            System.exit(0)

        } catch (e: Exception) {
            Log.e("MyActivity", "Error during onBackPressed: ${e.message}", e)
        }
    }







    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val TAG = "Camera"
        private val REQUIRED_PERMISSIONS = mutableListOf(
            Manifest.permission.CAMERA
        ).apply {
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
    }
}
