# Protobuf Transportation

The [ProtoBuf V3](https://developers.google.com/protocol-buffers/) encoding was chosen
for size efficient bytes transport. This format is a wide adopted standard originally
developed by Google. 

At the current moment only the encoding standard is adopted and not yet the schema 
generation.

Check out the [ProtoBuf encoding documentation](https://developers.google.com/protocol-buffers/docs/encoding) 
for much more detail on how values are encoded.

## Key Value pairs

A ProtoBuf message is constructed by key value pairs in which the key contains a tag
to indicate which property is encoded and a wire_type to indicate which type of value
was encoded. The value is encoded in the transport byte format which is documented
for each [property type](properties/properties.md).

## Wire Types

Maryk supports all wire types supported by ProtoBuf. 

* VarInt - Variable Integer. Is for numeric values and grows with the size of the 
value.
* Length Delimited - Used for variable length values. It precedes the value with the
length of bytes. It can also contain key/value pairs of embedded messages.
* 32 Bit - Used for a value of 4 bytes.
* 64 Bit - Used for a value of 8 bytes.
* Start Group / End Group - Not used at the moment and is also deprecated in ProtoBuf.  