package com.hiraeth.flame.ui.camera

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.hiraeth.flame.databinding.DialogCaptureDetailsBinding

class CaptureDetailsDialogFragment : DialogFragment() {

    private var _binding: DialogCaptureDetailsBinding? = null
    private val binding get() = _binding!!

    private var onSavedListener: ((String, String) -> Unit)? = null
    private var onCancelledListener: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Restores custom title bar properties and uses clear window backdrops
        setStyle(STYLE_NO_TITLE, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogCaptureDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.setCanceledOnTouchOutside(false)

        binding.btnSave.setOnClickListener {
            val title = binding.titleInput.text?.toString()?.trim().orEmpty()
            val description = binding.descInput.text?.toString()?.trim().orEmpty()

            if (title.isNotEmpty() && description.isNotEmpty()) {
                onSavedListener?.invoke(title, description)
                dismiss()
            } else {
                Toast.makeText(requireContext(), "Save failed: All details cannot be empty", Toast.LENGTH_LONG).show()
            }
        }

        binding.btnCancel.setOnClickListener {
            onCancelledListener?.invoke()
            dismiss()
        }
    }

    // ✅ THE STRUCTURAL FIX: Resizes layout boundaries safely inside the view lifecycle window calculations
    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            val displayMetrics = resources.displayMetrics
            // Dynamically calculate exactly 90% of the physical screen width space
            val targetWidth = (displayMetrics.widthPixels * 0.90).toInt()

            // Apply parameters without affecting wrap_content layout heights
            window.setLayout(targetWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    fun setListeners(onSaved: (String, String) -> Unit, onCancelled: () -> Unit) {
        this.onSavedListener = onSaved
        this.onCancelledListener = onCancelled
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = CaptureDetailsDialogFragment()
    }
}
