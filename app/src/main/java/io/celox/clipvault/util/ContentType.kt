package io.celox.clipvault.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import io.celox.clipvault.R

enum class ContentType(val icon: ImageVector, val color: Color, val labelRes: Int) {
    INSTAGRAM(Icons.Default.Link, Color(0xFFE1306C), R.string.content_type_instagram),
    FACEBOOK(Icons.Default.Link, Color(0xFF1877F2), R.string.content_type_facebook),
    YOUTUBE(Icons.Default.PlayCircle, Color(0xFFFF0000), R.string.content_type_youtube),
    TWITTER(Icons.Default.Link, Color(0xFF000000), R.string.content_type_twitter),
    TIKTOK(Icons.Default.Link, Color(0xFF010101), R.string.content_type_tiktok),
    LINKEDIN(Icons.Default.Link, Color(0xFF0A66C2), R.string.content_type_linkedin),
    GITHUB(Icons.Default.Code, Color(0xFF333333), R.string.content_type_github),
    JSON(Icons.Default.DataObject, Color(0xFFFFA000), R.string.content_type_json),
    COLOR_HEX(Icons.Default.Palette, Color(0xFFE91E63), R.string.content_type_color),
    COORDINATES(Icons.Default.LocationOn, Color(0xFF4CAF50), R.string.content_type_coordinates),
    IBAN(Icons.Default.AccountBalance, Color(0xFF5C6BC0), R.string.content_type_iban),
    MARKDOWN(Icons.Default.Description, Color(0xFF78909C), R.string.content_type_markdown),
    CODE(Icons.Default.Terminal, Color(0xFF26A69A), R.string.content_type_code),
    ADDRESS(Icons.Default.Home, Color(0xFF8D6E63), R.string.content_type_address),
    URL(Icons.Default.Link, Color(0xFF666666), R.string.content_type_url),
    EMAIL(Icons.Default.AlternateEmail, Color(0xFF4285F4), R.string.content_type_email),
    PHONE(Icons.Default.Phone, Color(0xFF34A853), R.string.content_type_phone),
    TEXT(Icons.AutoMirrored.Filled.Notes, Color(0xFF999999), R.string.content_type_text)
}
