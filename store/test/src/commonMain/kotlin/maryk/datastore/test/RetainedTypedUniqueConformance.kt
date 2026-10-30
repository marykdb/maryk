package maryk.datastore.test

import maryk.core.models.DataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.IsUsableInMultiType
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.multiType
import maryk.core.properties.definitions.string
import maryk.core.properties.enum.IndexedEnumImpl
import maryk.core.properties.enum.MultiTypeEnum
import maryk.core.properties.enum.MultiTypeEnumDefinition
import maryk.core.properties.references.dsl.atType
import maryk.core.properties.types.Key
import maryk.core.properties.types.invoke
import maryk.core.query.changes.Change
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.change
import maryk.core.query.filters.Equals
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.scan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.values.Values
import maryk.datastore.shared.IsDataStore
import kotlin.test.assertEquals
import kotlin.test.assertIs

object RetainedUniqueEmbedded : DataModel<RetainedUniqueEmbedded>() {
    val text by string(1u, unique = true)
}

sealed class RetainedUniqueType<T : Any>(
    index: UInt,
    override val definition: IsUsableInMultiType<T, *>,
) : IndexedEnumImpl<RetainedUniqueType<Any>>(index), MultiTypeEnum<T> {
    object ObjectCase : RetainedUniqueType<Values<RetainedUniqueEmbedded>>(1u,
        EmbeddedValuesDefinition(dataModel = { RetainedUniqueEmbedded }))
    object StringCase : RetainedUniqueType<String>(2u, StringDefinition())

    companion object : MultiTypeEnumDefinition<RetainedUniqueType<out Any>>(
        RetainedUniqueType::class, values = { listOf(ObjectCase, StringCase) },
    )
}

object RetainedUniqueRecord : RootDataModel<RetainedUniqueRecord>() {
    val choice by multiType(1u, typeEnum = RetainedUniqueType, typeIsFinal = false)
    val note by string(2u, required = false)
}

/** Inactive branch cells must not acquire a unique claim when the record is undeleted. */
suspend fun assertRetainedTypedUniqueClaims(
    store: IsDataStore,
    retainChildren: suspend (Key<RetainedUniqueRecord>, ByteArray, suspend () -> Unit) -> Unit,
) {
    val first = assertIs<AddSuccess<RetainedUniqueRecord>>(store.execute(RetainedUniqueRecord.add(RetainedUniqueRecord.create {
        choice with RetainedUniqueType.ObjectCase(RetainedUniqueEmbedded.create { text with "old" })
    })).statuses.single())
    retainChildren(first.key, RetainedUniqueRecord.ref { choice }.toStorageByteArray()) {
        assertIs<ChangeSuccess<RetainedUniqueRecord>>(store.execute(RetainedUniqueRecord.change(first.key.change(
            Change(RetainedUniqueRecord.ref { choice } with RetainedUniqueType.StringCase("new")),
        ))).statuses.single())
    }
    val replacement = assertIs<AddSuccess<RetainedUniqueRecord>>(store.execute(RetainedUniqueRecord.add(RetainedUniqueRecord.create {
        choice with RetainedUniqueType.ObjectCase(RetainedUniqueEmbedded.create { text with "old" })
    })).statuses.single())
    assertIs<ChangeSuccess<RetainedUniqueRecord>>(store.execute(RetainedUniqueRecord.change(first.key.change(
        Change(RetainedUniqueRecord.ref { note } with "unrelated edit"),
    ))).statuses.single())
    for (deleted in listOf(true, false)) {
        assertIs<ChangeSuccess<RetainedUniqueRecord>>(store.execute(RetainedUniqueRecord.change(first.key.change(
            ObjectSoftDeleteChange(deleted),
        ))).statuses.single(), "soft-delete=$deleted must ignore the inactive unique leaf")
    }
    val filter = Equals(RetainedUniqueRecord.ref { choice.atType(RetainedUniqueType.ObjectCase) { text } } with "old")
    assertEquals(listOf(replacement.key), store.execute(RetainedUniqueRecord.scan(where = filter)).values.map { it.key })
}
