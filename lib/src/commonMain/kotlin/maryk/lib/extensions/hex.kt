package maryk.lib.extensions

val HEX_CHARS = setOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')

/** Converts ByteArray into a String Hex value */
fun ByteArray.toHex() = this.toHexString()

/** Converts [hex] String into a ByteArray */
fun initByteArrayByHex(hex: String) = hex.hexToByteArray()
