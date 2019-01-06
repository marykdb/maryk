@file:Suppress("EXPERIMENTAL_UNSIGNED_LITERALS", "EXPERIMENTAL_API_USAGE")

package maryk.core.processors.datastore

import maryk.core.properties.types.TypedValue
import maryk.test.models.ComplexMapModel
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.Option.V1
import maryk.test.models.Option.V3

val complexValues = ComplexMapModel(
    stringString = mapOf("v1" to "a", "v22" to "b"),
    intObject = mapOf(
        1u to EmbeddedMarykModel("t1"),
        2u to EmbeddedMarykModel("t2", EmbeddedMarykModel("te2"))
    ),
    intMulti = mapOf(
        2u to TypedValue(V3, EmbeddedMarykModel("m3", EmbeddedMarykModel("me3"))),
        5u to TypedValue(V1, "TEST")
    )
)

val complexValuesAsStorables = arrayOf(
    "0c" to 2,
    "0c7631" to "a",
    "0c763232" to "b",
    "14" to 2,
    "140000000109" to "t1",
    "140000000209" to "t2",
    "14000000021109" to "te2",
    "1c" to 2,
    "1c000000021d09" to "m3",
    "1c000000021d1109" to "me3",
    "1c00000005" to TypedValue(V1, "TEST")
)
