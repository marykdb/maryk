# Collect & Inject Values Requests

Maryk allows values from one response to be injected into another request. You can request an object, collect specific values from the response and use them in a follow‑up request on the same or a different data model.

This approach avoids multiple round trips between client and datastore by bundling related operations into a single request.

Combining several requests in one reduces latency and improves overall throughput.

The example below collects and injects values to fetch the friends of two people.

## Example: Fetching all data of the Friends of Two Persons

```kotlin
Requests(
    CollectRequest(
        "collectedResponse",
        Person.get(
            Person.key("dR9gVdRcSPw2molM1AiOng"),
            Person.key("Vc4WgX/mQHYCSEoLtfLSUQ")
        )
    ),
    // This get request needs to be defined as an ObjectValues object so the response can be injected later
    GetRequest.run {
        create(
            from with Person,
            keys injectWith Inject(
                "collectedResponse",
                // Reference to the friends within the Values of the response
                ValuesResponse { values.atAny { values.refWithDM(Person) { friends } } }
            ),
            context = context,
        )
    }
)
```

First we send a `CollectRequest` to fetch two people and store the response as `collectedResponse`.

Next we define a `GetRequest` to fetch their friends. `injectWith` pulls the friend references from `collectedResponse` and injects them into the new request.

## Conclusion

Collect & Inject enables dynamic request workflows by consolidating related queries, reducing round trips and speeding up data retrieval.
