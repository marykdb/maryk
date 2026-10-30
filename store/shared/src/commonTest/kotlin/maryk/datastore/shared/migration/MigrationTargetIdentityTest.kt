package maryk.datastore.shared.migration

import kotlinx.coroutines.test.runTest
import maryk.core.definitions.Definitions
import maryk.core.definitions.MarykPrimitive
import maryk.core.models.definitions.DataModelDefinition
import maryk.core.models.DataModel
import maryk.core.models.IsRootDataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.enum
import maryk.core.properties.enum.IndexedEnumComparable
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.reference
import maryk.core.properties.definitions.set
import maryk.core.properties.types.numeric.UInt32
import maryk.core.properties.types.numeric.UInt64
import maryk.core.protobuf.WriteCache
import maryk.core.query.StorageDefinitionsConversionContext
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse

private class IdentityLeaf(name: String, contents: Map<String, String>) : DataModel<IdentityLeaf>({ DataModelDefinition(name = name) }) {
    val defaults by map(1u, keyDefinition = StringDefinition(), valueDefinition = StringDefinition(), default = contents)
}

private object IdentityShortReference : RootDataModel<IdentityShortReference>(name = "IdentityReference",
    keyDefinition = { IdentityShortReference.id.ref() }) {
    val id by number(1u, type = UInt32, final = true)
}

private object IdentityLongReference : RootDataModel<IdentityLongReference>(name = "IdentityReference",
    keyDefinition = { IdentityLongReference.id.ref() }) {
    val id by number(1u, type = UInt64, final = true)
}

private class IdentityTarget(
    reverse: Boolean = false,
    listDefault: List<String> = listOf("a", "b"),
    nestedValue: String = "2",
    referenceTarget: IsRootDataModel = IdentityShortReference,
) : RootDataModel<IdentityTarget>(name = "IdentityTarget") {
    private val pairs = listOf("first" to "1", "second" to nestedValue).let { if (reverse) it.reversed() else it }
    private val firstLeaf = IdentityLeaf("FirstLeaf", pairs.toMap())
    private val secondLeaf = IdentityLeaf("SecondLeaf", pairs.toMap())
    val mapping by map(1u, keyDefinition = StringDefinition(), valueDefinition = StringDefinition(), default = pairs.toMap())
    val labels by set(2u, valueDefinition = StringDefinition(), default = pairs.map { it.first }.toSet())
    val sequence by list(3u, valueDefinition = StringDefinition(), default = listDefault)
    val first by embed(4u, dataModel = { firstLeaf })
    val second by embed(5u, dataModel = { secondLeaf })
    val target by reference(6u, dataModel = { referenceTarget })
    private val reverseDependencies = reverse

    override fun getAllDependencies(dependencySet: MutableList<MarykPrimitive>) {
        super.getAllDependencies(dependencySet)
        if (reverseDependencies) dependencySet.reverse()
    }
}

private enum class IdentityEnumCase(override val index: UInt) : IndexedEnumComparable<IdentityEnumCase> {
    First(1u), Second(2u);
    override val alternativeNames: Set<String>? = null
}

private class IdentityEnumTarget(reverse: Boolean) : RootDataModel<IdentityEnumTarget>(name = "IdentityEnumTarget") {
    val case by enum(1u, enum = IndexedEnumDefinition(IdentityEnumCase::class, {
        if (reverse) IdentityEnumCase.entries.reversed() else IdentityEnumCase.entries
    }))
}

class MigrationTargetIdentityTest {
    @Test
    fun enumCaseInsertionOrderIsCanonicalOnlyForMigrationIdentity() = runTest {
        val first = IdentityEnumTarget(false)
        val reversed = IdentityEnumTarget(true)
        assertContentEquals(migrationTargetIdentity(first), migrationTargetIdentity(reversed))
        fun ordinaryBytes(model: IdentityEnumTarget): ByteArray {
            val context = StorageDefinitionsConversionContext()
            val cache = WriteCache()
            val size = RootDataModel.Model.Serializer.calculateObjectProtoBufLength(model, cache, context)
            var offset = 0
            return ByteArray(size).also { bytes ->
                RootDataModel.Model.Serializer.writeObjectProtoBuf(model, cache, { bytes[offset++] = it }, context)
            }
        }
        // Canonicalization must not silently change the ordinary persisted/transport schema codec.
        assertFalse(ordinaryBytes(first).contentEquals(ordinaryBytes(reversed)))
    }

    @Test
    fun unorderedDefaultAndDependencyOrderDoesNotChangeIdentity() = runTest {
        assertContentEquals(migrationTargetIdentity(IdentityTarget()), migrationTargetIdentity(IdentityTarget(reverse = true)))
    }

    @Test
    fun persistedSchemaRoundtripPreservesTheTargetIdentity() = runTest {
        for (reverse in listOf(false, true)) {
            val target = IdentityTarget(reverse = reverse)
            val writeContext = StorageDefinitionsConversionContext()
            val rootCache = WriteCache()
            val rootSize = RootDataModel.Model.Serializer.calculateObjectProtoBufLength(target, rootCache, writeContext)
            val rootBytes = ByteArray(rootSize)
            var offset = 0
            RootDataModel.Model.Serializer.writeObjectProtoBuf(target, rootCache, { rootBytes[offset++] = it }, writeContext)
            val dependencies = mutableListOf<MarykPrimitive>().also(target::getAllDependencies)
            val definitions = Definitions(dependencies)
            val dependenciesCache = WriteCache()
            val dependenciesSize = Definitions.Serializer.calculateObjectProtoBufLength(definitions, dependenciesCache, writeContext)
            val dependencyBytes = ByteArray(dependenciesSize)
            offset = 0
            Definitions.Serializer.writeObjectProtoBuf(definitions, dependenciesCache, { dependencyBytes[offset++] = it }, writeContext)
            val readContext = StorageDefinitionsConversionContext()
            offset = 0
            Definitions.Serializer.readProtoBuf(dependenciesSize, { dependencyBytes[offset++] }, readContext).toDataObject()
            offset = 0
            val persisted = RootDataModel.Model.Serializer.readProtoBuf(rootSize, { rootBytes[offset++] }, readContext).toDataObject()
            assertContentEquals(migrationTargetIdentity(target), migrationTargetIdentity(persisted))
        }
    }

    @Test
    fun orderedDefaultsNestedValuesAndReferenceWidthsRemainPartOfIdentity() = runTest {
        val original = migrationTargetIdentity(IdentityTarget())
        for (changed in listOf(
            IdentityTarget(listDefault = listOf("b", "a")),
            IdentityTarget(nestedValue = "different"),
            IdentityTarget(referenceTarget = IdentityLongReference),
        )) assertFalse(original.contentEquals(migrationTargetIdentity(changed)))
    }
}
