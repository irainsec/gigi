package com.aman.gigi.model

import com.google.gson.annotations.SerializedName

data class RemoteNotification(
    @SerializedName("id") val id: String,
    @SerializedName("packageName") val packageName: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("text") val text: String? = null,
    @SerializedName("timestamp") val timestamp: Long? = null,
    @SerializedName("iconUrl") val iconUrl: String? = null,
    @SerializedName("icon_base64") val iconBase64: String? = null, // for legacy real-time relay
    @SerializedName("isClearable") val isClearable: Boolean = true,
    @SerializedName("connectionId") val connectionId: String? = null
)
