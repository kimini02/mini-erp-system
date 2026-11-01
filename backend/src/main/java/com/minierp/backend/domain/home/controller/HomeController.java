package com.minierp.backend.domain.home.controller;

import com.minierp.backend.domain.home.service.HomeService;
import com.minierp.backend.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.time.Clock;

@RestController
@RequestMapping("/api/v1/home")
@RequiredArgsConstructor
public class HomeController {
    private final HomeService service;
    private final Clock clock;
    // Login is enforced by JWT security; each widget checks its own menu permission.
    @GetMapping
    public ApiResponse<?> home() {
        return ApiResponse.success(clock, service.home());
    }
}
