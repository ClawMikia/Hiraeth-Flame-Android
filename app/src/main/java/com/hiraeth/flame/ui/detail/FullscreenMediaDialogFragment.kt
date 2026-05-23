package com.hiraeth.flame.ui.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.DialogFragment
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import coil.load
import com.hiraeth.flame.databinding.FragmentMediaFullscreenBinding
import java.io.File

class FullscreenMediaDialogFragment : DialogFragment() {

    private var _binding: FragmentMediaFullscreenBinding? = null
    private val binding get() = _binding!!

    private var player: ExoPlayer? = null
    private var isVideo: Boolean = false
    private var mediaFile: File? = null

    companion object {
        private const val ARG_FILE = "file"
        private const val ARG_IS_VIDEO = "is_video"

        fun newInstance(file: File, isVideo: Boolean): FullscreenMediaDialogFragment {
            return FullscreenMediaDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_FILE, file.absolutePath)
                    putBoolean(ARG_IS_VIDEO, isVideo)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Set fullscreen style for dialog
        setStyle(STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMediaFullscreenBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val filePath = arguments?.getString(ARG_FILE) ?: return
        isVideo = arguments?.getBoolean(ARG_IS_VIDEO) ?: false
        mediaFile = File(filePath)

        // Hide system UI for immersive fullscreen
        hideSystemUI()

        // Close button listener
        binding.btnCloseFullscreen.setOnClickListener {
            dismiss()
        }

        // Load media
        if (isVideo) {
            binding.fullscreenPlayerView.visibility = View.VISIBLE
            binding.fullscreenImageView.visibility = View.GONE

            player = ExoPlayer.Builder(requireContext()).build().also { exo ->
                exo.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(mediaFile)))
                exo.prepare()
                binding.fullscreenPlayerView.player = exo
            }
        } else {
            binding.fullscreenImageView.visibility = View.VISIBLE
            binding.fullscreenPlayerView.visibility = View.GONE

            binding.fullscreenImageView.load(mediaFile) { crossfade(true) }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { dialogWindow ->
            // Force the dialog's window container layout to use complete screen real estate
            dialogWindow.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
            // Properly configure navigation bar color to solid black
            dialogWindow.navigationBarColor = ContextCompat.getColor(requireContext(), android.R.color.black)
        }
    }

    private fun hideSystemUI() {
        dialog?.window?.let { dialogWindow ->
            val windowInsetsController = WindowCompat.getInsetsController(dialogWindow, dialogWindow.decorView)
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
            windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun showSystemUI() {
        dialog?.window?.let { dialogWindow ->
            val windowInsetsController = WindowCompat.getInsetsController(dialogWindow, dialogWindow.decorView)
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        showSystemUI()
        releasePlayer()
        _binding = null
    }

    private fun releasePlayer() {
        binding.fullscreenPlayerView.player = null
        player?.release()
        player = null
    }
}
