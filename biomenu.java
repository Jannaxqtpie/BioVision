package com.surendramaran.yolov8tflite;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class biomenu extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_biomenu);

        ImageView imageView = findViewById(R.id.detection);
        ImageView imageView2 = findViewById(R.id.chickencam);
        ImageView imageView3 = findViewById(R.id.tilapiacam);

        View imageView1 = findViewById(R.id.Guide);




        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                Intent intent = new Intent(biomenu.this, MainActivity.class); // Replace AnotherActivity with your desired activity
                startActivity(intent);
                finish();
            }
        });


        imageView3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                Intent intent = new Intent(biomenu.this, tilapiadetection.class); // Replace AnotherActivity with your desired activity
                startActivity(intent);
                finish();
            }
        });

        imageView1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                Intent intent = new Intent(biomenu.this, guide.class); // Replace AnotherActivity with your desired activity
                startActivity(intent);
                finish();
            }
        });

        imageView2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                Intent intent = new Intent(biomenu.this, chickencdetection.class); // Replace AnotherActivity with your desired activity
                startActivity(intent);
                finish();
            }
        });


        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }
}