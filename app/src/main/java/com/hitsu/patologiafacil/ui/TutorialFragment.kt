package com.hitsu.patologiafacil.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.hitsu.patologiafacil.util.FirstRunPrefs
import com.hitsu.patologiafacil.R
import com.hitsu.patologiafacil.databinding.FragmentTutorialBinding

class TutorialFragment : Fragment() {
    private var _binding: FragmentTutorialBinding? = null
    private val binding get() = _binding!!
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTutorialBinding.inflate(inflater, container, false)
        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnStartUsing.setOnClickListener {
            FirstRunPrefs.setTutorialSeen(requireContext(), true)
            try {
                findNavController().navigate(R.id.introBasicFragment)
            } catch (_: Exception) {
                findNavController().navigate(R.id.captureFragment)
            }
        }
    }
    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

