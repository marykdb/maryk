package maryk.lib.extensions

/** Converts number to String and prefixes zeros until [totalDigits] count is reached */
fun Number.zeroFill(totalDigits: Int): String {
    var string = this.toString()
    for (it in string.length until totalDigits) {
        string = "0$string"
    }
    return string
}
