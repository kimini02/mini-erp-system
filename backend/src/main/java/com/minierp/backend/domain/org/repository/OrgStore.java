package com.minierp.backend.domain.org.repository;

import com.minierp.backend.global.persistence.SqlStore;
import com.minierp.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.time.*;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.map;

@Repository
@RequiredArgsConstructor
public class OrgStore {
    private final SqlStore db;

    public long insertDepartment(String name, String code, Long parent, Integer sort) {
        return db.insert(
                "INSERT INTO departments(name,code,parent_id,sort_order,active) VALUES(:name,:code,:parent,:sort,1)",
                map("name", name, "code", code, "parent", parent, "sort", sort));
    }

    public int updateDepartmentCode(long id, String code) {
        return db.update("UPDATE departments SET code=:code WHERE id=:id", map("id", id, "code", code));
    }

    public int updateDepartment(long id, String name, Integer sort) {
        return db.update(
                "UPDATE departments SET name=COALESCE(:name,name),sort_order=COALESCE(:sort,sort_order) WHERE id=:id",
                map("id", id, "name", name, "sort", sort));
    }

    public int updateDepartmentParent(long id, Long parent) {
        return db.update("UPDATE departments SET parent_id=:parent WHERE id=:id", map("id", id, "parent", parent));
    }

    public Map<String, Object> lockHeadCandidate(Long id) {
        return db.one("SELECT id,department_id,status FROM users WHERE id=:id FOR UPDATE", map("id", id),
                ErrorCode.USER_NOT_FOUND);
    }

    public int updateDepartmentHead(long id, Long head) {
        return db.update("UPDATE departments SET head_user_id=:head WHERE id=:id", map("id", id, "head", head));
    }

    public int deleteDepartment(long id) {
        return db.update("DELETE FROM departments WHERE id=:id", map("id", id));
    }

    public int updateDefaultSchedule(LocalTime start, LocalTime end, Integer grace) {
        return db.update(
                "UPDATE work_schedules SET start_time=:start,end_time=:end,late_grace_min=:grace WHERE is_default=1",
                map("start", start, "end", end, "grace", grace));
    }

    public int updateDepartmentToDefaultSchedule(long id) {
        return db.update("UPDATE departments SET work_schedule_id=NULL WHERE id=:id", map("id", id));
    }

    public int updateDepartmentSchedule(long id, Long schedule) {
        return db.update("UPDATE departments SET work_schedule_id=:schedule WHERE id=:id",
                map("id", id, "schedule", schedule));
    }

    public long insertPosition(String name) {
        return db.insert("INSERT INTO positions(name,rank_order) VALUES(:name,0)", map("name", name));
    }

    public int updatePositionName(long id, String name) {
        return db.update("UPDATE positions SET name=:name WHERE id=:id", map("id", id, "name", name));
    }

    public int updatePositionOrder(Integer rank, Long id) {
        return db.update("UPDATE positions SET rank_order=:rank WHERE id=:id", map("rank", rank, "id", id));
    }

    public Map<String, Object> lockPosition(long id) {
        return db.one("SELECT id FROM positions WHERE id=:id FOR UPDATE", map("id", id), ErrorCode.POSITION_NOT_FOUND);
    }

    public int deletePosition(long id) {
        return db.update("DELETE FROM positions WHERE id=:id", map("id", id));
    }

    public long insertHoliday(LocalDate date, String name, Boolean sub) {
        return db.insert("INSERT INTO holidays(holiday_date,name,substitute) VALUES(:date,:name,:sub)",
                map("date", date, "name", name, "sub", sub));
    }

    public int updateHoliday(long id, String name, Boolean sub) {
        return db.update(
                "UPDATE holidays SET name=COALESCE(:name,name),substitute=COALESCE(:sub,substitute) WHERE id=:id",
                map("id", id, "name", name, "sub", sub));
    }

    public int deleteHoliday(long id) {
        return db.update("DELETE FROM holidays WHERE id=:id", map("id", id));
    }

    public long insertWorkSchedule(LocalTime start, LocalTime end, Integer grace, String name) {
        var params = workScheduleParameters(start, end, grace, name);
        return db.insert(
                "INSERT INTO work_schedules(name,start_time,end_time,late_grace_min,is_default) VALUES(:name,:start,:end,:grace,0)",
                params);
    }

    public int updateWorkSchedule(LocalTime start, LocalTime end, Integer grace, String name, Long id) {
        var params = workScheduleParameters(start, end, grace, name);
        params.put("id", id);
        return db.update("UPDATE work_schedules SET start_time=:start,end_time=:end,late_grace_min=:grace WHERE id=:id",
                params);
    }

    private Map<String, Object> workScheduleParameters(LocalTime start, LocalTime end, Integer grace, String name) {
        return map("start", start, "end", end, "grace", grace, "name", name);
    }
}
