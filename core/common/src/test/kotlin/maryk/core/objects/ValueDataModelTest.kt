package maryk.core.objects

import maryk.TestValueObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.query.DataModelContext
import kotlin.test.Test

internal class ValueDataModelTest {
    @Test
    fun convert_definition_to_ProtoBuf_and_back() {
        checkProtoBufConversion(TestValueObject, ValueDataModel.Model, DataModelContext(), ::compareDataModels)
    }

    @Test
    fun convert_definition_to_JSON_and_back() {
        checkJsonConversion(TestValueObject, ValueDataModel.Model, DataModelContext(), ::compareDataModels)
    }
}

