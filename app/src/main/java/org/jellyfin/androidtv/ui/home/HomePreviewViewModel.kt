package org.jellyfin.androidtv.ui.home

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem

class HomePreviewViewModel : ViewModel() {
    private val _selectedItem = MutableStateFlow<BaseRowItem?>(null)
    val selectedItem: StateFlow<BaseRowItem?> = _selectedItem.asStateFlow()

    fun updateSelectedItem(item: BaseRowItem?) {
        _selectedItem.value = item
    }
}