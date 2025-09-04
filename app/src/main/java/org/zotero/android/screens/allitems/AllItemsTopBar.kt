package org.zotero.android.screens.allitems

import androidx.compose.runtime.Composable
import org.zotero.android.architecture.ui.CustomLayoutSize

@Composable
internal fun AllItemsTopBar(
    viewState: AllItemsViewState,
    viewModel: AllItemsViewModel,
    layoutType: CustomLayoutSize.LayoutType,
) {
    if (viewState.isEditing) {
        AllItemsEditingTopBar(
            selectedKeysSize = viewState.selectedKeys?.size ?: 0,
            allSelected = viewState.areAllSelected,
            onCancelClicked = viewModel::onDone,
            toggleSelectionState = viewModel::toggleSelectionState
        )
    } else {
        if (layoutType.isTablet()) {
            AllItemsTabletSearchBar(viewState, viewModel)
        } else {
            AllItemsPhoneAppSearchBar(viewState, viewModel)
        }
    }

}
