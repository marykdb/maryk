package maryk.core.properties.references

import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.incrementingMap
import maryk.core.properties.exceptions.InvalidSizeException
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.properties.types.numeric.UInt32
import maryk.core.protobuf.WriteCache
import maryk.test.ByteCollector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

private object AllocationValidationRecord : RootDataModel<AllocationValidationRecord>() {
    val text by incrementingMap(1u, keyNumberDescriptor = UInt32, valueDefinition = StringDefinition(minSize = 3u))
    val lists by incrementingMap(2u, keyNumberDescriptor = UInt32,
        valueDefinition = ListDefinition(valueDefinition = StringDefinition(minSize = 3u)))
    val sets by incrementingMap(3u, keyNumberDescriptor = UInt32,
        valueDefinition = SetDefinition(valueDefinition = StringDefinition(minSize = 3u)))
}

class IncMapValidationReferenceTest {
    @Test
    fun rejectedAdditionMembersRetainReadableReferences() {
        val model = AllocationValidationRecord
        for (index in listOf(0, 127, 128, 255, 256, Int.MAX_VALUE)) {
            checkFailure(model.text.addIndexRef(index, model.text.ref()), "x", "text.^$index")
        }
        checkFailure(model.lists.addIndexRef(0, model.lists.ref()), listOf("x"), "lists.^0.@0")
        checkFailure(model.sets.addIndexRef(0, model.sets.ref()), setOf("x"), "sets.^0.#x")
    }

    @Test
    fun additionIndexesCannotBeNegative() {
        assertFailsWith<IllegalArgumentException> {
            AllocationValidationRecord.text.addIndexRef(-1, AllocationValidationRecord.text.ref())
        }
    }

    private fun <T : Any> checkFailure(
        reference: IsPropertyReference<T, IsPropertyDefinition<T>, *>,
        value: T,
        expected: String,
    ) {
        var failure = assertFailsWith<ValidationException> {
            reference.propertyDefinition.validateWithRef(null, value) { reference }
        }
        while (failure is ValidationUmbrellaException) failure = failure.exceptions.single()
        val actual = requireNotNull(assertIs<InvalidSizeException>(failure).reference)
        assertEquals(expected, actual.completeName)
        assertEquals(actual, AllocationValidationRecord.getPropertyReferenceByName(expected))
        ByteCollector().apply {
            val cache = WriteCache()
            reserve(actual.calculateTransportByteLength(cache))
            actual.writeTransportBytes(cache, ::write)
            assertEquals(actual, AllocationValidationRecord.getPropertyReferenceByBytes(size, ::read))
        }
    }
}
