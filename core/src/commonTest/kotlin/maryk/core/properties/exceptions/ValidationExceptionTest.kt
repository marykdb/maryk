package maryk.core.properties.exceptions

import maryk.SimpleMarykModel
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import kotlin.test.Test

class ValidationExceptionTest {
    private val ref = SimpleMarykModel.ref { value }

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

    private val context = RequestContext(
        mapOf(
            SimpleMarykModel.name toUnitLambda { SimpleMarykModel }
        ),
        dataModel = SimpleMarykModel
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
