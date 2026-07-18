package maryk.core.properties.definitions

/** Definition whose sortable storage representation can be bytewise reversed. */
interface IsReversibleStorageDefinition {
    val reversedStorage: Boolean?
}
