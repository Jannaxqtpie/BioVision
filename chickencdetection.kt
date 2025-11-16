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
import com.surendramaran.yolov8tflite.Constants.LABELS_PATH_NEW
import com.surendramaran.yolov8tflite.Constants.MODEL_PATH_NEW
import com.surendramaran.yolov8tflite.databinding.ActivityChickencdetectionBinding
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
import com.surendramaran.yolov8tflite.databinding.ActivityMainBinding
import java.util.Locale


class chickencdetection : AppCompatActivity(), Detector.DetectorListener {
    private lateinit var binding: ActivityChickencdetectionBinding
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
        binding = ActivityChickencdetectionBinding.inflate(layoutInflater)
        setContentView(binding.root)



        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
            }
        }


        noObjectDetectedTextView = binding.noObjectDetected // Initialize the TextView

        detector = Detector(baseContext, Constants.MODEL_PATH_NEW, Constants.LABELS_PATH_NEW, this)
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
        "gizzard" to "The muscular stomach, or gizzard, is positioned directly after the proventriculus, nestled partly between the liver lobes and partly behind the left liver lobe. It has a flattened, rounded shape similar to a convex lens, with one side slightly larger than the other. Each surface is coated with a glossy layer of tendinous tissue, thickest at the center and gradually thinning toward the edges absorption (Poultry Hub Australia, 2020). ",
        "heart" to "The function of the heart in chickens is to support their high metabolic needs, enabling activities like running, flying short distances, and other energy-intensive behaviors (5 Answers from Research papers. (n.d.)).",
        "kidney" to "The two kidneys in domestic poultry make up the excretory system. The ureters in each kidney transport the urine the kidneys generate to the cloaca, where it is expelled from the body. An animal becomes debilitated and frequently dies rapidly when the kidneys are sick or injured and cannot function effectively (Poultry Hub Australia, 2021).",
        "small_intestine" to "The small intestine plays a crucial role in digestion, producing essential enzymes, and serving as the primary site for both food digestion and nutrient absorption (Poultry Hub Australia, 2020).",
        "liver" to "Chicken liver is nutritious organ meat derived from chickens. The liver is a nutritional powerhouse since it stores vital vitamins and minerals and removes pollutants from the body (Chicken Liver. (n.d.)).",
        "lungs" to "Chicken lungs are securely linked to the ribs, are relatively small, and are not able to expand. In contrast to the chest muscles and sternum of mammals, the diaphragm, chest muscles, and sternum (keel) of birds are not fully developed (Small and backyard poultry. (n.d.)).",
        "large_intestine" to "The large intestine is relatively short, with a similar diameter to the small intestine. It runs nearly in a straight line below the vertebrae, ending at the cloaca. This section is sometimes referred to as the colon and rectum, with the rectum as the terminal segment absorption (Poultry Hub Australia, 2020). ",
        "spleen" to "Although there are physiological and anatomical differences between the spleens of birds and mammals, the spleen is an important organ of the immune system in both species. The size of the spleen in birds can be used as a gauge of the immune system's reaction to various stimuli (Vali, et.al, 2023).",
        "gallbladder" to "Under the spleen on the right lobe is the gall bladder. One of the two bile ducts that exit from the right lobe comes from the gall bladder, whereas the other one connects the liver directly to the small intestine. The left and right lobes are connected by a network of ducts (Poultry Hub Australia, 2020).",
        "kidney" to "The two kidneys in domestic poultry make up the excretory system. The ureters in each kidney transport the urine the kidneys generate to the cloaca, where it is expelled from the body. An animal becomes debilitated and frequently dies rapidly when the kidneys are sick or injured and cannot function effectively (Poultry Hub Australia, 2021)."
    )




    private val labelComparisons = mapOf(
        "gizzard" to Pair(
            "The gizzard in chickens grinds food to aid digestion, often containing small stones or grit that help in this process.",
            "In humans, the stomach performs a similar function of breaking down food using gastric juices, but it does not have a muscular grinding mechanism like the gizzard."
        ),
        "heart" to Pair(
            "The heart pumps blood throughout the chicken's body, supplying oxygen and nutrients.",
            "The hearts of chickens and humans both serve the primary function of pumping blood throughout the body, but their structural adaptations differ significantly. In chickens, the heart is adapted to support the high metabolic demands of activities such as running and short-distance flying, which require quick circulation of oxygen and nutrients (5 Answers from Research papers, n.d.). In contrast, the human heart is a muscular organ with four chambers that pump blood through the circulatory system, maintaining homeostasis and supporting all body systems (Human Anatomy, 2023)."
        ),
        "large_intestine" to Pair(
            "The large intestine absorbs water and salts from food remnants, forming solid waste for excretion.",
            "The large intestines of chickens and humans both handle waste products, but with distinct structural and functional differences. In chickens, the large intestine is short and connects directly to the cloaca, where it facilitates the expulsion of waste (Poultry Hub Australia, 2020). In humans, the large intestine is much longer and plays a crucial role in water absorption and the formation of feces before waste is excreted through the rectum (Human Anatomy, 2023)."
        ),
        "liver" to Pair(
            "The liver processes nutrients, detoxifies harmful substances, and produces bile for digestion.",
            "Both chickens and humans have livers that play vital roles in metabolism, but their functions vary slightly in scope. The chicken liver is essential for storing vitamins and minerals, as well as detoxifying the body by processing and eliminating harmful substances (Chicken Liver, n.d.). On the other hand, the human liver is more complex, performing not only detoxification but also bile production, protein synthesis, and nutrient storage (Human Anatomy, 2023)."
        ),
        "lungs" to Pair(
            "The lungs in chickens are responsible for gas exchange, supplying oxygen and expelling carbon dioxide.",
            "The respiratory systems of chickens and humans also differ greatly, particularly in the structure and function of the lungs. Chicken lungs are small, rigid, and immobile, attached to the ribs, and rely on air sacs to facilitate air movement through their bodies (Small and Backyard Poultry, n.d.). In contrast, human lungs are large, spongy, and capable of expanding and contracting, allowing for efficient gas exchange through alveoli (Human Anatomy, 2023)."
        ),
        "small_intestine" to Pair(
            "The small intestine is where most digestion and nutrient absorption occurs.",
            "The small intestine in both chickens and humans is responsible for digestion and nutrient absorption, but their sizes and functions differ. Chickens have a relatively short small intestine that produces digestive enzymes and absorbs nutrients, which is suited to their more rapid digestion (Poultry Hub Australia, 2020). In contrast, the human small intestine is longer and divided into three sections—duodenum, jejunum, and ileum—each specialized for digestion and absorption of nutrients over a longer period (Human Anatomy, 2023)."
        ),
        "spleen" to Pair(
            "The spleen filters blood and produces lymphocytes, playing a role in the immune response.",
            "The spleen has immune system roles in both chickens and humans but with some variations. In chickens, the spleen's size is often used as an indicator of immune activity, with larger spleens suggesting stronger immune responses (Vali et al., 2023). In humans, the spleen is part of the lymphatic system and functions to filter blood, recycle red blood cells, and produce white blood cells for immune defense (Human Anatomy, 2023)."
        ),
        "gallbladder" to Pair(
            "The gallbladder stores bile produced by the liver for fat digestion.",
            "The gallbladder in both chickens and humans serves the purpose of storing bile, but there are differences in its location and function. In chickens, the gallbladder is located beneath the spleen on the right lobe of the liver, and it connects to both the liver and small intestine via bile ducts to aid in digestion (Poultry Hub Australia, 2020). In humans, the gallbladder stores bile produced by the liver and releases it into the small intestine to assist with digestion, especially of fats (Human Anatomy, 2023)."
        ),
        "kidney" to Pair(
            "The two kidneys in domestic poultry make up the excretory system. The ureters in each kidney transport the urine the kidneys generate to the cloaca, where it is expelled from the body. An animal becomes debilitated and frequently dies rapidly when the kidneys are sick or injured and cannot function effectively (Poultry Hub Australia, 2021).",
            "The kidneys in chickens and humans serve similar functions, primarily related to waste elimination, but with differences in structure and function. In chickens, the kidneys are located near the midline of the body and excrete waste products into the cloaca, which also serves as the excretory outlet (Poultry Hub Australia, 2021). In humans, the kidneys filter blood, removing waste and regulating fluid balance, while the processed urine is excreted through the urethra (Human Anatomy, 2023)."
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
        "kidney" to Pair(
            "The two kidneys in domestic poultry make up the excretory system. The ureters in each kidney transport the urine the kidneys generate to the cloaca, where it is expelled from the body. An animal becomes debilitated and frequently dies rapidly when the kidneys are sick or injured and cannot function effectively (Poultry Hub Australia, 2021).",
            "In frogs, the kidneys filter waste from the blood similarly to other vertebrates, while in chickens, two kidneys comprise the excretory system, filtering waste, regulating bodily fluids, and excreting nitrogen waste as uric acid, which helps conserve water—a crucial adaptation for terrestrial life (Poultry Hub Australia, 2021). This difference highlights each species' unique adaptations in waste management to suit their environments."
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
        "lungs" to Pair(
            "The lungs facilitate the exchange of gases, allowing the frog to take in oxygen from the air and expel carbon dioxide.",
            "The chicken’s lungs are small and attached to the ribs, relying on air sacs to aid in the exchange of gases (Small and Backyard Poultry, n.d.). Conversely, tilapia, like other fish, use gills for oxygen extraction from water, with highly vascularized gill filaments facilitating efficient oxygen uptake (Parenti et al., 2024)."
        ),
        "gizzard" to Pair(
            "The stomach secretes gastric juices, which contain enzymes and acids that break down food into a semi-liquid form called chyme before it moves to the small intestine.",
            "The chicken's gizzard serves as a mechanical stomach that grinds food to aid in digestion (Poultry Hub Australia, 2020). In contrast, the tilapia has a stomach that breaks down food chemically, helping with nutrient absorption (Parenti et al., 2024)."
        ),
        "large_intestine" to Pair(
            "The small intestine is the primary site for digestion of food and absorption of nutrients, including carbohydrates, proteins, and fats, into the bloodstream.",
            "The chicken's large intestine primarily handles water reabsorption, ensuring efficient digestion of nutrients (Poultry Hub Australia, 2020). In tilapia, the intestine serves as the site for nutrient absorption, with its structure varying depending on the species' diet (Parenti et al., 2024)."
        ),
        "small_intestine" to Pair(
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
            setTextColor(ContextCompat.getColor(this@chickencdetection,R.color.blue))
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
            setTextColor(ContextCompat.getColor(this@chickencdetection, android.R.color.black))
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
                setTextColor(ContextCompat.getColor(this@chickencdetection, android.R.color.white))
                setBackgroundColor(ContextCompat.getColor(this@chickencdetection, R.color.blue))
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
                setTextColor(ContextCompat.getColor(this@chickencdetection, android.R.color.white))
                setBackgroundColor(ContextCompat.getColor(this@chickencdetection, R.color.blue))
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
                setTextColor(ContextCompat.getColor(this@chickencdetection, android.R.color.white))
                setBackgroundColor(ContextCompat.getColor(this@chickencdetection, R.color.blue))
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
            setBackgroundColor(ContextCompat.getColor(this@chickencdetection,R.color.blue )) // Set background to white
            setTextColor(ContextCompat.getColor(this@chickencdetection, android.R.color.white)) // Set text color to black
        }


        alertDialog.window?.decorView?.setBackgroundColor(ContextCompat.getColor(this, R.color.white))
    }



    private fun showChickenComparison(label: String, chickenComparison: String) {
        val comparisonDialogBuilder = androidx.appcompat.app.AlertDialog.Builder(this)


        val titleTextView = TextView(this).apply {
            text = "$label Comparison with Frog"
            textSize = 20f
            setTextColor(ContextCompat.getColor(this@chickencdetection,R.color.blue))
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
            setTextColor(ContextCompat.getColor(this@chickencdetection, android.R.color.black))
            setLineSpacing(0f, 1.0f) // Adjust spacing for better readability
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                justificationMode = LineBreaker.JUSTIFICATION_MODE_INTER_WORD
            }
            setTextColor(ContextCompat.getColor(this@chickencdetection, android.R.color.white))
            setBackgroundColor(ContextCompat.getColor(this@chickencdetection,R.color.blue)) // Use blue color
        }


        comparisonAlertDialog.window?.decorView?.setBackgroundColor(ContextCompat.getColor(this, R.color.white))


        comparisonAlertDialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            setBackgroundColor(ContextCompat.getColor(this@chickencdetection,R.color.blue))
            setTextColor(ContextCompat.getColor(this@chickencdetection, android.R.color.white))
        }
    }

    private fun showHumanComparison(label: String, humanComparison: String) {
        val comparisonDialogBuilder = androidx.appcompat.app.AlertDialog.Builder(this)


        val titleTextView = TextView(this).apply {
            text = "$label Comparison with Human"
            textSize = 20f
            setTextColor(ContextCompat.getColor(this@chickencdetection, R.color.blue))
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
            setTextColor(ContextCompat.getColor(this@chickencdetection, android.R.color.black))
            setLineSpacing(0f, 1.0f) // Adjust spacing for better readability
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                justificationMode = LineBreaker.JUSTIFICATION_MODE_INTER_WORD
            }
            setTextColor(ContextCompat.getColor(this@chickencdetection, android.R.color.white))
            setBackgroundColor(ContextCompat.getColor(this@chickencdetection,R.color.blue)) // Use blue color
        }



        comparisonAlertDialog.window?.decorView?.setBackgroundColor(ContextCompat.getColor(this, R.color.white))


        comparisonAlertDialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            setBackgroundColor(ContextCompat.getColor(this@chickencdetection, R.color.blue))
            setTextColor(ContextCompat.getColor(this@chickencdetection, android.R.color.white))
        }
    }

    private fun showTilapiaComparison(label: String, tilapiaComparison: String) {
        val comparisonDialogBuilder = androidx.appcompat.app.AlertDialog.Builder(this)


        val titleTextView = TextView(this).apply {
            text = "$label Comparison with Tilapia"
            textSize = 20f
            setTextColor(ContextCompat.getColor(this@chickencdetection, R.color.blue))
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
            setTextColor(ContextCompat.getColor(this@chickencdetection, android.R.color.black))
            setLineSpacing(0f, 1.0f) // Adjust spacing for better readability
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                justificationMode = LineBreaker.JUSTIFICATION_MODE_INTER_WORD
            }
            setTextColor(ContextCompat.getColor(this@chickencdetection, android.R.color.white))
            setBackgroundColor(ContextCompat.getColor(this@chickencdetection,R.color.blue)) // Use blue color
        }



        comparisonAlertDialog.window?.decorView?.setBackgroundColor(ContextCompat.getColor(this, R.color.white))


        comparisonAlertDialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            setBackgroundColor(ContextCompat.getColor(this@chickencdetection,R.color.blue))
            setTextColor(ContextCompat.getColor(this@chickencdetection, android.R.color.white))
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
        val intent = Intent(this, FullScreenImageActivity2::class.java).apply {
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
