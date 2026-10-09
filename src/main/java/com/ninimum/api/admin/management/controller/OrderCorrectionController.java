package com.ninimum.api.admin.management.controller;

import com.ninimum.api.admin.management.service.OrderCorrectionService;
import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.Result;
import com.ninimum.api.common.VersionResponseResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ninimum/api/v1/admin/management/orders")
public class OrderCorrectionController extends BaseController {
    private final OrderCorrectionService service;
    @javax.annotation.PostConstruct public void init() {setApiVersion(com.ninimum.api.constants.Constant.api_version);}
    @PostMapping("/{id}/correction") public Object correct(@PathVariable long id,@RequestBody Map<String,Object> body) {
        service.correct(id,body);return setResult(Result.SUCCESS,null);
    }
    @ExceptionHandler({IllegalArgumentException.class,AccessDeniedException.class})
    public ResponseEntity<VersionResponseResult> invalid(RuntimeException error) {
        var response=new VersionResponseResult();response.setResultCode("ORDER_CORRECTION_REJECTED");response.setResultMsg(error.getMessage());
        return ResponseEntity.status(error instanceof AccessDeniedException?403:409).body(response);
    }
}
