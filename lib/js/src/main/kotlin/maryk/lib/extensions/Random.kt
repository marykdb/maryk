@file:Suppress("DEPRECATION")

package maryk.lib.extensions

import kotlin.js.Math

actual fun Byte.Companion.random() = Double.random().toByte()
actual fun Short.Companion.random() = Double.random().toShort()
actual fun Int.Companion.random() = Double.random().toInt()
actual fun Long.Companion.random() = Double.random().toLong()

actual fun Float.Companion.random() = Double.random().toFloat()
actual fun Double.Companion.random() = Math.random()

actual fun randomBytes(size: Int) = ByteArray(size) {
    Byte.random()
}
