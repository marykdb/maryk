package maryk.core.properties.definitions

import kotlinx.datetime.LocalDateTime
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.exceptions.OutOfRangeException
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.properties.types.DateUnit
import maryk.core.properties.types.roundToDateUnit
import maryk.core.query.DefinitionsContext
import maryk.test.ByteCollector
import maryk.test.models.TestValueObject
import maryk.test.models.TestValueObject2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.expect

internal class ValueObjectDefinitionTest {
    private val def = ValueObjectDefinition(
        dataModel = TestValueObject,
    )
    private val defMaxDefined = ValueObjectDefinition(
        required = false,
        final = true,
        unique = true,
        minValue = TestValueObject(
            int = 0,
            dateTime = LocalDateTime(2007, 12, 5, 0, 0, 0),
            bool = false
        ),
        maxValue = TestValueObject(
            int = 999,
            dateTime = LocalDateTime(2017, 12, 5, 0, 0, 0),
            bool = true
        ),
        dataModel = TestValueObject,
        default = TestValueObject(
            int = 10,
            dateTime = LocalDateTime(2010, 10, 10, 0, 0, 0),
            bool = true
        )
    )

    @Test
    fun hasValues() {
        expect(TestValueObject) { def.dataModel }
    }

    val value = TestValueObject(
        int = 4,
        dateTime = DateTimeDefinition.nowUTC().roundToDateUnit(DateUnit.Seconds),
        bool = true
    )

    @Test
    fun convertValuesToStorageBytesAndBack() {
        val bc = ByteCollector()
        bc.reserve(
            def.calculateStorageByteLength(value)
        )
        def.writeStorageBytes(value, bc::write)
        val new = def.readStorageBytes(bc.size, bc::read)

        assertEquals(value, new)
    }

    @Test
    fun convertValuesToTransportBytesAndBack() {
        val bc = ByteCollector()

        checkProtoBufConversion(bc, value, this.def)
    }

    @Test
    fun convertValuesToStringAndBack() {
        expect(value) {
            def.fromString(
                def.asString(value)
            )
        }
    }

    @Test
    fun validate() {
        def.validateWithRef(
            newValue = TestValueObject(
                int = 4,
                dateTime = DateTimeDefinition.nowUTC(),
                bool = true
            )
        )
        val e = assertFailsWith<ValidationUmbrellaException> {
            def.validateWithRef(
                newValue = TestValueObject(
                    int = 1000,
                    dateTime = DateTimeDefinition.nowUTC(),
                    bool = true
                )
            )
        }

        expect(1) { e.exceptions.size }

        with(e.exceptions[0] as OutOfRangeException) {
            expect("int") { this.reference!!.completeName }
        }
    }

    @Test
    fun convertDefinitionToProtoBufAndBack() {
        checkProtoBufConversion(this.def, ValueObjectDefinition.Model, { DefinitionsContext() })
        checkProtoBufConversion(this.defMaxDefined, ValueObjectDefinition.Model, { DefinitionsContext() })
    }

    @Test
    fun convertDefinitionToJSONAndBack() {
        checkJsonConversion(this.def, ValueObjectDefinition.Model, { DefinitionsContext() })
        checkJsonConversion(this.defMaxDefined, ValueObjectDefinition.Model, { DefinitionsContext() })
    }

    @Test
    fun convertDefinitionToYAMLAndBack() {
        checkYamlConversion(this.def, ValueObjectDefinition.Model, { DefinitionsContext() })

        expect(
            """
            required: false
            final: true
            unique: true
            dataModel: TestValueObject
            minValue:
              int: 0
              dateTime: '2007-12-05T00:00'
              bool: false
            maxValue:
              int: 999
              dateTime: '2017-12-05T00:00'
              bool: true
            default:
              int: 10
              dateTime: '2010-10-10T00:00'
              bool: true

            """.trimIndent()
        ) {
            checkYamlConversion(this.defMaxDefined, ValueObjectDefinition.Model, { DefinitionsContext() })
        }
    }


    @Test
    fun isCompatible() {
        assertTrue {
            ValueObjectDefinition(dataModel = TestValueObject).compatibleWith(
                ValueObjectDefinition(dataModel = TestValueObject)
            )
        }

        assertFalse {
            ValueObjectDefinition(dataModel = TestValueObject).compatibleWith(
                ValueObjectDefinition(dataModel = TestValueObject2)
            )
        }
    }
}
