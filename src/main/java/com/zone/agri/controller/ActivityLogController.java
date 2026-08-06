package com.zone.agri.controller;

import com.zone.agri.dto.response.activity.ActivityLogModuleResponse;
import com.zone.agri.dto.response.activity.ActivityLogResponse;
import com.zone.agri.service.ActivityLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/activity-logs")
@RequiredArgsConstructor
@Tag(name = "Activity Logs", description = "Nhật ký hoạt động hệ thống")
public class ActivityLogController {

    private final ActivityLogService activityLogService;

    @Operation(summary = "Tra cứu nhật ký hoạt động")
    @GetMapping
    public ResponseEntity<Page<ActivityLogResponse>> search(
            @RequestParam(required = false) Long actorUserId,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String keyword,
            Pageable pageable) {
        return ResponseEntity.ok(activityLogService.search(
                actorUserId,
                branchId,
                module,
                fromDate,
                toDate,
                keyword,
                pageable));
    }

    @Operation(summary = "Danh sách nhóm chức năng có thể lọc")
    @GetMapping("/modules")
    public ResponseEntity<List<ActivityLogModuleResponse>> modules() {
        return ResponseEntity.ok(activityLogService.getModules());
    }
}
