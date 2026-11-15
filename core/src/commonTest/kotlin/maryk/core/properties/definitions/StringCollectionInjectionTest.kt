package maryk.core.properties.definitions

import maryk.core.inject.Inject
import maryk.core.models.DataModel
import maryk.core.models.RootDataModel
import maryk.core.protobuf.WriteCache
import maryk.core.protobuf.LengthOnlyWriteCache
import maryk.core.query.createRequestContext
import kotlin.test.Test
import kotlin.test.assertEquals

private object StringCollectionChild : DataModel<StringCollectionChild>() {
    val entries by list(1u, valueDefinition = StringDefinition())
    val title by string(2u, required = false)
}

private object StringCollectionParent : RootDataModel<StringCollectionParent>() {
    val children by list(1u, valueDefinition = EmbeddedValuesDefinition(dataModel = { StringCollectionChild }))
    val tail by string(2u, required = false)
}

class StringCollectionInjectionTest {
    @Test
    fun stringCollectionsPreserveNestedSiblingInjectionPathsAndWireValues() {
        val context = StringCollectionParent.createRequestContext()
        val values = StringCollectionParent.create(context = context) {
            children with listOf(
                StringCollectionChild.create(context = context) {
                    entries with listOf("alpha", "β", "🙂")
                    title with Inject("first")
                },
                StringCollectionChild.create {
                    entries with emptyList()
                    title with "ordinary"
                },
            )
            tail with Inject("last")
        }
        val cache = WriteCache()
        val size = StringCollectionParent.Serializer.calculateProtoBufLength(values, cache, context)
        val bytes = ByteArray(size)
        var offset = 0
        StringCollectionParent.Serializer.writeProtoBuf(values, cache, { bytes[offset++] = it }, context)
        assertEquals(size, offset)
        assertEquals(listOf("children.@0.title", "tail"), context.collectedInjects?.map { it.reference.completeName })
        val sizingContext = StringCollectionParent.createRequestContext()
        assertEquals(size, StringCollectionParent.Serializer.calculateProtoBufLength(values, LengthOnlyWriteCache, sizingContext))
        assertEquals(context.collectedInjects?.map { it.reference.completeName }, sizingContext.collectedInjects?.map { it.reference.completeName })
        offset = 0
        val decoded = StringCollectionParent.Serializer.readProtoBuf(size, { bytes[offset++] }, StringCollectionParent.createRequestContext())
        val decodedChildren = decoded { children }!!
        assertEquals(listOf("alpha", "β", "🙂"), decodedChildren[0] { entries })
        assertEquals(emptyList(), decodedChildren[1] { entries })
        assertEquals("ordinary", decodedChildren[1] { title })
    }
}
