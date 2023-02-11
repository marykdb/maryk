# Protobuf Transportation

The encoding standard for [ProtoBuf V3](https://developers.google.com/protocol-buffers/) has been adopted for efficient
and compact transportation of data. Developed by Google, ProtoBuf is a widely adopted standard. Currently, only the 
encoding standard has been adopted, and schema generation is yet to be implemented.

For a more in-depth understanding of how values are encoded, refer to the [ProtoBuf encoding documentation](https://developers.google.com/protocol-buffers/docs/encoding) 

## Key Value pairs

A ProtoBuf message is built using key-value pairs, where the key contains a tag that identifies the encoded property and
a wire_type that indicates the type of value that was encoded. The value is encoded in the byte format for transport, 
and the encoding format for each property type is documented in the [properties documentation](properties/properties.md).

## Wire Types

Maryk supports all wire types supported by ProtoBuf, including:

* VarInt: A variable integer used for numeric values that grow in size with the value.
* Length Delimited: Used for variable length values. The length of the bytes is preceded by the value.
  It can also contain key-value pairs of embedded messages.
* 32 Bit: Used for values of 4 bytes.
* 64 Bit: Used for values of 8 bytes.
* Start Group / End Group: Not currently used and also deprecated in ProtoBuf.  
