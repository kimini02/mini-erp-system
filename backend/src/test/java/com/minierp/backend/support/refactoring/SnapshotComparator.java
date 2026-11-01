package com.minierp.backend.support.refactoring;

import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.util.*;

/** Standalone CLI; adds no JUnit tests and no build plugin. Exit 1 means a contract difference. */
public final class SnapshotComparator {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String, String> ORDER_ALLOWLIST = Map.of(
            "com.minierp.backend.integration.M5ProductionTest#<before-all>",
            "Map.of account iteration may swap CREATE USER statements across JVMs");
    private int differences;
    private int orderChanges;
    private final String selected;
    private SnapshotComparator(String selected) { this.selected = selected; }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) throw new IllegalArgumentException("Usage: BASELINE CANDIDATE [test-name-fragment]");
        SnapshotComparator comparison = new SnapshotComparator(args.length > 2 ? args[2] : "");
        comparison.sql(Path.of(args[0]), Path.of(args[1]));
        comparison.responses(Path.of(args[0]), Path.of(args[1]));
        System.out.printf("REFACTORING_COMPARE differences=%d sqlOrderChanges=%d%n", comparison.differences, comparison.orderChanges);
        if (comparison.differences > 0) System.exit(1);
    }

    private void difference(String test, String request, String path, Object expected, Object actual) {
        differences++;
        if (differences <= 100) System.err.printf("DIFF test=%s request=%s key=%s expected=%s actual=%s%n",
                test, request, path, brief(expected), brief(actual));
    }
    private String brief(Object value) {
        String text = Objects.toString(value);
        return text.length() <= 600 ? text : text.substring(0, 600) + "…";
    }
    private boolean included(String name) { return name.contains(selected); }
    private JsonNode read(Path directory, String name) throws Exception { return JSON.readTree(directory.resolve(name).toFile()); }
    private Set<String> fields(JsonNode node) {
        Set<String> names = new TreeSet<>(); node.fieldNames().forEachRemaining(names::add); return names;
    }
    private Map<String, Long> sqlCounts(JsonNode frame) {
        Map<String, Long> counts = new TreeMap<>();
        frame.path("sql").forEach(run -> {
            String sql = run.path("sql").asText();
            if (!sql.equals(SafetyNetRun.MONITOR)) counts.merge(sql, run.path("count").asLong(), Long::sum);
        });
        return counts;
    }

    private void sql(Path baseline, Path candidate) throws Exception {
        JsonNode before = read(baseline, "sql.json"), after = read(candidate, "sql.json");
        if (!before.path("recordingPolicy").equals(after.path("recordingPolicy")))
            difference("suite", "SQL", "recordingPolicy", before.path("recordingPolicy"), after.path("recordingPolicy"));
        if (!before.path("diagnosticExclusion").equals(after.path("diagnosticExclusion")))
            difference("suite", "SQL", "diagnosticExclusion", before.path("diagnosticExclusion"), after.path("diagnosticExclusion"));
        if (selected.isEmpty()) {
            Set<String> expected = new TreeSet<>(), actual = new TreeSet<>();
            before.path("distinctSql").forEach(sql -> expected.add(sql.asText()));
            after.path("distinctSql").forEach(sql -> actual.add(sql.asText()));
            for (String sql : expected) if (!actual.contains(sql)) difference("suite", "SQL", "distinctSql.removed", sql, "MISSING");
            for (String sql : actual) if (!expected.contains(sql)) difference("suite", "SQL", "distinctSql.added", "MISSING", sql);
        }
        before = before.path("tests"); after = after.path("tests");
        Set<String> names = fields(before); names.addAll(fields(after));
        for (String test : names) {
            if (!included(test)) continue;
            JsonNode a = before.get(test), b = after.get(test);
            if (a == null || b == null) { difference(test, "SQL", "$test", a, b); continue; }
            if (!a.path("comparedQueryCount").equals(b.path("comparedQueryCount"))) {
                difference(test, "SQL", "queryCount", a.path("comparedQueryCount"), b.path("comparedQueryCount"));
            }
            Set<String> expected = new TreeSet<>(), actual = new TreeSet<>();
            a.path("sql").forEach(sql -> expected.add(sql.path("sql").asText()));
            b.path("sql").forEach(sql -> actual.add(sql.path("sql").asText()));
            for (String sql : expected) if (!actual.contains(sql)) difference(test, "SQL", "sql.removed", sql, "MISSING");
            for (String sql : actual) if (!expected.contains(sql)) difference(test, "SQL", "sql.added", "MISSING", sql);
            Map<String, Long> leftCounts = sqlCounts(a), rightCounts = sqlCounts(b);
            Set<String> shapes = new TreeSet<>(leftCounts.keySet()); shapes.addAll(rightCounts.keySet());
            for (String shape : shapes) {
                long left = leftCounts.getOrDefault(shape, 0L), right = rightCounts.getOrDefault(shape, 0L);
                if (left != right) difference(test, "SQL", "sqlCounts[" + shape + "]", left, right);
            }
            if (leftCounts.equals(rightCounts) && !a.path("sql").equals(b.path("sql"))) {
                boolean worker = a.path("workerSql").asBoolean(false) || b.path("workerSql").asBoolean(false);
                String allowed = ORDER_ALLOWLIST.get(test);
                if (!worker && allowed == null) {
                    difference(test, "SQL", "sql.order", "identical serial execution order", "changed");
                } else {
                    orderChanges++;
                    System.out.printf("SQL_ORDER_CHANGED test=%s reason=%s rawCount=%s->%s diagnosticCount=%s->%s%n", test,
                            allowed != null ? allowed : "worker-thread SQL (including connection-pool workers)",
                            a.path("queryCount"), b.path("queryCount"), a.path("diagnosticQueryCount"), b.path("diagnosticQueryCount"));
                }
            }
        }
    }

    private Map<String, List<JsonNode>> requests(JsonNode responses) {
        Map<String, List<JsonNode>> groups = new TreeMap<>();
        int ordinal = 0;
        for (JsonNode item : responses) {
            ordinal++;
            boolean concurrent = item.path("concurrent").asBoolean(false);
            String key = item.path("method").asText() + " " + item.path("path").asText() + " "
                    + item.path("query") + (concurrent ? " concurrent status=" + item.path("status")
                    : " request=" + item.path("requestIndex").asInt(ordinal));
            groups.computeIfAbsent(key, unused -> new ArrayList<>()).add(item);
        }
        // Main-thread requests keep their occurrence index, including status transitions.
        // Only worker completion order is unordered; every response body's arrays stay ordered.
        groups.values().forEach(items -> {
            if (!items.isEmpty() && items.get(0).path("concurrent").asBoolean(false)) {
                items.sort(Comparator.comparing(item -> item.path("body").asText()));
            }
        });
        return groups;
    }
    private void responses(Path baseline, Path candidate) throws Exception {
        JsonNode before = read(baseline, "responses.json").path("tests"), after = read(candidate, "responses.json").path("tests");
        Set<String> names = fields(before); names.addAll(fields(after));
        for (String test : names) {
            if (!included(test)) continue;
            if (!before.has(test) || !after.has(test)) { difference(test, "HTTP", "$test", before.get(test), after.get(test)); continue; }
            Map<String, List<JsonNode>> a = requests(before.get(test)), b = requests(after.get(test));
            Set<String> requests = new TreeSet<>(a.keySet()); requests.addAll(b.keySet());
            for (String request : requests) {
                List<JsonNode> x = a.getOrDefault(request, List.of()), y = b.getOrDefault(request, List.of());
                if (x.size() != y.size()) difference(test, request, "$request.count", x.size(), y.size());
                for (int i = 0; i < Math.min(x.size(), y.size()); i++) {
                    JsonNode left = x.get(i), right = y.get(i);
                    if (!left.path("status").equals(right.path("status"))) {
                        difference(test, request, "$status", left.path("status"), right.path("status"));
                    }
                    if (!left.path("kind").equals(right.path("kind"))) difference(test, request, "$body.kind", left.path("kind"), right.path("kind"));
                    if (left.path("kind").asText().equals("json") && right.path("kind").asText().equals("json")) {
                        Object first = ExactJson.read(left.path("body").asText(), (path, atom) -> atom);
                        Object second = ExactJson.read(right.path("body").asText(), (path, atom) -> atom);
                        value(test, request + " #" + (i + 1), "$", first, second);
                    } else if (!left.path("body").equals(right.path("body"))) {
                        difference(test, request, "$body", left.path("body"), right.path("body"));
                    }
                }
            }
        }
    }
    @SuppressWarnings("unchecked")
    private void value(String test, String request, String path, Object expected, Object actual) {
        if (expected instanceof Map<?, ?> first && actual instanceof Map<?, ?> second) {
            Set<String> keys = new TreeSet<>((Set<String>) first.keySet()); keys.addAll((Set<String>) second.keySet());
            for (String key : keys) {
                if (!first.containsKey(key) || !second.containsKey(key)) {
                    difference(test, request, path + "." + key, first.containsKey(key) ? first.get(key) : "<MISSING_KEY>",
                            second.containsKey(key) ? second.get(key) : "<MISSING_KEY>");
                } else value(test, request, path + "." + key, first.get(key), second.get(key));
            }
        } else if (expected instanceof List<?> first && actual instanceof List<?> second) {
            if (first.size() != second.size()) difference(test, request, path + ".length", first.size(), second.size());
            for (int i = 0; i < Math.min(first.size(), second.size()); i++) value(test, request, path + "[" + i + "]", first.get(i), second.get(i));
        } else if (!Objects.equals(expected, actual)) difference(test, request, path, expected, actual);
    }
}
