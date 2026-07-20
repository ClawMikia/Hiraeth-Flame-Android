package com.hiraeth.flame.ui.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import androidx.viewpager2.widget.CompositePageTransformer
import androidx.viewpager2.widget.MarginPageTransformer
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hiraeth.flame.R
import com.hiraeth.flame.data.db.AlbumWithMedia
import com.hiraeth.flame.databinding.FragmentMediaDetailBinding
import com.hiraeth.flame.di.AppContainer
import com.hiraeth.flame.util.ExportHelper
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.abs

class MediaDetailFragment : Fragment() {

    private var _binding: FragmentMediaDetailBinding? = null
    private val binding get() = _binding!!

    private val container: AppContainer get() = (requireActivity().application as com.hiraeth.flame.HiraethApplication).container

    private val mediaId: Long get() = requireArguments().getLong("mediaId")
    private val albumId: Long get() = requireArguments().getLong("albumId", -1L)

    private val viewModel: MediaDetailViewModel by viewModels {
        MediaDetailViewModel.factory(container.mediaRepository, container.albumRepository, mediaId, albumId)
    }

    private lateinit var pagerAdapter: MediaPagerAdapter
    private var cachedAlbums: List<AlbumWithMedia> = emptyList()
    private var isInitialJumpDone = false

    private var pendingExportFormat: ExportHelper.ImageFormat? = null

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        if (uri != null) {
            val media = viewModel.media.value ?: return@registerForActivityResult
            val mimeType = if (media.isVideo) "video/mp4" else {
                when (pendingExportFormat) {
                    ExportHelper.ImageFormat.PNG -> "image/png"
                    ExportHelper.ImageFormat.JPG -> "image/jpeg"
                    null -> "image/jpeg"
                }
            }
            // Re-launch with correct MIME type by reusing the URI directly
            viewModel.exportMedia(requireContext(), uri, pendingExportFormat) { success ->
                if (success) {
                    Toast.makeText(requireContext(), getString(R.string.export_complete), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), getString(R.string.export_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

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

        pagerAdapter = MediaPagerAdapter(container)
        binding.viewPager.adapter = pagerAdapter
        
        val transformer = CompositePageTransformer().apply {
            addTransformer(MarginPageTransformer(40))
            addTransformer { page, position ->
                val r = 1 - abs(position)
                page.scaleY = 0.90f + r * 0.10f
                page.alpha = 0.5f + r * 0.5f
            }
        }
        binding.viewPager.setPageTransformer(transformer)
        
        (binding.viewPager.getChildAt(0) as? androidx.recyclerview.widget.RecyclerView)?.let { rv ->
            rv.setOnTouchListener { _, _ ->
                binding.nestedScrollView.requestDisallowInterceptTouchEvent(true)
                false
            }
        }
        
        binding.viewPager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    val item = pagerAdapter.currentList.getOrNull(position)
                    if (item != null) {
                        viewModel.setCurrentId(item.id)
                        pagerAdapter.playVideo(
                            position,
                            binding.viewPager.getChildAt(0) as androidx.recyclerview.widget.RecyclerView,
                        )
                    }
                }
            },
        )

        binding.btnSaveMeta.setOnClickListener {
            val title = binding.titleInput.text?.toString()?.trim().orEmpty()
            val description = binding.descInput.text?.toString()?.trim().orEmpty()

            if (title.isNotEmpty()) {
                viewModel.saveMetadata(title, description)
                Toast.makeText(requireContext(), "Changes saved successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Save failed: Title cannot be empty", Toast.LENGTH_LONG).show()
            }
        }

        binding.btnAddAlbum.setOnClickListener { showAlbumPicker() }

        binding.btnEditImage.setOnClickListener {
            val m = viewModel.media.value
            if (m != null) {
                findNavController().navigate(
                    R.id.action_mediaDetail_to_imageEditor,
                    Bundle().apply { putLong("mediaId", m.id) }
                )
            }
        }

        binding.btnExport.setOnClickListener { showFormatPicker() }

        binding.btnDelete.setOnClickListener {
            if (albumId != -1L) {
                showRemoveFromAlbumConfirmation()
            } else {
                showDeleteConfirmation()
            }
        }

        binding.btnPrev.setOnClickListener { 
            binding.viewPager.currentItem -= 1
        }
        binding.btnNext.setOnClickListener { 
            binding.viewPager.currentItem += 1
        }

        binding.btnFullscreen.setOnClickListener {
            val m = viewModel.media.value
            if (m != null) {
                FullscreenMediaDialogFragment.newInstance(m.id, albumId)
                    .show(parentFragmentManager, "fullscreen_media")
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.albums.collect { cachedAlbums = it }
                }
                
                launch {
                    viewModel.navigationItems.collect { items ->
                        pagerAdapter.submitList(items) {
                            if (!isInitialJumpDone) {
                                val index = items.indexOfFirst { it.id == mediaId }
                                if (index != -1) {
                                    binding.viewPager.setCurrentItem(index, false)
                                    isInitialJumpDone = true
                                }
                            }
                        }
                    }
                }

                launch {
                    viewModel.media.collect { m ->
                        if (m == null) return@collect
                        binding.btnEditImage.visibility = if (m.isVideo) View.GONE else View.VISIBLE
                        if (!binding.titleInput.hasFocus()) {
                            binding.titleInput.setText(m.displayName)
                        }
                        if (!binding.descInput.hasFocus()) {
                            binding.descInput.setText(m.description)
                        }
                    }
                }

                launch {
                    combine(viewModel.navigationIds, viewModel.currentId) { ids, currentId ->
                        ids to currentId
                    }.collect { (ids, currentId) ->
                        val index = ids.indexOf(currentId)
                        binding.btnPrev.isEnabled = index > 0
                        binding.btnNext.isEnabled = (index != -1) && (index < ids.size - 1)
                        
                        val hasMulti = ids.size > 1
                        binding.btnPrev.visibility = if (hasMulti) View.VISIBLE else View.GONE
                        binding.btnNext.visibility = if (hasMulti) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }

    private fun showFormatPicker() {
        val currentMedia = viewModel.media.value ?: return
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_format_picker, null)
        val radioGroup = dialogView.findViewById<android.widget.RadioGroup>(R.id.format_group)
        val jpgOption = dialogView.findViewById<android.widget.RadioButton>(R.id.format_jpg)
        val pngOption = dialogView.findViewById<android.widget.RadioButton>(R.id.format_png)
        val mp4Option = dialogView.findViewById<android.widget.RadioButton>(R.id.format_mp4)

        if (currentMedia.isVideo) {
            jpgOption.visibility = View.GONE
            pngOption.visibility = View.GONE
            mp4Option.visibility = View.VISIBLE
            mp4Option.isChecked = true
        } else {
            jpgOption.visibility = View.VISIBLE
            pngOption.visibility = View.VISIBLE
            mp4Option.visibility = View.GONE
            jpgOption.isChecked = true
        }

        MaterialAlertDialogBuilder(requireContext(), R.style.Dialog_Neon)
            .setView(dialogView)
            .setPositiveButton("Export") { _, _ ->
                val checkedId = radioGroup.checkedRadioButtonId
                if (currentMedia.isVideo) {
                    pendingExportFormat = null
                    exportLauncher.launch("${currentMedia.displayName}.mp4")
                } else {
                    val format = when (checkedId) {
                        R.id.format_png -> ExportHelper.ImageFormat.PNG
                        else -> ExportHelper.ImageFormat.JPG
                    }
                    pendingExportFormat = format
                    val ext = if (format == ExportHelper.ImageFormat.PNG) "png" else "jpg"
                    exportLauncher.launch("${currentMedia.displayName}.$ext")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteConfirmation() {
        MaterialAlertDialogBuilder(requireContext(), R.style.Dialog_Neon)
            .setTitle(R.string.delete_confirmation_title)
            .setMessage(R.string.delete_confirmation_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                viewModel.delete { findNavController().popBackStack() }
            }
            .show()
    }

    private fun showRemoveFromAlbumConfirmation() {
        MaterialAlertDialogBuilder(requireContext(), R.style.Dialog_Neon)
            .setTitle("Remove from Album?")
            .setMessage("This item will be removed from this album but will remain in your library.")
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton("Remove") { _, _ ->
                viewModel.removeFromCurrentAlbum { findNavController().popBackStack() }
            }
            .show()
    }

    private fun showAlbumPicker() {
        val names = cachedAlbums.map { it.album.name }.toTypedArray()
        if (names.isEmpty()) {
            Toast.makeText(requireContext(), "No albums created yet", Toast.LENGTH_SHORT).show()
            return
        }
        MaterialAlertDialogBuilder(requireContext(), R.style.Dialog_Neon)
            .setTitle("Add to Album")
            .setItems(names) { _, which, ->
                viewModel.addToAlbum(cachedAlbums[which].album.id)
                Toast.makeText(requireContext(), "Added to ${names[which]}", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pagerAdapter.releasePlayer()
        _binding = null
    }
}
