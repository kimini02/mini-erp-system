package com.minierp.backend.domain.org.controller;

import com.minierp.backend.domain.org.service.OrgService;
import com.minierp.backend.domain.org.dto.OrgRequests.*;
import com.minierp.backend.global.permission.*;
import com.minierp.backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.time.Clock;
import java.util.List;
import static com.minierp.backend.global.permission.Action.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class OrgController {
    private final OrgService service;
    private final Clock clock;
    private ApiResponse<?> ok(Object value) {
        return ApiResponse.success(clock, value);
    }

    @GetMapping("/departments/tree")
    public ApiResponse<?> tree() {
        return ok(service.tree());
    }

    @GetMapping("/departments/{id}")
    public ApiResponse<?> department(@PathVariable long id) {
        return ok(service.detail(id));
    }

    @PostMapping("/departments")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(menu = "DEPARTMENT", action = CREATE)
    public ApiResponse<?> create(@Valid @RequestBody DepartmentCreate r) {
        return ok(service.createDepartment(r));
    }

    @PatchMapping("/departments/{id}")
    @RequirePermission(menu = "DEPARTMENT", action = UPDATE)
    public ApiResponse<?> patch(@PathVariable long id, @Valid @RequestBody DepartmentPatch r) {
        return ok(service.patchDepartment(id, r));
    }

    @PatchMapping("/departments/{id}/parent")
    @RequirePermission(menu = "DEPARTMENT", action = UPDATE)
    public ApiResponse<?> move(@PathVariable long id, @RequestBody Parent r) {
        return ok(service.move(id, r.parentId()));
    }

    @PutMapping("/departments/{id}/head")
    @RequirePermission(menu = "DEPARTMENT", action = UPDATE)
    public ApiResponse<?> head(@PathVariable long id, @Valid @RequestBody Head r) {
        return ok(service.setHead(id, r.userId()));
    }

    @DeleteMapping("/departments/{id}/head")
    @RequirePermission(menu = "DEPARTMENT", action = UPDATE)
    public ApiResponse<?> clearHead(@PathVariable long id) {
        return ok(service.setHead(id, null));
    }

    @PutMapping("/departments/{id}/work-schedule")
    @RequirePermission(menu = "DEPARTMENT", action = UPDATE)
    public ApiResponse<?> schedule(@PathVariable long id, @RequestBody Schedule r) {
        return ok(service.departmentSchedule(id, r));
    }

    @DeleteMapping("/departments/{id}")
    @RequirePermission(menu = "DEPARTMENT", action = DELETE)
    public ApiResponse<?> deleteDepartment(@PathVariable long id) {
        service.deleteDepartment(id);
        return ok(null);
    }

    @GetMapping("/work-schedules/default")
    public ApiResponse<?> schedule() {
        return ok(service.defaultSchedule());
    }

    @PutMapping("/work-schedules/default")
    @RequirePermission(menu = "DEPARTMENT", action = UPDATE)
    public ApiResponse<?> schedule(@RequestBody Schedule r) {
        return ok(service.defaultSchedule(r));
    }

    @GetMapping("/positions")
    public ApiResponse<?> positions() {
        return ok(service.positions());
    }

    @PostMapping("/positions")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(menu = "DEPARTMENT", action = CREATE)
    public ApiResponse<?> position(@Valid @RequestBody PositionName r) {
        return ok(service.createPosition(r.name()));
    }

    @PatchMapping("/positions/{id}")
    @RequirePermission(menu = "DEPARTMENT", action = UPDATE)
    public ApiResponse<?> position(@PathVariable long id, @Valid @RequestBody PositionName r) {
        return ok(service.renamePosition(id, r.name()));
    }

    @PutMapping("/positions/order")
    @RequirePermission(menu = "DEPARTMENT", action = UPDATE)
    public ApiResponse<?> order(@Valid @RequestBody List<@Valid PositionOrder> r) {
        return ok(service.orderPositions(r));
    }

    @DeleteMapping("/positions/{id}")
    @RequirePermission(menu = "DEPARTMENT", action = DELETE)
    public ApiResponse<?> deletePosition(@PathVariable long id) {
        service.deletePosition(id);
        return ok(null);
    }

    @GetMapping("/holidays")
    public ApiResponse<?> holidays(@RequestParam int year) {
        return ok(service.holidays(year));
    }

    @PostMapping("/holidays")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(menu = "HOLIDAY", action = CREATE)
    public ApiResponse<?> holiday(@Valid @RequestBody HolidayCreate r) {
        return ok(service.createHoliday(r));
    }

    @PatchMapping("/holidays/{id}")
    @RequirePermission(menu = "HOLIDAY", action = UPDATE)
    public ApiResponse<?> holiday(@PathVariable long id, @Valid @RequestBody HolidayPatch r) {
        return ok(service.patchHoliday(id, r));
    }

    @DeleteMapping("/holidays/{id}")
    @RequirePermission(menu = "HOLIDAY", action = DELETE)
    public ApiResponse<?> deleteHoliday(@PathVariable long id) {
        service.deleteHoliday(id);
        return ok(null);
    }
}
