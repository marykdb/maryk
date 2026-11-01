# Data Aggregations

Aggregations help you analyse data by summarising large result sets into concise values. Maryk offers built‑in operators for counts, sums, averages, minimums, maximums and more.

You can organise results into buckets by date (hour, week, month, year) or by enum value. Bucketing breaks a dataset into manageable groups so trends become clearer.

Maryk divides aggregation operators into two categories:
- **Value operators** compute a single statistic such as a count or sum.
- **Bucketing operators** group values by date, enum or object type and can contain nested value operators.

Operator support follows property capabilities:

- `ValueCount` accepts any property.
- `Min` and `Max` accept any `IsComparableDefinition`.
- `Sum`, `Average`, and `Stats` require an `IsArithmeticDefinition`.

Primitive `NumberDefinition` and exact `DecimalDefinition` properties are
arithmetic. Comparable non-arithmetic values such as strings, dates, and fixed
bytes support `Min` and `Max`, but not arithmetic aggregations.

Use Decimal aggregations for exact values such as monetary totals, rates, and
fixed-unit quantities. A Decimal with `scale = 0u` is equally suitable for
whole-number totals that need a range beyond `Long`; averages still retain that
zero scale and therefore round half-to-even to a whole number.

Integer and Decimal aggregations accumulate exactly before producing a response.
An average can therefore succeed even when its intermediate sum exceeds the
property type. Integer averages truncate towards zero; Decimal averages retain
the property scale and round half-to-even. `Sum` and `Stats` throw
`ArithmeticException` when the final sum cannot fit the existing result type
(or Decimal storage width), rather than returning a wrapped value. Intermediate
signed sums may cancel before that final check. Float/Double retain their IEEE
arithmetic behavior. Existing record data and response schemas do not change.

Aggregations can be added to any `Get` or `Scan` request through the `aggregations` property. Responses return the aggregated values alongside the requested objects.

Build the `Aggregations` argument from the operators below. For example:

```kotlin
val aggregations = Aggregations("count" to ValueCount(Person.ref { email }))
val response = dataStore.execute(Person.get(*keys.toTypedArray(), aggregations = aggregations))
```

The examples use illustrative models and property references.

## Value Based Operators

### ValueCount

Counts present values for a property.

**Use case:** Count users with a present email address.

**Code example:**
```kotlin
val aggregations = Aggregations(
    "count" to ValueCount(
        Person.ref { email }
    )
)
```

### Sum

Sums numeric values for a property.

**Use case:** Calculate the sum of order amounts for all customers.

**Code example:**
```kotlin
val aggregations = Aggregations(
    "total" to Sum(
        Person.ref { orderAmount }
    )
)
```

### Average

Calculates the average numeric value for a property.

Integer averages retain their existing integer-division behavior. Decimal
averages retain the property scale and use round-half-to-even when the exact
result is between two representable values.

**Use case:** Calculate the average age of users.

**Code example:**
```kotlin
val aggregations = Aggregations(
    "average" to Average(
        Person.ref { age }
    )
)
```

### Min (Minimum)

Returns the smallest comparable value.

**Use case:** Find the earliest registration date of users.

**Code example:**
```kotlin
val aggregations = Aggregations(
    "min" to Min(
        Person.ref { registrationDate }
    )
)
```

### Max (Maximum)

Returns the largest comparable value.

**Use case:** Find the latest registration date of users.

**Code example:**
```kotlin
val aggregations = Aggregations(
    "max" to Max(
        Person.ref { registrationDate }
    )
)
```

### Stats

Returns sum, average, minimum, maximum and count together.

**Use case:** Retrieve a statistical summary of all orders for a specific product.

**Code example:**
```kotlin
val aggregations = Aggregations(
    "stats" to Stats(
        ProductOrder.ref { orderId }
    )
)
```

## Bucket Based (Grouped) Operators

Bucket operators group values by time, enum option or multi-type case. Each bucket can contain nested aggregations.

### DateHistogram

Groups values by time unit and applies nested aggregations to each bucket.

**Use case:** Analyze the total amount and average order value received per day.

**Code example:**
```kotlin
val aggregations = Aggregations(
    "by day" to DateHistogram(
        Order.ref { orderDate },
        DateUnit.Days,
        Aggregations(
            "total" to Sum(
                Order.ref { orderAmount }
            ),
            "average" to Average(
                Order.ref { orderAmount }
            )
        )
    )
)
```

### EnumValues

Groups values by enum option and applies nested aggregations to each bucket.

**Use case:** Analyze the number of users and total revenue per account type.

**Code example:**
```kotlin
val aggregations = Aggregations(
    "each enum" to EnumValues(
        User.ref { accountType },
        Aggregations(
            "count" to ValueCount(
                User.ref { accountId }
            ),
            "revenue" to Sum(
                User.ref { accountRevenue }
            )
        )
    )
)
```

### Types

The **Types** operator organizes data into buckets based on the object type for properties that allow multiple object 
types. This operator is useful for evaluating the distribution of your data for attributes that can have different 
object types.

**Use case:** Analyze the total and average values for different object types in a multi-type attribute.

**Code example:**
```kotlin
val aggregations = Aggregations(
    "each type" to Types(
        Reports.ref { multiType.type },
        Aggregations(
            "total" to Sum(
                Reports.ref { itemCount }
            ),
            "average" to Average(
                Reports.ref { price }
            )
        )
    )
)
```

These bucket-based operators can be combined with the previously mentioned value-based operators to perform deeper 
analysis on your grouped data. By using these operators together, you can effectively segment your data into meaningful 
categories, providing easily understandable insights into your dataset as a whole, as well as targeted subsets.

## Combining Multiple Aggregation Operators

This example groups sales by month and account type, calculating totals and averages within each bucket.

**Code example:**
```kotlin
val getResponse = dataStore.execute(
    Sale.get(
        *keys.toTypedArray(),
        aggregations = Aggregations(
            "salesByMonth" to DateHistogram(
                Sale.ref { orderDate },
                DateUnit.Months,
                Aggregations(
                    "total" to Sum(
                        Sale.ref { saleAmount }
                    ),
                    "average" to Average(
                        Sale.ref { saleAmount }
                    ),
                    "eachAccountType" to EnumValues(
                        Sale.ref { accountType },
                        Aggregations(
                            "totalByType" to Sum(
                                Sale.ref { saleAmount }
                            ),
                            "averageByType" to Average(
                                Sale.ref { saleAmount }
                            )
                        )
                    )
                )
            )
        )
    )
)
```

## Performance Considerations

When using aggregation operators on large datasets or deeply nested structures, performance can become a concern. Here 
are some tips to optimize performance and resource usage:

- **Avoid calculating unnecessary aggregations:** Only include the required aggregation operators in your query to 
  minimize data processing overhead.
- **Use filters to narrow down your dataset:** By applying appropriate filters, you can reduce the amount of data being
  processed and improve the overall performance of your aggregation queries.
- **Consider denormalizing your data model:** If possible, restructure your data model to simplify or remove nested 
  structures, thus easing the complexity of your aggregation queries.
