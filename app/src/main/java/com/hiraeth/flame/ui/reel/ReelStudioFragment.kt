package com.hiraeth.flame.ui.reel

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.hiraeth.flame.R
import com.hiraeth.flame.databinding.FragmentReelBinding
import kotlinx.coroutines.launch

class ReelStudioFragment : Fragment() {

    private var _binding: FragmentReelBinding? = null
    private val binding get() = _binding!!

    private val container get() = (requireActivity().application as com.hiraeth.flame.HiraethApplication).container

    private val viewModel: ReelStudioViewModel by viewModels {
        ReelStudioViewModel.factory(container.mediaRepository, container.mediaStorage.reelsDir)
    }

    private lateinit var adapter: ReelClipsAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentReelBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val navController = findNavController()
        val appBarConfig = AppBarConfiguration(setOf(R.id.libraryFragment, R.id.cameraFragment, R.id.albumsFragment))
        binding.toolbar.setupWithNavController(navController, appBarConfig)

        adapter = ReelClipsAdapter(
            container = container,
            selectedIds = { viewModel.selected.value },
            onToggle = { id ->
                viewModel.toggle(id)
                adapter.notifyDataSetChanged()
            },
            onOpen = { id ->
                findNavController().navigate(
                    R.id.action_reel_to_detail,
                    bundleOf(
                        "mediaId" to id,
                        "albumId" to -1L
                    ),
                )
            },
        )
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter

        binding.btnStage.setOnClickListener { viewModel.stageReelProject() }
        binding.btnClear.setOnClickListener {
            viewModel.clearSelection()
            adapter.notifyDataSetChanged()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.videos.collect { adapter.submitList(it) }
                }
                launch {
                    viewModel.status.collect { binding.statusText.text = it.orEmpty() }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
