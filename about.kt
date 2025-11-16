package com.surendramaran.yolov8tflite

import android.content.Intent
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class about : AppCompatActivity() {
    override fun onBackPressed() {
        try {
            Log.d("MyActivity", "Back button pressed")



            val intent = Intent(this, biomenu::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)


            System.exit(0)

        } catch (e: Exception) {
            Log.e("MyActivity", "Error during onBackPressed: ${e.message}", e)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_about)


        val buttonView: Button = findViewById(R.id.guideback)

        buttonView.setOnClickListener {
            val intent = Intent(this@about, biomenu::class.java)
            startActivity(intent)
            finish()

        }

        val textView: TextView = findViewById(R.id.frogheart)
        val textView1: TextView = findViewById(R.id.frogliver)
        textView.movementMethod = LinkMovementMethod.getInstance()
        textView1.movementMethod = LinkMovementMethod.getInstance()
        val textView3: TextView = findViewById(R.id.frogstomach)
        val textView4: TextView = findViewById(R.id.frogspleen)
        textView3.movementMethod = LinkMovementMethod.getInstance()
        textView4.movementMethod = LinkMovementMethod.getInstance()
        val textView5: TextView = findViewById(R.id.froglarge)
        val textView6: TextView = findViewById(R.id.frogsmall)
        textView5.movementMethod = LinkMovementMethod.getInstance()
        textView6.movementMethod = LinkMovementMethod.getInstance()
        val textView7: TextView = findViewById(R.id.froggallbladder)
        val textView8: TextView = findViewById(R.id.froglungs)
        textView7.movementMethod = LinkMovementMethod.getInstance()
        textView8.movementMethod = LinkMovementMethod.getInstance()

        val textView9: TextView = findViewById(R.id.chickheart)
        val textView10: TextView = findViewById(R.id.chickliver)
        textView9.movementMethod = LinkMovementMethod.getInstance()
        textView10.movementMethod = LinkMovementMethod.getInstance()
        val textView11: TextView = findViewById(R.id.chicklungs)
        val textView12: TextView = findViewById(R.id.chickgallbladder)
        textView11.movementMethod = LinkMovementMethod.getInstance()
        textView12.movementMethod = LinkMovementMethod.getInstance()
        val textView13: TextView = findViewById(R.id.chickslpeen)
        val textView14: TextView = findViewById(R.id.chickgizard)
        textView13.movementMethod = LinkMovementMethod.getInstance()
        textView14.movementMethod = LinkMovementMethod.getInstance()
        val textView15: TextView = findViewById(R.id.chicklarge)
        val textView16: TextView = findViewById(R.id.chicksmall)
        textView15.movementMethod = LinkMovementMethod.getInstance()
        textView16.movementMethod = LinkMovementMethod.getInstance()

        val textView17: TextView = findViewById(R.id.fishheart)
        val textView18: TextView = findViewById(R.id.fishliver)
        textView17.movementMethod = LinkMovementMethod.getInstance()
        textView18.movementMethod = LinkMovementMethod.getInstance()
        val textView19: TextView = findViewById(R.id.fishgills)
        val textView20: TextView = findViewById(R.id.fishstomach)
        textView19.movementMethod = LinkMovementMethod.getInstance()
        textView20.movementMethod = LinkMovementMethod.getInstance()
        val textView21: TextView = findViewById(R.id.fishintestine)
        val textView22: TextView = findViewById(R.id.fishspleen)
        textView21.movementMethod = LinkMovementMethod.getInstance()
        textView22.movementMethod = LinkMovementMethod.getInstance()
        val textView23: TextView = findViewById(R.id.fishgallbladder)
        val textView24: TextView = findViewById(R.id.fishswimbladder)
        textView23.movementMethod = LinkMovementMethod.getInstance()
        textView24.movementMethod = LinkMovementMethod.getInstance()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
}