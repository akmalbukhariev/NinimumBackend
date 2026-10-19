package com.ninimum.api.order.recurring;
import com.ninimum.api.common.*;import com.ninimum.api.security.CurrentCustomer;
import org.springframework.web.bind.annotation.*;import java.util.Map;
@RestController @lombok.RequiredArgsConstructor
@RequestMapping("/ninimum/api/v1/order/recurring")
public class RecurringOrdersController extends BaseController {
 private final RecurringOrders service;private final CurrentCustomer customer;
 @PostMapping("/list") public Object list()throws Exception{return setResult(Result.SUCCESS,service.list(customer.id()));}
 @PostMapping("/preview") public Object preview(@RequestBody Map<String,Object> body)throws Exception{return setResult(Result.SUCCESS,service.preview(customer.id(),number(body.get("source_order_id"))));}
 @PostMapping("/save") public Object save(@RequestBody Map<String,Object> body)throws Exception{service.save(customer.id(),number(body.get("source_order_id")),number(body.get("max_total")),Boolean.TRUE.equals(body.get("consent")));return setResult(Result.SUCCESS);}
 @PostMapping("/{id}/pause") public Object pause(@PathVariable long id)throws Exception{service.pause(customer.id(),id);return setResult(Result.SUCCESS);}
 @PostMapping("/{id}/resume") public Object resume(@PathVariable long id)throws Exception{service.resume(customer.id(),id);return setResult(Result.SUCCESS);}
 private static long number(Object value){try{return new java.math.BigDecimal(String.valueOf(value)).longValueExact();}catch(Exception ex){throw new IllegalArgumentException("Invalid number");}}
 /** Stock and price checks from the order quote are shown to the customer as a normal message. */
 @ExceptionHandler({IllegalArgumentException.class,com.ninimum.api.order.service.StockUnavailableException.class,com.ninimum.api.order.service.impl.PriceChangedException.class}) public Object invalid(Exception ex){var result=setResult(Result.FAILED);result.setResultMsg(ex.getMessage()!=null?ex.getMessage():"Narx o‘zgardi. Qayta ko‘rib chiqing.");return result;}
}
