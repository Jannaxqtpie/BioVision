package com.surendramaran.yolov8tflite

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class guide : AppCompatActivity() {

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_guide)



        val ImageView1 = findViewById(R.id.chicken) as View
        val ImageView2 = findViewById(R.id.fish) as View
        val ImageView3 = findViewById(R.id.frog) as View


        val buttonView: Button = findViewById(R.id.guideback)

        buttonView.setOnClickListener {
            val intent = Intent(this@guide, DrawerNav::class.java)
            startActivity(intent)
            finish()

        }


        ImageView1.setOnClickListener {
            val intent: Intent = Intent(
                this@guide,
                guidechicken::class.java
            )
            startActivity(intent)
            finish()

        }
        ImageView2.setOnClickListener {
            val intent: Intent =
                Intent(this@guide, guidetilapia::class.java)
            startActivity(intent)
            finish()

        }
        ImageView3.setOnClickListener {
            val intent: Intent =
                Intent(this@guide, guidefrog::class.java)
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