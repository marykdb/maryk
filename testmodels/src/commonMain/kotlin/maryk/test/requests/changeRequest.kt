package maryk.test.requests

import maryk.core.query.changes.change
import maryk.core.query.requests.change
import maryk.test.models.SimpleMarykModel

private val key1 = SimpleMarykModel.key("MYc6LBYcT38nWxoE1ahNxA")
private val key2 = SimpleMarykModel.key("lneV6ioyQL0vnbkLqwVw+A")

val changeRequest = SimpleMarykModel.Model.change(
    key1.change(),
    key2.change()
)
