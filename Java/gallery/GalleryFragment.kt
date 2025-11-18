package com.surendramaran.yolov8tflite.ui.gallery

import android.content.Intent
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.surendramaran.yolov8tflite.DrawerNav
import com.surendramaran.yolov8tflite.R
import com.surendramaran.yolov8tflite.databinding.FragmentGalleryBinding

class GalleryFragment : Fragment() {

    private var _binding: FragmentGalleryBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val galleryViewModel =
            ViewModelProvider(this).get(GalleryViewModel::class.java)

        _binding = FragmentGalleryBinding.inflate(inflater, container, false)
        val root: View = binding.root


        val buttonView: Button = root.findViewById(R.id.guideback)

        buttonView.setOnClickListener {
            val intent = Intent(requireContext(), DrawerNav::class.java)
            startActivity(intent)
        }

        // TextView initializations and enabling LinkMovementMethod
        val textViewIds = listOf(
            R.id.frogheart, R.id.frogliver, R.id.frogstomach, R.id.frogspleen,
            R.id.froglarge, R.id.frogsmall, R.id.froggallbladder, R.id.froglungs,
            R.id.chickheart, R.id.chickliver, R.id.chicklungs, R.id.chickgallbladder,
            R.id.chickslpeen, R.id.chickgizard, R.id.chicklarge, R.id.chicksmall,
            R.id.fishheart, R.id.fishliver, R.id.fishgills, R.id.fishstomach,
            R.id.fishintestine, R.id.fishspleen, R.id.fishgallbladder, R.id.fishswimbladder
        )

        for (id in textViewIds) {
            val textView: TextView = root.findViewById(id)
            textView.movementMethod = LinkMovementMethod.getInstance()
        }

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}