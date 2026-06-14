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
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hiraeth.flame.R
import com.hiraeth.flame.databinding.FragmentAlbumsBinding
import kotlinx.coroutines.launch

class AlbumsFragment : Fragment() {

    private var _binding: FragmentAlbumsBinding? = null
    private val binding get() = _binding!!

    private val container get() =
        (requireActivity().application as com.hiraeth.flame.HiraethApplication).container

    private val viewModel: AlbumsViewModel by viewModels {
        AlbumsViewModel.factory(container.albumRepository)
    }

    private lateinit var adapter: AlbumsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAlbumsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = AlbumsAdapter(
            container = container,
            onAlbumClick = { id ->
                val b = Bundle().apply { putLong("albumId", id) }
                findNavController().navigate(R.id.action_albums_to_albumDetail, b)
            }
        )
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter

        val navController = findNavController()
        val appBarConfig = AppBarConfiguration(
            setOf(R.id.libraryFragment, R.id.cameraFragment, R.id.albumsFragment),
        )
        binding.toolbar.setupWithNavController(navController, appBarConfig)

        binding.fabNewAlbum.setOnClickListener { showCreateAlbumDialog() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.albums.collect { adapter.submitList(it) }
            }
        }
    }

    private fun showCreateAlbumDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_new_album, null)
        val inputName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(
            R.id.album_name_input,
        )
        val inputDesc = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(
            R.id.description_input,
        )
        val btnCancel = dialogView.findViewById<View>(R.id.btn_cancel)
        val btnCreate = dialogView.findViewById<View>(R.id.btn_create)

        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.Dialog_Neon)
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnCreate.setOnClickListener {
            val name = inputName.text?.toString().orEmpty().trim()
            val desc = inputDesc.text?.toString().orEmpty().trim()
            if (name.isNotBlank()) {
                viewModel.createAlbum(name, desc)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
