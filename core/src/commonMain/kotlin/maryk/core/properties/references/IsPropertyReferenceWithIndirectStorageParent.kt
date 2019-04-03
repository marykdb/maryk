package maryk.core.properties.references

import maryk.core.properties.definitions.IsPropertyDefinition

/**
 * Property reference with indirect parent for storage. The parent of the parent is encoded in byte storage
 * This because the direct parent is encoded by reference implementing this
 */
interface IsPropertyReferenceWithIndirectStorageParent<T : Any, out D : IsPropertyDefinition<T>, out P : IsPropertyReferenceForValues<*, *, *, *>, V : Any> : IsPropertyReferenceWithParent<T, D, P, V> {
    override fun calculateStorageByteLength(): Int {
        // Calculate bytes above the setReference parent
        val parentCount = this.parentReference?.parentReference?.calculateStorageByteLength() ?: 0
        return parentCount + this.calculateSelfStorageByteLength()
    }

    override fun writeStorageBytes(writer: (byte: Byte) -> Unit) {
        // Calculate bytes above the setReference parent
        this.parentReference?.parentReference?.writeStorageBytes(writer)
        this.writeSelfStorageBytes(writer)
    }
}
