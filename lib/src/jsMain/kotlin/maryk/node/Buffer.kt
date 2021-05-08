@file:JsModule("buffer")
@file:JsNonModule
@file:Suppress("INTERFACE_WITH_SUPERCLASS", "OVERRIDING_FINAL_MEMBER", "RETURN_TYPE_MISMATCH_ON_OVERRIDE")
package maryk.node

external class Buffer(value: String, encoding: String) {
    val length: Int

    fun toString(encoding: String): String
    fun values(): ValueIterator

    companion object {
        fun from(value: ByteArray): Buffer
        fun from(value: String, encoding: String): Buffer
    }

    class ValueIterator {
        fun next(): Value
        class Value {
            val value: Byte
        }
    }
}
