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
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.CompositePageTransformer
import androidx.viewpager2.widget.MarginPageTransformer
import androidx.viewpager2.widget.ViewPager2
import com.hiraeth.flame.databinding.FragmentMediaFullscreenBinding
import com.hiraeth.flame.di.AppContainer
import kotlinx.coroutines.launch

class FullscreenMediaDialogFragment : DialogFragment() {

    private var _binding: FragmentMediaFullscreenBinding? = null
    private val binding get() = _binding!!

    private val container: AppContainer get() = (requireActivity().application as com.hiraeth.flame.HiraethApplication).container

    private val initialMediaId: Long get() = requireArguments().getLong(ARG_MEDIA_ID)
    private val albumId: Long get() = requireArguments().getLong(ARG_ALBUM_ID, -1L)

    private val viewModel: MediaDetailViewModel by viewModels {
        MediaDetailViewModel.factory(container.mediaRepository, container.albumRepository, initialMediaId, albumId)
    }

    private lateinit var pagerAdapter: MediaPagerAdapter
    private var isInitialJumpDone = false

    companion object {
        private const val ARG_MEDIA_ID = "media_id"
        private const val ARG_ALBUM_ID = "album_id"

        fun newInstance(mediaId: Long, albumId: Long = -1L): FullscreenMediaDialogFragment {
            return FullscreenMediaDialogFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_MEDIA_ID, mediaId)
                    putLong(ARG_ALBUM_ID, albumId)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        hideSystemUI()

        pagerAdapter = MediaPagerAdapter(container)
        binding.fullscreenViewPager.adapter = pagerAdapter

        // Apply "soft" transitions for premium feel
        val transformer = CompositePageTransformer().apply {
            addTransformer(MarginPageTransformer(40))
            addTransformer { page, position ->
                val r = 1 - Math.abs(position)
                page.scaleY = 0.92f + r * 0.08f
                page.alpha = 0.6f + r * 0.4f
            }
        }
        binding.fullscreenViewPager.setPageTransformer(transformer)

        binding.fullscreenViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                pagerAdapter.playVideo(position, binding.fullscreenViewPager.getChildAt(0) as androidx.recyclerview.widget.RecyclerView)
                updateUIForPosition(position)
            }
        })

        binding.btnPrevFullscreen.setOnClickListener {
            val current = binding.fullscreenViewPager.currentItem
            if (current > 0) binding.fullscreenViewPager.setCurrentItem(current - 1, true)
        }

        binding.btnNextFullscreen.setOnClickListener {
            val current = binding.fullscreenViewPager.currentItem
            if (current < pagerAdapter.itemCount - 1) binding.fullscreenViewPager.setCurrentItem(current + 1, true)
        }

        binding.btnCloseFullscreen.setOnClickListener {
            dismiss()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.navigationItems.collect { items ->
                    pagerAdapter.submitList(items) {
                        if (!isInitialJumpDone) {
                            val index = items.indexOfFirst { it.id == initialMediaId }
                            if (index != -1) {
                                binding.fullscreenViewPager.setCurrentItem(index, false)
                                isInitialJumpDone = true
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { dialogWindow ->
            dialogWindow.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
            dialogWindow.navigationBarColor = ContextCompat.getColor(requireContext(), android.R.color.black)
        }
    }

    private fun updateUIForPosition(position: Int) {
        val items = pagerAdapter.currentList
        if (items.isEmpty()) return

        val item = items[position]
        binding.tvFullscreenTitle.text = item.displayName
        binding.tvFullscreenSubtitle.text = getString(com.hiraeth.flame.R.string.media_index_format, position + 1, items.size)

        binding.btnPrevFullscreen.visibility = if (position > 0) View.VISIBLE else View.INVISIBLE
        binding.btnNextFullscreen.visibility = if (position < items.size - 1) View.VISIBLE else View.INVISIBLE
        
        viewModel.setCurrentId(item.id)
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
        pagerAdapter.releasePlayer()
        _binding = null
    }
}
