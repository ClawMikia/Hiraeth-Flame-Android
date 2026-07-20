package com.hiraeth.flame.ui.library

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.Bundle
import android.util.Log
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
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
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hiraeth.flame.R
import com.hiraeth.flame.databinding.FragmentLibraryBinding
import com.hiraeth.flame.domain.LibrarySort
import com.hiraeth.flame.domain.LibraryViewMode
import com.hiraeth.flame.domain.MediaTypeFilter
import com.hiraeth.flame.ui.util.AppPermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.ceil
import kotlin.math.sqrt

class LibraryFragment : Fragment() {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!

    private val container get() = (requireActivity().application as com.hiraeth.flame.HiraethApplication).container

    private val viewModel: LibraryViewModel by viewModels {
        LibraryViewModel.factory(container.mediaRepository, container.albumRepository)
    }

    private lateinit var adapter: MediaLibraryAdapter

    private var targetCombineCount = 0

    private val createZipLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            val selected = adapter.getSelectedItems()
            exportSelectedToZip(selected, uri)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = MediaLibraryAdapter(
            container = container,
            gridMode = viewModel.viewModeState.value == LibraryViewMode.Grid,
            onItemClick = { id ->
                val b = Bundle().apply { 
                    putLong("mediaId", id)
                    putLong("albumId", -1L)
                }
                findNavController().navigate(R.id.action_library_to_detail, b)
            },
            onHeaderClick = { albumId ->
                viewModel.toggleAlbumExpanded(albumId)
            },
            onLongClick = { id ->
                adapter.enterSelectionModeWithItem(id) { count ->
                    updateSelectionBar(count)
                }
                updateSelectionBar(adapter.getSelectedCount())
            }
        )
        binding.recycler.adapter = adapter
        applyLayoutManager()

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

        val sortLabels = LibrarySort.entries.map { it.name.replace(Regex("([a-z])([A-Z])"), "$1 $2") }
        binding.sortSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, sortLabels)
        binding.sortSpinner.setSelection(LibrarySort.entries.indexOf(LibrarySort.DateNewest))
        binding.sortSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, position: Int, id: Long) {
                viewModel.setSort(LibrarySort.entries[position])
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.setQuery(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.tagFilter.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.setTagFilter(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.filterAll.setOnClickListener { viewModel.setTypeFilter(MediaTypeFilter.All) }
        binding.filterPhotos.setOnClickListener { viewModel.setTypeFilter(MediaTypeFilter.ImagesOnly) }
        binding.filterVideos.setOnClickListener { viewModel.setTypeFilter(MediaTypeFilter.VideosOnly) }

        binding.fabImport.setOnClickListener {
            if (hasAllPermissions()) {
                findNavController().navigate(R.id.action_library_to_import)
            } else {
                (activity as? com.hiraeth.flame.MainActivity)?.requestAppPermissions()
            }
        }

        binding.btnGrantPermissions.setOnClickListener {
            (activity as? com.hiraeth.flame.MainActivity)?.requestAppPermissions()
        }

        val navController = findNavController()
        val appBarConfig = AppBarConfiguration(
            setOf(R.id.libraryFragment, R.id.cameraFragment, R.id.albumsFragment),
        )
        binding.toolbar.setupWithNavController(navController, appBarConfig)
        binding.toolbar.inflateMenu(R.menu.menu_library)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_quick_import -> {
                    Toast.makeText(requireContext(), "Use FAB or Album import", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.action_toggle_view -> {
                    viewModel.toggleViewMode()
                    true
                }
                R.id.action_reel -> {
                    findNavController().navigate(R.id.action_library_to_reel)
                    true
                }
                R.id.action_combine -> {
                    showCombineDialog()
                    true
                }
                R.id.action_delete_multiple -> {
                    if (adapter.isSelectionMode()) {
                        val selected = adapter.getSelectedItems()
                        if (selected.isNotEmpty()) {
                            showMultiDeleteConfirmation(selected)
                        } else {
                            adapter.exitSelectionMode()
                            updateSelectionBar(0)
                        }
                    } else {
                        adapter.enterSelectionMode { count ->
                            updateSelectionBar(count)
                        }
                        Toast.makeText(requireContext(), "Select items then click Delete again", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                else -> false
            }
        }

        setupSelectionBar()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.items.collect { adapter.submitList(it) }
                }
                launch {
                    viewModel.viewModeState.collect { mode ->
                        val grid = mode == LibraryViewMode.Grid
                        adapter.setGridMode(grid)
                        applyLayoutManager()
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                updatePermissionUi()
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
            binding.fabImport.visibility = View.GONE
        } else {
            binding.selectionBar.visibility = View.GONE
            binding.fabImport.visibility = View.VISIBLE
        }
    }

    private fun showMoveToAlbumDialog(mediaIds: List<Long>) {
        val albums = viewModel.albums.value
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_move_to_album, null)
        val recyclerAlbums = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler_albums)
        val btnCreateNew = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_create_new_album)

        val albumNames = albums.map { it.album.name }.toMutableList()
        albumNames.add(0, "None (remove from all albums)")

        recyclerAlbums.layoutManager = LinearLayoutManager(requireContext())
        recyclerAlbums.adapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): androidx.recyclerview.widget.RecyclerView.ViewHolder {
                val tv = android.widget.TextView(parent.context).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 120)
                    setPadding(48, 0, 48, 0)
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
                    setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                    background = android.util.TypedValue().let { tv ->
                        android.graphics.drawable.ColorDrawable().apply {
                            color = ContextCompat.getColor(context, R.color.bg_card)
                        }
                    }
                }
                return object : androidx.recyclerview.widget.RecyclerView.ViewHolder(tv) {}
            }

            override fun getItemCount(): Int = albumNames.size

            override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
                (holder.itemView as android.widget.TextView).text = albumNames[position]
                holder.itemView.setOnClickListener {
                    if (position == 0) {
                        // "None" option - remove from all albums
                        mediaIds.forEach { mediaId ->
                            albums.forEach { awm ->
                                if (awm.media.any { it.id == mediaId }) {
                                    viewModel.addToAlbum(awm.album.id, emptyList())
                                }
                            }
                        }
                        adapter.exitSelectionMode()
                        updateSelectionBar(0)
                        Toast.makeText(requireContext(), "Removed from all albums", Toast.LENGTH_SHORT).show()
                    } else {
                        val targetAlbum = albums[position - 1]
                        viewModel.addToAlbum(targetAlbum.album.id, mediaIds)
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
                viewModel.createAlbum(name, desc) { albumId ->
                    viewModel.addToAlbum(albumId, mediaIds)
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

    private fun exportSelectedToZip(selected: List<com.hiraeth.flame.data.db.MediaEntity>, destUri: android.net.Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            Toast.makeText(requireContext(), getString(R.string.exporting), Toast.LENGTH_SHORT).show()
            val success = withContext(Dispatchers.IO) {
                try {
                    requireContext().contentResolver.openOutputStream(destUri)?.use { os ->
                        ZipOutputStream(BufferedOutputStream(os)).use { zos ->
                            selected.forEach { media ->
                                val file = container.mediaRepository.resolveFile(media)
                                if (file.exists()) {
                                    val entry = ZipEntry(file.name)
                                    zos.putNextEntry(entry)
                                    file.inputStream().use { input -> input.copyTo(zos) }
                                    zos.closeEntry()
                                }
                            }
                        }
                    }
                    true
                } catch (e: Exception) {
                    Log.e("LibraryFragment", "Zip export failed", e)
                    false
                }
            }
            if (success) {
                adapter.exitSelectionMode()
                updateSelectionBar(0)
                Toast.makeText(requireContext(), getString(R.string.export_complete), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), getString(R.string.export_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showMultiDeleteConfirmation(items: List<com.hiraeth.flame.data.db.MediaEntity>) {
        MaterialAlertDialogBuilder(requireContext(), R.style.Dialog_Neon)
            .setTitle(getString(R.string.delete_selected_title, items.size))
            .setMessage(getString(R.string.delete_selected_message))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    viewModel.deleteAll(items)
                    adapter.exitSelectionMode()
                    updateSelectionBar(0)
                    Toast.makeText(requireContext(), "Items deleted", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showCombineDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_combine_images, null)
        val input = dialogView.findViewById<EditText>(R.id.combine_count_input)
        input.hint = "e.g. 3"

        MaterialAlertDialogBuilder(requireContext(), R.style.Dialog_Neon)
            .setTitle("Combine Images")
            .setMessage("How many images do you want to combine?")
            .setView(dialogView)
            .setPositiveButton("Select") { _, _ ->
                val count = input.text.toString().toIntOrNull() ?: 0
                if (count > 1) {
                    startSelectionMode(count)
                } else {
                    Toast.makeText(requireContext(), "Enter a number > 1", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startSelectionMode(count: Int) {
        targetCombineCount = count
        Toast.makeText(requireContext(), "Select $count images to combine", Toast.LENGTH_LONG).show()
        adapter.enterSelectionMode { selectedCount ->
            updateSelectionBar(selectedCount)
            if (selectedCount == targetCombineCount) {
                combineSelectedImages()
            }
        }
    }

    private fun combineSelectedImages() {
        val selected = adapter.getSelectedItems()
        adapter.exitSelectionMode()
        updateSelectionBar(0)
        
        if (selected.any { it.isVideo }) {
            Toast.makeText(requireContext(), "Only images can be combined", Toast.LENGTH_SHORT).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            Toast.makeText(requireContext(), "Combining images...", Toast.LENGTH_SHORT).show()
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    val bitmaps = selected.map { entity ->
                        val file = container.mediaRepository.resolveFile(entity)
                        BitmapFactory.decodeFile(file.absolutePath)
                    }.filterNotNull()

                    if (bitmaps.isEmpty()) return@withContext null

                    val n = bitmaps.size
                    val cols = ceil(sqrt(n.toDouble())).toInt()
                    val rows = ceil(n.toDouble() / cols).toInt()

                    val cellWidth = bitmaps.maxOf { it.width }
                    val cellHeight = bitmaps.maxOf { it.height }
                    
                    val totalWidth = cellWidth * cols
                    val totalHeight = cellHeight * rows
                    
                    val result = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(result)
                    canvas.drawColor(android.graphics.Color.BLACK)

                    for (i in bitmaps.indices) {
                        val r = i / cols
                        val c = i % cols
                        val b = bitmaps[i]
                        
                        val left = c * cellWidth.toFloat()
                        val top = r * cellHeight.toFloat()
                        
                        val matrix = android.graphics.Matrix()
                        val scale: Float
                        var dx = 0f
                        var dy = 0f

                        if (b.width * cellHeight > cellWidth * b.height) {
                            scale = cellHeight.toFloat() / b.height.toFloat()
                            dx = (cellWidth - b.width * scale) * 0.5f
                        } else {
                            scale = cellWidth.toFloat() / b.width.toFloat()
                            dy = (cellHeight - b.height * scale) * 0.5f
                        }

                        matrix.setScale(scale, scale)
                        matrix.postTranslate(left + dx, top + dy)
                        
                        canvas.save()
                        canvas.clipRect(left, top, left + cellWidth, top + cellHeight)
                        canvas.drawBitmap(b, matrix, null)
                        canvas.restore()
                    }
                    result
                }

                if (bitmap != null) {
                    container.mediaRepository.saveBitmapAsMedia(
                        bitmap, 
                        "Combined Image", 
                        "Created by combining ${selected.size} images"
                    )
                    Toast.makeText(requireContext(), "Image combined and saved!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("LibraryFragment", "Failed to combine images", e)
                Toast.makeText(requireContext(), "Failed to combine images", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun applyLayoutManager() {
        val grid = viewModel.viewModeState.value == LibraryViewMode.Grid
        if (grid) {
            val gm = GridLayoutManager(requireContext(), 3)
            gm.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    return if (adapter.getItemViewType(position) == 0) 3 else 1 // 0 is TYPE_HEADER
                }
            }
            binding.recycler.layoutManager = gm
        } else {
            binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun findFirstTextView(viewGroup: ViewGroup): android.widget.TextView? {
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            if (child is android.widget.TextView && child !is com.google.android.material.button.MaterialButton && child !is com.google.android.material.textfield.TextInputEditText) {
                return child
            } else if (child is ViewGroup) {
                val found = findFirstTextView(child)
                if (found != null) return found
            }
        }
        return null
    }

    private fun hasAllPermissions(): Boolean =
        AppPermissions.requiredPermissions().all {
            ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
        }

    private fun updatePermissionUi() {
        val ok = hasAllPermissions()
        binding.btnGrantPermissions.visibility = if (ok) View.GONE else View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        updatePermissionUi()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
