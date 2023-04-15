# Collect & Inject Values Requests

In Maryk, you have the ability to seamlessly inject values into subsequent requests.
This feature allows you to build and execute requests based on the responses of previous 
requests. For instance, you can request an object, collect  
specific values from its response, and then inject those collected values into a request related 
data from the same or another data model.

The Collect & Inject feature in Maryk not only simplifies the process of building interdependent
requests but also boosts efficiency by reducing the number of round trips between the datastore 
and the client. By enabling you to collect and inject values from previous responses into new 
requests, this feature ensures that multiple related queries can be executed in a single request,
instead of requiring separate requests for each step.

This results in faster querying and overall more efficient data retrieval, as the client doesn't
have to wait for the response of one request to build the next one. By combining several requests
into one and making better use of the retrieved data, you ultimately minimize the latency and
enhance your application's performance.

Here we'll illustrate how to collect and inject values for a use-case involving
fetching the data of all the friends of two persons.

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

In the example above, we first send a `CollectRequest` to fetch information about two persons
using their respective keys. We collect the response and store it under the name 
`collectedResponse`.

Next, we define a `GetRequest` which is designed to fetch the friends of the persons we previously
requested. Here, we use the `injectWith` function to inject the collected response of the previous
request using the stored name `collectedResponse`. We reference the friends from the previous 
response using `ValuesResponse` and then send the request. As a result, we successfully fetch 
the data of the friends of the two persons.

## Conclusion

The Collect & Inject feature in Maryk allows for dynamic and efficient execution of interdependent
requests, opening up a vast range of possibilities in building complex data queries.

It can significantly improve the querying process in Maryk by consolidating related requests, reducing
round trips, and increasing the speed and efficiency of data retrieval. By understanding how to utilize 
Collect & Inject, you can create smarter, more adaptable request workflows for retrieving related data.
