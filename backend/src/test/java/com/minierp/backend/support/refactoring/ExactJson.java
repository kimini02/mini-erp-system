package com.minierp.backend.support.refactoring;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.*;
import java.util.function.BiFunction;
/** Streaming tokens retain numeric lexemes: no JsonNode/Double/BigDecimal round-trip. */
public final class ExactJson {
    private static final ObjectMapper JSON = new ObjectMapper();
    public record Atom(String kind, String text) {
    }

    private ExactJson() {
    }

    public static Object read(String text, BiFunction<String, Atom, Atom> mask) throws IOException {
        try (JsonParser parser = JSON.getFactory().createParser(text)) {
            parser.nextToken();
            Object value = value(parser, "$", mask);
            if (parser.nextToken() != null) {
                throw new IOException("Trailing JSON tokens");
            }
            return value;
        }
    }

    private static Object value(
            JsonParser parser,
            String path,
            BiFunction<String, Atom, Atom> mask) throws IOException {
        if (parser.currentToken() == JsonToken.START_OBJECT) {
            Map<String, Object> object = new TreeMap<>();
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String key = parser.currentName();
                parser.nextToken();
                if (object.containsKey(key)) {
                    throw new IOException("Duplicate key at " + path + "." + key);
                }
                object.put(key, value(parser, path + "." + key, mask));
            }
            return object;
        }
        if (parser.currentToken() == JsonToken.START_ARRAY) {
            List<Object> array = new ArrayList<>();
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                array.add(value(parser, path + "[" + array.size() + "]", mask));
            }
            return array;
        }
        if (parser.currentToken() == null) {
            throw new IOException("Missing JSON value at " + path);
        }
        String kind = parser.currentToken() == JsonToken.VALUE_STRING
                ? "string"
                : parser.currentToken().isNumeric() ? "number" : "literal";
        return mask.apply(path, new Atom(kind, parser.getText()));
    }

    public static String write(Object value) throws IOException {
        if (value instanceof Atom atom) {
            return atom.kind.equals("string") ? JSON.writeValueAsString(atom.text) : atom.text;
        }
        if (value instanceof Map<?, ?> map) {
            List<String> items = new ArrayList<>();
            for (var entry : map.entrySet()) {
                items.add(JSON.writeValueAsString(entry.getKey()) + ":" + write(entry.getValue()));
            }
            return "{" + String.join(",", items) + "}";
        }
        List<String> items = new ArrayList<>();
        for (Object item : (List<?>) value) {
            items.add(write(item));
        }
        return "[" + String.join(",", items) + "]";
    }
}
