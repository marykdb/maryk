package maryk.core.properties.exceptions

import maryk.SimpleMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.models.RootDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.query.DataModelPropertyContext
import kotlin.test.Test

class ValidationExceptionTest {
    private val ref = SimpleMarykObject.ref { value }

    private val validationUmbrellaException = ValidationUmbrellaException(null, listOf(
        AlreadySetException(ref),
        InvalidSizeException(ref, "wrong", 1, 3),
        InvalidValueException(ref, "wrong"),
        OutOfRangeException(ref, "wrong", "a", "g"),
        RequiredException(ref),
        ValidationUmbrellaException(
            ref,
            listOf(
                NotEnoughItemsException(ref, 2, 3),
                TooMuchItemsException(ref, 10, 3)
            )
        )
    ))

    @Suppress("UNCHECKED_CAST")
    private val context = DataModelPropertyContext(
        mapOf(
            SimpleMarykObject.name to { SimpleMarykObject }
        ),
        dataModel = SimpleMarykObject as RootDataModel<Any, ObjectPropertyDefinitions<Any>>
    )

    @Test
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.validationUmbrellaException, ValidationUmbrellaException, { this.context })
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(this.validationUmbrellaException, ValidationUmbrellaException, { this.context })
    }

    @Test
    fun convert_to_YALM_and_back() {
        checkYamlConversion(this.validationUmbrellaException, ValidationUmbrellaException, { this.context })
    }
}
