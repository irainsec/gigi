package com.aman.gigi.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class RemotePhoto(
    val id: String,
    val name: String,
    val dateTaken: Long,
    val size: Long,
    val thumbnailBase64: String? = null,
    val mimeType: String? = null
) : Parcelable
