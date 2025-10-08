package com.hitsu.patologiafacil

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.hitsu.patologiafacil.databinding.ActivityMainBinding
import com.hitsu.patologiafacil.util.FirstRunPrefs

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHost.navController

        val topLevelDestinations = setOf(
            R.id.tutorialFragment,
            R.id.introBasicFragment,
            R.id.captureFragment,
            R.id.historyFragment
        )

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val showBottomNav = destination.id in topLevelDestinations
            binding.bottomNav.visibility = if (showBottomNav) View.VISIBLE else View.GONE
            if (showBottomNav) {
                binding.bottomNav.menu.findItem(destination.id)?.isChecked = true
            }
        }

        val bottomNav: BottomNavigationView = binding.bottomNav
        bottomNav.setOnItemSelectedListener { item ->
            if (item.itemId == navController.currentDestination?.id) {
                return@setOnItemSelectedListener true
            }

            val popped = navController.popBackStack(item.itemId, false)
            if (!popped) {
                try {
                    navController.navigate(item.itemId)
                } catch (_: IllegalArgumentException) {
                    return@setOnItemSelectedListener false
                }
            }

            true
        }

        bottomNav.setOnItemReselectedListener { /* no-op: mantém estado da aba atual */ }

        // Onboarding agora é a startDestination no nav_graph; nada a fazer aqui
    }
}
