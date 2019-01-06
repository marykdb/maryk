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
    "091d09" to "u3",
    "091d1109" to "ue3",
    "14" to 2,
    "147631" to "a",
    "14763232" to "b",
    "1c" to 2,
    "1c0000000109" to "t1",
    "1c0000000209" to "t2",
    "1c000000021109" to "te2",
    "24" to 2,
    "24000000021d09" to "m3",
    "24000000021d1109" to "me3",
    "2400000005" to TypedValue(V1, "TEST")
)
