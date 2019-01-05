@file:Suppress("EXPERIMENTAL_UNSIGNED_LITERALS")

package maryk.core.processors.datastore

import maryk.test.models.ComplexMapModel
import maryk.test.models.EmbeddedMarykModel

val complexValues = ComplexMapModel(
    stringString = mapOf("v1" to "a", "v22" to "b"),
    intObject = mapOf(
        1u to EmbeddedMarykModel("t1"),
        2u to EmbeddedMarykModel("t2", EmbeddedMarykModel("te2"))
    )
)

val complexValuesAsStorables = arrayOf(
    "0c" to 2,
    "0c7631" to "a",
    "0c763232" to "b",
    "14" to 2,
    "140000000109" to "t1",
    "140000000209" to "t2",
    "14000000021109" to "te2"
)
