package com.minierp.backend.support.refactoring;

import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.util.*;
/** Standalone CLI; adds no JUnit tests and no build plugin. Exit 1 means a contract difference. */
public final class SnapshotComparator {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String M5_BOOT = "com.minierp.backend.integration.M5ProductionTest#<before-all>";
    private static final Set<String> M5_ACCOUNT_CREATIONS = Set.of("create user 'workflow_app'@'%' identified by ?",
            "create user 'workflow_migrator'@'%' identified by ?");
    // Non-test threads do not imply concurrent SQL. See R-01-review-hardening.md, second review.
    private static final Map<String, String> STRICT_ORDER = Map.of(
            "com.minierp.backend.integration.M5ProductionTest#separatedAccountsMigrateAndPerformRepresentativeWritesButRejectDdl()",
                    "Blocking HttpClient.send completes each request before the next starts",
            "com.minierp.backend.integration.DepartmentHeadConcurrencyTest#headCheckUsesCurrentReadEvenWithAnOlderTransactionSnapshot()[test-template-invocation:#1]",
                    "snapshot/assigned latches serialize worker SELECT, main assignment, worker MOVE",
            "com.minierp.backend.integration.DepartmentHeadConcurrencyTest#headCheckUsesCurrentReadEvenWithAnOlderTransactionSnapshot()[test-template-invocation:#2]",
                    "snapshot/assigned latches serialize worker SELECT, main assignment, worker RETIRED",
            "com.minierp.backend.integration.DepartmentHeadConcurrencyTest#headCheckUsesCurrentReadEvenWithAnOlderTransactionSnapshot()[test-template-invocation:#3]",
                    "snapshot/assigned latches serialize worker SELECT, main assignment, worker ON_LEAVE",
            "com.minierp.backend.integration.M1AcceptanceTest#concurrentInvitationLinksCannotOverwriteFirstPassword()",
                    "Only the valid invitation reaches SQL; revoked token fails at the in-memory owner lookup",
            "com.minierp.backend.integration.M2DocumentIntegrationTest#concurrentSubmissionsCannotSpendSameRemainingDays()",
                    "RecordingOrder v2 completes each submission before releasing the next",
            "com.minierp.backend.integration.M4BoardConcurrencyTest#projectLockPreventsWriteSkewInTenConcurrentCompletions()",
                    "RecordingOrder v2 completes each move before releasing the next");
    private int differences;
    private int orderChanges;
    private final String selected;
    private SnapshotComparator(String selected) {
        this.selected = selected;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: BASELINE CANDIDATE [test-name-fragment]");
        }
        SnapshotComparator comparison = new SnapshotComparator(args.length > 2 ? args[2] : "");
        comparison.sql(Path.of(args[0]), Path.of(args[1]));
        comparison.responses(Path.of(args[0]), Path.of(args[1]));
        System.out.printf("REFACTORING_COMPARE differences=%d sqlOrderChanges=%d%n", comparison.differences,
                comparison.orderChanges);
        if (comparison.differences > 0) {
            System.exit(1);
        }
    }

    private void difference(String test, String request, String path, Object expected, Object actual) {
        differences++;
        if (differences <= 100) {
            System.err.printf("DIFF test=%s request=%s key=%s expected=%s actual=%s%n", test, request, path,
                    brief(expected), brief(actual));
        }
    }

    private String brief(Object value) {
        String text = Objects.toString(value);
        return text.length() <= 600 ? text : text.substring(0, 600) + "…";
    }

    private boolean included(String name) {
        return name.contains(selected);
    }

    private JsonNode read(Path directory, String name) throws Exception {
        return JSON.readTree(directory.resolve(name).toFile());
    }

    private Set<String> fields(JsonNode node) {
        Set<String> names = new TreeSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private Map<String, Long> sqlCounts(JsonNode frame) {
        Map<String, Long> counts = new TreeMap<>();
        frame.path("sql").forEach(run -> {
            String sql = run.path("sql").asText();
            if (!sql.equals(SafetyNetRun.MONITOR)) {
                counts.merge(sql, run.path("count").asLong(), Long::sum);
            }
        });
        return counts;
    }

    private boolean onlyM5AccountCreationSwap(JsonNode before, JsonNode after) {
        if (!before.isArray() || !after.isArray() || before.size() != after.size()) {
            return false;
        }
        List<Integer> changed = new ArrayList<>();
        for (int i = 0; i < before.size(); i++) {
            if (!before.get(i).equals(after.get(i))) {
                changed.add(i);
            }
        }
        if (changed.size() != 2) {
            return false;
        }
        int first = changed.get(0);
        int second = changed.get(1);
        JsonNode left = before.get(first);
        JsonNode right = before.get(second);
        Set<String> sql = new HashSet<>(List.of(left.path("sql").asText(), right.path("sql").asText()));
        return sql.equals(M5_ACCOUNT_CREATIONS) && left.path("count").asLong() == 1 && right.path("count").asLong() == 1
                && left.equals(after.get(second)) && right.equals(after.get(first));
    }

    private void sql(Path baseline, Path candidate) throws Exception {
        JsonNode before = read(baseline, "sql.json");
        JsonNode after = read(candidate, "sql.json");
        if (!before.path("recordingPolicy").equals(after.path("recordingPolicy"))) {
            difference("suite", "SQL", "recordingPolicy", before.path("recordingPolicy"),
                    after.path("recordingPolicy"));
        }
        if (!before.path("diagnosticExclusion").equals(after.path("diagnosticExclusion"))) {
            difference("suite", "SQL", "diagnosticExclusion", before.path("diagnosticExclusion"),
                    after.path("diagnosticExclusion"));
        }
        if (selected.isEmpty()) {
            Set<String> expected = new TreeSet<>();
            Set<String> actual = new TreeSet<>();
            before.path("distinctSql").forEach(sql -> expected.add(sql.asText()));
            after.path("distinctSql").forEach(sql -> actual.add(sql.asText()));
            for (String sql : expected) {
                if (!actual.contains(sql)) {
                    difference("suite", "SQL", "distinctSql.removed", sql, "MISSING");
                }
            }
            for (String sql : actual) {
                if (!expected.contains(sql)) {
                    difference("suite", "SQL", "distinctSql.added", "MISSING", sql);
                }
            }
        }
        before = before.path("tests");
        after = after.path("tests");
        Set<String> names = fields(before);
        names.addAll(fields(after));
        for (String test : names) {
            if (!included(test)) {
                continue;
            }
            JsonNode a = before.get(test);
            JsonNode b = after.get(test);
            if (a == null || b == null) {
                difference(test, "SQL", "$test", a, b);
                continue;
            }
            if (!a.path("workerSql").equals(b.path("workerSql"))) {
                difference(test, "SQL", "workerSql", a.path("workerSql"), b.path("workerSql"));
            }
            if (!a.path("comparedQueryCount").equals(b.path("comparedQueryCount"))) {
                difference(test, "SQL", "queryCount", a.path("comparedQueryCount"), b.path("comparedQueryCount"));
            }
            Set<String> expected = new TreeSet<>();
            Set<String> actual = new TreeSet<>();
            a.path("sql").forEach(sql -> expected.add(sql.path("sql").asText()));
            b.path("sql").forEach(sql -> actual.add(sql.path("sql").asText()));
            for (String sql : expected) {
                if (!actual.contains(sql)) {
                    difference(test, "SQL", "sql.removed", sql, "MISSING");
                }
            }
            for (String sql : actual) {
                if (!expected.contains(sql)) {
                    difference(test, "SQL", "sql.added", "MISSING", sql);
                }
            }
            Map<String, Long> leftCounts = sqlCounts(a);
            Map<String, Long> rightCounts = sqlCounts(b);
            Set<String> shapes = new TreeSet<>(leftCounts.keySet());
            shapes.addAll(rightCounts.keySet());
            for (String shape : shapes) {
                long left = leftCounts.getOrDefault(shape, 0L);
                long right = rightCounts.getOrDefault(shape, 0L);
                if (left != right) {
                    difference(test, "SQL", "sqlCounts[" + shape + "]", left, right);
                }
            }
            if (leftCounts.equals(rightCounts) && !a.path("sql").equals(b.path("sql"))) {
                String strict = STRICT_ORDER.get(test);
                String allowed = null;
                if (strict == null) {
                    if (M5_BOOT.equals(test)) {
                        if (onlyM5AccountCreationSwap(a.path("sql"), b.path("sql"))) {
                            allowed =
                                    "Map.of swaps only workflow_app/workflow_migrator CREATE USER; all other runs identical";
                        }
                    } else if (a.path("workerSql").asBoolean(false)) {
                        allowed = "baseline worker-thread SQL (including connection-pool workers)";
                    }
                }
                if (allowed == null) {
                    difference(test, "SQL", "sql.order",
                            strict != null ? "strict order: " + strict : "identical serial execution order", "changed");
                } else {
                    orderChanges++;
                    System.out.printf("SQL_ORDER_CHANGED test=%s reason=%s rawCount=%s->%s diagnosticCount=%s->%s%n",
                            test, allowed, a.path("queryCount"), b.path("queryCount"), a.path("diagnosticQueryCount"),
                            b.path("diagnosticQueryCount"));
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
            String key = item.path("method").asText() + " " + item.path("path").asText() + " " + item.path("query")
                    + (concurrent
                            ? " concurrent status=" + item.path("status")
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
        JsonNode before = read(baseline, "responses.json").path("tests");
        JsonNode after = read(candidate, "responses.json").path("tests");
        Set<String> names = fields(before);
        names.addAll(fields(after));
        for (String test : names) {
            if (!included(test)) {
                continue;
            }
            if (!before.has(test) || !after.has(test)) {
                difference(test, "HTTP", "$test", before.get(test), after.get(test));
                continue;
            }
            Map<String, List<JsonNode>> a = requests(before.get(test));
            Map<String, List<JsonNode>> b = requests(after.get(test));
            Set<String> requests = new TreeSet<>(a.keySet());
            requests.addAll(b.keySet());
            for (String request : requests) {
                List<JsonNode> x = a.getOrDefault(request, List.of());
                List<JsonNode> y = b.getOrDefault(request, List.of());
                if (x.size() != y.size()) {
                    difference(test, request, "$request.count", x.size(), y.size());
                }
                for (int i = 0; i < Math.min(x.size(), y.size()); i++) {
                    JsonNode left = x.get(i);
                    JsonNode right = y.get(i);
                    if (!left.path("status").equals(right.path("status"))) {
                        difference(test, request, "$status", left.path("status"), right.path("status"));
                    }
                    if (!left.path("kind").equals(right.path("kind"))) {
                        difference(test, request, "$body.kind", left.path("kind"), right.path("kind"));
                    }
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
            Set<String> keys = new TreeSet<>((Set<String>) first.keySet());
            keys.addAll((Set<String>) second.keySet());
            for (String key : keys) {
                if (!first.containsKey(key) || !second.containsKey(key)) {
                    difference(test, request, path + "." + key,
                            first.containsKey(key) ? first.get(key) : "<MISSING_KEY>",
                            second.containsKey(key) ? second.get(key) : "<MISSING_KEY>");
                } else {
                    value(test, request, path + "." + key, first.get(key), second.get(key));
                }
            }
        } else if (expected instanceof List<?> first && actual instanceof List<?> second) {
            if (first.size() != second.size()) {
                difference(test, request, path + ".length", first.size(), second.size());
            }
            for (int i = 0; i < Math.min(first.size(), second.size()); i++) {
                value(test, request, path + "[" + i + "]", first.get(i), second.get(i));
            }
        } else if (!Objects.equals(expected, actual)) {
            difference(test, request, path, expected, actual);
        }
    }
}
