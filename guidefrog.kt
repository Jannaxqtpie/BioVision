package com.surendramaran.yolov8tflite

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class guidefrog : AppCompatActivity() {

    override fun onBackPressed() {
        super.onBackPressed()
        val intent = Intent(this, guide::class.java)
        startActivity(intent)

        finish()

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_guidefrog)

        val buttonView: Button = findViewById(R.id.guideback)

        buttonView.setOnClickListener {
            val intent = Intent(this@guidefrog, guide::class.java)
            startActivity(intent)
            finish()
        }


        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
}