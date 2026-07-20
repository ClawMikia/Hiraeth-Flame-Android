package com.hiraeth.flame.ui.albums

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hiraeth.flame.R
import com.hiraeth.flame.databinding.FragmentAlbumDetailBinding
import com.hiraeth.flame.databinding.DialogManageMediaBinding
import com.hiraeth.flame.ui.library.MediaLibraryAdapter
import kotlinx.coroutines.launch

class AlbumDetailFragment : Fragment() {

    private var _binding: FragmentAlbumDetailBinding? = null
    private val binding get() = _binding!!

    private val container get() = (requireActivity().application as com.hiraeth.flame.HiraethApplication).container
    private val albumId: Long get() = requireArguments().getLong("albumId")

    private val viewModel: AlbumDetailViewModel by viewModels {
        AlbumDetailViewModel.factory(container.albumRepository, container.mediaRepository, albumId)
    }

    private lateinit var adapter: MediaLibraryAdapter

    private val createZipLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            val selected = adapter.getSelectedItems()
            if (selected.isNotEmpty()) {
                viewModel.exportSelectedToZip(requireContext(), selected, uri) { success ->
                    if (success) {
                        adapter.exitSelectionMode()
                        updateSelectionBar(0)
                        Toast.makeText(requireContext(), getString(R.string.export_complete), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), getString(R.string.export_failed), Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                viewModel.exportToZip(requireContext(), uri) { success ->
                    if (success) {
                        Toast.makeText(requireContext(), "Album exported successfully!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "Export failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAlbumDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolbar.setupWithNavController(findNavController())

        adapter = MediaLibraryAdapter(
            container = container,
            gridMode = true,
            onItemClick = { id ->
                val b = Bundle().apply { 
                    putLong("mediaId", id)
                    putLong("albumId", albumId)
                }
                findNavController().navigate(R.id.action_albumDetail_to_detail, b)
            },
            onLongClick = { id ->
                adapter.enterSelectionModeWithItem(id) { count ->
                    updateSelectionBar(count)
                }
                updateSelectionBar(adapter.getSelectedCount())
            }
        )

        binding.recyclerMedia.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.recyclerMedia.adapter = adapter

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (adapter.isSelectionMode()) {
                    adapter.exitSelectionMode()
                    updateSelectionBar(0)
                } else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        binding.btnEditAlbum.setOnClickListener { showEditAlbumDialog() }
        binding.btnFilter.setOnClickListener { showFilterDialog() }
        binding.btnExport.setOnClickListener {
            if (adapter.isSelectionMode()) {
                val selected = adapter.getSelectedItems()
                if (selected.isNotEmpty()) {
                    createZipLauncher.launch("selected_media.zip")
                } else {
                    val albumName = viewModel.albumWithMedia.value?.album?.name ?: "Album"
                    createZipLauncher.launch("${albumName.replace(" ", "_")}.zip")
                }
            } else {
                val albumName = viewModel.albumWithMedia.value?.album?.name ?: "Album"
                createZipLauncher.launch("${albumName.replace(" ", "_")}.zip")
            }
        }
        binding.btnAddDevice.setOnClickListener {
            showAddFromLibraryDialog()
        }
        binding.btnDeleteAlbum.setOnClickListener {
            showDeleteAlbumConfirmation()
        }

        setupSelectionBar()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.albumWithMedia.collect { awm ->
                        if (awm == null) return@collect
                        binding.albumTitle.text = awm.album.name
                        binding.albumDescription.text = awm.album.description
                        binding.toolbar.title = awm.album.name
                    }
                }
                launch {
                    viewModel.filteredMedia.collect { 
                        adapter.submitList(it.map { com.hiraeth.flame.ui.library.LibraryListItem.Media(it) }) 
                    }
                }
                launch {
                    viewModel.isExporting.collect { exporting ->
                        binding.btnExport.isEnabled = !exporting
                        if (exporting) {
                            Toast.makeText(requireContext(), "Zipping album...", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                launch {
                    viewModel.availableMedia.collect { /* Keep active */ }
                }
            }
        }
    }

    private fun setupSelectionBar() {
        binding.selectionCount.text = getString(R.string.files_selected, 0)

        binding.btnSelectAll.setOnClickListener {
            adapter.selectAll()
            updateSelectionBar(adapter.getSelectedCount())
        }

        binding.btnDeselectAll.setOnClickListener {
            adapter.deselectAll()
            updateSelectionBar(0)
        }

        binding.btnMoveToAlbum.setOnClickListener {
            val selected = adapter.getSelectedItems()
            if (selected.isNotEmpty()) {
                showMoveToAlbumDialog(selected.map { it.id })
            }
        }

        binding.btnExportSelected.setOnClickListener {
            val selected = adapter.getSelectedItems()
            if (selected.isNotEmpty()) {
                createZipLauncher.launch("selected_media.zip")
            } else {
                Toast.makeText(requireContext(), getString(R.string.select_items_to_export), Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnDeleteSelected.setOnClickListener {
            val selected = adapter.getSelectedItems()
            if (selected.isNotEmpty()) {
                showMultiDeleteConfirmation(selected)
            }
        }
    }

    private fun updateSelectionBar(count: Int) {
        if (count > 0 && adapter.isSelectionMode()) {
            binding.selectionBar.visibility = View.VISIBLE
            binding.selectionCount.text = getString(R.string.files_selected, count)
        } else {
            binding.selectionBar.visibility = View.GONE
        }
    }

    private fun showMoveToAlbumDialog(mediaIds: List<Long>) {
        val albums = viewModel.albums.value
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_move_to_album, null)
        val recyclerAlbums = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler_albums)
        val btnCreateNew = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_create_new_album)

        val albumNames = albums.filter { it.album.id != albumId }.map { it.album.name }.toMutableList()
        albumNames.add(0, "None (remove from all albums)")

        val targetAlbums = albums.filter { it.album.id != albumId }

        recyclerAlbums.layoutManager = LinearLayoutManager(requireContext())
        recyclerAlbums.adapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): androidx.recyclerview.widget.RecyclerView.ViewHolder {
                val tv = android.widget.TextView(parent.context).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 120)
                    setPadding(48, 0, 48, 0)
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
                    setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                    setBackgroundColor(ContextCompat.getColor(context, R.color.bg_card))
                }
                return object : androidx.recyclerview.widget.RecyclerView.ViewHolder(tv) {}
            }

            override fun getItemCount(): Int = albumNames.size

            override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
                (holder.itemView as android.widget.TextView).text = albumNames[position]
                holder.itemView.setOnClickListener {
                    if (position == 0) {
                        // Remove from current album
                        viewModel.updateAlbumMedia(emptyList(), mediaIds)
                        adapter.exitSelectionMode()
                        updateSelectionBar(0)
                        Toast.makeText(requireContext(), "Removed from album", Toast.LENGTH_SHORT).show()
                    } else {
                        val targetAlbum = targetAlbums[position - 1]
                        viewModel.addToAlbum(targetAlbum.album.id, mediaIds)
                        // Also remove from current album
                        viewModel.updateAlbumMedia(emptyList(), mediaIds)
                        adapter.exitSelectionMode()
                        updateSelectionBar(0)
                        Toast.makeText(requireContext(), "Moved to ${targetAlbum.album.name}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.Dialog_Neon)
            .setView(dialogView)
            .create()

        btnCreateNew.setOnClickListener {
            dialog.dismiss()
            showCreateAlbumAndMoveDialog(mediaIds)
        }

        dialog.show()
    }

    private fun showCreateAlbumAndMoveDialog(mediaIds: List<Long>) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_new_album, null)
        val inputName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.album_name_input)
        val inputDesc = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.description_input)
        val btnCancel = dialogView.findViewById<View>(R.id.btn_cancel)
        val btnCreate = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_create)

        val titleText = (dialogView as? ViewGroup)?.let { findFirstTextView(it) }
        titleText?.text = "Create Album"
        btnCreate.text = "Create & Move"

        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.Dialog_Neon)
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnCreate.setOnClickListener {
            val name = inputName.text?.toString().orEmpty().trim()
            val desc = inputDesc.text?.toString().orEmpty().trim()
            if (name.isNotBlank()) {
                viewModel.createAlbum(name, desc) { newAlbumId ->
                    viewModel.addToAlbum(newAlbumId, mediaIds)
                    viewModel.updateAlbumMedia(emptyList(), mediaIds)
                    requireActivity().runOnUiThread {
                        adapter.exitSelectionMode()
                        updateSelectionBar(0)
                        Toast.makeText(requireContext(), "Created '$name' and moved items", Toast.LENGTH_SHORT).show()
                    }
                }
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showMultiDeleteConfirmation(items: List<com.hiraeth.flame.data.db.MediaEntity>) {
        MaterialAlertDialogBuilder(requireContext(), R.style.Dialog_Neon)
            .setTitle(getString(R.string.delete_selected_title, items.size))
            .setMessage(getString(R.string.delete_selected_message))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    viewModel.deleteMedia(items)
                    adapter.exitSelectionMode()
                    updateSelectionBar(0)
                    Toast.makeText(requireContext(), "Items deleted", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showAddFromLibraryDialog() {
        val allMedia = viewModel.availableMedia.value
        val albumData = viewModel.albumWithMedia.value ?: return
        
        if (allMedia.isEmpty()) {
            Toast.makeText(requireContext(), "No media in library to manage.", Toast.LENGTH_SHORT).show()
            return
        }

        val currentMediaIds = albumData.media.map { it.id }.toSet()
        val dialogBinding = DialogManageMediaBinding.inflate(layoutInflater)
        val selectionAdapter = MediaSelectionAdapter(container, currentMediaIds)
        
        dialogBinding.recyclerSelection.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = selectionAdapter
        }
        selectionAdapter.submitList(allMedia)

        MaterialAlertDialogBuilder(requireContext(), R.style.Dialog_Neon)
            .setView(dialogBinding.root)
            .setPositiveButton("Update") { _, _ ->
                val finalSelectedIds = selectionAdapter.getSelectedIds()
                val toAdd = finalSelectedIds.filter { it !in currentMediaIds }
                val toRemove = currentMediaIds.filter { it !in finalSelectedIds }
                
                if (toAdd.isNotEmpty() || toRemove.isNotEmpty()) {
                    viewModel.updateAlbumMedia(toAdd, toRemove)
                    Toast.makeText(requireContext(), "Album updated", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditAlbumDialog() {
        val awm = viewModel.albumWithMedia.value ?: return
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_new_album, null)

        val inputName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.album_name_input)
        val inputDesc = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.description_input)
        val btnCancel = dialogView.findViewById<View>(R.id.btn_cancel)
        val btnCreate = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_create)

        val titleText = (dialogView as? ViewGroup)?.let { findFirstTextView(it) }
        titleText?.text = "Edit Album"

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
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_new_album, null)

        val inputFilter = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.album_name_input)
        val inputDesc = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.description_input)
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_cancel)
        val btnApply = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_create)

        val titleText = (dialogView as? ViewGroup)?.let { findFirstTextView(it) }
        titleText?.text = "Filter Media"

        inputFilter.hint = null
        inputFilter.setText(viewModel.filter.value)

        (inputFilter.parent?.parent as? com.google.android.material.textfield.TextInputLayout)?.hint = "Anything from the album"
        (inputFilter.parent as? com.google.android.material.textfield.TextInputLayout)?.hint = "Anything from the album"

        inputDesc?.visibility = View.GONE
        (inputDesc?.parent as? View)?.visibility = View.GONE
        (inputDesc?.parent?.parent as? View)?.let { grandParent ->
            if (grandParent !is ViewGroup || grandParent.id != dialogView.id) {
                grandParent.visibility = View.GONE
            }
        }

        btnCancel.text = "Clear"
        btnApply.text = "Apply"

        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.Dialog_Neon)
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener {
            viewModel.setFilter("")
            dialog.dismiss()
        }

        btnApply.setOnClickListener {
            viewModel.setFilter(inputFilter.text?.toString().orEmpty())
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showDeleteAlbumConfirmation() {
        MaterialAlertDialogBuilder(requireContext(), R.style.Dialog_Neon)
            .setTitle(R.string.delete_album_title)
            .setMessage(R.string.delete_album_message)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                viewModel.deleteAlbum {
                    findNavController().popBackStack()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun findFirstTextView(viewGroup: ViewGroup): TextView? {
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            if (child is TextView && child !is com.google.android.material.button.MaterialButton && child !is com.google.android.material.textfield.TextInputEditText) {
                return child
            } else if (child is ViewGroup) {
                val found = findFirstTextView(child)
                if (found != null) return found
            }
        }
        return null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
