# msgpack-jakarata-json

Ecode/Decode msgpack as jakarta-json instances

## Usage

### Basic useage

```java
import java.io.OutputStream;
import java.io.InputStream;

import org.msgpack.core.MessagePack;

import jakarta.json.JsonValue;
import at.bestsolution.msgpack.json.MsgpackJson

...

public class Sample {
    ...
    public static void serialize(OutputStream stream, JsonValue value) {
        var packer = MessagePack.newDefaultBufferPacker(stream);
        var msgpackJson = MsgpackJson.builder().build();
        msgpackJson.encode(packer, value);
        // Only flush, close() would close the underlying stream
        packer.flush(); 
    }

    public static JsonValue deserialize(InputStream stream) {
        var unpacker = MessagePack.newDefaultUnpacker(stream);
        var msgpackJson = MsgpackJson.builder().build();
        return msgpackJson.decode(unpacker);
    }
}
```

### Cached Strings

If you encode eg Enum-like strings it might make sense to reuse `JsonString` instances when deserializing them. Let's say your enum is made of 'DEFAULT', 'SUCCESS', 'ERROR' you can configure the `MsgpackJson`-instance like this.

```java
var msgpackJson = MsgpackJson.builder()
    .cachedStrings(Set.of("DEFAULT", "SUCCESS", "ERROR"))
    .build();
```

### Cached Numbers

All numbers between -128 and 127 are cached, like `Integer.valueOf()` does it.
