@file:Suppress("DEPRECATION")

package maryk.lib.extensions

import kotlin.js.Math

actual fun Byte.Companion.random() = Int.random().toByte()
actual fun Short.Companion.random() = Int.random().toShort()
actual fun Int.Companion.random() = js("(Math.random() * Math.pow(2, 32)) | 0").unsafeCast<Int>()
actual fun Long.Companion.random() = Double.random().toLong()

actual fun Float.Companion.random() = Double.random().toFloat()
actual fun Double.Companion.random() = Math.random()

actual fun randomBytes(size: Int) = ByteArray(size) {
    Byte.random()
}
