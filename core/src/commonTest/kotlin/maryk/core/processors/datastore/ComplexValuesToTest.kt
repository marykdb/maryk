@file:Suppress("EXPERIMENTAL_UNSIGNED_LITERALS", "EXPERIMENTAL_API_USAGE")

package maryk.core.processors.datastore

import maryk.core.properties.types.TypedValue
import maryk.test.models.ComplexModel
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.Option.V1
import maryk.test.models.Option.V3

val complexValues = ComplexModel(
    multi = TypedValue(V3, EmbeddedMarykModel("u3", EmbeddedMarykModel("ue3"))),
    mapStringString = mapOf("v1" to "a", "v22" to "b"),
    mapIntObject = mapOf(
        1u to EmbeddedMarykModel("t1"),
        2u to EmbeddedMarykModel("t2", EmbeddedMarykModel("te2"))
    ),
    mapIntMulti = mapOf(
        2u to TypedValue(V3, EmbeddedMarykModel("m3", EmbeddedMarykModel("me3"))),
        5u to TypedValue(V1, "TEST")
    )
)

val complexValuesAsStorables = arrayOf(
    "09" to TypedValue(V3, Unit),
    "091d" to Unit,
    "091d09" to "u3",
    "091d16" to Unit,
    "091d1609" to "ue3",
    "14" to 2,
    "14027631" to "a",
    "1403763232" to "b",
    "1c" to 2,
    "1c0400000001" to Unit,
    "1c040000000109" to "t1",
    "1c0400000002" to Unit,
    "1c040000000209" to "t2",
    "1c040000000216" to Unit,
    "1c04000000021609" to "te2",
    "24" to 2,
    "240400000002" to TypedValue(V3, Unit),
    "2404000000021d" to Unit,
    "2404000000021d09" to "m3",
    "2404000000021d16" to Unit,
    "2404000000021d1609" to "me3",
    "240400000005" to TypedValue(V1, "TEST")
)
