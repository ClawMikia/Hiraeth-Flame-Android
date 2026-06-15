package com.hiraeth.flame.ui.albums

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.hiraeth.flame.data.db.MediaEntity
import com.hiraeth.flame.databinding.ItemMediaSelectionBinding
import com.hiraeth.flame.di.AppContainer

class MediaSelectionAdapter(
    private val container: AppContainer,
    private val initialSelectedIds: Set<Long>
) : ListAdapter<MediaEntity, MediaSelectionAdapter.VH>(DIFF) {

    private val selectedIds = initialSelectedIds.toMutableSet()

    fun getSelectedIds(): Set<Long> = selectedIds

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemMediaSelectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val file = container.mediaStorage.resolveRelative(item.relativePath)
        
        holder.binding.thumbnail.load(file)
        holder.binding.title.text = item.displayName
        holder.binding.videoIcon.visibility = if (item.isVideo) View.VISIBLE else View.GONE
        
        // Remove listener before setting checked to avoid recursion/wrong updates
        holder.binding.checkbox.setOnCheckedChangeListener(null)
        holder.binding.checkbox.isChecked = selectedIds.contains(item.id)
        
        holder.binding.checkbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) selectedIds.add(item.id) else selectedIds.remove(item.id)
        }
        
        holder.itemView.setOnClickListener {
            holder.binding.checkbox.toggle()
        }
    }

    class VH(val binding: ItemMediaSelectionBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<MediaEntity>() {
            override fun areItemsTheSame(old: MediaEntity, new: MediaEntity) = old.id == new.id
            override fun areContentsTheSame(old: MediaEntity, new: MediaEntity) = old == new
        }
    }
}
