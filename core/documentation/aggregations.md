# Data Aggregations

Aggregations are an essential feature to analyze and gain insights from your collected data. 
They help to summarize and process large volumes of information, providing a broad understanding
of your data while also allowing you to focus on specific subsets. Maryk offers a set of built-in
aggregation functionalities like count, sum, average, min/max value, and other statistical 
aggregations to facilitate analyzing your stored data with ease.

To organize your data and gain deeper insights, you can group it based on date units like hour,
week, month, and year or by enum values. This helps to break down your data into manageable chunks,
making it more understandable and allowing you to derive meaningful conclusions from it.

The aggregation operators in Maryk are categorized into two main types, value operators and bucketing
operators. Value operators perform aggregations over data such as count, sum, average, min/max 
value, and combined stats. On the other hand, bucketing operators collect values based on a bucket
of date, enum, or object type.

These aggregations can be added to any `Get` or `Scan` request within the `aggregations` property and the responses
also contain the aggregated values within the `aggregations` property besides the queried values/updates/changes.

In the following sections, we will provide a short summary of each operator, along with their use
cases and code examples to demonstrate their usage within Maryk.

## Value Based Operators

### ValueCount

The value count aggregation operator calculates the total number of values present for the specified property. This operator can be particularly helpful when you want to know the total count of instances or occurrences for a specific attribute.

**Use case:** Count the number of users with a specified email address.

**Code example:**
```
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

The sum aggregation operator calculates the total sum of all numeric values for the specified property. This operator is useful when you want to find the total quantity of a particular attribute.

**Use case:** Calculate the sum of order amounts for all customers.

**Code example:**
```
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

The average aggregation operator computes the average value of all numeric values for the specified property. This operator is helpful when you want to find the central tendency of a particular attribute.

**Use case:** Calculate the average age of users.

**Code example:**
```
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

The min aggregation operator finds the minimum value among all numeric attributes for the specified property. This operator is beneficial when you want to identify the smallest quantity of a given attribute.

**Use case:** Find the earliest registration date of users.

**Code example:**
```
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

The max aggregation operator identifies the maximum value among all numeric attributes for the specified property. This operator is useful when you want to ascertain the highest quantity of a given attribute.

**Use case:** Find the latest registration date of users.

**Code example:**
```
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

The stats aggregation operator provides a combination of different aggregation results, including sum, average, min, max, and count for the specified property. This operator can assist you in gaining a comprehensive view of your data for the relevant attribute.

**Use case:** Retrieve a statistical summary of all orders for a specific product.

**Code example:**
```
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

Bucket based operators are essential when you want to categorize and group your data based on a
specific attribute or criterion. These operators help you analyze large data sets by dividing 
them into smaller and more meaningful subsets. Maryk offers various bucketing operators that 
allow you to group data by time, enum value, or object type.

Using bucket-based operators along with value-based operators allows you to perform deeper
analysis on your data subsets (buckets). By combining these operators, you can execute aggregated
calculations like count, sum, average, min/max value, and combined stats within the specified
groupings.

In the following sections, we will cover each of these grouped operators, along with their use
cases and code examples to demonstrate their usage within Maryk.

### DateHistogram

The DateHistogram operator groups data into buckets based on specified time units, such as hours,
weeks, months, or years. This operator is useful when you want to analyze trends for a property
over those specific time intervals.

**Use case:** Analyze the total amount and average order value received per day.

**Code example:**
```
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

The EnumValues operator groups data based on the enumerated values of a specified property. 
This operator can help you assess the distribution of your data for a given attribute with
enumerated values, providing insights into the frequency of each category.

**Use case:** Analyze the number of users and total revenue per account type.

**Code example:**
```
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

The Types operator organizes data into buckets based on the object type, for properties that allow
multiple object types. This operator is useful when evaluating the distribution of your data for
attributes that can have different object types.

**Use case:** Analyze the total and average values for different object types in a multi-type attribute.

**Code example:**
```
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

These bucket-based operators can be combined with the previously mentioned value-based operators to help you perform deeper analysis on your grouped data. By using these operators in conjunction, you can effectively segment your data into meaningful categories, providing easily understandable insights into your dataset as a whole, as well as targeted subsets.

## Combining multiple aggregation operators

To demonstrate the powerful combination of multiple aggregation and bucket operators, let's 
consider a sales dataset with information about products sold, order timestamps, and customer
account types. In this use case, we will calculate the total and average sales amount for each 
month, and segment the data further by customer account types.

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

In the example above, we combine DateHistogram and EnumValues bucket operators with Sum and Average value operators to create a comprehensive analysis of sales data. This enables us to better understand sales trends over time and across different customer account types, providing valuable insights to drive informed decision-making.

## Performance considerations

When using aggregation operators on large datasets or deeply nested structures, performance can become a concern. 
Here are some tips to optimize performance and resource usage:

- Avoid calculating unnecessary aggregations: Only include the required aggregation operators
  in your query to minimize data processing overhead.
- Use filters to narrow down your data set: By applying appropriate filters, you can reduce 
  the amount of data being processed and improve the overall performance of your aggregation 
  queries.
- Consider denormalizing your data model: If possible, restructure your data model to simplify
  or remove nested structures, thus easing the complexity of your aggregation queries.

## In conclusion

Utilizing both value-based and bucket-based operators in Maryk offers a diverse and powerful
set of analytical tools that can help you uncover valuable insights within your data. By combining 
these operators, you can explore different aspects of your data, from granular details to
overarching trends, effectively organizing them into manageable subsets.

Harnessing the capabilities of these aggregation operators will allow you to efficiently analyze
and understand the complexities of your data while improving the overall knowledge you can extract
from your stored information.
