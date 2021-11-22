package maryk.lib.extensions

/** Converts number to String and prefixes zeros until [totalDigits] count is reached */
fun Number.zeroFill(totalDigits: Int) =
    this.toString().padStart(totalDigits, '0')
