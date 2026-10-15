package com.ninimum.api.warehouse;

import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.Result;
import com.ninimum.api.common.VersionResponseResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ninimum/api/v1/admin/warehouse/suppliers")
public class SupplierController extends BaseController {
    private final SupplierService service;
    @javax.annotation.PostConstruct public void init(){setApiVersion(com.ninimum.api.constants.Constant.api_version);}
    @GetMapping public Object list(@RequestParam(required=false) String search){return setResult(Result.SUCCESS,service.list(search));}
    @GetMapping("/calendar") public Object calendar(@RequestParam String from,@RequestParam String to){return setResult(Result.SUCCESS,service.calendar(from,to));}
    @GetMapping("/{id}") public Object detail(@PathVariable long id){return setResult(Result.SUCCESS,service.detail(id));}
    @PostMapping public Object create(@RequestBody Map<String,Object> body){return setResult(Result.SUCCESS,service.save(null,body));}
    @PutMapping("/{id}") public Object update(@PathVariable long id,@RequestBody Map<String,Object> body){return setResult(Result.SUCCESS,service.save(id,body));}
    @PostMapping("/{id}/payment-records") public Object record(@PathVariable long id,@RequestBody Map<String,Object> body){return setResult(Result.SUCCESS,service.recordPayment(id,body));}
    @PostMapping("/{id}/payment-records/{paymentId}/void") public Object voidRecord(@PathVariable long id,@PathVariable long paymentId,@RequestBody Map<String,Object> body){service.voidPayment(id,paymentId,java.util.Objects.toString(body.get("reason"),""));return setResult(Result.SUCCESS);}
    @ExceptionHandler(WarehouseException.class) public ResponseEntity<VersionResponseResult> invalid(WarehouseException ex){
        var response=new VersionResponseResult();response.setResultCode(ex.getMessage());response.setResultMsg(ex.getMessage());return ResponseEntity.status(409).body(response);
    }
}
