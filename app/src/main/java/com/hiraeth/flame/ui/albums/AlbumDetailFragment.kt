package com.hiraeth.flame.ui.albums

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hiraeth.flame.R
import com.hiraeth.flame.databinding.FragmentAlbumDetailBinding
import com.hiraeth.flame.ui.library.MediaLibraryAdapter
import kotlinx.coroutines.launch

class AlbumDetailFragment : Fragment() {

    private var _binding: FragmentAlbumDetailBinding? = null
    private val binding get() = _binding!!

    private val container get() = (requireActivity().application as com.hiraeth.flame.HiraethApplication).container
    private val albumId: Long get() = requireArguments().getLong("albumId")

    private val viewModel: AlbumDetailViewModel by viewModels {
        AlbumDetailViewModel.factory(container.albumRepository, albumId)
    }

    private lateinit var adapter: MediaLibraryAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAlbumDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolbar.setupWithNavController(findNavController())

        adapter = MediaLibraryAdapter(container, gridMode = true) { id ->
            val b = Bundle().apply { putLong("mediaId", id) }
            findNavController().navigate(R.id.action_albumDetail_to_detail, b)
        }

        binding.recyclerMedia.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.recyclerMedia.adapter = adapter

        binding.btnEditAlbum.setOnClickListener { showEditAlbumDialog() }
        binding.btnFilter.setOnClickListener { showFilterDialog() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.albumWithMedia.collect { awm ->
                        if (awm == null) return@collect
                        binding.albumTitle.text = awm.album.name
                        binding.albumDescription.text = awm.album.description
                    }
                }
                launch {
                    viewModel.filteredMedia.collect { adapter.submitList(it) }
                }
            }
        }
    }

    private fun showEditAlbumDialog() {
        val awm = viewModel.albumWithMedia.value ?: return
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_new_album, null)
        val inputName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.album_name_input)
        val inputDesc = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.description_input)
        val btnCancel = dialogView.findViewById<View>(R.id.btn_cancel)
        val btnCreate = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_create)

        inputName.setText(awm.album.name)
        inputDesc.setText(awm.album.description)
        btnCreate.text = "Save"

        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.Dialog_Neon)
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnCreate.setOnClickListener {
            val name = inputName.text?.toString().orEmpty().trim()
            val desc = inputDesc.text?.toString().orEmpty().trim()
            if (name.isNotBlank()) {
                viewModel.updateAlbum(name, desc)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showFilterDialog() {
        val input = com.google.android.material.textfield.TextInputEditText(requireContext())
        input.hint = "Search title or description..."
        input.setText(viewModel.filter.value)

        MaterialAlertDialogBuilder(requireContext(), R.style.Dialog_Neon)
            .setTitle("Filter Media")
            .setView(input)
            .setPositiveButton("Apply") { _, _ ->
                viewModel.setFilter(input.text?.toString().orEmpty())
            }
            .setNegativeButton("Clear") { _, _ ->
                viewModel.setFilter("")
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
