package maryk.core.properties.references

import maryk.core.properties.definitions.IsPropertyDefinition

/** Property reference with parent */
interface IsPropertyReferenceWithParent<T : Any, out D : IsPropertyDefinition<T>, out P : AnyPropertyReference, V : Any> : IsPropertyReference<T, D, V> {
    val parentReference: P?

    override fun calculateStorageByteLength(): Int {
        val parent = this.parentReference?.calculateStorageByteLength() ?: 0
        return parent + this.calculateSelfStorageByteLength()
    }

    override fun writeStorageBytes(writer: (byte: Byte) -> Unit) {
        this.parentReference?.writeStorageBytes(writer)
        this.writeSelfStorageBytes(writer)
    }
}
