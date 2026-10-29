package com.minierp.backend.support.refactoring;

import com.minierp.backend.support.JdbcProbe;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;
import java.sql.Statement;
import java.util.*;
import static net.bytebuddy.matcher.ElementMatchers.*;

/** Observe the existing MariaDB JDBC execution boundary without replacing connection/statement objects. */
public final class JdbcRecording {
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final Map<Object, List<String>> BATCHES = Collections.synchronizedMap(new WeakHashMap<>());
    private JdbcRecording() { }

    static void install() {
        try {
            var instrumentation = ByteBuddyAgent.install();
            for (String name : List.of("Statement", "BasePreparedStatement", "ClientPreparedStatement",
                    "ServerPreparedStatement", "BaseCallableStatement", "ProcedureStatement", "FunctionStatement")) {
                Class<?> type = Class.forName("org.mariadb.jdbc." + name);
                new ByteBuddy().redefine(type).visit(Advice.to(ExecuteAdvice.class).on(isPublic().and(not(isAbstract()))
                        .and(namedOneOf("execute", "executeQuery", "executeUpdate", "executeLargeUpdate",
                                "addBatch", "clearBatch", "executeBatch", "executeLargeBatch"))))
                        .make().load(type.getClassLoader(), ClassReloadingStrategy.of(instrumentation));
            }
        } catch (Exception error) {
            throw new IllegalStateException("SQL recording must not silently miss JDBC executions", error);
        }
    }

    public static class ExecuteAdvice {
        @Advice.OnMethodEnter
        public static void enter(@Advice.This Object statement, @Advice.Origin("#m") String method,
                @Advice.AllArguments Object[] args) throws Exception {
            JdbcRecording.enter(statement, method, args);
        }
        @Advice.OnMethodExit(onThrowable = Throwable.class)
        public static void exit() { JdbcRecording.exit(); }
    }

    public static void enter(Object statement, String name, Object[] args) throws Exception {
        int depth = DEPTH.get();
        DEPTH.set(depth + 1);
        if (depth != 0) return; // executeQuery -> execute and executeBatch -> executeLargeBatch count once.
        if (name.equals("clearBatch")) { BATCHES.remove(statement); return; }
        if (name.equals("executeBatch") || name.equals("executeLargeBatch")) {
            List<String> batch = BATCHES.remove(statement);
            if (batch != null) for (String sql : batch) JdbcProbe.recordSql(sql, 1);
            return;
        }
        String sql;
        if (args.length > 0 && args[0] instanceof String text) sql = text;
        else {
            // Driver getLastSql returns BasePreparedStatement.sql, not bound values/toString().
            sql = (String) statement.getClass().getMethod("getLastSql").invoke(statement);
        }
        if (name.equals("addBatch")) BATCHES.computeIfAbsent(statement, unused -> new ArrayList<>()).add(sql);
        else JdbcProbe.recordSql(sql, 1);
    }

    public static void exit() {
        int depth = DEPTH.get() - 1;
        if (depth == 0) DEPTH.remove(); else DEPTH.set(depth);
    }
}
