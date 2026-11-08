package com.minierp.backend.domain.org.dto;

import java.util.List;
import java.util.Map;
import static com.minierp.backend.global.persistence.SqlStore.number;

public record OrgDepartmentResponseDto(
        long id,
        String name,
        String code,
        Long parentId,
        List<Path> path,
        Head head,
        FallbackHead fallbackHead,
        WorkSchedule workSchedule,
        long directMemberCount,
        long childCount) {
    public record Path(long id, String name) {
        public static Path from(Map<String, Object> row) {
            return new Path(number(row, "id"), (String) row.get("name"));
        }
    }

    public record Head(long id, String name, String position) {
        public static Head from(Map<String, Object> row) {
            if (row == null) return null;
            return new Head(number(row, "id"), (String) row.get("name"), (String) row.get("position"));
        }

        public static Head fromDepartment(Map<String, Object> row) {
            if (row.get("head_user_id") == null) return null;
            return new Head(number(row, "head_user_id"),
                    (String) row.get("head_name"), (String) row.get("head_position"));
        }
    }

    public record FallbackHead(long id, String name, String position, String departmentName) {
        public static FallbackHead from(Map<String, Object> row) {
            if (row == null) return null;
            return new FallbackHead(number(row, "id"), (String) row.get("name"),
                    (String) row.get("position"), (String) row.get("departmentName"));
        }
    }

    public record WorkSchedule(
            long id,
            String name,
            String startTime,
            String endTime,
            long lateGraceMin,
            boolean useDefault) {
        public static WorkSchedule from(Map<String, Object> row, boolean useDefault) {
            return new WorkSchedule(number(row, "id"), (String) row.get("name"), (String) row.get("startTime"),
                    (String) row.get("endTime"), number(row, "lateGraceMin"), useDefault);
        }
    }
}
