package com.minierp.backend.domain.attendance.entity;
import com.minierp.backend.domain.user.entity.User;
import com.minierp.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;
@Entity @Table(name="attendances") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
@org.hibernate.annotations.Immutable
public class Attendance extends BaseEntity {
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="user_id",nullable=false) private User user;
    @Column(name="work_date",nullable=false) private LocalDate workDate;
    @Column(name="clock_in_at") private LocalDateTime clockInAt;
    @Column(name="clock_out_at") private LocalDateTime clockOutAt;
    @Column(name="sched_start",nullable=false) private LocalTime schedStart;
    @Column(name="sched_end",nullable=false) private LocalTime schedEnd;
    @Column(name="sched_grace_min",nullable=false) private int schedGraceMin;
    @Column(name="sched_source",nullable=false) private String schedSource;
    @Column(name="sched_department_name",nullable=false) private String schedDepartmentName;
    @Column(name="day_type",nullable=false) private String dayType;
    @Column(name="in_result",nullable=false) private String inResult;
    @Column(name="out_result") private String outResult;
    @Column(name="absent",nullable=false) private boolean absent;
    @Column(name="judged_at") private LocalDateTime judgedAt;
    @Column(name="fix_document_id") private Long fixDocumentId;
    // Read-only compatibility for legacy DTOs until the M3 API removal task.
    public LocalTime getClockInTime() {return clockInAt==null?null:clockInAt.toLocalTime();}
    public LocalTime getClockOutTime() {return clockOutAt==null?null:clockOutAt.toLocalTime();}
    public AttendanceStatus getAttStatus() {return "LEAVE".equals(dayType)?AttendanceStatus.LEAVE:absent?AttendanceStatus.ABSENT:"LATE".equals(inResult)?AttendanceStatus.LATE:AttendanceStatus.NORMAL;}
}
