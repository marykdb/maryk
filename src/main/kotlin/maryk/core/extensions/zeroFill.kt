package maryk.core.extensions

/**
 * Converts number to string and prefixes zeros until count is reached
 * @param totalDigits: total digits
 */
fun Number.zeroFill(totalDigits: Int): String {
    var string = this.toString()
    (string.length until totalDigits).forEach {
        string = '0' + string
    }
    return string
}