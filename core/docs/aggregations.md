# Data Aggregations

Aggregations help you analyse data by summarising large result sets into concise values. Maryk offers built‑in operators for counts, sums, averages, minimums, maximums and more.

You can organise results into buckets by date (hour, week, month, year) or by enum value. Bucketing breaks a dataset into manageable groups so trends become clearer.

Maryk divides aggregation operators into two categories:
- **Value operators** compute a single statistic such as a count or sum.
- **Bucketing operators** group values by date, enum or object type and can contain nested value operators.

Aggregations can be added to any `Get` or `Scan` request through the `aggregations` property. Responses return the aggregated values alongside the requested objects.

The sections below summarise each operator with short examples to demonstrate how they are used in Maryk.

## Value Based Operators

### ValueCount

The **ValueCount** aggregation operator calculates the total number of values present for a specified property. This 
operator is particularly useful when you want to know the total count of instances or occurrences for a specific attribute.

**Use case:** Count the number of users with a specified email address.

**Code example:**
```kotlin
val getResponse = dataStore.execute(
    Person.get(
        *keys.toTypedArray(),
        aggregations = Aggregations(
            "count" to ValueCount(
                Person { email::ref }
            )
        )
    )
)
```

### Sum

The **Sum** aggregation operator calculates the total sum of all numeric values for a specified property. This operator 
is beneficial when you want to find the total quantity of a particular attribute.

**Use case:** Calculate the sum of order amounts for all customers.

**Code example:**
```kotlin
val getResponse = dataStore.execute(
    Person.get(
        *keys.toTypedArray(),
        aggregations = Aggregations(
            "total" to Sum(
                Person { orderAmount::ref }
            )
        )
    )
)
```

### Average

The **Average** aggregation operator computes the average value of all numeric values for a specified property. This 
operator is helpful when you want to find the central tendency of a particular attribute.

**Use case:** Calculate the average age of users.

**Code example:**
```kotlin
val getResponse = dataStore.execute(
    Person.get(
        *keys.toTypedArray(),
        aggregations = Aggregations(
            "average" to Average(
                Person { age::ref }
            )
        )
    )
)
```

### Min (Minimum)

The **Min** aggregation operator finds the minimum value among all numeric attributes for a specified property. This 
operator is beneficial when you want to identify the smallest quantity of a given attribute.

**Use case:** Find the earliest registration date of users.

**Code example:**
```kotlin
val getResponse = dataStore.execute(
    Person.get(
        *keys.toTypedArray(),
        aggregations = Aggregations(
            "min" to Min(
                Person { registrationDate::ref }
            )
        )
    )
)
```

### Max (Maximum)

The **Max** aggregation operator identifies the maximum value among all numeric attributes for a specified property. 
This operator is useful when you want to ascertain the highest quantity of a given attribute.

**Use case:** Find the latest registration date of users.

**Code example:**
```kotlin
val getResponse = dataStore.execute(
    Person.get(
        *keys.toTypedArray(),
        aggregations = Aggregations(
            "max" to Max(
                Person { registrationDate::ref }
            )
        )
    )
)
```

### Stats

The **Stats** aggregation operator provides a combination of different aggregation results, including sum, average, 
min, max, and count for a specified property. This operator assists in gaining a comprehensive view of your data for the relevant attribute.

**Use case:** Retrieve a statistical summary of all orders for a specific product.

**Code example:**
```kotlin
val getResponse = dataStore.execute(
    ProductOrder.get(
        *keys.toTypedArray(),
        aggregations = Aggregations(
            "stats" to Stats(
                ProductOrder { orderId::ref }
            )
        )
    )
)
```

## Bucket Based (Grouped) Operators

Bucket-based operators are essential for categorizing and grouping your data based on specific attributes or criteria. 
These operators help analyze large datasets by dividing them into smaller, more meaningful subsets. Maryk offers various 
bucketing operators that allow you to group data by time, enum value, or object type.

Using bucket-based operators alongside value-based operators enables deeper analysis of your data subsets (buckets). By 
combining these operators, you can execute aggregated calculations like count, sum, average, min/max value, and combined
stats within the specified groupings.

In the following sections, we will cover each of these grouped operators, along with their use cases and code examples to
demonstrate their usage within Maryk.

### DateHistogram

The **DateHistogram** operator groups data into buckets based on specified time units, such as hours, weeks, months, or 
years. This operator is useful for analyzing trends for a property over those specific time intervals.

**Use case:** Analyze the total amount and average order value received per day.

**Code example:**
```kotlin
val getResponse = dataStore.execute(
    Order.get(
        *keys.toTypedArray(),
        aggregations = Aggregations(
            "by day" to DateHistogram(
                Order { orderDate::ref },
                DateUnit.Days,
                Aggregations(
                    "total" to Sum(
                        Order { orderAmount::ref }
                    ),
                    "average" to Average(
                        Order { orderAmount::ref }
                    )
                )
            )
        )
    )
)
```

### EnumValues

The **EnumValues** operator groups data based on the enumerated values of a specified property. This operator helps 
assess the distribution of your data for a given attribute with enumerated values, providing insights into the frequency 
of each category.

**Use case:** Analyze the number of users and total revenue per account type.

**Code example:**
```kotlin
val getResponse = dataStore.execute(
    User.get(
        *keys.toTypedArray(),
        aggregations = Aggregations(
            "each enum" to EnumValues(
                User { accountType::ref },
                Aggregations(
                    "count" to ValueCount(
                        User { accountId::ref }
                    ),
                    "revenue" to Sum(
                        User { accountRevenue::ref }
                    )
                )
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
val getResponse = dataStore.execute(
    Data.get(
        *keys.toTypedArray(),
        aggregations = Aggregations(
            "each type" to Types(
                Reports { multiType.refToType() },
                Aggregations(
                    "total" to Sum(
                        Reports { itemCount::ref }
                    ),
                    "average" to Average(
                        Reports { price::ref }
                    )
                )
            )
        )
    )
)
```

These bucket-based operators can be combined with the previously mentioned value-based operators to perform deeper 
analysis on your grouped data. By using these operators together, you can effectively segment your data into meaningful 
categories, providing easily understandable insights into your dataset as a whole, as well as targeted subsets.

## Combining Multiple Aggregation Operators

To demonstrate the powerful combination of multiple aggregation and bucket operators, consider a sales dataset 
containing information about products sold, order timestamps, and customer account types. In this use case, we will 
calculate the total and average sales amount for each month, segmented further by customer account types.

**Use case:** Calculate the total and average sales amount per month for each customer account type.

**Code example:**
```kotlin
val getResponse = dataStore.execute(
    Sale.get(
        *keys.toTypedArray(),
        aggregations = Aggregations(
            "salesByMonth" to DateHistogram(
                Sale { orderDate::ref },
                DateUnit.Months,
                Aggregations(
                    "total" to Sum(
                        Sale { saleAmount::ref }
                    ),
                    "average" to Average(
                        Sale { saleAmount::ref }
                    ),
                    "eachAccountType" to EnumValues(
                        Sale { accountType::ref },
                        Aggregations(
                            "totalByType" to Sum(
                                Sale { saleAmount::ref }
                            ),
                            "averageByType" to Average(
                                Sale { saleAmount::ref }
                            )
                        )
                    )
                )
            )
        )
    )
)
```

In the example above, we combine **DateHistogram** and **EnumValues** bucket operators with **Sum** and **Average** 
value operators to create a comprehensive analysis of sales data. This enables us to better understand sales trends over 
time and across different customer account types, providing valuable insights for informed decision-making.

## Performance Considerations

When using aggregation operators on large datasets or deeply nested structures, performance can become a concern. Here 
are some tips to optimize performance and resource usage:

- **Avoid calculating unnecessary aggregations:** Only include the required aggregation operators in your query to 
  minimize data processing overhead.
- **Use filters to narrow down your dataset:** By applying appropriate filters, you can reduce the amount of data being
  processed and improve the overall performance of your aggregation queries.
- **Consider denormalizing your data model:** If possible, restructure your data model to simplify or remove nested 
  structures, thus easing the complexity of your aggregation queries.

## In Conclusion

Utilizing both value-based and bucket-based operators in Maryk offers a diverse and powerful set of analytical tools 
that can help you uncover valuable insights within your data. By combining these operators, you can explore different 
aspects of your data, from granular details to overarching trends, effectively organizing them into manageable subsets.

Harnessing the capabilities of these aggregation operators will allow you to efficiently analyze and understand the 
complexities of your data while improving the overall knowledge you can extract from your stored information.
