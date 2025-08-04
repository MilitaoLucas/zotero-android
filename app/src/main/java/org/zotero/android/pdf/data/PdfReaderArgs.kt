package org.zotero.android.pdf.data

import android.net.Uri
import org.zotero.android.sync.Library

data class PdfReaderArgs(
    val key: String,
    val parentKey: String?,
    val library: Library,
    val uri: Uri,
    val page: Int?,
    val preselectedAnnotationKey: String?,
)
