package maryk.core.query.requests

import java.lang.reflect.Modifier
import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.query.filters.IsFilter
import maryk.core.query.responses.DataFetchType
import maryk.core.query.responses.UpdatesResponse
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScanUpdateHistoryJvmCompatibilityTest {
    @Test
    fun legacyResponseCopyDescriptorsRemainPublicAndPreserveCursor() {
        val responseClass = UpdatesResponse::class.java
        val parameters = arrayOf(IsRootDataModel::class.java, List::class.java, DataFetchType::class.java)
        val copy = responseClass.getDeclaredMethod("copy", *parameters)
        val copyDefault = responseClass.getDeclaredMethod(
            "copy\$default", responseClass, *parameters, Int::class.javaPrimitiveType!!, Any::class.java,
        )
        assertTrue(Modifier.isPublic(copy.modifiers))
        assertTrue(Modifier.isPublic(copyDefault.modifiers))
        assertTrue(Modifier.isStatic(copyDefault.modifiers))
        assertEquals(responseClass, copy.returnType)
        assertEquals(responseClass, copyDefault.returnType)

        val request = SimpleMarykModel.scanUpdateHistory()
        val key = SimpleMarykModel.key("WWurg6ysTsozoMei_SurOw")
        val response = UpdatesResponse(SimpleMarykModel, emptyList(), null, request.createHistoryCursor(900uL, 100uL, key))
        assertEquals(response, copy.invoke(response, SimpleMarykModel, emptyList<Any>(), null))
        assertEquals(response, copyDefault.invoke(null, response, null, null, null, 7, null))
    }

    @Test
    fun legacyCopyDescriptorsRemainPublicAndPreserveCursor() {
        val requestClass = ScanUpdateHistoryRequest::class.java
        val parameters = arrayOf(
            IsRootDataModel::class.java,
            IsFilter::class.java,
            Int::class.javaPrimitiveType!!,
            Long::class.javaPrimitiveType!!,
            ULong::class.java,
            RootPropRefGraph::class.java,
            Boolean::class.javaPrimitiveType!!,
        )
        // These names and parameter types are the original generated data-class ABI.
        val copy = requestClass.getDeclaredMethod("copy-eIM7m1U", *parameters)
        val copyDefault = requestClass.getDeclaredMethod(
            "copy-eIM7m1U\$default",
            requestClass,
            *parameters,
            Int::class.javaPrimitiveType!!,
            Any::class.java,
        )
        assertTrue(Modifier.isPublic(copy.modifiers))
        assertTrue(Modifier.isPublic(copyDefault.modifiers))
        assertTrue(Modifier.isStatic(copyDefault.modifiers))
        assertEquals(requestClass, copy.returnType)
        assertEquals(requestClass, copyDefault.returnType)

        val initial = SimpleMarykModel.scanUpdateHistory(limit = 25u)
        val key = SimpleMarykModel.key("WWurg6ysTsozoMei_SurOw")
        val request = initial.copy(cursor = initial.createHistoryCursor(900uL, 100uL, key))
        val copied = copy.invoke(request, request.dataModel, request.where, 10, 0L, null, null, true)
        assertEquals(request.copy(limit = 10u), copied)
        // Default every argument except limit, as an existing copy(limit = 10u) caller does.
        val copiedWithDefaults = copyDefault.invoke(null, request, null, null, 10, 0L, null, null, false, 123, null)
        assertEquals(request.copy(limit = 10u), copiedWithDefaults)
    }
}
