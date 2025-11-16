package com.surendramaran.yolov8tflite

import android.Manifest
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.text.LineBreaker
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MenuItem
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
import com.surendramaran.yolov8tflite.Constants.LABELS_PATH
import com.surendramaran.yolov8tflite.Constants.MODEL_PATH
import com.surendramaran.yolov8tflite.databinding.ActivityMainBinding
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.widget.ScrollView
import android.widget.LinearLayout
import com.google.android.material.button.MaterialButton
import java.util.Locale


class MainActivity : AppCompatActivity(), Detector.DetectorListener {
    private lateinit var binding: ActivityMainBinding
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
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)



        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
            }
        }


        noObjectDetectedTextView = binding.noObjectDetected // Initialize the TextView

        detector = Detector(baseContext, MODEL_PATH, LABELS_PATH, this)
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
        "heart" to "Heart - In the ventricle, the three chambered frog heart combines blood that has been oxygenated and deoxygenated. As a result, the body never gets enough blood that is rich in oxygen (NSF, (n.d).",
        "liver" to "secretes bile and breaks down the molecules of food (Gall, (n.d)).",
        "gallbladder" to "A muscular, pear-shaped storage sac, the gallbladder is attached to the liver through ducts called the biliary tract and stores bile (Lindenmeyer, 2022).",
        "spleen" to "The frog's circulatory system has an organ that produces, stores, and eliminates blood cells (Gall, (n.d)).",
        "large_intestine" to "the digestive system's posterior organ, which keeps undigested food (Gall, (n.d)).",
        "small_intestine" to "the primary organ involved in food digestion and absorption (Gall, (n.d)).",
        "lungs" to "The glottis serves as an aperture connecting the mouth to a pair of thin-walled sacs that make up the frog's lungs (Libretexts, 2022).",
        "stomach" to "combines food with enzymes and stores it before digestion may start (Gall, (n.d)).",
        "fat-bodies" to "fat masses inside frogs' bodily chambers. essential for both mating and hibernation (Gall, (n.d)).",
        "ovary" to "The ovary is a paired structure that is located near the kidneys. The mesovarium is a peritoneal fold that surrounds the kidneys and contains these yellow-colored structures. They create ova during the oogenesis process. Nearly 2500–3000 ova are released all at once by a mature female."
    )




    private val labelComparisons = mapOf(
        "heart" to Pair(
            "The heart pumps blood throughout the frog's body, supplying oxygen to tissues and organs while transporting carbon dioxide and other waste products to excretory organs.",
            "The heart of a frog is three-chambered, consisting of two atria and one ventricle, which allows for a mixture of oxygenated and deoxygenated blood. This results in less efficient circulation compared to more advanced vertebrates (NSF, n.d.). In contrast, the human heart is four-chambered, with two atria and two ventricles, which ensures the complete separation of oxygenated and deoxygenated blood, allowing for more efficient circulation and oxygen delivery to all parts of the body (Human anatomy, 2023)."
        ),
        "liver" to Pair(
            "The liver helps filter and remove toxins from the bloodstream. It plays a role in converting nutrients from food into usable forms, such as converting excess glucose into glycogen for storage.",
            "In frogs, the liver has a significant role in secreting bile and breaking down food molecules, which aids digestion (Gall, n.d.). The human liver, on the other hand, has a much broader set of functions, including detoxification, nutrient storage, and protein synthesis, in addition to its role in bile production (Human anatomy, 2023)."
        ),
        "lungs" to Pair(
            "The lungs facilitate the exchange of gases, allowing the frog to take in oxygen from the air and expel carbon dioxide.",
            "Frogs have simple lungs that are thin-walled and connected to the mouth via the glottis. Air is drawn into the mouth and pushed into the lungs for gas exchange (Libretexts, 2022). Human lungs are more complex, consisting of various airways and alveoli where efficient gas exchange occurs, separating oxygen from carbon dioxide to be expelled through exhalation (Human anatomy, 2023)."
        ),
        "stomach" to Pair(
            "The stomach secretes gastric juices, which contain enzymes and acids that break down food into a semi-liquid form called chyme before it moves to the small intestine.",
            "In frogs, the stomach is responsible for mixing food with digestive enzymes to begin the process of digestion (Gall, n.d.). The human stomach, however, is more specialized, with distinct regions that handle food storage and break it down using both mechanical and chemical digestion, involving gastric juices (Human anatomy, 2023)."
        ),
        "small_intestine" to Pair(
            "The small intestine is the primary site for digestion of food and absorption of nutrients, including carbohydrates, proteins, and fats, into the bloodstream.",
            "The small intestine of frogs is responsible for absorbing nutrients from digested food (Gall, n.d.). Humans have a similar function, with the small intestine being the primary site for nutrient absorption. It is divided into three sections: the duodenum, jejunum, and ileum, each serving distinct roles in nutrient processing (Human anatomy, 2023)."
        ),
        "large_intestine" to Pair(
            "The large intestine absorbs water and electrolytes from indigestible food matter, which helps prevent dehydration. It compacts waste material into feces, which is then excreted through the cloaca.",
            "In frogs, the large intestine is primarily involved in storing undigested food before it is eliminated (Gall, n.d.). The human large intestine, however, absorbs water and salts from remaining food matter, forming solid waste that is eventually excreted (Human anatomy, 2023)."
        ),
        "gallbladder" to Pair(
            "The gallbladder stores bile produced by the liver until it is needed for digestion, particularly in the breakdown of fats.",
            "The frog’s gallbladder stores bile produced by the liver and releases it into the intestines to aid digestion (Lindenmeyer, 2022). Similarly, in humans, the gallbladder stores bile produced by the liver, releasing it into the small intestine to aid in the digestion of fats (Human anatomy, 2023)."
        ),
        "spleen" to Pair(
            "The spleen filters and removes old or damaged red blood cells from the bloodstream. It plays a role in the immune response by producing lymphocytes (a type of white blood cell) that help fight infections.",
            "Frogs have a spleen that plays a critical role in the production, storage, and elimination of blood cells (Gall, n.d.). In humans, the spleen is part of the lymphatic system and assists in recycling red blood cells and proliferating white blood cells to help support the immune system (Human anatomy, 2023)."
        ),
        "fat-bodies" to Pair(
            "fat masses inside frogs' bodily chambers. essential for both mating and hibernation (Gall, (n.d)).",
            "Frogs store energy in fat bodies, which are vital for reproduction and hibernation, particularly in cold weather (Gall, n.d.). In humans, fat tissue also serves as an energy reserve, but it has additional roles, including providing insulation and supporting various hormone functions (Human anatomy, 2023)."
        ),
        "ovary" to Pair(
            "fat masses inside frogs' bodily chambers. essential for both mating and hibernation (Gall, (n.d)).",
            "The frog's ovaries are two sacs with lobes containing oocytes, melanophore cells, and fatty bodies, primarily functioning in egg production for reproduction. In contrast, human ovaries are small, oval-shaped glands located beside the uterus, responsible for producing and storing eggs, as well as secreting hormones that regulate the menstrual cycle and pregnancy. While both serve reproductive functions, the structural and hormonal roles of human ovaries are more complex, including the release of eggs during ovulation, which can lead to pregnancy if fertilized."
        )
    )


    private val labelComparisonsfrogchicken = mapOf(
        "heart" to Pair(
            "The heart pumps blood throughout the frog's body, supplying oxygen to tissues and organs while transporting carbon dioxide and other waste products to excretory organs.",
            "The frog’s heart, with its three chambers (two atria and one ventricle), mixes oxygenated and deoxygenated blood, leading to a less efficient oxygen delivery system, as tissues receive blood that is only partially rich in oxygen (NSF, n.d.). In contrast, the chicken’s four-chambered heart—two atria and two ventricles—fully separates oxygenated from deoxygenated blood, optimizing oxygen transport to meet the bird's high metabolic demands required for activities like running and short-distance flying (5 Answers from Research Papers, n.d.). "
        ),
        "liver" to Pair(
            "The liver helps filter and remove toxins from the bloodstream. It plays a role in converting nutrients from food into usable forms, such as converting excess glucose into glycogen for storage.",
            "The liver in frogs secretes bile, aiding in the breakdown of food molecules, playing a key role in digestion (Gall, n.d.). In chickens, the liver goes further by storing essential vitamins and minerals and removing toxins, thus making it a nutritional powerhouse that meets the bird's high metabolic needs (Chicken Liver, n.d.). While both livers assist in digestion and detoxification, the chicken liver's additional role in nutrient storage provides essential metabolic support."
        ),
        "lungs" to Pair(
            "The lungs facilitate the exchange of gases, allowing the frog to take in oxygen from the air and expel carbon dioxide.",
            "In frogs, the lungs consist of thin-walled sacs connected to the mouth via the glottis, allowing airflow through the mouth, while they can also rely on cutaneous respiration, breathing through the skin while in water (Libretexts, 2022). Chicken lungs, on the other hand, are relatively small and attached closely to the ribs, functioning with air sacs to maintain efficient oxygen exchange; they cannot expand like mammal lungs due to the absence of a fully developed diaphragm and chest muscles (Small and backyard poultry, n.d.). The frog’s lungs offer less efficiency in oxygen exchange, supplemented by skin-based respiration, whereas chicken lungs are highly specialized, depending on air sacs for constant airflow and improved oxygen uptake."
        ),
        "stomach" to Pair(
            "The stomach secretes gastric juices, which contain enzymes and acids that break down food into a semi-liquid form called chyme before it moves to the small intestine.",
            "The frog’s stomach combines and stores food with enzymes, initiating digestion before it proceeds further in the digestive tract (Gall, n.d.). In contrast, the chicken's gizzard functions as a \"mechanical stomach,\" grinding and mashing food with strong muscles, a feature compensating for their lack of teeth and aiding in their unique dietary needs (Poultry Hub Australia, 2020)2. Thus, while the frog’s stomach breaks down food through enzymatic action, the chicken’s gizzard provides a more mechanical approach to food processing, each suited to the animal's dietary habits and anatomy."
        ),
        "small_intestine" to Pair(
            "The small intestine is the primary site for digestion of food and absorption of nutrients, including carbohydrates, proteins, and fats, into the bloodstream.",
            "In frogs, the small intestine serves as the main site for digestion and nutrient absorption, facilitating essential nutrient uptake. Chickens, on the other hand, have a more complex small intestine divided into the duodenum and lower sections, which, in collaboration with the pancreas and liver, enhances digestive efficiency to meet their higher energy demands (Poultry Hub Australia, 2020; Gall, n.d.)12"
        ),
        "large_intestine" to Pair(
            "The large intestine absorbs water and electrolytes from indigestible food matter, which helps prevent dehydration. It compacts waste material into feces, which is then excreted through the cloaca.",
            "In frogs, the large intestine stores undigested food and reabsorbs water, playing a role in water conservation. Similarly, the chicken's large intestine, though shorter than its small intestine, is essential for reabsorbing water during the final digestion stages, which is critical for terrestrial adaptation (Poultry Hub Australia, 2020; Gall, n.d.)12."
        ),
        "gallbladder" to Pair(
            "The gallbladder stores bile produced by the liver until it is needed for digestion, particularly in the breakdown of fats.",
            "The frog’s gallbladder is a small, pear-shaped sac attached to the liver, storing bile until it is required for digestion (Lindenmeyer, 2022). In chickens, the gallbladder is located on the right lobe of the liver and is connected to the small intestine via a more complex duct system to meet their higher digestive needs (Poultry Hub Australia, 2020). Both serve the same fundamental purpose of bile storage, though the chicken’s more intricate bile duct network accommodates its advanced digestive requirements."
        ),
        "spleen" to Pair(
            "The spleen filters and removes old or damaged red blood cells from the bloodstream. It plays a role in the immune response by producing lymphocytes (a type of white blood cell) that help fight infections.",
            "The frog’s spleen plays a role in blood health by producing, storing, and removing blood cells within the circulatory system (Gall, n.d.). In contrast, the chicken spleen primarily supports the immune system, with its size varying in response to immune challenges, indicating its role in adapting to immune stimuli (Vali et al., 2023). Both organs are essential for immunity and blood maintenance, yet the chicken spleen has a greater emphasis on flexible immune response, reflecting its critical role in avian health."
        ),
        "ovary" to Pair(
            "The spleen filters and removes old or damaged red blood cells from the bloodstream. It plays a role in the immune response by producing lymphocytes (a type of white blood cell) that help fight infections.",
            "The frog's ovary consists of two sacs with lobes containing thousands of oocytes, along with melanophore cells and fatty bodies attached to the kidneys. In contrast, the chicken's ovary is a cluster of sacs with ova in follicles that mature into yolks for egg-laying. While both serve reproductive functions, their structures reflect the differing needs of amphibians and birds."
        )
    )

    private val labelComparisonsfrogtilapia = mapOf(
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
        "small_intestine" to Pair(
            "The small intestine is the primary site for digestion of food and absorption of nutrients, including carbohydrates, proteins, and fats, into the bloodstream.",
            "In terms of intestines, both species rely on these organs for nutrient absorption, though the frog’s small intestine is primarily dedicated to digestion and nutrient uptake, while the tilapia’s intestines have evolved to vary in length based on its diet, optimizing nutrient absorption (Gall, n.d.; Parenti & Weitzman, 2024). The large intestine in both animals functions to store undigested material, but in tilapia, it also plays a significant role in excreting waste material after nutrient absorption, reflecting its aquatic environment (Gall, n.d.; Parenti & Weitzman, 2024)."
        ),
        "large_intestine" to Pair(
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
        ),
        "ovary" to Pair(
            "The spleen filters and removes old or damaged red blood cells from the bloodstream. It plays a role in the immune response by producing lymphocytes (a type of white blood cell) that help fight infections.",
            "The frog's ovaries are two sacs with lobes containing thousands of oocytes, melanophore cells, and finger-shaped fatty bodies attached to the kidneys. In contrast, tilapia ovaries are paired, sac-shaped organs suspended from the coelom wall, covered by the viscera peritoneum and an inner layer called the tunica albuginea. While both serve reproductive functions, the structural differences reflect the unique reproductive adaptations of amphibians and fish."
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
            setTextColor(ContextCompat.getColor(this@MainActivity,R.color.blue))
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
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.black))
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
                setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.white))
                setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.blue))
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
                text = "Compare with Chicken"
                setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.white))
                setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.blue))
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
                text = "Compare with Tilapia"
                setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.white))
                setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.blue))
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
            setBackgroundColor(ContextCompat.getColor(this@MainActivity,R.color.blue )) // Set background to white
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.white)) // Set text color to black
        }


        alertDialog.window?.decorView?.setBackgroundColor(ContextCompat.getColor(this, R.color.white))
    }



    private fun showChickenComparison(label: String, chickenComparison: String) {
        val comparisonDialogBuilder = androidx.appcompat.app.AlertDialog.Builder(this)


        val titleTextView = TextView(this).apply {
            text = "$label Comparison with Chicken"
            textSize = 20f
            setTextColor(ContextCompat.getColor(this@MainActivity,R.color.blue))
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
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.black))
            setLineSpacing(0f, 1.0f) // Adjust spacing for better readability
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                justificationMode = LineBreaker.JUSTIFICATION_MODE_INTER_WORD
            }
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.white))
            setBackgroundColor(ContextCompat.getColor(this@MainActivity,R.color.blue)) // Use blue color
        }


        comparisonAlertDialog.window?.decorView?.setBackgroundColor(ContextCompat.getColor(this, R.color.white))


        comparisonAlertDialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            setBackgroundColor(ContextCompat.getColor(this@MainActivity,R.color.blue))
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.white))
        }
    }

    private fun showHumanComparison(label: String, humanComparison: String) {
        val comparisonDialogBuilder = androidx.appcompat.app.AlertDialog.Builder(this)


        val titleTextView = TextView(this).apply {
            text = "$label Comparison with Human"
            textSize = 20f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.blue))
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
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.black))
            setLineSpacing(0f, 1.0f) // Adjust spacing for better readability
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                justificationMode = LineBreaker.JUSTIFICATION_MODE_INTER_WORD
            }
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.white))
            setBackgroundColor(ContextCompat.getColor(this@MainActivity,R.color.blue)) // Use blue color
        }



        comparisonAlertDialog.window?.decorView?.setBackgroundColor(ContextCompat.getColor(this, R.color.white))


        comparisonAlertDialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.blue))
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.white))
        }
    }

    private fun showTilapiaComparison(label: String, tilapiaComparison: String) {
        val comparisonDialogBuilder = androidx.appcompat.app.AlertDialog.Builder(this)


        val titleTextView = TextView(this).apply {
            text = "$label Comparison with Tilapia"
            textSize = 20f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.blue))
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
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.black))
            setLineSpacing(0f, 1.0f) // Adjust spacing for better readability
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                justificationMode = LineBreaker.JUSTIFICATION_MODE_INTER_WORD
            }
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.white))
            setBackgroundColor(ContextCompat.getColor(this@MainActivity,R.color.blue)) // Use blue color
        }




        comparisonAlertDialog.window?.decorView?.setBackgroundColor(ContextCompat.getColor(this, R.color.white))


        comparisonAlertDialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            setBackgroundColor(ContextCompat.getColor(this@MainActivity,R.color.blue))
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.white))
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
        val intent = Intent(this, FullScreenImageActivity::class.java).apply {
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
