package com.minierp.backend.integration;

import com.minierp.backend.domain.org.service.OrgService;
import com.minierp.backend.domain.user.dto.EmployeeRequests;
import com.minierp.backend.domain.user.entity.User;
import com.minierp.backend.domain.user.entity.UserStatus;
import com.minierp.backend.domain.user.repository.UserRepository;
import com.minierp.backend.domain.user.service.EmployeeService;
import com.minierp.backend.global.exception.BusinessException;
import com.minierp.backend.global.permission.PermissionAccess;
import com.minierp.backend.global.persistence.SqlStore;
import com.minierp.backend.support.MariaDbIntegrationTest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
/** Real transactions with controlled interleavings; neither SQL nor lock results are mocked. */
class DepartmentHeadConcurrencyTest extends MariaDbIntegrationTest {
    @Autowired
    EmployeeService employees;
    @Autowired
    OrgService organizations;
    @Autowired
    UserRepository users;
    @Autowired
    PlatformTransactionManager transactions;
    @MockitoBean
    PermissionAccess permissions;
    @MockitoSpyBean
    SqlStore db;
    private long candidate() {
        db.update("UPDATE departments SET head_user_id=NULL WHERE id=2", Map.of());
        String login = "head-" + UUID.randomUUID();
        return users.saveAndFlush(User.create("부서장후보", login, login + "@example.test", "fixture-only", "01", "사원"))
                .getId();
    }

    private void change(long id, String kind) {
        if (kind.equals("MOVE")) {
            employees.patch(id, new EmployeeRequests.Patch(null, null, 3L, null, null));
        } else {
            employees.status(id, UserStatus.valueOf(kind));
        }
    }

    private String attempt(Runnable action) {
        try {
            action.run();
            return "OK";
        } catch (BusinessException ex) {
            return ex.getErrorCode().name();
        }
    }

    private void validHead() {
        assertThat(db.count(
                "SELECT COUNT(*) FROM departments d JOIN users u ON u.id=d.head_user_id WHERE u.department_id<>d.id OR u.status<>'ACTIVE'",
                Map.of())).isZero();
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        assertThat(latch.await(10, TimeUnit.SECONDS)).as("concurrent transaction reached barrier").isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "MOVE", "RETIRED", "ON_LEAVE"
    })
    void employeeChangeAndHeadAssignmentPreserveInvariant(String kind) throws Exception {
        int violations = 0;
        for (int trial = 0; trial < 3; trial++) {
            reset(db);
            long id = candidate();
            var checked = new CountDownLatch(1);
            var resume = new CountDownLatch(1);
            doAnswer(inv -> {
                String sql = inv.getArgument(0);
                if (sql.startsWith("UPDATE users SET") && Thread.currentThread().getName().equals("employee-change")) {
                    checked.countDown();
                    await(resume);
                }
                return inv.callRealMethod();
            }).when(db).update(anyString(), anyMap());
            var pool = Executors.newFixedThreadPool(2);
            try {
                var mutation = pool.submit(() -> {
                    Thread.currentThread().setName("employee-change");
                    return attempt(() -> change(id, kind));
                });
                await(checked);
                var head = pool.submit(() -> attempt(() -> organizations.setHead(2, id)));
                // Before the fix setHead commits in this window. After the fix it waits for the tree lock.
                try {
                    head.get(1, TimeUnit.SECONDS);
                } catch (TimeoutException expectedLockWait) {
                } finally {
                    resume.countDown();
                }
                String changed = mutation.get(10, TimeUnit.SECONDS);
                String assigned = head.get(10, TimeUnit.SECONDS);
                if (changed.equals("OK") && assigned.equals("OK")) {
                    violations++;
                }
                assertThat(changed).isEqualTo("OK");
            } finally {
                resume.countDown();
                pool.shutdownNow();
            }
        }
        System.out.printf("M1-P2-2 mutation-first %s: invariant violations %d/3%n", kind, violations);
        assertThat(violations).isZero();
        validHead();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "MOVE", "RETIRED", "ON_LEAVE"
    })
    void headCheckUsesCurrentReadEvenWithAnOlderTransactionSnapshot(String kind) throws Exception {
        int violations = 0;
        for (int trial = 0; trial < 3; trial++) {
            reset(db);
            long id = candidate();
            var snapshot = new CountDownLatch(1);
            var assigned = new CountDownLatch(1);
            var pool = Executors.newSingleThreadExecutor();
            try {
                var mutation = pool.submit(() -> attempt(() -> new TransactionTemplate(transactions)
                        .executeWithoutResult(tx -> {
                    // Establish a REPEATABLE READ snapshot before the other transaction assigns a head.
                    assertThat(db.count("SELECT COUNT(*) FROM departments WHERE head_user_id=:id", Map.of("id", id)))
                            .isZero();
                    snapshot.countDown();
                    try {
                        await(assigned);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(ex);
                    }
                    change(id, kind);
                })));
                await(snapshot);
                organizations.setHead(2, id);
                assigned.countDown();
                String result = mutation.get(10, TimeUnit.SECONDS);
                if (result.equals("OK")) {
                    violations++;
                } else {
                    assertThat(result).isEqualTo("HEAD_MUST_BE_REASSIGNED");
                }
            } finally {
                assigned.countDown();
                pool.shutdownNow();
            }
        }
        System.out.printf("M1-P2-2 older-snapshot %s: invariant violations %d/3%n", kind, violations);
        assertThat(violations).isZero();
        validHead();
    }
}
