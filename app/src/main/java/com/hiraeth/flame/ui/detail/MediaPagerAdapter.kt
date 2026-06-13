package com.hiraeth.flame.ui.detail

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.hiraeth.flame.data.db.MediaEntity
import com.hiraeth.flame.databinding.ItemMediaPagerBinding
import com.hiraeth.flame.di.AppContainer

class MediaPagerAdapter(
    private val container: AppContainer
) : ListAdapter<MediaEntity, MediaPagerAdapter.MediaVH>(DIFF) {

    private var activePlayer: ExoPlayer? = null
    private var activePlayerView: androidx.media3.ui.PlayerView? = null

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<MediaEntity>() {
            override fun areItemsTheSame(old: MediaEntity, new: MediaEntity) = old.id == new.id
            override fun areContentsTheSame(old: MediaEntity, new: MediaEntity) = old == new
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaVH {
        val binding = ItemMediaPagerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MediaVH(binding)
    }

    override fun onBindViewHolder(holder: MediaVH, position: Int) {
        val item = getItem(position)
        val file = container.mediaStorage.resolveRelative(item.relativePath)

        if (item.isVideo) {
            holder.binding.imageView.visibility = View.VISIBLE // Show thumb first
            holder.binding.imageView.load(file)
            holder.binding.playerView.visibility = View.GONE
            holder.binding.playerView.player = null
        } else {
            holder.binding.playerView.visibility = View.GONE
            holder.binding.playerView.player = null
            holder.binding.imageView.visibility = View.VISIBLE
            holder.binding.imageView.load(file) { crossfade(true) }
            
            // PhotoView handles its own internal zoom. 
            // ViewPager2 will work correctly as long as we don't interfere with touch events.
        }
    }

    fun playVideo(position: Int, recyclerView: RecyclerView) {
        releasePlayer()
        val item = getItem(position)
        if (!item.isVideo) return

        val holder = recyclerView.findViewHolderForAdapterPosition(position) as? MediaVH ?: return
        val file = container.mediaStorage.resolveRelative(item.relativePath)

        holder.binding.imageView.visibility = View.GONE
        holder.binding.playerView.visibility = View.VISIBLE

        val player = ExoPlayer.Builder(holder.itemView.context).build().also { exo ->
            exo.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(file)))
            exo.prepare()
            exo.playWhenReady = false
            holder.binding.playerView.player = exo
        }
        activePlayer = player
        activePlayerView = holder.binding.playerView
    }

    fun releasePlayer() {
        activePlayer?.release()
        activePlayer = null
        activePlayerView?.player = null
        activePlayerView = null
    }

    class MediaVH(val binding: ItemMediaPagerBinding) : RecyclerView.ViewHolder(binding.root)
}
