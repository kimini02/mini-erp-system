package com.minierp.backend.domain.approval.controller;
import com.minierp.backend.domain.approval.service.InboxService;
import com.minierp.backend.global.permission.*;
import com.minierp.backend.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.time.Clock;
@RestController @RequestMapping("/api/v1/approval-inbox") @RequiredArgsConstructor
public class InboxController {
    private final InboxService service;private final Clock clock;
    @GetMapping @RequirePermission(menu="APPROVAL_INBOX",action=Action.VIEW)
    public ApiResponse<?> list(@RequestParam(defaultValue="todo") String box,@RequestParam(required=false) String formCode,@RequestParam(required=false) String q,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {return ApiResponse.success(clock,service.list(box,formCode,q,page,size));}
    @GetMapping("/counts") @RequirePermission(menu="APPROVAL_INBOX",action=Action.VIEW)
    public ApiResponse<?> counts() {return ApiResponse.success(clock,service.counts());}
}
