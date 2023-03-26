package maryk.test.requests

import maryk.core.properties.key
import maryk.core.query.requests.delete
import maryk.test.models.SimpleMarykModel

private val key1 = SimpleMarykModel.key("B4CeT0fDRxYnEmSTQuLA2A")
private val key2 = SimpleMarykModel.key("oDHjQh7GSDwyPX2kTUAniQ")

val deleteRequest = SimpleMarykModel.delete(
    key1,
    key2,
    hardDelete = true
)
