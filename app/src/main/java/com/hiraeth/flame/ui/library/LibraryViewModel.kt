package com.hiraeth.flame.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hiraeth.flame.data.db.MediaEntity
import com.hiraeth.flame.data.repository.MediaRepository
import com.hiraeth.flame.domain.LibrarySort
import com.hiraeth.flame.domain.LibraryViewMode
import com.hiraeth.flame.domain.MediaTypeFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

/** Holds the first four inputs for [LibraryViewModel.items] (nested [combine] avoids 7-way overload issues on K2). */
private data class LibraryMainInputs(
    val list: List<MediaEntity>,
    val query: String,
    val typeFilter: MediaTypeFilter,
    val sort: LibrarySort,
)

private data class LibraryDateTagInputs(
    val tagFilter: String,
    val dateFrom: Long?,
    val dateTo: Long?,
)

class LibraryViewModel(
    private val repository: MediaRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val queryState: StateFlow<String> = _query.asStateFlow()
    private val typeFilter = MutableStateFlow(MediaTypeFilter.All)
    private val sort = MutableStateFlow(LibrarySort.DateNewest)
    private val viewMode = MutableStateFlow(LibraryViewMode.Grid)
    private val tagFilter = MutableStateFlow("")
    /** Inclusive day start (local) or null if disabled. */
    private val dateFromEpoch = MutableStateFlow<Long?>(null)
    private val dateToEpoch = MutableStateFlow<Long?>(null)

    val viewModeState: StateFlow<LibraryViewMode> = viewMode

    val items: StateFlow<List<MediaEntity>> = combine(
        combine(
            repository.observeAll(),
            _query,
            typeFilter,
            sort,
        ) { list, q, tf, s ->
            LibraryMainInputs(list, q, tf, s)
        },
        combine(
            tagFilter,
            dateFromEpoch,
            dateToEpoch,
        ) { tag, from, to ->
            LibraryDateTagInputs(tag, from, to)
        },
    ) { main, dates ->
        val list = main.list
        val q = main.query
        val tf = main.typeFilter
        val s = main.sort
        val tag = dates.tagFilter
        val from = dates.dateFrom
        val to = dates.dateTo
        list.asSequence()
            .filter { entity ->
                when (tf) {
                    MediaTypeFilter.All -> true
                    MediaTypeFilter.ImagesOnly -> !entity.isVideo
                    MediaTypeFilter.VideosOnly -> entity.isVideo
                }
            }
            .filter { entity ->
                val fromOk = from?.let { entity.modifiedAtEpochMs >= it } ?: true
                val toOk = to?.let { entity.modifiedAtEpochMs <= it } ?: true
                fromOk && toOk
            }
            .sortedWith(comparatorFor(s))
            .toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(value: String) {
        _query.value = value
    }

    fun setTypeFilter(value: MediaTypeFilter) {
        typeFilter.value = value
    }

    fun setSort(value: LibrarySort) {
        sort.value = value
    }

    fun toggleViewMode() {
        viewMode.value = if (viewMode.value == LibraryViewMode.Grid) LibraryViewMode.List else LibraryViewMode.Grid
    }

    fun setTagFilter(value: String) {
        tagFilter.value = value
    }

    /** Sets filter to start/end of a calendar day in local timezone. */
    fun setDateRangeDay(year: Int, monthZeroBased: Int, day: Int) {
        val cal = Calendar.getInstance()
        cal.set(year, monthZeroBased, day, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.set(year, monthZeroBased, day, 23, 59, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val end = cal.timeInMillis
        dateFromEpoch.value = start
        dateToEpoch.value = end
    }

    fun clearDateFilter() {
        dateFromEpoch.value = null
        dateToEpoch.value = null
    }

    fun delete(entity: MediaEntity) {
        viewModelScope.launch { repository.delete(entity) }
    }

    private fun comparatorFor(sort: LibrarySort): Comparator<MediaEntity> =
        when (sort) {
            LibrarySort.DateNewest -> compareByDescending { it.modifiedAtEpochMs }
            LibrarySort.DateOldest -> compareBy { it.modifiedAtEpochMs }
            LibrarySort.NameAZ -> compareBy { it.displayName.lowercase() }
            LibrarySort.NameZA -> compareByDescending { it.displayName.lowercase() }
            LibrarySort.SizeLargest -> compareByDescending { it.sizeBytes }
            LibrarySort.SizeSmallest -> compareBy { it.sizeBytes }
        }

    companion object {
        fun factory(repository: MediaRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(LibraryViewModel::class.java))
                    return LibraryViewModel(repository) as T
                }
            }
    }
}
