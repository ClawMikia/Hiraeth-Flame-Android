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
    private var activePosition: Int = -1
    private var recyclerView: RecyclerView? = null
    private val rotations = mutableMapOf<Int, Float>()

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<MediaEntity>() {
            override fun areItemsTheSame(old: MediaEntity, new: MediaEntity) = old.id == new.id
            override fun areContentsTheSame(old: MediaEntity, new: MediaEntity) = old == new
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        this.recyclerView = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaVH {
        val binding = ItemMediaPagerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MediaVH(binding)
    }

    fun rotate(position: Int, recyclerView: RecyclerView) {
        val currentRotation = rotations.getOrDefault(position, 0f)
        val newRotation = (currentRotation + 90f) % 360f
        rotations[position] = newRotation
        
        val holder = recyclerView.findViewHolderForAdapterPosition(position) as? MediaVH ?: return
        holder.binding.imageView.rotation = newRotation
    }

    override fun onBindViewHolder(holder: MediaVH, position: Int) {
        val item = getItem(position)
        val file = container.mediaStorage.resolveRelative(item.relativePath)
        
        holder.binding.imageView.rotation = rotations.getOrDefault(position, 0f)

        if (item.isVideo) {
            if (position == activePosition) {
                holder.binding.imageView.visibility = View.GONE
                holder.binding.playerView.visibility = View.VISIBLE
                // Trigger play again to ensure it's loaded in THIS holder
                holder.itemView.post { 
                    recyclerView?.let { playVideo(position, it) }
                }
            } else {
                holder.binding.imageView.visibility = View.VISIBLE
                holder.binding.imageView.load(file)
                holder.binding.playerView.visibility = View.GONE
                holder.binding.playerView.player = null
            }
        } else {
            holder.binding.playerView.visibility = View.GONE
            holder.binding.playerView.player = null
            holder.binding.imageView.visibility = View.VISIBLE
            holder.binding.imageView.load(file) { crossfade(true) }
        }
    }

    fun playVideo(position: Int, recyclerView: RecyclerView) {
        activePosition = position
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
            exo.playWhenReady = true // Auto-play when selected
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
