package com.minierp.backend.domain.leave.service;
import com.minierp.backend.global.batch.BatchRunRecorder;
import com.minierp.backend.global.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.Map;
@Service @RequiredArgsConstructor
public class LeaveGrantService {
    private final LeaveGrantWork work;private final BatchRunRecorder runs;private final Clock clock;
    public Map<String,Object> runToday() {return run(LocalDate.now(clock));}
    public Map<String,Object> run(LocalDate date) {
        if(date==null || date.isAfter(LocalDate.now(clock)))throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);var start=LocalDateTime.now(clock);
        try {var result=work.run(date);return runs.record("LEAVE_GRANT",date,"SUCCESS",result.processed(),result.skipped(),result.message(),start);}
        catch(RuntimeException ex){runs.record("LEAVE_GRANT",date,"FAILED",0,0,"연차 부여에 실패했습니다. 서버 상태를 확인한 뒤 다시 실행하세요.",start);throw ex;}
    }
}
