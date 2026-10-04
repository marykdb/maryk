package maryk.datastore.shared.encryption

import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.fixedBytes
import maryk.core.properties.definitions.geoPoint
import maryk.core.properties.definitions.index.AnyOf
import maryk.core.properties.definitions.index.GeoHash
import maryk.core.properties.definitions.index.LegacyNormalize
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.index.Normalize
import maryk.core.properties.definitions.index.ReferenceToMax
import maryk.core.properties.definitions.index.Reversed
import maryk.core.properties.definitions.index.Split
import maryk.core.properties.definitions.index.SplitOn
import maryk.core.properties.definitions.index.UUIDv4Key
import maryk.core.properties.definitions.index.UUIDv7Key
import maryk.core.properties.definitions.string
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IndexDependenciesTest {
    @Test
    fun detectsSensitiveLeavesThroughEveryBuiltInCompositeAndTransform() {
        val secret = IndexModel.secret.ref()
        listOf(
            secret,
            AnyOf("search", secret),
            Multiple(Multiple(AnyOf(secret))),
            Normalize(Normalize(secret)),
            Normalize(Split(secret, SplitOn.Whitespace)),
            LegacyNormalize(Normalize(secret)),
            Split(Normalize(secret), SplitOn.WordBoundary),
            Reversed(secret),
        ).forEach { index ->
            assertTrue(index.mayContainPropertyReference(secret), index.toString())
            assertFalse(index.mayContainPropertyReference(IndexModel.publicValue.ref()), index.toString())
        }
        assertTrue(GeoHash(IndexModel.location.ref()).mayContainPropertyReference(IndexModel.location.ref()))
        assertTrue(ReferenceToMax(IndexModel.id.ref()).mayContainPropertyReference(IndexModel.id.ref()))
        assertFalse(UUIDv4Key.mayContainPropertyReference(secret))
        assertFalse(UUIDv7Key.mayContainPropertyReference(secret))
    }
}

private object IndexModel : RootDataModel<IndexModel>() {
    val secret by string(1u)
    val publicValue by string(2u)
    val location by geoPoint(3u)
    val id by fixedBytes(4u, byteSize = 16, final = true)
}
