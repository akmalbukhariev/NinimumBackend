package com.ninimum.api.warehouse;

import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.Result;
import com.ninimum.api.common.VersionResponseResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/ninimum/api/v1/admin/warehouse")
public class WarehouseController extends BaseController {
    private final WarehouseService service;
    @javax.annotation.PostConstruct public void init() { setApiVersion(com.ninimum.api.constants.Constant.api_version); }
    private Object ok(Object data) { return setResult(Result.SUCCESS,data); }
    @GetMapping("/status") public Object status() { return ok(Map.of("enabled",service.isEnabled(),"stage",1)); }
    @GetMapping("/locations") public Object locations() { return ok(service.locations()); }
    @PostMapping("/locations") public Object createLocation(@RequestBody Map<String,Object> body) { return ok(service.createLocation(body)); }
    @GetMapping("/stock") public Object stock(@RequestParam(required=false) String search,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20",name="page_size") int size) {
        return ok(service.stock(search,page,size));
    }
    @GetMapping("/movements") public Object movements(@RequestParam(required=false,name="product_id") Long product,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20",name="page_size") int size) {
        return ok(service.movements(product,page,size));
    }
    @GetMapping("/receipts") public Object receipts(@RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="20",name="page_size") int size) { return ok(service.receipts(page,size)); }
    @GetMapping("/receipts/{id}") public Object receipt(@PathVariable long id) { return ok(service.receipt(id)); }
    @PostMapping("/receipts") public Object createReceipt(@RequestBody Map<String,Object> body) { return ok(service.createReceipt(body)); }
    @PostMapping("/receipts/{id}/post") public Object post(@PathVariable long id) { return ok(service.postReceipt(id)); }
    @PostMapping("/receipts/{id}/reverse") public Object reverse(@PathVariable long id,@RequestBody Map<String,Object> body) {
        return ok(service.reverseReceipt(id,body.get("reason")==null ? null : String.valueOf(body.get("reason"))));
    }
    @GetMapping("/preparation") public Object preparation(@RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="20",name="page_size") int size) { return ok(service.preparation(page,size)); }
    @GetMapping("/preparation/{id}/items") public Object preparationItems(@PathVariable long id) { return ok(service.preparationItems(id)); }

    private ResponseEntity<VersionResponseResult> error(String code,int status) {
        VersionResponseResult response=new VersionResponseResult();
        response.setResultCode(code); response.setResultMsg(code);
        return ResponseEntity.status(status).body(response);
    }
    @ExceptionHandler(WarehouseException.class) public ResponseEntity<VersionResponseResult> invalid(WarehouseException ex) {
        return error(ex.getMessage(), "WAREHOUSE_NOT_ENABLED".equals(ex.getMessage()) ? 503 : 409);
    }
    @ExceptionHandler(DuplicateKeyException.class) public ResponseEntity<VersionResponseResult> duplicate(DuplicateKeyException ex) {
        return error("WAREHOUSE_DUPLICATE",409);
    }
    @ExceptionHandler(Exception.class) public ResponseEntity<VersionResponseResult> unexpected(Exception ex) {
        log.error("Warehouse request failed",ex);
        return error("WAREHOUSE_SERVER_ERROR",500);
    }
}
