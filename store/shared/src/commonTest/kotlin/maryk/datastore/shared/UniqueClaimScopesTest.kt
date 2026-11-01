package maryk.datastore.shared

import maryk.core.models.DataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.string
import maryk.core.properties.types.Bytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private object ClaimDetails : DataModel<ClaimDetails>() {
    val email by string(1u, unique = true)
    val label by string(2u)
}

private object ClaimRecord : RootDataModel<ClaimRecord>() {
    val details by embed(1u, dataModel = { ClaimDetails })
    val entries by map(2u, keyDefinition = StringDefinition(),
        valueDefinition = EmbeddedValuesDefinition(dataModel = { ClaimDetails }))
    val alias by string(3u, unique = true)
}

class UniqueClaimScopesTest {
    @Test
    fun replacingParentIncludesItsClaimsWithoutScanningUnrelatedFields() {
        val parent = ClaimRecord.details.ref().toStorageByteArray()
        val email = ClaimRecord.ref { details { email } }.toStorageByteArray()
        val alias = ClaimRecord.alias.ref().toStorageByteArray()
        val scopes = ClaimRecord.uniqueClaimScopes(listOf(email, alias, parent, parent.copyOf()))
        assertEquals(setOf(Bytes(parent), Bytes(alias)), scopes.map(::Bytes).toSet())
        assertTrue(email.isAffectedUniqueClaim(listOf(parent)))
        assertTrue(parent.isAffectedUniqueClaim(listOf(email)))
        assertFalse(alias.isAffectedUniqueClaim(listOf(parent)))
    }

    @Test
    fun mapEntryChangesKeepSiblingClaimsOutOfScope() {
        val first = ClaimRecord.ref { entries at "first" }.toStorageByteArray()
        val second = ClaimRecord.ref { entries at "second" }.toStorageByteArray()
        val email = ClaimDetails.ref(ClaimRecord.ref { entries at "first" }) { email }.toStorageByteArray()
        val scopes = ClaimRecord.uniqueClaimScopes(listOf(first, email))
        assertEquals(listOf(Bytes(first)), scopes.map(::Bytes))
        assertTrue(email.isAffectedUniqueClaim(listOf(first)))
        assertFalse(second.isAffectedUniqueClaim(listOf(first)))
    }

    @Test
    fun wholeRecordAndEmptyMutationHaveDifferentScopes() {
        assertEquals(listOf(Bytes(byteArrayOf())), ClaimRecord.uniqueClaimScopes(null).map(::Bytes))
        assertEquals(emptyList(), ClaimRecord.uniqueClaimScopes(emptyList()))
        val alias = ClaimRecord.alias.ref().toStorageByteArray()
        assertTrue(alias.isAffectedUniqueClaim(null))
        assertFalse(alias.isAffectedUniqueClaim(emptyList()))
    }
}
