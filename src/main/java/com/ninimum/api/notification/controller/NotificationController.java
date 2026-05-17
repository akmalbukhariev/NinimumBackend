package com.ninimum.api.notification.controller;

import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.Result;
import com.ninimum.api.common.VersionResponseResult;
import com.ninimum.api.constants.Constant;
import com.ninimum.api.dto.NotificationDto;
import com.ninimum.api.notification.service.INotificationService;
import com.ninimum.api.param.DeleteNotificationParam;
import com.ninimum.api.param.NotificationListParam;
import com.ninimum.api.param.ReadNotificationParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Notification", description = "User notification APIs.")
@RequestMapping(value={"/ninimum/api/v1/notification"})
public class NotificationController extends BaseController {

    private final INotificationService notificationService;

    @PostConstruct
    public void init() {
        setApiVersion(Constant.api_version);
    }

    @Operation(
            tags = {"Notification"},
            summary = "1. Notification list",
            description = "Returns notification list by user ID.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/getNotificationList", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> getNotificationList(@RequestBody NotificationListParam param) {
        VersionResponseResult result = null;

        try {
            List<NotificationDto> notifications = this.notificationService.getNotificationList(param);
            result = this.setResult(Result.SUCCESS, notifications);
        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("NotificationController => getNotificationList: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"Notification"},
            summary = "2. Read notification",
            description = "Updates notification read status.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PutMapping(value = "/readNotification", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> readNotification(@RequestBody ReadNotificationParam param) {
        VersionResponseResult result = null;

        try {
            int resultNum = this.notificationService.readNotification(param);

            if (resultNum != 0) {
                result = this.setResult(Result.SUCCESS);
            } else {
                result = this.setResult(Result.SERVER_ERROR);
            }

        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("NotificationController => readNotification: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"Notification"},
            summary = "3. Delete notification",
            description = "Deletes notification.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @DeleteMapping(value = "/deleteNotification", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> deleteNotification(@RequestBody DeleteNotificationParam param) {
        VersionResponseResult result = null;

        try {
            int resultNum = this.notificationService.deleteNotification(param);

            if (resultNum != 0) {
                result = this.setResult(Result.SUCCESS);
            } else {
                result = this.setResult(Result.SERVER_ERROR);
            }

        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("NotificationController => deleteNotification: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}