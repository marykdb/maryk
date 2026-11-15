package maryk.datastore.shared

import maryk.core.models.RootDataModel
import maryk.core.models.graph
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.string
import maryk.core.properties.definitions.reference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.filters.Equals
import maryk.core.query.filters.Exists
import maryk.core.query.pairs.with
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

private object ReadSelectionModel : RootDataModel<ReadSelectionModel>() {
    val first by string(1u)
    val second by string(2u)
    val entries by list(3u, valueDefinition = StringDefinition())
    val target by reference(4u, dataModel = { ReadSelectionModel })
}

private object ForeignReadSelection : RootDataModel<ForeignReadSelection>() {
    val first by string(1u)
}

class ScalarReadSelectionTest {
    @Test
    fun onlyCanonicalBuiltInRootScalarsAreAccepted() {
        assertSame(ReadSelectionModel.first, ReadSelectionModel.rootScalarReadProperty(ReadSelectionModel.first.ref()))
        assertNull(ReadSelectionModel.rootScalarReadProperty(ForeignReadSelection.first.ref()))
        assertNull(ReadSelectionModel.rootScalarReadProperty(ReadSelectionModel.entries.ref()))
        assertNull(ReadSelectionModel.rootScalarReadProperty(alias(ReadSelectionModel.first.ref())))
        assertSame(ReadSelectionModel.target, simpleScalarReadProperty(ReadSelectionModel.target))
        assertNull(ReadSelectionModel.rootScalarReadProperty(ReadSelectionModel.target.ref()))
    }

    @Test
    fun requiredInputsUnionSelectionAndPredicatesWithoutExposingExtraFields() {
        val selected = ReadSelectionModel.graph { listOf(first) }
        val planned = ReadSelectionModel.requiredReadSelection(selected, Equals(ReadSelectionModel.second.ref() with "filter"), null)
        assertEquals(listOf(1u, 2u), planned?.properties?.map { it.index })
        assertEquals(listOf(1u), selected.properties.map { it.index })
        assertNull(ReadSelectionModel.requiredReadSelection(selected, Exists(ReadSelectionModel.entries.ref()), null))
    }

    private fun <T : Any, D : IsPropertyDefinition<T>, V : Any> alias(reference: IsPropertyReference<T, D, V>) =
        object : IsPropertyReference<T, D, V> by reference {}
}
