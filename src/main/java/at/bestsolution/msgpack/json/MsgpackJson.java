package at.bestsolution.msgpack.json;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.msgpack.core.MessageFormat;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;

import jakarta.json.Json;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

/**
 * Utility class for encoding and decoding JSON values to and from MessagePack
 * format.
 */
public class MsgpackJson {
    /**
     * Builder for configuring and creating a MsgpackJson instance.
     */
    public static class Builder {
        private Map<String, JsonString> stringCache;

        private Builder() {
        }

        /**
         * Sets a cache of strings to be used during decoding. This is helpful for
         * example when deoding JSON data representing an Enum where the same strings
         * are used
         * 
         * @param stringCache the strings to cache
         * @return the builder instance
         */
        public Builder cachedStrings(Set<String> stringCache) {
            this.stringCache = stringCache.stream()
                    .collect(java.util.stream.Collectors.toUnmodifiableMap(s -> s,
                            s -> Json.createValue(s)));
            return this;
        }

        /**
         * Builds the MsgpackJson instance with the configured settings.
         * 
         * @return the mspack instance
         */
        public MsgpackJson build() {
            return new MsgpackJson(stringCache);
        }
    }

    private static final JsonNumber[] CACHE = new JsonNumber[256];
    private Map<String, JsonString> stringCache = null;

    /**
     * Creates a new builder for configuring a MsgpackJson instance.
     * 
     * @return the builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    MsgpackJson(Map<String, JsonString> stringCache) {
        this.stringCache = stringCache;
    }

    /**
     * Decodes a MessagePack value into a JSON value using the configured strategy.
     * 
     * @param unpacker the MessageUnpacker to read the MessagePack data
     * @return the decoded JSON value
     * @throws IOException if an I/O error occurs during unpacking
     */
    public JsonValue decode(MessageUnpacker unpacker) throws IOException {
        MessageFormat format = unpacker.getNextFormat();
        return switch (format.getValueType()) {
            case MAP -> {
                var mapSize = unpacker.unpackMapHeader();
                if (mapSize == 0) {
                    yield JsonValue.EMPTY_JSON_OBJECT;
                }
                var objBuilder = Json.createObjectBuilder();
                for (int i = 0; i < mapSize; i++) {
                    var key = unpacker.unpackString();
                    var value = decode(unpacker);
                    objBuilder.add(key, value);
                }
                yield objBuilder.build();
            }
            case ARRAY -> {
                var arraySize = unpacker.unpackArrayHeader();
                if (arraySize == 0) {
                    yield JsonValue.EMPTY_JSON_ARRAY;
                }
                var arrBuilder = jakarta.json.Json.createArrayBuilder();
                for (int i = 0; i < arraySize; i++) {
                    var value = decode(unpacker);
                    arrBuilder.add(value);
                }
                yield arrBuilder.build();
            }
            case STRING -> {
                var str = unpacker.unpackString();
                var rv = stringCache != null ? stringCache.get(str) : null;
                yield rv != null ? rv : Json.createValue(str);
            }
            case INTEGER -> {
                if (format == MessageFormat.UINT64) {
                    var ul = unpacker.unpackBigInteger();
                    yield Json.createValue(ul);
                }

                var l = unpacker.unpackLong();
                if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
                    yield number((int) l);
                } else {
                    yield number(l);
                }
            }
            case FLOAT -> {
                var d = unpacker.unpackDouble();
                yield Json.createValue(d);
            }
            case BOOLEAN -> {
                var b = unpacker.unpackBoolean();
                yield b ? JsonValue.TRUE : JsonValue.FALSE;
            }
            case NIL -> {
                unpacker.unpackNil();
                yield JsonValue.NULL;
            }
            case BINARY -> {
                var data = unpacker.unpackBinaryHeader() > 0 ? unpacker.readPayload(unpacker.unpackBinaryHeader())
                        : new byte[0];
                var base64 = Base64.getEncoder().encodeToString(data);
                yield Json.createValue(base64);
            }
            case EXTENSION ->
                throw new IOException("Extension types are not supported");
        };
    }

    /**
     * Decodes a MessagePack values into a list of JSON values using the configured
     * strategy.
     * 
     * @param unpacker the MessageUnpacker to read the MessagePack data
     * @return the list of decoded JSON values
     * @throws IOException if an I/O error occurs during unpacking
     */
    public List<JsonValue> decodeList(MessageUnpacker unpacker) throws IOException {
        List<JsonValue> result = new java.util.ArrayList<>();
        while (unpacker.hasNext()) {
            result.add(decode(unpacker));
        }
        return result;
    }

    /**
     * Encodes a JSON value into MessagePack format.
     *
     * @param packer the MessagePacker to write the MessagePack data
     * @param value  the JSON value to encode
     * @throws IOException if an I/O error occurs during packing
     */
    public void encode(MessagePacker packer, JsonValue value) throws IOException {
        if (value.getValueType() == JsonValue.ValueType.STRING) {
            packer.packString(((JsonString) value).getString());
        } else if (value.getValueType() == JsonValue.ValueType.NUMBER) {
            var num = (JsonNumber) value;
            if (num.isIntegral()) {
                try {
                    var l = num.longValueExact();
                    if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
                        packer.packInt((int) l);
                    } else {
                        packer.packLong(l);
                    }

                } catch (ArithmeticException e) {
                    packer.packBigInteger(num.bigIntegerValue());
                }
            } else {
                var d = num.doubleValue();
                packer.packDouble(d);
            }
        } else if (value.getValueType() == JsonValue.ValueType.OBJECT) {
            JsonObject obj = (JsonObject) value;
            packer.packMapHeader(obj.size());
            for (String key : obj.keySet()) {
                packer.packString(key);
                encode(packer, obj.get(key));
            }
        } else if (value.getValueType() == JsonValue.ValueType.ARRAY) {
            jakarta.json.JsonArray arr = (jakarta.json.JsonArray) value;
            packer.packArrayHeader(arr.size());
            for (JsonValue val : arr) {
                encode(packer, val);
            }
        } else if (value.getValueType() == JsonValue.ValueType.TRUE) {
            packer.packBoolean(true);
        } else if (value.getValueType() == JsonValue.ValueType.FALSE) {
            packer.packBoolean(false);
        } else if (value.getValueType() == JsonValue.ValueType.NULL) {
            packer.packNil();
        }
    }

    /**
     * Encodes a list of JSON values into MessagePack format.
     *
     * @param packer the MessagePacker to write the MessagePack data
     * @param values the list of JSON values to encode
     * @throws IOException if an I/O error occurs during packing
     */
    public void encodeList(MessagePacker packer, List<? extends JsonValue> values) throws IOException {
        for (JsonValue value : values) {
            encode(packer, value);
        }
    }

    private static JsonNumber number(long l) {
        if (l >= -128 && l <= 127) {
            int idx = (int) l + 128;
            JsonNumber cached = CACHE[idx];
            if (cached == null) {
                cached = Json.createValue((int) l);
                CACHE[idx] = cached;
            }
            return cached;
        }
        return Json.createValue(l);
    }

    private static JsonNumber number(int l) {
        if (l >= -128 && l <= 127) {
            int idx = (int) l + 128;
            JsonNumber cached = CACHE[idx];
            if (cached == null) {
                cached = Json.createValue(l);
                CACHE[idx] = cached;
            }
            return cached;
        }
        return Json.createValue(l);
    }
}
