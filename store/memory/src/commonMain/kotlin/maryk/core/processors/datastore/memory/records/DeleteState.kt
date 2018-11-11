@file:Suppress("EXPERIMENTAL_API_USAGE", "EXPERIMENTAL_FEATURE_WARNING")

package maryk.core.processors.datastore.memory.records

internal sealed class DeleteState {
    object NeverDeleted: DeleteState()
    class Deleted(val version: ULong): DeleteState()
}
