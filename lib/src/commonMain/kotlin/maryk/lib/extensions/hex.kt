package maryk.lib.extensions

import kotlin.text.hexToByteArray

val HEX_CHARS = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')

/** Converts ByteArray into a String Hex value */
@OptIn(ExperimentalStdlibApi::class)
fun ByteArray.toHex() = this.toHexString()

/** Converts [hex] String into a ByteArray */
@OptIn(ExperimentalStdlibApi::class)
fun initByteArrayByHex(hex: String) = hex.hexToByteArray()
