package maryk.core.properties.exceptions

import maryk.SimpleMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.PropertyDefinitions
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
                            TooLittleItemsException(ref, 2, 3),
                            TooMuchItemsException(ref, 10, 3)
                    )
            )
    ))

    @Suppress("UNCHECKED_CAST")
    private val context = DataModelPropertyContext(
            mapOf(
                    SimpleMarykObject.name to SimpleMarykObject
            ),
            dataModel = SimpleMarykObject as RootDataModel<Any, PropertyDefinitions<Any>>
    )

    @Test
    fun testProtoBufConversion() {
        checkProtoBufConversion(this.validationUmbrellaException, ValidationUmbrellaException, this.context)
    }

    @Test
    fun testJsonConversion() {
        checkJsonConversion(this.validationUmbrellaException, ValidationUmbrellaException, this.context)
    }
}