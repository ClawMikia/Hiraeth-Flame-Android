package com.hiraeth.flame.ui.albums

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import coil.load
import com.hiraeth.flame.data.db.AlbumWithMedia
import com.hiraeth.flame.databinding.ItemAlbumBinding
import com.hiraeth.flame.di.AppContainer

class AlbumsAdapter(
    private val container: AppContainer,
    private val onAlbumClick: (Long) -> Unit,
) : ListAdapter<AlbumWithMedia, AlbumsAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<AlbumWithMedia>() {
            override fun areItemsTheSame(old: AlbumWithMedia, new: AlbumWithMedia) =
                old.album.id == new.album.id
            override fun areContentsTheSame(old: AlbumWithMedia, new: AlbumWithMedia) =
                old == new
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemAlbumBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = getItem(position)
        val binding = holder.binding
        binding.albumName.text = row.album.name
        binding.albumDescription.text = row.album.description
        val count = row.media.size
        binding.albumCount.text = if (count == 1) "1 item" else "$count items"

        binding.clickOverlay.setOnClickListener { onAlbumClick(row.album.id) }

        binding.thumbRow.removeAllViews()
        val d = holder.itemView.resources.displayMetrics.density
        val side = (68 * d).toInt()
        val cornerPx = (8 * d).toInt()
        val margin = (6 * d).toInt()
        for (m in row.media.take(5)) {
            val iv = ImageView(holder.itemView.context).apply {
                layoutParams = LinearLayout.LayoutParams(side, side).apply { marginEnd = margin }
                scaleType = ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
                outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
                val bg = android.graphics.drawable.GradientDrawable()
                bg.cornerRadius = cornerPx.toFloat()
                background = bg
            }
            iv.load(container.mediaStorage.resolveRelative(m.relativePath)) { crossfade(200) }
            // Removed individual media click to make the whole item clickable for album navigation
            binding.thumbRow.addView(iv)
        }
    }

    class VH(val binding: ItemAlbumBinding) : androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root)
}
