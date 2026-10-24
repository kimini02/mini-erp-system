package com.minierp.backend.domain.org.service;
import com.minierp.backend.global.persistence.SqlStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.map;
@Component @RequiredArgsConstructor
public class WorkingDays {
    private final SqlStore db;
    public long count(LocalDate start,LocalDate end) {
        Set<LocalDate> holidays=new HashSet<>();
        db.rows("SELECT holiday_date FROM holidays WHERE holiday_date BETWEEN :start AND :end",map("start",start,"end",end)).forEach(row->holidays.add((LocalDate)row.get("holiday_date")));
        return start.datesUntil(end.plusDays(1)).filter(date->date.getDayOfWeek()!=DayOfWeek.SATURDAY && date.getDayOfWeek()!=DayOfWeek.SUNDAY && !holidays.contains(date)).count();
    }
}
