package io.celox.clipvault.util

import androidx.annotation.StringRes
import io.celox.clipvault.R

enum class SortOrder(@StringRes val labelRes: Int) {
    NEWEST(R.string.sort_newest),
    OLDEST(R.string.sort_oldest),
    A_Z(R.string.sort_a_z),
    Z_A(R.string.sort_z_a),
    LONGEST(R.string.sort_longest),
    SHORTEST(R.string.sort_shortest)
}
