package com.ninimum.api.deliveryapp.dispatch;
import com.ninimum.api.common.*;
import com.ninimum.api.constants.Constant;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.Objects;
@RestController
@RequestMapping("/ninimum/api/v1/admin/management/dispatch")
@PreAuthorize("hasAuthority('SUPER_ADMIN')")
public class CourierDispatchController extends BaseController {
 private final CourierDispatch service;
 public CourierDispatchController(CourierDispatch service){this.service=service;setApiVersion(Constant.api_version);}
 @GetMapping public Object settings(){return setResult(Result.SUCCESS,service.settings());}
 @PutMapping public Object settings(@RequestBody Map<String,Object> body){service.save(body);return setResult(Result.SUCCESS);}
 @GetMapping("/history") public Object history(@RequestParam(defaultValue="1")int page){return setResult(Result.SUCCESS,service.history(page));}
 @PostMapping("/{job}/retry") public Object retry(@PathVariable long job,@RequestBody Map<String,Object> body){service.retry(job,Objects.toString(body.get("reason"),""));return setResult(Result.SUCCESS);}
 @GetMapping("/escalated") public Object escalated(){return setResult(Result.SUCCESS,service.escalated());}
}
