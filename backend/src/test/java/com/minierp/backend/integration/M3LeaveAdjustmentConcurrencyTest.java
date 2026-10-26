package com.minierp.backend.integration;
import com.minierp.backend.support.AttendanceTestSupport;
import com.minierp.backend.domain.user.entity.UserRole;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.sql.*;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
class M3LeaveAdjustmentConcurrencyTest extends AttendanceTestSupport {
    long funded() {var u=user("동시 조정",UserRole.USER);jdbc.update("INSERT INTO leave_ledger(user_id,leave_year,entry_type,days,created_at) VALUES(?,?,'GRANT',10,?)",u.getId(),date.getYear(),LocalDateTime.now(clock));return u.getId();}
    BigDecimal balance(long id) {return jdbc.queryForObject("SELECT SUM(days) FROM leave_ledger WHERE user_id=? AND leave_year=?",BigDecimal.class,id,date.getYear());}
    @Test void unlockedModelFailsThenLockedAdjustmentsAndSubmissionsPreserveBalance() throws Exception {
        var pool=Executors.newFixedThreadPool(2);int before=0;
        try {
            // First reproduce the unsafe read/check/append shape. No production endpoint bypass is retained.
            for(int n=0;n<10;n++) {
                long id=funded();var gate=new CyclicBarrier(2);Callable<Void> old=()->{try(var c=DriverManager.getConnection(DATABASE.getJdbcUrl(),DATABASE.getUsername(),DATABASE.getPassword())){
                    c.setAutoCommit(false);c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
                    try(var read=c.prepareStatement("SELECT SUM(days) FROM leave_ledger WHERE user_id=? AND leave_year=?")){read.setLong(1,id);read.setInt(2,date.getYear());try(var r=read.executeQuery()){r.next();assertThat(r.getBigDecimal(1)).isEqualByComparingTo("10");}}
                    gate.await(10,TimeUnit.SECONDS);
                    try(var insert=c.prepareStatement("INSERT INTO leave_ledger(user_id,leave_year,entry_type,days,created_at) VALUES(?,?,'ADJUST',-6,?)")){insert.setLong(1,id);insert.setInt(2,date.getYear());insert.setObject(3,LocalDateTime.now(clock));insert.executeUpdate();}c.commit();return null;
                }};var a=pool.submit(old);var b=pool.submit(old);a.get(20,TimeUnit.SECONDS);b.get(20,TimeUnit.SECONDS);if(balance(id).signum()<0)before++;
            }
            assertThat(before).isEqualTo(10);System.out.println("M3_ADJUST_BEFORE trials=10 negative_balances="+before);
            for(int n=0;n<10;n++) {
                long id=funded();var gate=new CyclicBarrier(2);Callable<Integer> safe=()->{gate.await(10,TimeUnit.SECONDS);return postAs("/api/v1/leave/admin/adjustments",chiefToken,Map.of("userId",id,"year",date.getYear(),"days",-6,"memo","동시 조정")).andReturn().getResponse().getStatus();};
                var a=pool.submit(safe);var b=pool.submit(safe);assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,422);assertThat(balance(id)).isEqualByComparingTo("4");
            }
            System.out.println("M3_ADJUST_AFTER trials=10 negative_balances=0");
            long id=funded();var actor=users.findById(id).orElseThrow();String actorToken=token(actor);
            long doc=data(postAs("/api/v1/approval-documents",actorToken,Map.of("formCode","LEAVE","title","동시 상신","body",leaveBody("ANNUAL",start,start.plusDays(7))))).path("id").asLong();
            var gate=new CyclicBarrier(2);var submit=pool.submit(()->{gate.await(10,TimeUnit.SECONDS);return postAs("/api/v1/approval-documents/"+doc+"/submit",actorToken,null).andReturn().getResponse().getStatus();});
            var adjust=pool.submit(()->{gate.await(10,TimeUnit.SECONDS);return postAs("/api/v1/leave/admin/adjustments",chiefToken,Map.of("userId",id,"year",date.getYear(),"days",-6,"memo","상신 경합")).andReturn().getResponse().getStatus();});
            assertThat(List.of(submit.get(20,TimeUnit.SECONDS),adjust.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,422);assertThat(balance(id)).isEqualByComparingTo("4");
        } finally {pool.shutdownNow();}
    }
}
