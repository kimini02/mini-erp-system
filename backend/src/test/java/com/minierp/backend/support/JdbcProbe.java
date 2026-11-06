package com.minierp.backend.support;

import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
/** Test-only JDBC boundary measurement. Counts executed statements and returned rows, not engine page reads. */
public final class JdbcProbe {
    private static final ThreadLocal<Trace> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<Observer> OBSERVER = new ThreadLocal<>();
    public static void recordSql(String sql, long count) {
        com.minierp.backend.support.refactoring.SafetyNetRun.sql(sql, count);
    }

    public static String normalizeSql(String sql) {
        return sql.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    public interface Observer {
        default void before(Statement statement, String sql) throws Exception {
        }
        default void after(Statement statement, String sql) throws Exception {
        }
    }

    /** Only test worker threads install hooks; production code has no test synchronization. */
    public static AutoCloseable observe(Observer observer) {
        OBSERVER.set(observer);
        return () -> OBSERVER.remove();
    }

    public static final class Query {
        public final String sql;
        public long rows;
        public long updates;
        Query(String sql) {
            this.sql = normalizeSql(sql);
        }
    }

    public static final class Trace implements AutoCloseable {
        public final List<Query> queries = new ArrayList<>();
        public long taskUpdates() {
            return queries.stream().filter(q -> q.sql.startsWith("update tasks")).mapToLong(q -> q.updates).sum();
        }
        public long rebalances() {
            return queries.stream()
                    .filter(q -> q.sql.startsWith("update tasks") && q.sql.contains("row_number()"))
                    .count();
        }
        public long projectReads() {
            return queries.stream()
                    .filter(q -> (q.sql.startsWith("select") || q.sql.startsWith("with"))
                            && (q.sql.contains("from projects") || q.sql.contains("from tasks")
                                    || q.sql.contains("from project_members")))
                    .count();
        }
        public void close() {
            CURRENT.remove();
        }
    }

    public static Trace start() {
        if (CURRENT.get() != null) {
            throw new IllegalStateException("Nested measurement");
        }
        Trace trace = new Trace();
        CURRENT.set(trace);
        return trace;
    }

    private static Object call(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    @SuppressWarnings("unchecked")
    private static<T> T proxy(Class<T> type, Object target, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(JdbcProbe.class.getClassLoader(), new Class<?>[] { type }, handler);
    }

    private static ResultSet result(ResultSet rs, Query query) {
        return proxy(ResultSet.class, rs, (p, m, a) -> {
            Object value = call(rs, m, a);
            if (m.getName().equals("next") && Boolean.TRUE.equals(value)) {
                query.rows++;
            }
            return value;
        });
    }

    private static Statement statement(Statement statement, String sql) {
        final Query[] current = { null };
        Class<? extends Statement> type = statement instanceof PreparedStatement
                ? PreparedStatement.class
                : Statement.class;
        return proxy(type, statement, (p, m, a) -> {
            String name = m.getName();
            boolean executing = name.equals("execute") || name.equals("executeQuery") || name.equals("executeUpdate")
                    || name.equals("executeLargeUpdate");
            Observer observer = OBSERVER.get();
            String observed = executing
                    ? (sql != null ? sql : (String) a[0]).replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT)
                    : null;
            if (executing && observer != null) {
                observer.before(statement, observed);
            }
            if (executing) {
                Trace trace = CURRENT.get();
                current[0] = trace == null ? null : new Query(sql != null ? sql : (String) a[0]);
                if (trace != null) {
                    trace.queries.add(current[0]);
                }
            }
            Object value = call(statement, m, a);
            Query query = current[0];
            if (executing && observer != null) {
                observer.after(statement, observed);
            }
            if (query != null) {
                if (value instanceof ResultSet rs && !name.equals("getGeneratedKeys")) {
                    return result(rs, query);
                }
                if (name.equals("executeUpdate") || name.equals("executeLargeUpdate")) {
                    query.updates = ((Number) value).longValue();
                }
            }
            return value;
        });
    }

    private static Connection connection(Connection connection) {
        return proxy(Connection.class, connection, (p, m, a) -> {
            Object value = call(connection, m, a);
            if (value instanceof Statement st) {
                return statement(st, a != null && a.length > 0 && a[0] instanceof String ? (String) a[0] : null);
            }
            return value;
        });
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Configuration {
        @Bean
        static BeanPostProcessor measuredDataSource() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String name) {
                    if (!(bean instanceof DataSource ds)) {
                        return bean;
                    }
                    return Proxy.newProxyInstance(JdbcProbe.class.getClassLoader(),
                            new Class<?>[] { DataSource.class, AutoCloseable.class }, (p, m, a) -> {
                        if (m.getName().equals("close")) {
                            if (ds instanceof AutoCloseable c) {
                                c.close();
                            }
                            return null;
                        }
                        Object value = call(ds, m, a);
                        return value instanceof Connection c ? connection(c) : value;
                    });
                }
            };
        }
    }
}
