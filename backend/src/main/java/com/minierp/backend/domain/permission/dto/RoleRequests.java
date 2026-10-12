package com.minierp.backend.domain.permission.dto;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.minierp.backend.global.permission.DataScope;
import java.util.List;
public final class RoleRequests {
    public record Create(@NotBlank @Size(max=100) String name,@Size(max=500) String description) {}
    public record Patch(@Size(min=1,max=100) String name,@Size(max=500) String description) {}
    public record Permission(@NotBlank String menu,boolean view,boolean create,boolean update,@JsonProperty("delete") boolean deleteAction,DataScope scope) {}
    public record Members(@NotNull List<@NotNull Long> userIds) {}
}
