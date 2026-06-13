package com.hiraeth.flame.ui.mediaimport

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import coil.load
import com.hiraeth.flame.R
import com.hiraeth.flame.databinding.FragmentImportBinding
import kotlinx.coroutines.launch

class ImportFragment : Fragment() {

    private var _binding: FragmentImportBinding? = null
    private val binding get() = _binding!!

    private val container get() = (requireActivity().application as com.hiraeth.flame.HiraethApplication).container

    private val viewModel: ImportPreviewViewModel by viewModels {
        ImportPreviewViewModel.factory(container.mediaRepository)
    }

    private var pickedUris: List<Uri> = emptyList()

    private val pickLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        pickedUris = uris
        if (uris.isNotEmpty()) {
            val firstUri = uris.first()
            binding.preview.load(firstUri) { crossfade(true) }
            
            val count = uris.size
            if (count > 1) {
                binding.detectLabel.text = getString(R.string.files_selected_count, count)
                binding.titleInput.isEnabled = false
                binding.titleInput.setText("Multiple Files")
            } else {
                val type = requireContext().contentResolver.getType(firstUri).orEmpty()
                val isVideo = type.startsWith("video/")
                binding.detectLabel.text = if (isVideo) "Detected: video" else "Detected: image"
                binding.titleInput.isEnabled = true
                binding.titleInput.setText(firstUri.lastPathSegment ?: "")
            }
            binding.errorText.visibility = View.GONE
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentImportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolbar.setupWithNavController(findNavController())

        binding.btnPick.setOnClickListener { pickLauncher.launch("*/*") }

        binding.btnSave.setOnClickListener {
            val uris = pickedUris
            val title = binding.titleInput.text?.toString()?.trim().orEmpty()
            val description = binding.descInput.text?.toString()?.trim().orEmpty()

            if (uris.isEmpty()) {
                binding.errorText.visibility = View.VISIBLE
                binding.errorText.text = "Please select at least one image or video file."
                Toast.makeText(requireContext(), "Save failed: No file selected", Toast.LENGTH_SHORT).show()
            } else if (uris.size == 1 && title.isEmpty()) {
                binding.errorText.visibility = View.VISIBLE
                binding.errorText.text = "Please ensure title is filled."
                Toast.makeText(requireContext(), "Save failed: Title cannot be empty", Toast.LENGTH_SHORT).show()
            } else {
                binding.errorText.visibility = View.GONE

                if (uris.size == 1) {
                    val uri = uris.first()
                    val type = requireContext().contentResolver.getType(uri).orEmpty()
                    val isVideo = type.startsWith("video/")
                    viewModel.import(
                        uri = uri,
                        displayName = title,
                        description = description,
                        isVideo = isVideo,
                        onImported = { id ->
                            findNavController().navigate(
                                R.id.action_import_to_detail,
                                bundleOf("mediaId" to id, "albumId" to -1L),
                            )
                        }
                    )
                } else {
                    val items = uris.map { uri ->
                        val type = requireContext().contentResolver.getType(uri).orEmpty()
                        uri to type.startsWith("video/")
                    }
                    viewModel.importMultiple(items, description) {
                        Toast.makeText(requireContext(), "Import complete!", Toast.LENGTH_LONG).show()
                        findNavController().popBackStack()
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.busy.collect { busy ->
                        binding.progress.visibility = if (busy) View.VISIBLE else View.GONE
                        binding.btnSave.isEnabled = !busy
                        binding.btnPick.isEnabled = !busy
                    }
                }
                launch {
                    viewModel.error.collect { err ->
                        if (err != null) {
                            binding.errorText.visibility = View.VISIBLE
                            binding.errorText.text = err
                        } else {
                            binding.errorText.visibility = View.GONE
                        }
                    }
                }
                launch {
                    viewModel.progressCount.collect { count ->
                        if (viewModel.busy.value && viewModel.totalCount.value > 1) {
                            binding.detectLabel.text = "Importing $count / ${viewModel.totalCount.value}..."
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
