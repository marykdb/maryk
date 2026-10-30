package maryk.datastore.shared

import maryk.core.exceptions.RequestException
import maryk.core.models.key
import maryk.core.query.changes.Change
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.pairs.with
import maryk.core.query.responses.updates.InitialChangesUpdate
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertFailsWith

class UpdateVersionValidationTest {
    private val key = SimpleMarykModel.key(SimpleMarykModel.create { value with "haha-update" })
    private val changes = listOf(ObjectCreate, Change(SimpleMarykModel.ref { value } with "haha-update"))

    @Test
    fun initialEnvelopeBoundsEveryRevisionBeforeReplay() {
        assertFailsWith<RequestException> {
            requireSafeUpdateVersion(InitialChangesUpdate(20uL, listOf(
                DataObjectVersionedChange(key = key, changes = listOf(VersionedChanges(10uL, changes),
                    VersionedChanges(21uL, changes))),
            )))
        }
    }

    @Test
    fun unorderedAndRepeatedRecordRevisionsRejectAcrossGroups() {
        for (later in listOf(19uL, 20uL)) {
            for (splitGroups in listOf(false, true)) {
                val first = VersionedChanges(20uL, changes)
                val second = VersionedChanges(later, changes)
                val records = if (splitGroups) listOf(
                    DataObjectVersionedChange(key = key, changes = listOf(first)),
                    DataObjectVersionedChange(key = key, changes = listOf(second)),
                ) else listOf(DataObjectVersionedChange(key = key, changes = listOf(first, second)))
                assertFailsWith<RequestException> { requireSafeUpdateVersion(InitialChangesUpdate(30uL, records)) }
            }
        }
        requireSafeUpdateVersion(InitialChangesUpdate(30uL, listOf(
            DataObjectVersionedChange(key = key, changes = listOf(VersionedChanges(20uL, changes))),
            DataObjectVersionedChange(key = key, changes = listOf(VersionedChanges(30uL, changes))),
        )))
    }

    @Test
    fun envelopeCanEqualOrExceedLastRevisionAtSafeBoundary() {
        for (version in listOf(20uL, ULong.MAX_VALUE - 2uL)) {
            requireSafeUpdateVersion(InitialChangesUpdate(version, listOf(
                DataObjectVersionedChange(key = key, changes = listOf(VersionedChanges(10uL, changes),
                    VersionedChanges(version, changes))),
            )))
        }
        requireSafeUpdateVersion(InitialChangesUpdate(20uL, emptyList<DataObjectVersionedChange<SimpleMarykModel>>()))
    }
}
