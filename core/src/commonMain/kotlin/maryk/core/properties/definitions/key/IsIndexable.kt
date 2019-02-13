package maryk.core.properties.definitions.key

/**
 * Defines this item is usable to describe an Index Key
 */
interface IsIndexable {
    /** The size it contributes to the key */
    val byteSize: Int

    val indexKeyPartType: IndexKeyPartType
}
