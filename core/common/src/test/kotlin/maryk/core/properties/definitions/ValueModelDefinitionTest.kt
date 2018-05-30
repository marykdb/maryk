package maryk.core.properties.definitions

import maryk.TestValueObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.ByteCollector
import maryk.core.properties.exceptions.OutOfRangeException
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.query.DataModelContext
import maryk.lib.time.Date
import maryk.lib.time.DateTime
import maryk.lib.time.Time
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

internal class ValueModelDefinitionTest {
    private val def = ValueModelDefinition(
        dataModel = TestValueObject
    )
    private val defMaxDefined = ValueModelDefinition(
        indexed = true,
        required = false,
        final = true,
        searchable = false,
        unique = true,
        minValue = TestValueObject(
            int = 0,
            dateTime = DateTime(2007,12,5),
            bool = false
        ),
        maxValue = TestValueObject(
            int = 999,
            dateTime = DateTime(2017,12,5),
            bool = true
        ),
        dataModel = TestValueObject,
        default = TestValueObject(
            int = 10,
            dateTime = DateTime(2010,10,10),
            bool = true
        )
    )

    @Test
    fun hasValues() {
        def.dataModel shouldBe TestValueObject
    }

    val value = TestValueObject(
        int = 4,
        dateTime = DateTime(date = Date.nowUTC(), time = Time.nowUTC().copy(milli = 0)),
        bool = true
    )

    @Test
    fun convert_values_to_storage_bytes_and_back() {
        val bc = ByteCollector()
        bc.reserve(
            def.calculateStorageByteLength(value)
        )
        def.writeStorageBytes(value, bc::write)
        val new = def.readStorageBytes(bc.size, bc::read)

        new shouldBe value
    }

    @Test
    fun convert_values_to_transport_bytes_and_back() {
        val bc = ByteCollector()

        checkProtoBufConversion(bc, value, this.def)
    }

    @Test
    fun convert_values_to_String_and_back() {
        def.fromString(
            def.asString(value)
        ) shouldBe value
    }

    @Test
    fun validate() {
        def.validateWithRef(newValue = TestValueObject(
            int = 4,
            dateTime = DateTime.nowUTC(),
            bool = true
        ))
        val e = shouldThrow<ValidationUmbrellaException> {
            def.validateWithRef(
                newValue = TestValueObject(
                    int = 1000,
                    dateTime = DateTime.nowUTC(),
                    bool = true
                )
            )
        }

        e.exceptions.size shouldBe 1

        with (e.exceptions[0] as OutOfRangeException) {
            this.reference!!.completeName shouldBe "int"
        }
    }

    @Test
    fun convert_definition_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.def, ValueModelDefinition.Model, { DataModelContext() })
        checkProtoBufConversion(this.defMaxDefined, ValueModelDefinition.Model, { DataModelContext() })
    }

    @Test
    fun convert_definition_to_JSON_and_back() {
        checkJsonConversion(this.def, ValueModelDefinition.Model, { DataModelContext() })
        checkJsonConversion(this.defMaxDefined, ValueModelDefinition.Model, { DataModelContext() })
    }

    @Test
    fun convert_definition_to_YAML_and_back() {
        checkYamlConversion(this.def, ValueModelDefinition.Model, { DataModelContext() })
        checkYamlConversion(this.defMaxDefined, ValueModelDefinition.Model, { DataModelContext() }) shouldBe """
        indexed: true
        searchable: false
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
    }
}
