package com.minierp.backend.domain.attendance.service;
import com.minierp.backend.global.exception.*;
import com.minierp.backend.global.batch.BatchRunRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.*;
@Service @RequiredArgsConstructor
public class AbsenceBatchService {
    private final AbsenceBatchWork work;private final BatchRunRecorder runs;private final Clock clock;
    public AbsenceBatchWork.Counts runPreviousDay() {return run(LocalDate.now(clock).minusDays(1));}
    public AbsenceBatchWork.Counts run(LocalDate date) {
        if(date==null || !date.isBefore(LocalDate.now(clock))) throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        var started=LocalDateTime.now(clock);
        try {
            var result=work.run(date);runs.record("ATTENDANCE_ABSENCE",date,"SUCCESS",result.processed(),result.skipped(),null,started);return result;
        } catch(RuntimeException error) {
            runs.record("ATTENDANCE_ABSENCE",date,"FAILED",0,0,"결근 판정에 실패했습니다. 서버 상태를 확인한 뒤 다시 실행하세요.",started);throw error;
        }
    }
}
