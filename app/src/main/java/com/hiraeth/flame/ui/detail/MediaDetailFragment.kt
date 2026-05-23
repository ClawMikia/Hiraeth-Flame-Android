package com.hiraeth.flame.ui.detail

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
import coil.load
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hiraeth.flame.R
import com.hiraeth.flame.data.db.AlbumWithMedia
import com.hiraeth.flame.databinding.FragmentMediaDetailBinding
import kotlinx.coroutines.launch
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

class MediaDetailFragment : Fragment() {

    private var _binding: FragmentMediaDetailBinding? = null
    private val binding get() = _binding!!

    private val container get() = (requireActivity().application as com.hiraeth.flame.HiraethApplication).container

    private val mediaId: Long get() = requireArguments().getLong("mediaId")

    private val viewModel: MediaDetailViewModel by viewModels {
        MediaDetailViewModel.factory(container.mediaRepository, container.albumRepository, mediaId)
    }

    private var player: ExoPlayer? = null
    private var cachedAlbums: List<AlbumWithMedia> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMediaDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val navController = findNavController()
        val appBarConfig = AppBarConfiguration(
            setOf(R.id.libraryFragment, R.id.cameraFragment, R.id.albumsFragment),
        )
        binding.toolbar.setupWithNavController(navController, appBarConfig)

        binding.btnSaveMeta.setOnClickListener {
            viewModel.saveMetadata(
                binding.titleInput.text?.toString().orEmpty(),
                binding.descInput.text?.toString().orEmpty(),
            )
        }

        binding.btnAddAlbum.setOnClickListener { showAlbumPicker() }

        binding.btnDelete.setOnClickListener {
            viewModel.delete { findNavController().popBackStack() }
        }

        // Fullscreen button click listener
        binding.btnFullscreen.setOnClickListener {
            viewModel.media.value?.let { media ->
                val file = container.mediaStorage.resolveRelative(media.relativePath)
                val fullscreenDialog = FullscreenMediaDialogFragment.newInstance(file, media.isVideo)
                fullscreenDialog.show(childFragmentManager, "fullscreen_media")
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.albums.collect { cachedAlbums = it }
                }
                launch {
                    viewModel.media.collect { m ->
                        if (m == null) return@collect
                        binding.titleInput.setText(m.displayName)
                        binding.descInput.setText(m.description)

                        releasePlayer()
                        val file = container.mediaStorage.resolveRelative(m.relativePath)
                        if (m.isVideo) {
                            binding.imageView.visibility = View.GONE
                            binding.playerView.visibility = View.VISIBLE
                            player = ExoPlayer.Builder(requireContext()).build().also { exo ->
                                exo.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(file)))
                                exo.prepare()
                                binding.playerView.player = exo
                            }
                        } else {
                            binding.playerView.player = null
                            binding.playerView.visibility = View.GONE
                            binding.imageView.visibility = View.VISIBLE
                            binding.imageView.load(file) { crossfade(true) }
                        }
                    }
                }
            }
        }
    }

    private fun showAlbumPicker() {
        val albums = cachedAlbums
        val names = albums.map { "${it.album.name} (${it.album.description})" }.toTypedArray()
        if (names.isEmpty()) {
            MaterialAlertDialogBuilder(requireContext())
                .setMessage("Create an album first from the Albums tab.")
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add to album")
            .setItems(names) { _, which ->
                viewModel.addToAlbum(albums[which].album.id)
            }
            .show()
    }

    private fun releasePlayer() {
        binding.playerView.player = null
        player?.release()
        player = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        releasePlayer()
        _binding = null
    }
}