package com.minierp.backend.global.batch;
import com.minierp.backend.global.persistence.SqlStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import static com.minierp.backend.global.persistence.SqlStore.*;
@Service @RequiredArgsConstructor
public class BatchRunRecorder {
    private final SqlStore db;private final Clock clock;
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void record(String job,LocalDate date,String status,int processed,int skipped,String message,LocalDateTime started) {
        db.update("INSERT INTO batch_runs(job_name,target_date,status,processed_count,skipped_count,message,started_at,finished_at) VALUES(:job,:date,:status,:processed,:skipped,:message,:started,:finished)",map("job",job,"date",date,"status",status,"processed",processed,"skipped",skipped,"message",message,"started",started,"finished",LocalDateTime.now(clock)));
    }
}
