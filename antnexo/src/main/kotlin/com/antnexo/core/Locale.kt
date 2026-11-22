package com.antnexo.core

/**
 * A locale identifier as BCP 47 without extensions (`en_CZ` → `en-CZ`, `en-US-u-ca-japanese` → `en-US`), at most 35
 * characters (what the API takes); `und` when empty.
 */
fun bcp47(tag: String): String {
    val base = tag.substringBefore('@').replace('_', '-')
    // drop extensions and private use: everything from the first singleton subtag on
    val parts = base.split('-').filter { it.isNotEmpty() }.takeWhile { it.length > 1 }
    val out = parts.joinToString("-").take(35).trimEnd('-')
    return out.ifEmpty { "und" }
}
