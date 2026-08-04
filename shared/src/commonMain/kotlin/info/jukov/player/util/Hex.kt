package info.jukov.player.util

fun ByteArray.toHex(): String =
    joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
