package maryk.datastore.shared

import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.models.fromChanges
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.references.IncMapReference
import maryk.core.query.RequestContext
import maryk.core.query.changes.Change
import maryk.core.query.changes.Check
import maryk.core.query.changes.IncMapAddition
import maryk.core.query.changes.IncMapChange
import maryk.core.query.changes.IncMapKeyAdditions
import maryk.core.query.changes.IndexChange
import maryk.core.query.changes.IsChange
import maryk.core.query.changes.MultiTypeChange

/** Replay committed values, rather than rerunning source-side checks or key allocation. */
fun normalizeReplicationChanges(changes: List<IsChange>): List<IsChange> {
    class Pending(val value: IncMapKeyAdditions<out Comparable<Any>, out Any>, var consumed: Boolean = false)
    val pending = changes.filterIsInstance<IncMapAddition>().flatMap { it.additions }.map { Pending(it) }
    val result = mutableListOf<IsChange>()

    @Suppress("UNCHECKED_CAST")
    fun materialize(addition: Pending, values: List<Any>?) {
        val keys = addition.value.addedKeys
            ?: throw RequestException("Replicated incrementing-map additions require resolved keys")
        val items = values ?: addition.value.addedValues
            ?: throw RequestException("Replicated incrementing-map additions require their values")
        if (keys.size != items.size) throw RequestException("Replicated incrementing-map keys and values have different sizes")
        val reference = addition.value.reference as IncMapReference<Comparable<Any>, Any, IsPropertyContext>
        for ((index, key) in keys.withIndex()) {
            result += Change(reference.propertyDefinition.valueRef(key, reference) with items[index])
        }
        addition.consumed = true
    }

    var additionOrdinal = 0
    for (change in changes) when (change) {
        is Check, is IndexChange, is MultiTypeChange -> Unit
        is IncMapChange -> for (command in change.valueChanges) {
            if (command.addValues.isNullOrEmpty()) continue
            val addition = pending.firstOrNull { !it.consumed && it.value.reference == command.reference }
                ?: throw RequestException("Replicated incrementing-map changes require resolved source keys")
            // Preserve the command's position relative to other mutations. Its original
            // values also survive transports which derive response values from context.
            materialize(addition, command.addValues)
        }
        is IncMapAddition -> repeat(change.additions.size) {
            val resolved = pending[additionOrdinal++]
            if (!resolved.consumed) materialize(resolved, null)
        }
        else -> result += change
    }
    return result
}

/** Reconstruct committed creation values without source-side checks or key allocation. */
fun <DM : IsRootDataModel> DM.fromReplicationChanges(context: RequestContext?, changes: List<IsChange>) =
    fromChanges(context, changes)
