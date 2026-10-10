package maryk.core.query.requests

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.exceptions.RequestException
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.query.RequestContext
import maryk.core.query.changes.Change
import maryk.core.query.changes.Check
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.values.ObjectValues
import maryk.core.query.responses.AtomicResponse
import maryk.core.query.responses.AtomicStatus
import maryk.test.models.Person
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class AtomicRequestTest {
    private fun context() = RequestContext(mapOf(
        SimpleMarykModel.Meta.name to DataModelReference(SimpleMarykModel),
        Person.Meta.name to DataModelReference(Person),
    ))
    private val request = atomicRequest("serialization") {
        val key = add(SimpleMarykModel, SimpleMarykModel.create { value with "haha-atomic" })
        add(Person, Person.create { firstName with "Atomic"; surname with "Test" })
        expect(SimpleMarykModel, key, Check(SimpleMarykModel.ref { value } with "haha-atomic"))
    }
    private fun compare(converted: AtomicRequest, original: AtomicRequest) {
        assertEquals(original.changeSetId, converted.changeSetId)
        assertEquals(original.requests, converted.requests)
        assertEquals(original.guards, converted.guards)
    }
    @Test
    fun json() { checkJsonConversion(request, AtomicRequest, ::context, ::compare) }
    @Test
    fun yaml() { checkYamlConversion(request, AtomicRequest, ::context, ::compare) }
    @Test
    fun protobuf() { checkProtoBufConversion(request, AtomicRequest, ::context, ::compare) }
    @Test
    fun batchEnvelope() {
        checkProtoBufConversion(Requests(request), Requests, ::context, checker = { converted, _ ->
            val value: Any = converted.requests.single()
            val decoded = if (value is ObjectValues<*, *>) value.toDataObject() else value
            compare(assertIs<AtomicRequest>(decoded), request)
        })
    }
    @Test
    fun rejectsInvalidMembers() {
        assertFailsWith<RequestException> { AtomicRequest("empty", emptyList()) }
        assertFailsWith<RequestException> { AtomicRequest("duplicate", request.requests + request.requests.first()) }
        val key = assertIs<AddRequest<SimpleMarykModel>>(request.requests.first()).keysForObjects!!.single()
        assertFailsWith<RequestException> { AtomicRequest("get", SimpleMarykModel.get(key)) }
        assertFailsWith<RequestException> { AtomicRequest("hard", SimpleMarykModel.delete(key, hardDelete = true)) }
        assertFailsWith<RequestException> { AtomicRequest("guards", request.requests, listOf(SimpleMarykModel.change(key.change(Change(SimpleMarykModel.ref { value } with "haha-new"))))) }
    }
    @Test
    fun responseRoundTrip() {
        val response = AtomicResponse(AtomicStatus.Unsupported, message = "unsupported")
        checkProtoBufConversion(response, AtomicResponse, ::context)
        checkJsonConversion(response, AtomicResponse, ::context)
        checkYamlConversion(response, AtomicResponse, ::context)
    }
}
