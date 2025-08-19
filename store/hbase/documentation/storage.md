# Hbase storage structure

Hbase can be used as a storage for Maryk data. It is a column oriented database and supports versioning. This document
describes the storage structure used by the Maryk HBase store.

# The Table

Each table contains the name, version and the entire model definition contained in bytes. This is used to validate if any 
client uses the right definition of the model. This to prevent any data corruption. It also contains the definitions of
any DataModel it depends on in its substructure. See [TableMetaColumns](../src/commonMain/kotlin/maryk/datastore/hbase/TableMetaColumns.kt)
for the exact column keys below which the values are stored.

The Table contains all the data but also all the unique value indexes and other indexes.

# The Data column family

One primary column family is used to store the data. The column family is named `d` which is the main column family
for storing all data in a versioned manner. 

Each row in the table is identified by the key of the data object which is determined by the key definition on the data 
model.

Each value is stored in a cell with the column name being the byte encoded property reference. It also uses the HLC based
version as the timestamp. Complex values use multiple columns to store the data, which use the byte representation of the property 
reference as the column name.

The data column family has some special columns to store metadata. They all start with the `0` byte.

- `0x00` - Indicator if the row is soft deleted. If this column is present and the value is `1` the row is soft deleted.
- `[0x00, 0x00]` - The created version of the row encoded as unsigned long.
- `[0x00, 0x01]` - The last modified version of the row encoded as unsigned long.

## Cell value structure

All data is stored in cells in the data column family. The value of the cell is encoded as a byte array prefixed with a
special indicator which helps to determine the type of data contained. This is primarily needed to differentiate between
types which can switch between a simple one value and a complex multiple value representation. It also indicates if
data was soft deleted. (This is because Hbase did not fully handled the deletes together with version in a reliable way)

See [TypeIndicator](../../shared/src/commonMain/kotlin/maryk/datastore/shared/TypeIndicator.kt) for all the specific
type prefixes which are used.

# The unique column family

A unique column family is used to store unique values. The column family is named `u` and is used to store unique values
for properties which are marked as unique in the data model.

Each row in the table uses the bytes representation of the property reference as the key. The value is encoded as the 
column name. The value of the cell contains the key of the data object.

# The index column families

For each index defined in the data model, a separate column family is created. The column family is prefixed with `i` 
followed by a base64 byte representation of the index reference with the end padding removed.

Each value is stored as a unique row in the column family. The column name contains the key of the data object and the 
value contains the value `0x01` if set. Deletions are marked as `0x00`. The timestamp is set to the HLC based version on
which the index was set or deleted.
