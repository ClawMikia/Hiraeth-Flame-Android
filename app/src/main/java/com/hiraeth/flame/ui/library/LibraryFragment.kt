package com.hiraeth.flame.ui.library

import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
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
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.hiraeth.flame.R
import com.hiraeth.flame.databinding.FragmentLibraryBinding
import com.hiraeth.flame.domain.LibrarySort
import com.hiraeth.flame.domain.LibraryViewMode
import com.hiraeth.flame.domain.MediaTypeFilter
import com.hiraeth.flame.ui.util.AppPermissions
import com.hiraeth.flame.ui.util.DriveServiceHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections

class LibraryFragment : Fragment() {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!

    private val container get() = (requireActivity().application as com.hiraeth.flame.HiraethApplication).container

    private val viewModel: LibraryViewModel by viewModels {
        LibraryViewModel.factory(container.mediaRepository)
    }

    private lateinit var adapter: MediaLibraryAdapter

    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            if (account != null) {
                // Check if Drive scope was actually granted
                if (GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_FILE))) {
                    startCloudBackup(account.email ?: "User")
                } else {
                    // Re-request specifically with Drive scope
                    GoogleSignIn.requestPermissions(
                        this,
                        1001, // arbitrary code, but we are using the new API launcher mostly
                        account,
                        Scope(DriveScopes.DRIVE_FILE)
                    )
                }
            }
        } catch (e: com.google.android.gms.common.api.ApiException) {
            val message = when (e.statusCode) {
                com.google.android.gms.common.api.CommonStatusCodes.NETWORK_ERROR -> "Network error. Check connection."
                com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes.SIGN_IN_CANCELLED -> "Sign-in cancelled."
                com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes.SIGN_IN_FAILED -> "Sign-in failed. Check developer console setup."
                else -> "Error code: ${e.statusCode}. Ensure SHA-1 is registered."
            }
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
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
            }
        )
        binding.recycler.adapter = adapter
        applyLayoutManager()

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
                R.id.action_toggle_view -> {
                    viewModel.toggleViewMode()
                    true
                }
                R.id.action_reel -> {
                    findNavController().navigate(R.id.action_library_to_reel)
                    true
                }
                R.id.action_cloud_backup -> {
                    showBackupConfirmation()
                    true
                }
                else -> false
            }
        }

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

    private fun showBackupConfirmation() {
        MaterialAlertDialogBuilder(requireContext(), R.style.Dialog_Neon)
            .setTitle("Backup to Google Drive")
            .setMessage("This will sync your entire media library, organized by albums, to your Google Drive account.")
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton("      SYNC      ") { _, _ ->
                requestGoogleSignIn()
            }
            .show()
    }

    private fun requestGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()

        val client = GoogleSignIn.getClient(requireActivity(), gso)
        googleSignInLauncher.launch(client.signInIntent)
    }

    @Suppress("DEPRECATION")
    private fun startCloudBackup(email: String) {
        val account = GoogleSignIn.getLastSignedInAccount(requireContext()) ?: return
        val credential = GoogleAccountCredential.usingOAuth2(
            requireContext(), Collections.singleton(DriveScopes.DRIVE_FILE)
        ).apply {
            selectedAccount = account.account
        }

        val driveService = Drive.Builder(
            AndroidHttp.newCompatibleTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("Hiraeth Flame").build()

        val helper = DriveServiceHelper(driveService)

        viewLifecycleOwner.lifecycleScope.launch {
            Toast.makeText(requireContext(), "Starting backup to Google Drive...", Toast.LENGTH_SHORT).show()
            try {
                val username = email.substringBefore("@")
                val rootFolderName = "Hiraeth Flame of $username"
                
                withContext(Dispatchers.IO) {
                    var rootId = helper.findFolder(rootFolderName)
                    if (rootId == null) {
                        rootId = helper.createFolder(rootFolderName)
                    }
                    if (rootId == null) throw Exception("Could not create root folder")

                    val albums = container.albumRepository.observeAlbums().first()
                    val allMedia = container.mediaRepository.observeAll().first()

                    for (albumWithMedia in albums) {
                        val albumFolderId = helper.createFolder(albumWithMedia.album.name, rootId)
                        for (media in albumWithMedia.media) {
                            val localFile = container.mediaRepository.resolveFile(media)
                            if (localFile.exists()) {
                                helper.uploadFile(localFile, media.mimeType, albumFolderId)
                            }
                        }
                    }

                    val mediaInAlbums = albums.asSequence().flatMap { it.media }.map { it.id }.toSet()
                    val looseMedia = allMedia.filter { it.id !in mediaInAlbums }
                    if (looseMedia.isNotEmpty()) {
                        val looseFolderId = helper.createFolder("Unorganized", rootId)
                        for (media in looseMedia) {
                            val localFile = container.mediaRepository.resolveFile(media)
                            if (localFile.exists()) {
                                helper.uploadFile(localFile, media.mimeType, looseFolderId)
                            }
                        }
                    }
                }
                Toast.makeText(requireContext(), "Backup complete!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Backup failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun applyLayoutManager() {
        val grid = viewModel.viewModeState.value == LibraryViewMode.Grid
        binding.recycler.layoutManager = if (grid) {
            GridLayoutManager(requireContext(), 3)
        } else {
            LinearLayoutManager(requireContext())
        }
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
