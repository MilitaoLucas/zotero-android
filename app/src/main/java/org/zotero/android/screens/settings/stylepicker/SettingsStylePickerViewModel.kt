package org.zotero.android.screens.settings.stylepicker

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.zotero.android.architecture.BaseViewModel2
import org.zotero.android.architecture.Defaults
import org.zotero.android.architecture.ViewEffect
import org.zotero.android.architecture.ViewState
import org.zotero.android.architecture.coroutines.Dispatchers
import org.zotero.android.database.DbWrapperBundle
import org.zotero.android.database.requests.ReadInstalledStylesDbRequest
import org.zotero.android.screens.settings.stylepicker.data.SettingsQuickCopyUpdateStyleEventStream
import org.zotero.android.styles.data.Style
import javax.inject.Inject

@HiltViewModel
internal class SettingsStylePickerViewModel @Inject constructor(
    private val dispatchers: Dispatchers,
    private val dbWrapperBundle: DbWrapperBundle,
    private val defaults: Defaults,
    private val settingsQuickCopyUpdateStyleEventStream: SettingsQuickCopyUpdateStyleEventStream,
) : BaseViewModel2<SettingsStylePickerViewState, SettingsStylePickerViewEffect>(SettingsStylePickerViewState()) {

    fun init() = initOnce {
        val selected = defaults.getQuickCopyStyleId()
        updateState {
            copy(selected = selected)
        }

        viewModelScope.launch {
            reloadStyles()
        }
    }

    private suspend fun reloadStyles() {
        val styles = loadStyles()
        updateState {
            copy(styles = styles)
        }
    }

    private suspend fun loadStyles(): List<Style> = withContext(dispatchers.io) {
        val rStyles = dbWrapperBundle.realmDbStorage.perform(ReadInstalledStylesDbRequest())
        val styles = rStyles.mapNotNull { Style.fromRStyle(it) }
        styles
    }

    fun onBack() {
        triggerEffect(SettingsStylePickerViewEffect.OnBack)
    }

    fun onItemTapped(style: Style) {
        settingsQuickCopyUpdateStyleEventStream.emitAsync(style)
        triggerEffect(SettingsStylePickerViewEffect.OnBack)
    }

}

internal data class SettingsStylePickerViewState(
    val styles: List<Style> = emptyList(),
    val selected: String = ""
) : ViewState

internal sealed class SettingsStylePickerViewEffect : ViewEffect {
    object OnBack : SettingsStylePickerViewEffect()
}