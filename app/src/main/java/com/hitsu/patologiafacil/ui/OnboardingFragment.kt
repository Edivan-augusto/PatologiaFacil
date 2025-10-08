package com.hitsu.patologiafacil.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.hitsu.patologiafacil.databinding.FragmentOnboardingBinding
import com.hitsu.patologiafacil.util.FirstRunPrefs

class OnboardingFragment : Fragment() {
    private var _binding: FragmentOnboardingBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOnboardingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnEnter.setOnClickListener {
            findNavController().navigate(com.hitsu.patologiafacil.R.id.action_onboarding_to_tutorial)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
