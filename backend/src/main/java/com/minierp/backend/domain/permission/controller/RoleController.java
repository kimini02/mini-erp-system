package com.minierp.backend.domain.permission.controller;
import com.minierp.backend.domain.permission.service.RoleService;
import com.minierp.backend.domain.permission.dto.RoleRequests.*;
import com.minierp.backend.global.permission.*;
import com.minierp.backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.time.Clock;
import java.util.List;
import static com.minierp.backend.global.permission.Action.*;
@RestController @RequestMapping("/api/v1") @RequiredArgsConstructor
public class RoleController {
    private final RoleService service;private final Clock clock;
    private ApiResponse<?> ok(Object value) { return ApiResponse.success(clock,value); }
    @GetMapping("/menus") @RequirePermission(menu="ROLE",action=VIEW) public ApiResponse<?> menus() { return ok(service.menus()); }
    @GetMapping("/roles") @RequirePermission(menu="ROLE",action=VIEW) public ApiResponse<?> roles() { return ok(service.roles()); }
    @PostMapping("/roles") @ResponseStatus(HttpStatus.CREATED) @RequirePermission(menu="ROLE",action=CREATE)
    public ApiResponse<?> create(@Valid @RequestBody Create r) { return ok(service.create(r)); }
    @PatchMapping("/roles/{id}") @RequirePermission(menu="ROLE",action=UPDATE)
    public ApiResponse<?> patch(@PathVariable long id,@Valid @RequestBody Patch r) { return ok(service.patch(id,r)); }
    @DeleteMapping("/roles/{id}") @RequirePermission(menu="ROLE",action=DELETE)
    public ApiResponse<?> delete(@PathVariable long id) { service.delete(id);return ok(null); }
    @GetMapping("/roles/{id}/permissions") @RequirePermission(menu="ROLE",action=VIEW)
    public ApiResponse<?> permissions(@PathVariable long id) { return ok(service.permissions(id)); }
    @PutMapping("/roles/{id}/permissions") @RequirePermission(menu="ROLE",action=UPDATE)
    public ApiResponse<?> permissions(@PathVariable long id,@Valid @RequestBody List<@Valid Permission> r) { return ok(service.permissions(id,r)); }
    @GetMapping("/roles/{id}/members") @RequirePermission(menu="ROLE",action=VIEW)
    public ApiResponse<?> members(@PathVariable long id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return ok(service.members(id,page,size)); }
    @PostMapping("/roles/{id}/members") @RequirePermission(menu="ROLE",action=UPDATE)
    public ApiResponse<?> members(@PathVariable long id,@Valid @RequestBody Members r) { service.addMembers(id,r.userIds());return ok(null); }
    @DeleteMapping("/roles/{id}/members/{userId}") @RequirePermission(menu="ROLE",action=UPDATE)
    public ApiResponse<?> member(@PathVariable long id,@PathVariable long userId) { service.removeMember(id,userId);return ok(null); }
}
