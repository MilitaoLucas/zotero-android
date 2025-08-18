package org.zotero.android.uicomponents.topbar

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import org.zotero.android.uicomponents.theme.CustomTheme

@Composable
fun HeadingTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = CustomTheme.colors.zoteroDefaultBlue,
    isEnabled: Boolean = true,
    isLoading: Boolean = false,
    style: TextStyle = CustomTheme.typography.default,
) {
    IconButton(
        onClick = { if (!isLoading) onClick() },
        modifier = modifier,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = contentColor,
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text = text,
                color = if (isEnabled) contentColor else CustomTheme.colors.disabledContent,
                style = style
            )
        }
    }
}
