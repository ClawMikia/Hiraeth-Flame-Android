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

    private var pickedUri: Uri? = null
    private var isVideo: Boolean = false

    private val pickLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pickedUri = uri
        if (uri != null) {
            val type = requireContext().contentResolver.getType(uri).orEmpty()
            isVideo = type.startsWith("video/")
            binding.preview.load(uri) { crossfade(true) }

            binding.detectLabel.text = if (isVideo) "Detected: video" else "Detected: image"
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
            val uri = pickedUri
            val title = binding.titleInput.text?.toString()?.trim().orEmpty()
            val description = binding.descInput.text?.toString()?.trim().orEmpty()

            if (uri == null) {
                binding.errorText.visibility = View.VISIBLE
                binding.errorText.text = "Please select an image or video file first."
                Toast.makeText(requireContext(), "Save failed: No file selected", Toast.LENGTH_SHORT).show()
            } else if (title.isEmpty() || description.isEmpty()) {
                binding.errorText.visibility = View.VISIBLE
                binding.errorText.text = "Please ensure both title and description are filled."
                if (title.isEmpty()) {
                    binding.titleInput.requestFocus()
                } else {
                    binding.descInput.requestFocus()
                }
                Toast.makeText(requireContext(), "Save failed: All details cannot be empty", Toast.LENGTH_SHORT).show()
            } else {
                binding.errorText.visibility = View.GONE

                Toast.makeText(requireContext(), "Changes saved successfully", Toast.LENGTH_SHORT).show()

                // ✅ FIXED: Parameter list order matches the updated ViewModel implementation signature
                viewModel.import(
                    uri = uri,
                    displayName = title,
                    description = description,
                    isVideo = isVideo,
                    onImported = { id ->
                        findNavController().navigate(
                            R.id.action_import_to_detail,
                            bundleOf(
                                "mediaId" to id,
                                "albumId" to -1L
                            ),
                        )
                    }
                )
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.busy.collect { busy ->
                        binding.progress.visibility = if (busy) View.VISIBLE else View.GONE
                        binding.btnSave.isEnabled = !busy
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
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
