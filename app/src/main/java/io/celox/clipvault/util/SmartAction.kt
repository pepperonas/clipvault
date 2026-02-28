package io.celox.clipvault.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sms
import androidx.compose.ui.graphics.vector.ImageVector
import io.celox.clipvault.R

enum class SmartActionType {
    COPY, OPEN_BROWSER, CALL, SEND_SMS, SEND_EMAIL, SHARE, OPEN_MAPS
}

data class SmartAction(
    val labelRes: Int,
    val icon: ImageVector,
    val actionType: SmartActionType
)

fun resolveSmartActions(contentType: ContentType): List<SmartAction> {
    val actions = mutableListOf<SmartAction>()

    // Copy is always first
    actions += SmartAction(R.string.action_copy, Icons.Default.ContentCopy, SmartActionType.COPY)

    // Type-specific actions
    when (contentType) {
        ContentType.URL, ContentType.INSTAGRAM, ContentType.FACEBOOK,
        ContentType.YOUTUBE, ContentType.TWITTER, ContentType.TIKTOK,
        ContentType.LINKEDIN, ContentType.GITHUB ->
            actions += SmartAction(R.string.action_open_browser, Icons.Default.Language, SmartActionType.OPEN_BROWSER)

        ContentType.PHONE -> {
            actions += SmartAction(R.string.action_call, Icons.Default.Phone, SmartActionType.CALL)
            actions += SmartAction(R.string.action_send_sms, Icons.Default.Sms, SmartActionType.SEND_SMS)
        }

        ContentType.EMAIL ->
            actions += SmartAction(R.string.action_send_email, Icons.Default.Email, SmartActionType.SEND_EMAIL)

        ContentType.COORDINATES, ContentType.ADDRESS ->
            actions += SmartAction(R.string.action_open_maps, Icons.Default.Map, SmartActionType.OPEN_MAPS)

        else -> { /* no extra actions */ }
    }

    // Share is always last
    actions += SmartAction(R.string.action_share, Icons.Default.Share, SmartActionType.SHARE)

    return actions
}
