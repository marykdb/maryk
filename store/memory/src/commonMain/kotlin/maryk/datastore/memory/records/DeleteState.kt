@file:Suppress("EXPERIMENTAL_API_USAGE", "EXPERIMENTAL_FEATURE_WARNING")

package maryk.datastore.memory.records

/** Defines States of if a DataRecord is deleted or not */
internal sealed class DeleteState {
    object NeverDeleted: DeleteState()
    class Deleted(val version: ULong): DeleteState()
}
