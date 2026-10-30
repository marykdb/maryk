package maryk.core.models.migration

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import maryk.checkProtoBufConversion
import maryk.core.exceptions.DefNotFoundException
import maryk.core.models.IsRootDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.models.RootDataModel
import maryk.core.models.migration.MigrationStatus.NeedsMigration
import maryk.core.models.migration.MigrationStatus.UpToDate
import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.reference
import maryk.core.properties.definitions.ReferenceDefinition
import maryk.core.properties.definitions.contextual.LazyDataModelReference
import maryk.core.properties.types.numeric.UInt32
import maryk.core.properties.types.numeric.UInt64
import maryk.core.query.DefinitionsContext

private class IdentityTarget(name: String) : RootDataModel<IdentityTarget>(name = name)

private object ShortKeyIdentityTarget : RootDataModel<ShortKeyIdentityTarget>(
    name = "KeyIdentityTarget",
    keyDefinition = { ShortKeyIdentityTarget.id.ref() },
) {
    val id by number(index = 1u, type = UInt32, final = true)
}

private object LongKeyIdentityTarget : RootDataModel<LongKeyIdentityTarget>(
    name = "KeyIdentityTarget",
    keyDefinition = { LongKeyIdentityTarget.id.ref() },
) {
    val id by number(index = 1u, type = UInt64, final = true)
}

private class ReferenceIdentityOwner(
    known: () -> IsValuesDataModel,
    target: () -> IsRootDataModel,
) : RootDataModel<ReferenceIdentityOwner>(name = "ReferenceIdentityOwner") {
    val known by embed(index = 1u, dataModel = known, required = false)
    val target by reference(index = 2u, dataModel = target, required = false)
}

private class EmbeddedIdentityOwner(
    known: () -> IsValuesDataModel,
    target: () -> IsValuesDataModel,
) : RootDataModel<EmbeddedIdentityOwner>(name = "EmbeddedIdentityOwner") {
    val known by embed(index = 1u, dataModel = known, required = false)
    val target by embed(index = 2u, dataModel = target, required = false)
}

private class EmbeddedIdentityNode(
    name: String,
    target: () -> IsValuesDataModel,
) : RootDataModel<EmbeddedIdentityNode>(name = name) {
    val target by embed(index = 1u, dataModel = target, required = false)
}

class MigrationModelIdentityTest {
    private val known = IdentityTarget("Known")
    private val other = IdentityTarget("Other")

    @Test
    fun changingReferenceTargetToSelfRequiresMigration() {
        val stored = ReferenceIdentityOwner({ known }, { other })
        lateinit var current: ReferenceIdentityOwner
        current = ReferenceIdentityOwner({ known }, { current })

        assertIs<NeedsMigration>(current.isMigrationNeeded(stored)).apply {
            assertTrue(migrationReasons.any { it.contains("comparing reference properties") })
        }
    }

    @Test
    fun changingReferenceTargetToPreviouslyVisitedModelRequiresMigration() {
        val stored = ReferenceIdentityOwner({ known }, { other })
        val current = ReferenceIdentityOwner({ known }, { known })

        assertIs<NeedsMigration>(current.isMigrationNeeded(stored))
        assertEquals(UpToDate, current.isMigrationNeeded(ReferenceIdentityOwner({ known }, { known })))
    }

    @Test
    fun changedReferenceTargetDoesNotResolveObsoleteSourceModel() {
        val stored = ReferenceDefinition(dataModelReference = {
            LazyDataModelReference<IsRootDataModel>("RemovedTarget", null) {
                error("An incompatible target name does not need to resolve its obsolete schema")
            }
        })
        val current = ReferenceDefinition(dataModel = { known })
        val reasons = mutableListOf<String>()
        assertEquals(false, current.compatibleWith(stored, mutableListOf("Known"), reasons::add))
        assertTrue(reasons.any { it.contains("RemovedTarget") })
    }

    @Test
    fun missingSourceReferenceWidthRequiresAuthoritativeSchema() {
        val stored = ReferenceDefinition(dataModelReference = {
            LazyDataModelReference<IsRootDataModel>("Known", null) {
                throw DefNotFoundException("No source root")
            }
        })
        val reasons = mutableListOf<String>()
        assertEquals(false, ReferenceDefinition(dataModel = { known }).compatibleWith(stored, null, reasons::add))
        assertTrue(reasons.any { it.contains("authoritative source schema") })
    }

    @Test
    fun unchangedSelfReferenceRemainsCompatible() {
        lateinit var stored: ReferenceIdentityOwner
        lateinit var current: ReferenceIdentityOwner
        stored = ReferenceIdentityOwner({ known }, { stored })
        current = ReferenceIdentityOwner({ known }, { current })

        assertEquals(UpToDate, current.isMigrationNeeded(stored))
    }

    @Test
    fun changingReferenceKeySizeRequiresParentMigration() {
        val stored = ReferenceIdentityOwner({ known }, { ShortKeyIdentityTarget })
        val current = ReferenceIdentityOwner({ known }, { LongKeyIdentityTarget })

        assertIs<NeedsMigration>(current.isMigrationNeeded(stored)).apply {
            assertTrue(migrationReasons.any { it.contains("Key byte size") })
        }
        assertEquals(UpToDate, stored.isMigrationNeeded(ReferenceIdentityOwner({ known }, { ShortKeyIdentityTarget })))

        // Stored schema contexts retain their source model's key size when the
        // reference definition is reconstructed from serialized metadata.
        val storedReference: ReferenceDefinition<*> = ReferenceDefinition(dataModel = { ShortKeyIdentityTarget })
        checkProtoBufConversion(
            storedReference,
            ReferenceDefinition.Model,
            context = { DefinitionsContext() },
            checker = { converted, _ ->
                val reasons = mutableListOf<String>()
                assertEquals(4, converted.byteSize)
                assertEquals(false, current.target.definition.compatibleWith(converted, mutableListOf(), reasons::add))
                assertTrue(reasons.any { it.contains("Key byte size") })
            },
        )
    }

    @Test
    fun changingEmbeddedTargetToSelfRequiresMigration() {
        val stored = EmbeddedIdentityOwner({ known }, { other })
        lateinit var current: EmbeddedIdentityOwner
        current = EmbeddedIdentityOwner({ known }, { current })

        assertIs<NeedsMigration>(current.isMigrationNeeded(stored)).apply {
            assertTrue(migrationReasons.any { it.contains("Other -> EmbeddedIdentityOwner") })
        }
    }

    @Test
    fun changingEmbeddedTargetToPreviouslyVisitedModelRequiresMigration() {
        val stored = EmbeddedIdentityOwner({ known }, { other })
        val current = EmbeddedIdentityOwner({ known }, { known })

        assertIs<NeedsMigration>(current.isMigrationNeeded(stored))
        assertEquals(UpToDate, current.isMigrationNeeded(EmbeddedIdentityOwner({ known }, { known })))
    }

    @Test
    fun unchangedEmbeddedCyclesTerminateAndRemainCompatible() {
        lateinit var storedSelf: EmbeddedIdentityNode
        lateinit var currentSelf: EmbeddedIdentityNode
        storedSelf = EmbeddedIdentityNode("Self", { storedSelf })
        currentSelf = EmbeddedIdentityNode("Self", { currentSelf })
        assertEquals(UpToDate, currentSelf.isMigrationNeeded(storedSelf))

        lateinit var storedFirst: EmbeddedIdentityNode
        lateinit var storedSecond: EmbeddedIdentityNode
        lateinit var currentFirst: EmbeddedIdentityNode
        lateinit var currentSecond: EmbeddedIdentityNode
        storedFirst = EmbeddedIdentityNode("First", { storedSecond })
        storedSecond = EmbeddedIdentityNode("Second", { storedFirst })
        currentFirst = EmbeddedIdentityNode("First", { currentSecond })
        currentSecond = EmbeddedIdentityNode("Second", { currentFirst })
        assertEquals(UpToDate, currentFirst.isMigrationNeeded(storedFirst))
    }
}
