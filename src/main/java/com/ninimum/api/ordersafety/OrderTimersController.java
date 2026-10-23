package com.ninimum.api.ordersafety;

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
@RequestMapping("/ninimum/api/v1/admin/management")
public class OrderTimersController extends BaseController {
    private final OrderTimersService service;
    @javax.annotation.PostConstruct public void init(){setApiVersion(com.ninimum.api.constants.Constant.api_version);}
    @GetMapping("/orders/problems") public Object problems(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20",name="page_size") int size,@RequestParam(defaultValue="false") boolean history){return setResult(Result.SUCCESS,service.problems(page,size,history));}
    @GetMapping("/orders/problems/events") public Object events(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20",name="page_size") int size){return setResult(Result.SUCCESS,service.events(page,size));}
    @GetMapping("/orders/problems/summary") public Object summary(){return setResult(Result.SUCCESS,service.summary());}
    @GetMapping("/order-timers") public Object settings(){return setResult(Result.SUCCESS,service.settings());}
    private final TelegramNotifier telegram;
    /** K7: whether Telegram alerts are set up on the server, and a test message (owner only, like the timers). */
    @GetMapping("/order-timers/telegram") public Object telegram(){owner();return setResult(Result.SUCCESS,Map.of("enabled",telegram.enabled()));}
    @PostMapping("/order-timers/telegram/test") public Object telegramTest(){owner();return setResult(Result.SUCCESS,Map.of("enabled",telegram.enabled(),"sent",telegram.test()));}
    private static void owner(){if(!com.ninimum.api.admin.access.StaffAccess.isOwner())throw new AccessDeniedException("Owner only");}
    @PutMapping("/order-timers") public Object save(@RequestBody Map<String,Object> body){service.saveSettings(body);return setResult(Result.SUCCESS,service.settings());}
    @ExceptionHandler({IllegalArgumentException.class,AccessDeniedException.class})
    public ResponseEntity<VersionResponseResult> invalid(RuntimeException error){var result=new VersionResponseResult();result.setResultCode("ORDER_TIMER_REJECTED");result.setResultMsg(error.getMessage());return ResponseEntity.status(error instanceof AccessDeniedException?403:400).body(result);}
}
