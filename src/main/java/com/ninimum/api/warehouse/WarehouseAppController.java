package com.ninimum.api.warehouse;
import com.ninimum.api.common.*;
import com.ninimum.api.constants.Constant;
import com.ninimum.api.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@Slf4j @RestController @RequiredArgsConstructor
@RequestMapping("/ninimum/api/v1/warehouse-app")
public class WarehouseAppController extends BaseController {
    private final WarehouseAppService service;
    private final JwtTokenProvider jwt;
    @javax.annotation.PostConstruct public void init() {setApiVersion(Constant.api_version);}
    private Object ok(Object value) {return setResult(Result.SUCCESS,value);}
    @PostMapping("/login") public Object login(@RequestBody Map<String,Object> body) {
        try {return ok(service.login(Objects.toString(body.get("worker_code"),""),Objects.toString(body.get("password"),""),code ->
            jwt.generateToken(new UsernamePasswordAuthenticationToken(code,null,List.of(new SimpleGrantedAuthority(Constant.ROLE_WAREHOUSE)))).getAccessToken()));}
        catch(WarehouseException ex) {if ("WAREHOUSE_PREPARATION_DISABLED".equals(ex.getMessage())) throw ex;throw new WarehouseException("WAREHOUSE_LOGIN_FAILED");}
    }
    @GetMapping("/me") public Object me(Authentication auth) {return ok(service.worker(auth.getName()));}
    @PostMapping("/logout") public Object logout(Authentication auth,@RequestHeader("Authorization") String token) {service.logout(auth.getName(),token.substring(7));return ok(null);}
    @GetMapping("/orders") public Object orders(Authentication auth,@RequestParam(defaultValue="queue") String view,@RequestParam(defaultValue="1") int page) {return ok(service.orders(auth.getName(),view,page,20,false));}
    @GetMapping("/orders/{id}") public Object detail(@PathVariable long id) {return ok(service.detail(id));}
    @PostMapping("/orders/{id}/claim") public Object claim(Authentication auth,@PathVariable long id) {return ok(service.claim(id,auth.getName()));}
    @PostMapping("/orders/{id}/check") public Object check(Authentication auth,@PathVariable long id,@RequestBody Map<String,Object> body) {return ok(service.check(id,auth.getName(),body));}
    @PostMapping("/orders/{id}/problem") public Object problem(Authentication auth,@PathVariable long id,@RequestBody Map<String,Object> body) {return ok(service.problem(id,auth.getName(),body));}
    @PostMapping("/orders/{id}/resume") public Object resume(Authentication auth,@PathVariable long id) {return ok(service.resume(id,auth.getName()));}
    @PostMapping("/orders/{id}/ready") public Object ready(Authentication auth,@PathVariable long id) {return ok(service.ready(id,auth.getName()));}
    @PostMapping("/orders/{id}/return-claim") public Object returnClaim(Authentication auth,@PathVariable long id) {return ok(service.returnClaim(id,auth.getName()));}
    @PostMapping("/orders/{id}/return-check") public Object returnCheck(Authentication auth,@PathVariable long id,@RequestBody Map<String,Object> body) {return ok(service.returnCheck(id,auth.getName(),body));}
    @PostMapping("/orders/{id}/return-receive") public Object returnReceive(Authentication auth,@PathVariable long id) {return ok(service.returnReceive(id,auth.getName()));}
    @ExceptionHandler(WarehouseException.class) public ResponseEntity<VersionResponseResult> invalid(WarehouseException ex) {return error(ex.getMessage(),"WAREHOUSE_LOGIN_FAILED".equals(ex.getMessage())?401:409);}
    @ExceptionHandler(Exception.class) public ResponseEntity<VersionResponseResult> failure(Exception ex) {log.error("Warehouse app request failed",ex);return error("WAREHOUSE_SERVER_ERROR",500);}
    private ResponseEntity<VersionResponseResult> error(String code,int status) {var r=new VersionResponseResult();r.setResultCode(code);r.setResultMsg(code);return ResponseEntity.status(status).body(r);}
}
