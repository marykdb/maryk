package maryk.lib.bytes

import maryk.lib.extensions.toHex
import kotlin.test.Test
import kotlin.test.expect

class Base64Test {
    @Test
    fun fromBase64() {
        expect("AA") { Base64.encode(byteArrayOf(0)) }
        expect("AQ") { Base64.encode(byteArrayOf(1)) }
        expect("Ag") { Base64.encode(byteArrayOf(2)) }
    }

    @Test
    fun toBase64() {
        expect("00") { Base64.decode("AA").toHex() }
        expect("ee") { Base64.decode("7g").toHex() }
        expect("d3") { Base64.decode("0w").toHex() }
        expect("ff") { Base64.decode("//").toHex() }
    }
}
