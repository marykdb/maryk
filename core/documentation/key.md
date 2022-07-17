# Keys

All DataObjects can only be stored below a key. The key is a unique index
to an object and any object can be retrieved by the key. A key is always
final and unique. If you store data inside an existing key it will
overwrite the data.

## UUIDs by default

If you don't define a Key definition the model will create 128 bit v4 UUID
keys. These guarantee uniqueness but will not help in scans.

## Right key from the start

Since a key structure cannot be changed (without complex migrations) once 
data is stored you need to be careful designing the right key for the 
DataModel. Always look into the primary use case and order of the data and
make it fast for that purpose. Secondary usecases can be helped with adding 
an index. 

## Properties which can be used in a key

All fixed byte length properties can be used as a key part. This way key
structure is always predictable and the location of key elements can
be used in scans.

This means you can use numbers, dates & times, fixed bytes, references,
enums, booleans, type of multi type objects and ValueDataModels which can
contain all the same kind of values. You cannot use Strings, flexible
bytes, Sets, Lists, Maps and embedded models since their byte length is not 
fixed.

## The order of keys
All keys are stored in order and with a data scan all data will be walked 
or skipped in that order. This means that if you start your key with a 
reference and you give a specific reference to the key, the data scan knows
exactly where to start. 

If you always request data with the newest on top it is wise to reverse the
date of creation so new items always come back first.

## Tips on designing a key
- If data "belongs" to something or somebody, always start the key with
a reference. This way it is easy to request data belonging to it.

- If data should always be ordered on time, include that time. Reverse the 
time if newest is almost always requested first.

- If the date has a primary MultiTypeProperty, include the type id so it is 
easy to quickly request DataObjects of a specific type. If time is part of
the key you need to include it after the time so all data is still ordered
on that time.

- If date with nanosecond precision by itself is not enough, it is possible
to add a random number to the field.
