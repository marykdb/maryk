# ProtoBuf Transport

Maryk uses the [ProtoBuf v3](https://developers.google.com/protocol-buffers/) encoding standard for efficient binary transport. Only the encoding is implemented; schema generation is not yet available.

For a more in-depth understanding of how values are encoded, refer to
the [ProtoBuf encoding documentation](https://developers.google.com/protocol-buffers/docs/encoding), which provides
detailed insights on the encoding mechanisms and examples.

## Key Value Pairs

A ProtoBuf message consists of key–value pairs. Each key combines a tag that identifies the property with a wire type that describes how the value is encoded. The [properties documentation](properties/README.md) lists the encoding used for each property type.

## Wire Types

Maryk supports all ProtoBuf wire types:

- **VarInt** – variable length integers for numeric values.
- **Length Delimited** – values prefixed by their byte length; also used for embedded messages.
- **32 Bit** – fixed-width 4‑byte values.
- **64 Bit** – fixed-width 8‑byte values.
- **Start Group / End Group** – deprecated in ProtoBuf and unused in Maryk.
