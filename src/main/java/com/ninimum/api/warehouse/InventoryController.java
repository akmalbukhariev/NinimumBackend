package com.ninimum.api.warehouse;
import com.ninimum.api.common.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.Map;
@RestController @lombok.RequiredArgsConstructor
@RequestMapping({"/ninimum/api/v1/admin/warehouse/inventory","/ninimum/api/v1/warehouse-app/inventory"})
@PreAuthorize("hasAnyAuthority('SUPER_ADMIN','ADMIN','WAREHOUSE')")
public class InventoryController extends BaseController {
 private final InventoryService service;
 @GetMapping("/status") public Object status(){return setResult(Result.SUCCESS,service.status());}
 @GetMapping("/balances") public Object balances(@RequestParam(required=false,name="product_id")Long product,@RequestParam(required=false,name="location_id")Long location,@RequestParam(defaultValue="1")int page){return safe(service.balances(product,location,page));}
 @GetMapping("/movements") public Object movements(@RequestParam(required=false,name="product_id")Long product,@RequestParam(defaultValue="1")int page){return setResult(Result.SUCCESS,service.movements(product,page));}
 @GetMapping("/expiry") public Object expiry(@RequestParam(defaultValue="30")int days){return safe(service.expiry(days));}
 @GetMapping("/documents") public Object documents(@RequestParam(defaultValue="1")int page,@RequestParam(required=false)String kind,@RequestParam(required=false)String status){return safe(service.documents(page,kind,status));}
 @GetMapping("/documents/{id}") public Object document(@PathVariable long id){return safe(service.document(id));}
 @PostMapping("/documents") public Object create(@RequestBody Map<String,Object> body){return setResult(Result.SUCCESS,service.create(body));}
 @PostMapping("/documents/{id}/count") public Object count(@PathVariable long id,@RequestBody Map<String,Object> body){return safe(service.submitCount(id,body));}
 @PostMapping("/documents/{id}/approve") public Object approve(@PathVariable long id,@RequestBody Map<String,Object> body){return safe(service.approve(id,body));}
 @PostMapping("/documents/{id}/void") public Object cancel(@PathVariable long id,@RequestBody Map<String,Object> body){service.cancel(id,java.util.Objects.toString(body.get("reason"),""));return setResult(Result.SUCCESS);}
 @GetMapping("/settings") @PreAuthorize("hasAuthority('SUPER_ADMIN')") public Object settings(){return setResult(Result.SUCCESS,service.settings());}
 @PutMapping("/settings") @PreAuthorize("hasAuthority('SUPER_ADMIN')") public Object settings(@RequestBody Map<String,Object> body){service.saveSettings(body);return setResult(Result.SUCCESS);}
 @GetMapping("/writeoff-summary") @PreAuthorize("hasAuthority('SUPER_ADMIN')") public Object writeoffSummary(){return setResult(Result.SUCCESS,service.writeoffSummary());}
 private Object safe(Object value){var a=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();boolean owner=a!=null&&a.getAuthorities().stream().anyMatch(r->"SUPER_ADMIN".equals(r.getAuthority()));boolean money=owner||a!=null&&a.getAuthorities().stream().anyMatch(r->"ADMIN".equals(r.getAuthority()))&&com.ninimum.api.admin.access.StaffAccess.can(com.ninimum.api.admin.access.StaffAccess.MONEY);return setResult(Result.SUCCESS,money?value:hideCosts(value));}
 private Object hideCosts(Object value){
  if(value instanceof Map<?,?> map){var copy=new java.util.LinkedHashMap<String,Object>();for(var e:map.entrySet()){String key=String.valueOf(e.getKey());if(!java.util.Set.of("unit_cost","unit_price","cost_total","unit_cost_snapshot","expected_stock").contains(key)&&!(map.get("count_document")!=null&&java.util.Set.of("physical_quantity","reserved_quantity","available_quantity").contains(key)))copy.put(key,hideCosts(e.getValue()));}return copy;}
  if(value instanceof Iterable<?> list){var copy=new java.util.ArrayList<Object>();for(Object child:list)copy.add(hideCosts(child));return copy;}return value;
 }
 @ExceptionHandler(WarehouseException.class) public Object invalid(WarehouseException ex){var r=setResult(Result.FAILED);r.setResultCode(ex.getMessage());r.setResultMsg(ex.getMessage());return r;}
}
