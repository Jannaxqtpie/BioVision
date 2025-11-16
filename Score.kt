package com.surendramaran.yolov8tflite

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

data class UserScore(
    val name: String,
    val score: String,
    val timestamp: Long
)

