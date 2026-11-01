package com.minierp.backend.support.refactoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minierp.backend.support.JdbcProbe;
import org.springframework.test.web.servlet.MvcResult;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class SafetyNetRun {
    private static final Map<String, Entry> ENTRIES = new TreeMap<>();
    private static final Map<String, String> SQL = new ConcurrentHashMap<>();
    private static final Set<String> MASKS = new TreeSet<>();
    private static String current = "suite#<unattributed>";
    private static long testThread = Thread.currentThread().getId();
    public static final String MONITOR = "select count(*) from information_schema.innodb_lock_waits w "
            + "join information_schema.innodb_trx t on t.trx_id=w.requesting_trx_id " + "where t.trx_mysql_thread_id=?";
    private static final class SqlRun {
        final String sql;
        long count;
        SqlRun(String sql, long count) {
            this.sql = sql;
            this.count = count;
        }
    }

    private static final class Entry {
        final List<SqlRun> sql = new ArrayList<>();
        final List<Map<String, Object>> responses = new ArrayList<>();
        long count;
        long monitor;
        int mainRequests;
        boolean workerSql;
    }

    private SafetyNetRun() {
    }

    public static boolean enabled() {
        return System.getProperty("refactoring.record") != null;
    }

    static synchronized String workerTest() {
        return Thread.currentThread().getId() == testThread ? null : current;
    }

    static synchronized void begin(String test) {
        current = test;
        testThread = Thread.currentThread().getId();
        ENTRIES.computeIfAbsent(test, key -> new Entry());
    }

    public static synchronized void sql(String sql, long count) {
        if (!enabled() || sql == null) {
            return;
        }
        String normalized = SQL.computeIfAbsent(sql, JdbcProbe::normalizeSql);
        Entry entry = ENTRIES.computeIfAbsent(current, key -> new Entry());
        entry.count += count;
        entry.workerSql |= Thread.currentThread().getId() != testThread;
        if (normalized.equals(MONITOR)) {
            entry.monitor += count;
        }
        SqlRun last = entry.sql.isEmpty() ? null : entry.sql.get(entry.sql.size() - 1);
        if (last != null && last.sql.equals(normalized)) {
            last.count += count;
        } else {
            entry.sql.add(new SqlRun(normalized, count));
        }
    }

    static synchronized void mask(String test, String path, String reason) {
        MASKS.add(test + " " + path.replaceAll("\\[[0-9]+]", "[]") + " : " + reason);
    }

    static void response(MvcResult result) throws Exception {
        final String test;
        final boolean concurrent;
        synchronized (SafetyNetRun.class) {
            test = current;
            concurrent = Thread.currentThread().getId() != testThread;
        }
        var request = result.getRequest();
        var response = result.getResponse();
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        String contentType = Objects.toString(response.getContentType(), "");
        String kind = body.isEmpty() ? "empty" : contentType.contains("json") ? "json" : "non-json";
        if (kind.equals("json")) {
            body = ExactJson.write(ExactJson.read(body, (path, atom) -> SnapshotMask.apply(test, path, atom)));
        } else if (!body.isEmpty()) {
            body = SnapshotMask.apply(test, "$body", new ExactJson.Atom("string", body)).text();
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("method", request.getMethod());
        item.put("path", SnapshotMask.path(request.getRequestURI()));
        Map<String, List<String>> query = new TreeMap<>();
        request.getParameterMap().forEach((key, values) -> query
                .put(key, Arrays.stream(values)
                .map(value -> SnapshotMask.apply(test, "$request.query." + key, new ExactJson.Atom("string", value))
                .text()).toList()));
        item.put("query", query);
        item.put("status", response.getStatus());
        item.put("kind", kind);
        item.put("body", body);
        synchronized (SafetyNetRun.class) {
            Entry entry = ENTRIES.computeIfAbsent(test, key -> new Entry());
            item.put("concurrent", concurrent);
            item.put("requestIndex", concurrent ? null :++entry.mainRequests);
            entry.responses.add(item);
        }
    }

    static synchronized Set<String> masks() {
        return Set.copyOf(MASKS);
    }

    // LinkedHashMap preserves the declared schema order across JVMs (Map.of does not).
    static Map<String, Object> orderedMap(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    static synchronized void write() throws Exception {
        Path directory = Path.of(System.getProperty("refactoring.record"));
        Files.createDirectories(directory);
        Map<String, Object> sql = new TreeMap<>();
        Map<String, Object> responses = new TreeMap<>();
        Set<String> distinct = new TreeSet<>();
        for (var test : ENTRIES.entrySet()) {
            Entry entry = test.getValue();
            List<Map<String, Object>> runs = new ArrayList<>();
            for (SqlRun run : entry.sql) {
                distinct.add(run.sql);
                runs.add(orderedMap("sql", run.sql, "count", run.count));
            }
            sql.put(test.getKey(), orderedMap("queryCount", entry.count, "comparedQueryCount", entry.count - entry
                    .monitor, "diagnosticQueryCount", entry.monitor, "workerSql", entry.workerSql, "sql", runs));
            responses.put(test.getKey(), entry.responses);
        }
        ObjectMapper mapper = new ObjectMapper();
        mapper.writerWithDefaultPrettyPrinter()
                .writeValue(directory.resolve("sql.json").toFile(),
                orderedMap("recordingPolicy", RecordingOrder.POLICY, "normalization", "whitespace collapse + lowercase",
                "diagnosticExclusion", MONITOR, "distinctSql", distinct, "tests", sql));
        mapper.writerWithDefaultPrettyPrinter()
                .writeValue(directory.resolve("responses.json").toFile(),
                orderedMap("recordingPolicy", RecordingOrder.POLICY, "tests", responses));
        Files.write(directory.resolve("masked-fields.txt"), MASKS, StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("classpath.txt"), System
                .getProperty("surefire.test.class.path", System.getProperty("java.class.path")));
        System.out.printf("REFACTORING_RECORDED frames=%d sql_shapes=%d responses=%d output=%s%n", ENTRIES.size(),
                distinct.size(), ENTRIES.values().stream().mapToInt(entry -> entry.responses.size()).sum(), directory);
    }
}
