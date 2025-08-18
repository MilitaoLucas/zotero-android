package org.zotero.android.database.objects

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.zotero.android.sync.LibraryIdentifier
import timber.log.Timber
import java.util.Date

@Parcelize
data class Attachment(
    val type: Kind,
    val title: String,
    val key: String,
    val libraryId: LibraryIdentifier,
    val url: String? = null,
    val dateAdded: Date = Date(),
) : Parcelable {

    val id: String
        get() {
            return this.key
        }

    val hasUrl: Boolean get() {
        return when(this.type) {
            is Kind.url -> true
            is Kind.file -> this.url != null
        }
    }

    val location: FileLocation?
        get() {
            return when (this.type) {
                is Kind.url -> null
                is Kind.file -> this.type.location
            }
        }

    enum class FileLocation {
        local, localAndChangedRemotely, remote, remoteMissing
    }

    enum class FileLinkType {
        importedUrl, importedFile, embeddedImage, linkedFile
    }

    sealed class Kind : Parcelable {
        @Parcelize
        data class file(
            val filename: String,
            val contentType: String,
            val location: FileLocation,
            val linkType: FileLinkType
        ) : Kind()

        @Parcelize
        data class url(val url: String) : Kind()
    }

    fun changed(location: FileLocation, condition: (FileLocation) -> Boolean): Attachment? {
        when {
            this.type is Kind.file && condition(this.type.location) -> {
                return Attachment(
                    type = Kind.file(
                        filename = this.type.filename,
                        contentType = this.type.contentType,
                        location = location,
                        linkType = this.type.linkType
                    ),
                    title = this.title,
                    url = this.url,
                    dateAdded = this.dateAdded,
                    key = this.key,
                    libraryId = this.libraryId
                )
            }

            this.type is Kind.url || this.type is Kind.file ->
                return null
            else -> return null
        }
    }

    fun changed(location: FileLocation): Attachment? {
        when {
            this.type is Kind.file && (this.type.location != location) -> {
                return Attachment(
                    type = Kind.file(
                        filename = this.type.filename,
                        contentType = this.type.contentType,
                        location = location,
                        linkType = this.type.linkType
                    ),
                    title = this.title,
                    url = this.url,
                    dateAdded = this.dateAdded,
                    key = this.key,
                    libraryId = this.libraryId
                )
            }

            this.type is Kind.url || this.type is Kind.file ->
                return null
            else -> return null
        }
    }

    companion object {
        fun initWithItemAndKind(item: RItem, type: Kind): Attachment? {
            val libraryId = item.libraryId
            if (libraryId == null) {
                Timber.e("Attachment: library not assigned to item ${item.key}")
                return null
            }
            return Attachment(
                libraryId = libraryId,
                key = item.key,
                title = item.displayTitle,
                type = type,
                dateAdded = item.dateAdded,
                url = item.fields.firstOrNull { it.key == FieldKeys.Item.url }?.value,
            )
        }
    }
}
