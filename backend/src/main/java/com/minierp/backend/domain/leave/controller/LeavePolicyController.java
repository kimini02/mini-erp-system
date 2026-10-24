package com.minierp.backend.domain.leave.controller;
import com.minierp.backend.domain.leave.dto.LeavePolicyResponseDto;
import com.minierp.backend.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.time.Clock;
import java.util.List;
@RestController @RequestMapping("/api/v1/leave/policy") @RequiredArgsConstructor
public class LeavePolicyController {
    private final Clock clock;
    // Compatibility contract only; M3 replaces the legacy position-based grant policy.
    @GetMapping public ApiResponse<?> policy() {
        return ApiResponse.success(clock,List.of(new LeavePolicyResponseDto("사원",14),new LeavePolicyResponseDto("대리",16),new LeavePolicyResponseDto("과장",17),new LeavePolicyResponseDto("팀장",18),new LeavePolicyResponseDto("관리소장",19)));
    }
}
