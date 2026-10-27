package com.minierp.backend.domain.task.dto;
import jakarta.validation.constraints.*;
import com.minierp.backend.global.entity.Priority;
import com.minierp.backend.domain.task.entity.TaskStatus;
import java.time.LocalDate;
import java.util.List;
public final class BoardTaskRequests {
 private BoardTaskRequests() {}
 public record Create(@NotBlank @Size(max=100) String title,@Size(max=1000) String content,TaskStatus status,
  List<@NotNull @Positive Long> assigneeIds,LocalDate endDate,Priority priority) {}
 public record Patch(@Size(min=1,max=100) String title,@Size(max=1000) String content,LocalDate endDate,Priority priority,List<@NotNull @Positive Long> assigneeIds) {}
 public record Move(@NotNull TaskStatus status,@Positive Long afterTaskId,@NotNull @Min(0) Integer version) {}
}
