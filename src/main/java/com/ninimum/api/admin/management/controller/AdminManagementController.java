package com.ninimum.api.admin.management.controller;

import com.ninimum.api.admin.management.service.IAdminManagementService;
import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.Result;
import com.ninimum.api.constants.Constant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/ninimum/api/v1/admin/management")
public class AdminManagementController extends BaseController {
    private final IAdminManagementService service;

    @PostConstruct
    public void init() {
        setApiVersion(Constant.api_version);
    }

    private ResponseEntity<Object> ok(Object data) {
        return new ResponseEntity<>(setResult(Result.SUCCESS, data), HttpStatus.OK);
    }

    private ResponseEntity<Object> changed(int count) {
        return new ResponseEntity<>(setResult(count >= 0 ? Result.SUCCESS : Result.SERVER_ERROR), HttpStatus.OK);
    }

    private ResponseEntity<Object> fail(Exception ex, String action) {
        if (ex instanceof com.ninimum.api.warehouse.WarehouseException) {
            var response = new com.ninimum.api.common.VersionResponseResult();
            response.setResultCode(ex.getMessage()); response.setResultMsg(ex.getMessage());
            return new ResponseEntity<>(response, HttpStatus.CONFLICT);
        }
        log.error("AdminManagementController => {}", action, ex);
        return new ResponseEntity<>(setResult(Result.SERVER_ERROR), HttpStatus.OK);
    }

    @GetMapping("/orders")
    public ResponseEntity<Object> orders(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(name="payment_status",required=false) String paymentStatus,
            @RequestParam(name="date_from",required=false) String from,
            @RequestParam(name="date_to",required=false) String to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        try {
            return ok(service.getFilteredOrders(search,status,paymentStatus,from,to,page,pageSize));
        } catch (Exception ex) {
            return fail(ex, "orders");
        }
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<Object> order(@PathVariable long id) {
        try {
            return ok(service.getOrder(id));
        } catch (Exception ex) {
            return fail(ex, "order");
        }
    }

    @PutMapping("/orders/{id}/status")
    public ResponseEntity<Object> orderStatus(@PathVariable long id, @RequestBody Map<String, Object> body) {
        try {
            return changed(service.updateOrderStatus(id, body));
        } catch (Exception ex) {
            return fail(ex, "orderStatus");
        }
    }

    @GetMapping("/products")
    public ResponseEntity<Object> products(
            @RequestParam(required = false) String search,
            @RequestParam(name = "category_id", required = false) Long categoryId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        try {
            return ok(service.getProducts(search, categoryId, active, page, pageSize));
        } catch (Exception ex) {
            return fail(ex, "products");
        }
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<Object> product(@PathVariable long id) {
        try {
            return ok(service.getProduct(id));
        } catch (Exception ex) {
            return fail(ex, "product");
        }
    }

    @PutMapping("/products/{id}")
    public ResponseEntity<Object> updateProduct(@PathVariable long id, @RequestBody Map<String, Object> body) {
        try {
            return changed(service.updateProduct(id, body));
        } catch (Exception ex) {
            return fail(ex, "updateProduct");
        }
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<Object> deleteProduct(@PathVariable long id) {
        try {
            return changed(service.deleteProduct(id));
        } catch (IllegalStateException ex) {
            log.warn("AdminManagementController => deleteProduct: {}", ex.getMessage());
            com.ninimum.api.common.VersionResponseResult result = setResult(Result.SERVER_ERROR);
            if (ex.getMessage() != null && !ex.getMessage().isBlank()) result.setResultMsg(ex.getMessage());
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception ex) {
            return fail(ex, "deleteProduct");
        }
    }

    @GetMapping({"/products/{id}/price-history","/products/{id}/prices"}) public ResponseEntity<Object> priceHistory(@PathVariable long id) {return ok(service.getProductPriceHistory(id));}

    @PostMapping(value = "/products/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Object> addProductImages(
            @PathVariable long id,
            @RequestParam(value = "images", required = false) List<MultipartFile> images) {
        try {
            return changed(service.addProductImages(id, images));
        } catch (Exception ex) {
            return fail(ex, "addProductImages");
        }
    }

    @PostMapping(value = "/products/{id}/image-data", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> addProductImageData(@PathVariable long id, @RequestBody Map<String, Object> body) {
        try {
            return changed(service.addProductImageData(id, body));
        } catch (Exception ex) {
            log.error("AdminManagementController => addProductImageData", ex);
            com.ninimum.api.common.VersionResponseResult result = setResult(Result.SERVER_ERROR);
            if (ex.getMessage() != null && !ex.getMessage().isBlank()) result.setResultMsg(ex.getMessage());
            return new ResponseEntity<>(result, HttpStatus.OK);
        }
    }

    @PostMapping(value = "/products/{id}/image-bytes")
    public ResponseEntity<Object> addProductImageBytes(
            @PathVariable long id,
            @RequestParam("fileName") String fileName,
            @RequestHeader(value = "Content-Type", required = false) String contentType,
            @RequestBody byte[] bytes) {
        try {
            return changed(service.addProductImageBytes(id, bytes, fileName, contentType));
        } catch (Exception ex) {
            log.error("AdminManagementController => addProductImageBytes", ex);
            com.ninimum.api.common.VersionResponseResult result = setResult(Result.SERVER_ERROR);
            if (ex.getMessage() != null && !ex.getMessage().isBlank()) result.setResultMsg(ex.getMessage());
            return new ResponseEntity<>(result, HttpStatus.OK);
        }
    }

    @DeleteMapping("/products/{productId}/images/{imageId}")
    public ResponseEntity<Object> deleteProductImage(@PathVariable long productId, @PathVariable long imageId) {
        try {
            return changed(service.deleteProductImage(productId, imageId));
        } catch (Exception ex) {
            return fail(ex, "deleteProductImage");
        }
    }

    @PostMapping("/categories/image-data")
    public ResponseEntity<Object> categoryImage(@RequestBody Map<String,Object> body) {
        try { return ok(Map.of("image_url",service.uploadCategoryImage(body))); }
        catch(Exception ex) { return fail(ex,"categoryImage"); }
    }

    @GetMapping("/categories")
    public ResponseEntity<Object> categories() {
        try {
            return ok(service.getCategories());
        } catch (Exception ex) {
            return fail(ex, "categories");
        }
    }

    @PostMapping("/categories")
    public ResponseEntity<Object> createCategory(@RequestBody Map<String, Object> body) {
        try {
            return changed(service.createCategory(body));
        } catch (Exception ex) {
            return fail(ex, "createCategory");
        }
    }

    @PutMapping("/categories/{id}")
    public ResponseEntity<Object> updateCategory(@PathVariable long id, @RequestBody Map<String, Object> body) {
        try {
            return changed(service.updateCategory(id, body));
        } catch (Exception ex) {
            return fail(ex, "updateCategory");
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    private com.ninimum.api.admin.management.service.CustomerCardService customerCards;

    @GetMapping("/customers/{id}")
    public ResponseEntity<Object> customerCard(@PathVariable long id,
            @RequestParam(defaultValue="orders") String section,@RequestParam(defaultValue="1") int page) {
        try { return ok(customerCards.get(id,section,page)); }
        catch(Exception ex) { return fail(ex,"customerCard"); }
    }

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.JdbcTemplate managementJdbc;

    @PutMapping("/questions/{id}/kind")
    public ResponseEntity<Object> classifyQuestion(@PathVariable long id,@RequestBody Map<String,Object> body) {
        try {
            String kind=java.util.Objects.toString(body.get("kind"),"");
            if(!java.util.Set.of("QUESTION","COMPLAINT").contains(kind))throw new IllegalArgumentException("Invalid question type");
            return changed(managementJdbc.update("UPDATE product_questions SET kind=?,classified_by=?,classified_at=UTC_TIMESTAMP(),updated_at=UTC_TIMESTAMP() WHERE id=?",kind,com.ninimum.api.warehouse.WarehouseService.actor(),id));
        } catch(Exception ex) {return fail(ex,"classifyQuestion");}
    }

    @org.springframework.beans.factory.annotation.Autowired
    private com.ninimum.api.admin.management.service.CustomerMergeService customerMerges;

    /** M11: active accounts whose phone numbers are the same number in different forms. */
    @GetMapping("/customers/duplicates")
    public ResponseEntity<Object> customerDuplicates() {
        try { return ok(customerMerges.duplicates()); }
        catch(Exception ex) { return fail(ex,"customerDuplicates"); }
    }

    /** M11: moves everything from merge_user_id to {id} and closes merge_user_id (owner only). */
    @PostMapping("/customers/{id}/merge")
    public ResponseEntity<Object> mergeCustomer(@PathVariable long id,@RequestBody Map<String,Object> body) {
        try {
            Object other=body.containsKey("merge_user_id")?body.get("merge_user_id"):body.get("mergeUserId");
            long mergeId=other instanceof Number?((Number)other).longValue():Long.parseLong(java.util.Objects.toString(other,"0"));
            return ok(customerMerges.merge(id,mergeId,java.util.Objects.toString(body.get("reason"),"")));
        }
        catch(NumberFormatException ex) { return fail(new com.ninimum.api.warehouse.WarehouseException("CUSTOMER_MERGE_INVALID"),"mergeCustomer"); }
        catch(Exception ex) { return fail(ex,"mergeCustomer"); }
    }

    @GetMapping("/customers")
    public ResponseEntity<Object> customers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        try {
            return ok(service.getCustomers(search, active, page, pageSize));
        } catch (Exception ex) {
            return fail(ex, "customers");
        }
    }

    @PutMapping("/customers/{id}/active")
    public ResponseEntity<Object> customerStatus(@PathVariable long id, @RequestBody Map<String, Boolean> body) {
        try {
            return changed(service.updateCustomerStatus(id, Boolean.TRUE.equals(body.get("active"))));
        } catch (Exception ex) {
            return fail(ex, "customerStatus");
        }
    }

    @GetMapping("/subscriptions")
    public ResponseEntity<Object> subscriptions(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        try {
            return ok(service.getSubscriptions(search, status, page, pageSize));
        } catch (Exception ex) {
            return fail(ex, "subscriptions");
        }
    }

    @PutMapping("/subscriptions/{id}/status")
    public ResponseEntity<Object> subscriptionStatus(@PathVariable long id, @RequestBody Map<String, String> body) {
        try {
            return changed(service.updateSubscriptionStatus(id, body.get("status")));
        } catch (Exception ex) {
            return fail(ex, "subscriptionStatus");
        }
    }

    @GetMapping("/tariffs")
    public ResponseEntity<Object> tariffs() {
        try {
            return ok(service.getTariffs());
        } catch (Exception ex) {
            return fail(ex, "tariffs");
        }
    }

    @PostMapping("/tariffs")
    public ResponseEntity<Object> createTariff(@RequestBody Map<String, Object> body) {
        try {
            return changed(service.createTariff(body));
        } catch (Exception ex) {
            return fail(ex, "createTariff");
        }
    }

    @PutMapping("/tariffs/{id}")
    public ResponseEntity<Object> updateTariff(@PathVariable long id, @RequestBody Map<String, Object> body) {
        try {
            return changed(service.updateTariff(id, body));
        } catch (Exception ex) {
            return fail(ex, "updateTariff");
        }
    }

    @GetMapping("/delivery/jobs")
    public ResponseEntity<Object> deliveryJobs(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        try {
            return ok(service.getDeliveryJobs(status, page, pageSize));
        } catch (Exception ex) {
            return fail(ex, "deliveryJobs");
        }
    }

    @GetMapping("/delivery/workers")
    public ResponseEntity<Object> deliveryWorkers() {
        try {
            return ok(service.getDeliveryWorkers());
        } catch (Exception ex) {
            return fail(ex, "deliveryWorkers");
        }
    }

    @PutMapping("/delivery/workers/{id}/status")
    public ResponseEntity<Object> deliveryWorkerStatus(@PathVariable long id, @RequestBody Map<String, String> body) {
        try {
            return changed(service.updateDeliveryWorkerStatus(id, body.get("status")));
        } catch (Exception ex) {
            return fail(ex, "deliveryWorkerStatus");
        }
    }

    @DeleteMapping("/delivery/workers/{id}")
    public ResponseEntity<Object> deleteDeliveryWorker(@PathVariable long id) {
        try {
            return changed(service.deleteDeliveryWorker(id));
        } catch (IllegalStateException ex) {
            log.warn("AdminManagementController => deleteDeliveryWorker: {}", ex.getMessage());
            com.ninimum.api.common.VersionResponseResult result = setResult(Result.SERVER_ERROR);
            if (ex.getMessage() != null && !ex.getMessage().isBlank()) result.setResultMsg(ex.getMessage());
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception ex) {
            return fail(ex, "deleteDeliveryWorker");
        }
    }

    @PutMapping("/delivery/jobs/{id}")
    public ResponseEntity<Object> updateDeliveryJob(@PathVariable long id, @RequestBody Map<String, Object> body) {
        try {
            return changed(service.updateDeliveryJob(id, body));
        } catch (Exception ex) {
            return fail(ex, "updateDeliveryJob");
        }
    }

    @GetMapping("/reviews")
    public ResponseEntity<Object> reviews(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        try {
            return ok(service.getReviews(search, active, page, pageSize));
        } catch (Exception ex) {
            return fail(ex, "reviews");
        }
    }

    @PutMapping("/reviews/{id}/active")
    public ResponseEntity<Object> reviewActive(@PathVariable long id, @RequestBody Map<String, Boolean> body) {
        try {
            return changed(service.updateReviewActive(id, Boolean.TRUE.equals(body.get("active"))));
        } catch (Exception ex) {
            return fail(ex, "reviewActive");
        }
    }

    @GetMapping("/questions")
    public ResponseEntity<Object> questions(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String kind,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        try {
            return ok(service.getFilteredQuestions(search,status,kind,page,pageSize));
        } catch (Exception ex) {
            return fail(ex, "questions");
        }
    }

    @PutMapping("/questions/{id}/answer")
    public ResponseEntity<Object> answerQuestion(@PathVariable long id, @RequestBody Map<String, String> body) {
        try {
            return changed(service.answerQuestion(id, body.get("answer")));
        } catch (Exception ex) {
            return fail(ex, "answerQuestion");
        }
    }

    @PutMapping("/questions/{id}/active")
    public ResponseEntity<Object> questionActive(@PathVariable long id, @RequestBody Map<String, Boolean> body) {
        try {
            return changed(service.updateQuestionActive(id, Boolean.TRUE.equals(body.get("active"))));
        } catch (Exception ex) {
            return fail(ex, "questionActive");
        }
    }

    @GetMapping("/promotions")
    public ResponseEntity<Object> promotions() {
        try {
            return ok(service.getPromotions());
        } catch (Exception ex) {
            return fail(ex, "promotions");
        }
    }

    @PostMapping("/promotions")
    public ResponseEntity<Object> createPromotion(@RequestBody Map<String, Object> body) {
        try {
            return changed(service.createPromotion(body));
        } catch (Exception ex) {
            return fail(ex, "createPromotion");
        }
    }

    @PutMapping("/promotions/{id}")
    public ResponseEntity<Object> updatePromotion(@PathVariable long id, @RequestBody Map<String, Object> body) {
        try {
            return changed(service.updatePromotion(id, body));
        } catch (Exception ex) {
            return fail(ex, "updatePromotion");
        }
    }

    @GetMapping("/banners")
    public ResponseEntity<Object> banners() {
        try {
            return ok(service.getBanners());
        } catch (Exception ex) {
            return fail(ex, "banners");
        }
    }

    @PutMapping("/banners")
    public ResponseEntity<Object> replaceBanners(@RequestBody List<Map<String, Object>> body) {
        try {
            service.replaceBanners(body);
            return ok(null);
        } catch (Exception ex) {
            return fail(ex, "replaceBanners");
        }
    }

    @GetMapping("/coupons")
    public ResponseEntity<Object> coupons() {
        try {
            return ok(service.getCoupons());
        } catch (Exception ex) {
            return fail(ex, "coupons");
        }
    }

    @PostMapping("/coupons")
    public ResponseEntity<Object> createCoupon(@RequestBody Map<String, Object> body) {
        try {
            return changed(service.createCoupon(body));
        } catch (Exception ex) {
            return fail(ex, "createCoupon");
        }
    }

    @PutMapping("/coupons/{id}")
    public ResponseEntity<Object> updateCoupon(@PathVariable long id, @RequestBody Map<String, Object> body) {
        try {
            return changed(service.updateCoupon(id, body));
        } catch (Exception ex) {
            return fail(ex, "updateCoupon");
        }
    }

    @GetMapping("/settings")
    public ResponseEntity<Object> settings() {
        try {
            return ok(service.getSettings());
        } catch (Exception ex) {
            return fail(ex, "settings");
        }
    }

    @PutMapping("/settings")
    public ResponseEntity<Object> updateSettings(@RequestBody Map<String, Object> body) {
        try {
            return changed(service.updateSettings(body));
        } catch (Exception ex) {
            return fail(ex, "updateSettings");
        }
    }

    @GetMapping("/admins")
    public ResponseEntity<Object> admins() {
        try {
            return ok(service.getAdmins());
        } catch (Exception ex) {
            return fail(ex, "admins");
        }
    }

    @PutMapping("/admins/{id}/status")
    public ResponseEntity<Object> adminStatus(@PathVariable long id, @RequestBody Map<String, String> body) {
        try {
            return changed(service.updateAdminStatus(id, body.get("status")));
        } catch (Exception ex) {
            return fail(ex, "adminStatus");
        }
    }
}
