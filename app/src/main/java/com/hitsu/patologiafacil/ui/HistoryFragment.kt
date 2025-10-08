package com.hitsu.patologiafacil.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.hitsu.patologiafacil.R
import com.hitsu.patologiafacil.data.AnalysisRepository
import com.hitsu.patologiafacil.data.AppDatabase
import com.hitsu.patologiafacil.databinding.FragmentHistoryBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HistoryFragment : Fragment() {
    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var vm: HistoryViewModel
    private lateinit var adapter: HistoryAdapter
    private var currentItems: List<com.hitsu.patologiafacil.data.AnalysisEntity> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Corrige título com caracteres válidos
        try { binding.toolbarHistory.title = getString(R.string.history_title) } catch (_: Exception) {}
        binding.chipAll.isChecked = true
        vm = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val dao = AppDatabase.instance(requireContext()).analysisDao()
                val repo = AnalysisRepository(dao)
                @Suppress("UNCHECKED_CAST")
                return HistoryViewModel(repo) as T
            }
        }).get(HistoryViewModel::class.java)

        // Adapter que faz o bind do card existente (sem alterar o XML)
        adapter = HistoryAdapter(
            inflater = layoutInflater,
            onDetails = { entity ->
                HistoryDetailDialog.newInstance(entity.id)
                    .show(parentFragmentManager, "history_detail")
            },
            onDelete = { entity ->
                AlertDialog.Builder(requireContext())
                    .setMessage(getString(R.string.confirm_remove_history))
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Remover") { _, _ -> vm.delete(entity.id) }
                    .show()
            }
        )
        binding.listHistory.adapter = adapter

        // Tap no card também abre detalhes
        binding.listHistory.setOnItemClickListener { _, _, position, _ ->
            val item = currentItems.getOrNull(position) ?: return@setOnItemClickListener
            HistoryDetailDialog.newInstance(item.id).show(parentFragmentManager, "history_detail")
        }

        // Long-press exclui quando não há botão X no card
        binding.listHistory.setOnItemLongClickListener { _, _, position, _ ->
            val item = currentItems.getOrNull(position) ?: return@setOnItemLongClickListener true
            AlertDialog.Builder(requireContext())
                .setMessage(getString(R.string.confirm_remove_history))
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Remover") { _, _ -> vm.delete(item.id) }
                .show()
            true
        }

        // Filtros (IDs existentes)
        binding.chipAll.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) vm.setFilter(setOf("LEVE", "MODERADA", "GRAVE"))
        }
        binding.chipLight.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) vm.setFilter(setOf("LEVE"))
        }
        binding.chipModerate.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) vm.setFilter(setOf("MODERADA"))
        }
        binding.chipCritical.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) vm.setFilter(setOf("GRAVE"))
        }

        // Observa a lista filtrada e faz o bind no card (imagem/título/data/resumo/badge/botões)
        viewLifecycleOwner.lifecycleScope.launch {
            vm.historicoFiltrado.collectLatest { list ->
                android.util.Log.d("PF-HistoryFrag", "collect list size=${list.size}")
                currentItems = list
                adapter.submit(list) // se usar HistoryAdapter
                binding.txtEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        // depois de setar os listeners dos chips:
        binding.chipAll.isChecked = true   // ativa o filtro "todos" na largada

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

