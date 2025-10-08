package com.hitsu.patologiafacil.ui

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.hitsu.patologiafacil.R

class IntroBasicFragment : Fragment(R.layout.fragment_intro_basic) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Botão inferior: Faça sua captura (leva à tela de captura)
        view.findViewById<View>(R.id.btnGoHome)?.setOnClickListener {
            try {
                findNavController().navigate(R.id.captureFragment)
            } catch (_: Exception) { }
        }
    }
}
