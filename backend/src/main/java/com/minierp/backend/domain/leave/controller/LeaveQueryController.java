package com.minierp.backend.domain.leave.controller;
import com.fasterxml.jackson.databind.JsonNode;
import com.minierp.backend.domain.leave.service.LeaveQueryService;
import com.minierp.backend.global.permission.*;
import com.minierp.backend.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.time.Clock;
@RestController @RequestMapping("/api/v1/leave") @RequiredArgsConstructor
public class LeaveQueryController {
    private final LeaveQueryService service;private final Clock clock;
    @GetMapping("/balance") public ApiResponse<?> balance(@RequestParam(required=false) Integer year) {return ApiResponse.success(clock,service.balance(year));}
    @GetMapping("/ledger") public ApiResponse<?> ledger(@RequestParam(required=false) Integer year,@RequestParam(required=false) String type) {return ApiResponse.success(clock,service.ledger(year,type));}
    @PostMapping("/days-preview") @RequirePermission(menu="APPROVAL_DRAFT",action=Action.CREATE)
    public ApiResponse<?> preview(@RequestBody JsonNode body) {return ApiResponse.success(clock,service.preview(body));}
}
