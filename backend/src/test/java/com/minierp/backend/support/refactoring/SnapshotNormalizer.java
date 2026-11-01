package com.minierp.backend.support.refactoring;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Reapply reviewed masking rules to existing recordings; no application or DB access. */
public final class SnapshotNormalizer {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: RAW_RECORDING NORMALIZED_RECORDING");
        }
        Path source = Path.of(args[0]);
        Path target = Path.of(args[1]);
        Files.createDirectories(target);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode tree = mapper.readTree(source.resolve("responses.json").toFile());
        var tests = tree.path("tests").fields();
        while (tests.hasNext()) {
            var test = tests.next();
            for (JsonNode item : test.getValue()) {
                ObjectNode response = (ObjectNode) item;
                String body = response.path("body").asText();
                if (response.path("kind").asText().equals("json")) {
                    response.put("body", ExactJson
                            .write(ExactJson.read(body, (path, atom) -> SnapshotMask
                            .apply(test.getKey(), path, atom))));
                } else {
                    response.put("body", SnapshotMask.apply(test.getKey(), "$body", new ExactJson.Atom("string", body))
                            .text());
                }
                response.put("path", SnapshotMask.path(response.path("path").asText()));
                response.path("query").fields().forEachRemaining(query -> {
                    for (int i = 0; i < query.getValue().size(); i++) {
                        var values = (com.fasterxml.jackson.databind.node.ArrayNode) query.getValue();
                        values.set(i, mapper.getNodeFactory()
                                .textNode(SnapshotMask.apply(test.getKey(), "$request.query." + query.getKey(),
                                new ExactJson.Atom("string", values.get(i).asText())).text()));
                    }
                });
            }
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(target.resolve("responses.json").toFile(), tree);
        Files.copy(source.resolve("sql.json"), target.resolve("sql.json"), StandardCopyOption.REPLACE_EXISTING);
        Set<String> audit = new TreeSet<>(Files
                .readAllLines(source.resolve("masked-fields.txt"), StandardCharsets.UTF_8));
        audit.addAll(SafetyNetRun.masks());
        Files.write(target.resolve("masked-fields.txt"), audit, StandardCharsets.UTF_8);
        Files.copy(source.resolve("classpath.txt"), target.resolve("classpath.txt"), StandardCopyOption
                .REPLACE_EXISTING);
        System.out.println("REFACTORING_NORMALIZED output=" + target);
    }
}
