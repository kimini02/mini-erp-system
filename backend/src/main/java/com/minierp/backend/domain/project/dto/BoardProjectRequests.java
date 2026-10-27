package com.minierp.backend.domain.project.dto;
import jakarta.validation.constraints.*;
import com.minierp.backend.global.entity.Priority;
import java.time.LocalDate;
import java.util.List;
public final class BoardProjectRequests {
 private BoardProjectRequests() {}
 public record Create(@NotBlank @Pattern(regexp="[A-Z0-9]{2,10}") String key,@NotBlank @Size(max=100) String title,
  @Size(max=1000) String content,@NotNull LocalDate startDate,@NotNull LocalDate endDate,Priority priority,@NotNull @Positive Long leaderId) {}
 public record Patch(@Null String key,@Size(min=1,max=100) String title,@Size(max=1000) String content,LocalDate startDate,LocalDate endDate,Priority priority,@Positive Long leaderId) {}
 public record Members(@NotNull List<@NotNull @Positive Long> userIds) {}
}
