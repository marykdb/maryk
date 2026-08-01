# Collect & Inject Values Requests

Maryk allows values from one response to be injected into another request. You can request an object, collect specific values from the response and use them in a follow‑up request on the same or a different data model.

This approach avoids multiple round trips between client and datastore by bundling related operations into a single request.

Combining several requests in one reduces latency and improves overall throughput.

The example below collects and injects values to fetch the friends of two people.

## Example: Fetching all data of the Friends of Two Persons

```kotlin
val context = RequestContext(definitionsContext)
val requests = Requests.create(context = context) {
    this.requests -= listOf(
        RequestType.Collect(
            CollectRequest(
                "collectedResponse",
                Person.get(
                    Person.key("dR9gVdRcSPw2molM1AiOng"),
                    Person.key("Vc4WgX/mQHYCSEoLtfLSUQ")
                )
            )
        ),
        RequestType.Get(
            // Injected requests stay as ObjectValues until the collected result is available.
            GetRequest.create(context = context) {
                from with Person
                keys with Inject(
                    "collectedResponse",
                    ValuesResponse {
                        values.atAny { values.refWithDM(Person) { friends } }
                    }
                )
            }
        )
    )
}

val responses = remote.execute(requests)
```

Within the batch, `CollectRequest` fetches two people and stores the response as
`collectedResponse`.

The following `GetRequest` fetches their friends. `Inject` pulls the friend
references from `collectedResponse` and injects them into the new request.

Batch responses are returned in request order. Execution stops at the first failure.
The batch is not transactionally atomic: writes completed before a later failure
remain committed.

## Conclusion

Collect & Inject enables dynamic request workflows by consolidating related queries, reducing round trips and speeding up data retrieval.
