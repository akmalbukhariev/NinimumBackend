package com.ninimum.api.tariff.benefits;
import com.ninimum.api.common.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequiredArgsConstructor
@RequestMapping("/ninimum/api/v1/admin/management/tariffs/{id}/benefits")
public class TariffBenefitController extends BaseController {
    private final TariffBenefitService service;
    @GetMapping public Object get(@PathVariable long id){return setResult(Result.SUCCESS,service.get(id));}
    @PutMapping public Object save(@PathVariable long id,@RequestBody Map<String,Object> body) throws Exception {service.save(id,body);return setResult(Result.SUCCESS,service.get(id));}
}
