package maryk.datastore.shared

import maryk.core.exceptions.RequestException
import maryk.core.query.changes.Change
import maryk.core.query.changes.Check
import maryk.core.query.changes.IncMapAddition
import maryk.core.query.changes.IncMapChange
import maryk.core.query.changes.IncMapKeyAdditions
import maryk.core.query.changes.IndexChange
import maryk.core.query.changes.MultiTypeChange
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReplicationChangesTest {
    @Test
    fun initialCreationInitializesMissingMapsAndPreservesExplicitLaterValues() {
        val reference = TestMarykModel.ref { incMap }
        val resolved = normalizeReplicationChanges(listOf(
            ObjectCreate,
            IncMapChange(reference.change(addValues = listOf("first"))),
            Change(reference.propertyDefinition.valueRef(42u, reference) with "explicit-after"),
            IncMapAddition(IncMapKeyAdditions(reference, listOf(42u), null)),
            IncMapAddition(IncMapKeyAdditions(reference, listOf(41u), listOf("second"))),
        ))
        val values = TestMarykModel.fromReplicationChanges(null, resolved)
        assertEquals(mapOf(42u to "explicit-after", 41u to "second"), values[reference])
    }

    @Test
    fun sourceAllocatedKeysPreserveMutationOrderAndIgnoreDerivedMetadata() {
        val reference = TestMarykModel.ref { incMap }
        val replacement = Change(reference.propertyDefinition.valueRef(42u, reference) with "later")
        val result = normalizeReplicationChanges(listOf(
            Check(TestMarykModel.ref { string } with "source precondition"),
            IncMapChange(reference.change(addValues = listOf("first"))),
            replacement,
            IndexChange(emptyList()),
            MultiTypeChange(),
            IncMapAddition(IncMapKeyAdditions(reference, listOf(42u), null)),
        ))
        assertEquals(listOf(Change(reference.propertyDefinition.valueRef(42u, reference) with "first"), replacement), result)
    }

    @Test
    fun separateAllocationsForTheSameMapRetainTheirOwnSourceKeysAndValues() {
        val reference = TestMarykModel.ref { incMap }
        val result = normalizeReplicationChanges(listOf(
            IncMapChange(reference.change(addValues = listOf("first"))),
            IncMapChange(reference.change(addValues = listOf("second"))),
            IncMapAddition(IncMapKeyAdditions(reference, listOf(42u), listOf("first")),
                IncMapKeyAdditions(reference, listOf(41u), listOf("second"))),
        ))
        assertEquals(listOf(Change(reference.propertyDefinition.valueRef(42u, reference) with "first"),
            Change(reference.propertyDefinition.valueRef(41u, reference) with "second")), result)
    }

    @Test
    fun unresolvedOrIncompleteAllocationsFailBeforeAnyReplay() {
        val reference = TestMarykModel.ref { incMap }
        assertFailsWith<RequestException> { normalizeReplicationChanges(listOf(IncMapChange(reference.change(addValues = listOf("first"))))) }
        assertFailsWith<RequestException> { normalizeReplicationChanges(listOf(IncMapAddition(IncMapKeyAdditions(reference, listOf(42u), null)))) }
        assertFailsWith<RequestException> { normalizeReplicationChanges(listOf(IncMapAddition(IncMapKeyAdditions(reference, listOf(42u, 41u), listOf("first"))))) }
    }
}
