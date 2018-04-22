# Filters

Filters can be applied on [Get](query.md#get) and [Scan](query.md#scan) object
queries. It is possible to construct a series of operations by using [`And`](#and)
and [`Or`](#or) filters. The filter can also be reversed with a [`Not`](#not)
filter

## Reference Filters
These filters operate on a property and is triggered from a property reference.

### Exists
Checks if a value exists

### Equals
Checks if a value on a property reference is equal to given value.

### GreaterThan
Checks if referenced value is greater than the given value.

### GreaterThanEquals
Checks if referenced value is greater than or equal to the given value.

### LessThan
Checks if referenced value is less than the given value.

### LessThanEquals
Checks if referenced value is less than or equal to the given value.

### Range
Checks if referenced value is within the given range.

### Prefix
Checks if referenced value is prefixed by given value.

### RegEx
Checks if referenced value matches with given regular expression.

### ValueIn
Checks if referenced value is within the set of given values.

## Filter operations
These filters run on top of other filters so they can provide a way to 
construct more complex queries.

### And
Returns true if all given filters match.

### Or
Returns true if one of given filters match.

### Not
Returns true if given filter does not match
