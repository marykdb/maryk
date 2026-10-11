package maryk.core.query.responses

import maryk.core.models.TypedObjectDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.enum
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.string
import maryk.core.properties.enum.IndexedEnumComparable
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.IsCoreEnum
import maryk.core.properties.types.numeric.UInt64
import maryk.core.values.ObjectValues

enum class AtomicFailureCode(override val index: UInt, override val alternativeNames: Set<String>? = null) :
    IndexedEnumComparable<AtomicFailureCode>, IsCoreEnum {
    LimitExceeded(1u), ChangeSetMismatch(2u);
    companion object : IndexedEnumDefinition<AtomicFailureCode>(AtomicFailureCode::class, { entries })
}

/** Stable limit names identify budgets; byte/work counts are backend accounting, not heap usage. */
data class AtomicFailure(
    val code: AtomicFailureCode,
    val limit: String? = null,
    val maximum: ULong? = null,
    val actual: ULong? = null,
) {
    companion object : TypedObjectDataModel<AtomicFailure, AtomicFailure.Companion, IsPropertyContext, IsPropertyContext>() {
        val code by enum(1u, AtomicFailure::code, enum = AtomicFailureCode)
        val limit by string(2u, AtomicFailure::limit, required = false)
        val maximum by number(3u, AtomicFailure::maximum, UInt64, required = false)
        val actual by number(4u, AtomicFailure::actual, UInt64, required = false)
        override fun invoke(values: ObjectValues<AtomicFailure, AtomicFailure.Companion>) = AtomicFailure(
            code = values(code.index), limit = values(limit.index), maximum = values(maximum.index), actual = values(actual.index),
        )
    }
}
