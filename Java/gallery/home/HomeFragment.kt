package com.surendramaran.yolov8tflite.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.surendramaran.yolov8tflite.MainActivity
import com.surendramaran.yolov8tflite.biomenu
import com.surendramaran.yolov8tflite.chickencdetection
import com.surendramaran.yolov8tflite.databinding.FragmentHomeBinding
import com.surendramaran.yolov8tflite.guide
import com.surendramaran.yolov8tflite.tilapiadetection

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val homeViewModel =
            ViewModelProvider(this).get(HomeViewModel::class.java)

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root


        val frogImageView = binding.detection

        // Set the click listener on the ImageView
        frogImageView.setOnClickListener {
            // Start the new activity
            val intent = Intent(requireContext(), MainActivity::class.java) // Replace `GuideActivity` with your actual activity
            startActivity(intent)
        }

        val fishImageView = binding.fish

        // Set the click listener on the ImageView
        fishImageView.setOnClickListener {
            // Start the new activity
            val intent = Intent(requireContext(), tilapiadetection::class.java) // Replace `GuideActivity` with your actual activity
            startActivity(intent)
        }

        val chickImageView = binding.chickencam

        // Set the click listener on the ImageView
        chickImageView.setOnClickListener {
            // Start the new activity
            val intent = Intent(requireContext(), chickencdetection::class.java) // Replace `GuideActivity` with your actual activity
            startActivity(intent)
        }

        val guideImageView = binding.Guide

        // Set the click listener on the ImageView
        guideImageView.setOnClickListener {
            // Start the new activity
            val intent = Intent(requireContext(), guide::class.java) // Replace `GuideActivity` with your actual activity
            startActivity(intent)
        }


        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}