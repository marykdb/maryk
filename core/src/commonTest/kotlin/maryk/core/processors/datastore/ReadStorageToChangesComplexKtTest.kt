package maryk.core.processors.datastore

import maryk.core.properties.definitions.wrapper.at
import maryk.core.properties.definitions.wrapper.atKeyAndType
import maryk.core.properties.definitions.wrapper.refAtKey
import maryk.core.properties.definitions.wrapper.refAtKeyAndType
import maryk.core.properties.types.TypedValue
import maryk.core.query.changes.Change
import maryk.core.query.changes.MultiTypeChange
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.pairs.with
import maryk.core.query.pairs.withType
import maryk.lib.extensions.initByteArrayByHex
import maryk.test.models.ComplexModel
import maryk.test.models.MultiTypeEnum.T1
import maryk.test.models.MultiTypeEnum.T3
import maryk.test.models.MultiTypeEnum.T4
import maryk.test.shouldBe
import kotlin.test.Test

private val complexValuesAsStorablesWithVersion = arrayOf(
    "09" to arrayOf(1234uL to TypedValue(T3, Unit)),
    "091d" to arrayOf(1234uL to Unit),
    "091d09" to arrayOf(1234uL to "u3"),
    "091d16" to arrayOf(1234uL to Unit),
    "091d1609" to arrayOf(1234uL to "ue3"),
    "14" to arrayOf(1234uL to 2),
    "14027631" to arrayOf(1234uL to "a"),
    "1403763232" to arrayOf(1234uL to "b"),
    "1c" to arrayOf(1234uL to 2),
    "1c0400000001" to arrayOf(1234uL to Unit),
    "1c040000000109" to arrayOf(1234uL to "t1"),
    "1c0400000002" to arrayOf(1234uL to Unit),
    "1c040000000209" to arrayOf(1234uL to "t2"),
    "1c040000000216" to arrayOf(1234uL to Unit),
    "1c04000000021609" to arrayOf(1234uL to "te2"),
    "24" to arrayOf(1234uL to 6),
    "240400000002" to arrayOf(1234uL to TypedValue(T3, Unit)),
    "2404000000021d" to arrayOf(1234uL to Unit),
    "2404000000021d09" to arrayOf(1234uL to "m3"),
    "2404000000021d16" to arrayOf(1234uL to Unit),
    "2404000000021d1609" to arrayOf(1234uL to "me3"),
    "240400000005" to arrayOf(1234uL to TypedValue(T1, "TEST")),
    "240400000007" to arrayOf(1234uL to TypedValue(T4, Unit))
)

class ReadStorageToChangesComplexKtTest {
    @Test
    fun convertStorageToChanges() {
        var qualifierIndex = -1
        val values = ComplexModel.readStorageToChanges(
            getQualifier = {
                complexValuesAsStorablesWithVersion.getOrNull(++qualifierIndex)?.let {
                    initByteArrayByHex(it.first)
                }
            },
            select = null,
            processValue = { _, _, changer ->
                complexValuesAsStorablesWithVersion[qualifierIndex].second.forEach {
                    changer(it.first, it.second)
                }
            }
        )

        values shouldBe listOf(
            VersionedChanges(
                1234UL,
                listOf(
                    MultiTypeChange(
                        ComplexModel.ref { multi } withType T3,
                        ComplexModel { mapIntMulti.refAt(2u) } withType T3,
                        ComplexModel { mapIntMulti.refAt(7u) } withType T4
                    ),
                    Change(
                        ComplexModel { multi.refWithType(T3) { value } } with "u3",
                        ComplexModel { multi.withType(T3) { model.ref { value } } } with "ue3",
                        ComplexModel { mapStringString.refAt("v1") } with "a",
                        ComplexModel { mapStringString.refAt("v22") } with "b",
                        ComplexModel { mapIntObject.refAt(1u) } with Unit,
                        ComplexModel { mapIntObject.refAtKey(1u) { value } } with "t1",
                        ComplexModel { mapIntObject.refAt(2u) } with Unit,
                        ComplexModel { mapIntObject.refAtKey(2u) { value } } with "t2",
                        ComplexModel { mapIntObject.at(2u) { model.ref { value } } } with "te2",
                        ComplexModel { mapIntMulti.refAtKeyAndType(2u, T3) { value } } with "m3",
                        ComplexModel { mapIntMulti.atKeyAndType(2u, T3) { model.ref { value } } } with "me3",
                        ComplexModel { mapIntMulti.refAt(5u) } with TypedValue(T1, "TEST")
                    )
                )
            )
        )
    }
}
