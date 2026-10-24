package com.minierp.backend.integration;
import com.minierp.backend.support.ApprovalApiTestSupport;
import com.minierp.backend.domain.approval.repository.InboxRepository;
import com.minierp.backend.global.persistence.SqlStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import java.time.LocalDateTime;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
class M2InboxPerformanceTest extends ApprovalApiTestSupport {
    @Autowired InboxRepository inbox;
    @MockitoSpyBean SqlStore observed;
    private final String marker="perf-"+UUID.randomUUID();
    private void insert(int first,int count) {
        List<Object[]> rows=new ArrayList<>();var now=LocalDateTime.now(clock);
        for(int n=first;n<first+count;n++) rows.add(new Object[]{marker+"-"+n,employee.getId(),"performance "+n,now,now,now});
        jdbc.batchUpdate("INSERT INTO approval_documents(doc_no,form_id,drafter_id,drafter_dept_id,title,status,current_step,version,manual_line,submitted_at,created_at,updated_at) VALUES(?,3,?,2,?,'IN_PROGRESS',1,0,0,?,?,?)",rows);
        jdbc.update("""
            INSERT INTO approval_lines(document_id,step_order,line_kind,approver_id,approver_name,approver_title,approver_dept,source,rule_label,status)
            SELECT d.id,1,'APPROVER',CASE WHEN MOD(d.id,100)=0 THEN ? ELSE ? END,'담당','팀장','개발','RULE','성능 검증','PENDING'
            FROM approval_documents d WHERE d.doc_no LIKE ? AND NOT EXISTS(SELECT 1 FROM approval_lines l WHERE l.document_id=d.id)
            """,leader.getId(),outsider.getId(),marker+"%");
    }
    private long readQueries(SqlStore target) {
        return mockingDetails(target).getInvocations().stream().filter(i->Set.of("rows","count").contains(i.getMethod().getName())).count();
    }
    private double median(String sql,Map<String,Object> args) {
        for(int n=0;n<3;n++) db.rows(sql,args);
        double[] ms=new double[9];for(int n=0;n<ms.length;n++) {long begin=System.nanoTime();db.rows(sql,args);ms[n]=(System.nanoTime()-begin)/1_000_000.0;}
        Arrays.sort(ms);return ms[ms.length/2];
    }
    @Test void tenThousandDocumentsUseTurnIndexAndConstantQueries() {
        SqlStore target=AopTestUtils.getUltimateTargetObject(observed);
        insert(0,100);clearInvocations(target);
        var small=inbox.search(leader.getId(),"todo",null,null,0,20);long smallQueries=readQueries(target);
        assertThat(small.get("content")).asList().isNotEmpty();assertThat(smallQueries).isEqualTo(3);
        insert(100,9900);jdbc.execute("ANALYZE TABLE approval_lines,approval_documents");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM approval_documents WHERE doc_no LIKE ?",Long.class,marker+"%")).isEqualTo(10000);
        clearInvocations(target);var large=inbox.search(leader.getId(),"todo",null,null,0,20);long largeQueries=readQueries(target);
        assertThat(large.get("content")).asList().hasSize(20);assertThat(largeQueries).isEqualTo(smallQueries);
        String sql=inbox.pageSql("todo",null,null),before=sql.replace("approval_lines l WHERE","approval_lines l IGNORE INDEX (idx_approval_lines_turn) WHERE");
        var args=map("actor",leader.getId(),"size",20,"offset",0);
        var beforePlan=db.rows("EXPLAIN "+before,args);var afterPlan=db.rows("EXPLAIN "+sql,args);
        assertThat(afterPlan).anySatisfy(row->assertThat(row.get("key")).isEqualTo("idx_approval_lines_turn"));
        double beforeMs=median(before,args),afterMs=median(sql,args);
        System.out.println("M2_INBOX_EXPLAIN_BEFORE="+beforePlan);System.out.println("M2_INBOX_EXPLAIN_AFTER="+afterPlan);
        System.out.printf(java.util.Locale.ROOT,"M2_INBOX_PERF rows=10000 small_queries=%d large_queries=%d warmups=3 samples=9 median_before_ms=%.3f median_after_ms=%.3f%n",smallQueries,largeQueries,beforeMs,afterMs);
    }
}
