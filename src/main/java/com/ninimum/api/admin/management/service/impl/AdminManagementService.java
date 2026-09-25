package com.ninimum.api.admin.management.service.impl;

import com.ninimum.api.admin.management.service.AdminManagementMapper;
import com.ninimum.api.admin.management.service.IAdminManagementService;
import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.file.service.impl.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminManagementService implements IAdminManagementService {
    private final AdminManagementMapper mapper;
    private final FileService fileService;

    private int page(int page) {
        return Math.max(page, 1);
    }

    private int size(int size) {
        return Math.min(Math.max(size, 1), 100);
    }

    private String clean(String value) {
        if (value == null) return null;
        String result = value.trim();
        return result.isEmpty() ? null : result;
    }

    private Map<String, Object> pageResult(List<CamelCaseMap> items, int total, int page, int pageSize) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", total);
        result.put("page", page);
        result.put("page_size", pageSize);
        return result;
    }

    @Override
    public Map<String, Object> getOrders(String search, String status, int page, int pageSize) {
        page = page(page);
        pageSize = size(pageSize);
        int offset = (page - 1) * pageSize;
        String cleanedSearch = clean(search);
        String cleanedStatus = clean(status);
        return pageResult(
                mapper.getOrders(cleanedSearch, cleanedStatus, offset, pageSize),
                mapper.countOrders(cleanedSearch, cleanedStatus),
                page,
                pageSize
        );
    }

    @Override
    public Map<String, Object> getOrder(long id) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("order", mapper.getOrder(id));
        result.put("items", mapper.getOrderItems(id));
        return result;
    }

    @Override
    @Transactional
    public int updateOrderStatus(long id, Map<String, Object> body) {
        String status = clean((String) body.get("status"));
        String paymentStatus = clean((String) body.get("payment_status"));
        int result = mapper.updateOrderStatus(id, status, paymentStatus);
        if (result > 0 && status != null) {
            mapper.syncDeliveryJobFromOrder(id, status);
        }
        return result;
    }

    @Override
    public Map<String, Object> getProducts(String search, Long categoryId, Boolean active, int page, int pageSize) {
        page = page(page);
        pageSize = size(pageSize);
        int offset = (page - 1) * pageSize;
        String cleanedSearch = clean(search);
        return pageResult(
                mapper.getProducts(cleanedSearch, categoryId, active, offset, pageSize),
                mapper.countProducts(cleanedSearch, categoryId, active),
                page,
                pageSize
        );
    }

    @Override
    public Map<String, Object> getProduct(long id) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("product", mapper.getProduct(id));
        result.put("images", mapper.getProductImages(id));
        return result;
    }

    @Override
    public int updateProduct(long id, Map<String, Object> body) {
        body.put("id", id);
        return mapper.updateProduct(body);
    }

    @Override
    @Transactional
    public int addProductImages(long id, List<MultipartFile> images) throws Exception {
        if (images == null || images.isEmpty()) return 0;
        int sortOrder = mapper.getMaxProductImageSortOrder(id);
        int changed = 0;
        for (MultipartFile image : images) {
            if (image == null || image.isEmpty()) continue;
            String path = fileService.saveProductImage(image);
            sortOrder++;
            changed += mapper.insertProductImage(id, path, sortOrder);
        }
        return changed;
    }

    @Override
    @Transactional
    public int deleteProductImage(long productId, long imageId) {
        CamelCaseMap image = mapper.getProductImage(productId, imageId);
        if (image == null) return 0;
        Object value = image.get("image_url");
        if (value == null) value = image.get("imageUrl");
        int changed = mapper.deleteProductImage(productId, imageId);
        if (changed > 0 && value != null) {
            fileService.deleteProductImage(String.valueOf(value));
        }
        return changed;
    }

    @Override
    public List<CamelCaseMap> getCategories() {
        return mapper.getCategories();
    }

    @Override
    public int createCategory(Map<String, Object> body) {
        return mapper.insertCategory(body);
    }

    @Override
    public int updateCategory(long id, Map<String, Object> body) {
        body.put("id", id);
        return mapper.updateCategory(body);
    }

    @Override
    public Map<String, Object> getCustomers(String search, Boolean active, int page, int pageSize) {
        page = page(page);
        pageSize = size(pageSize);
        int offset = (page - 1) * pageSize;
        String cleanedSearch = clean(search);
        return pageResult(
                mapper.getCustomers(cleanedSearch, active, offset, pageSize),
                mapper.countCustomers(cleanedSearch, active),
                page,
                pageSize
        );
    }

    @Override
    public int updateCustomerStatus(long id, boolean active) {
        return mapper.updateCustomerStatus(id, active);
    }

    @Override
    public Map<String, Object> getSubscriptions(String search, String status, int page, int pageSize) {
        page = page(page);
        pageSize = size(pageSize);
        int offset = (page - 1) * pageSize;
        String cleanedSearch = clean(search);
        String cleanedStatus = clean(status);
        return pageResult(
                mapper.getSubscriptions(cleanedSearch, cleanedStatus, offset, pageSize),
                mapper.countSubscriptions(cleanedSearch, cleanedStatus),
                page,
                pageSize
        );
    }

    @Override
    public int updateSubscriptionStatus(long id, String status) {
        return mapper.updateSubscriptionStatus(id, clean(status));
    }

    @Override
    public List<CamelCaseMap> getTariffs() {
        return mapper.getTariffs();
    }

    @Override
    public int createTariff(Map<String, Object> body) {
        return mapper.insertTariff(body);
    }

    @Override
    public int updateTariff(long id, Map<String, Object> body) {
        body.put("id", id);
        return mapper.updateTariff(body);
    }

    @Override
    @Transactional
    public Map<String, Object> getDeliveryJobs(String status, int page, int pageSize) {
        mapper.syncPaidOrders();
        page = page(page);
        pageSize = size(pageSize);
        int offset = (page - 1) * pageSize;
        String cleanedStatus = clean(status);
        return pageResult(
                mapper.getDeliveryJobs(cleanedStatus, offset, pageSize),
                mapper.countDeliveryJobs(cleanedStatus),
                page,
                pageSize
        );
    }

    @Override
    public List<CamelCaseMap> getDeliveryWorkers() {
        return mapper.getDeliveryWorkers();
    }

    @Override
    public int updateDeliveryWorkerStatus(long id, String status) {
        return mapper.updateDeliveryWorkerStatus(id, clean(status));
    }

    @Override
    @Transactional
    public int updateDeliveryJob(long id, Map<String, Object> body) {
        body.put("id", id);
        int result = mapper.updateDeliveryJob(body);
        if (result > 0) mapper.syncOrderFromDeliveryJob(id);
        return result;
    }

    @Override
    public Map<String, Object> getReviews(String search, Boolean active, int page, int pageSize) {
        page = page(page);
        pageSize = size(pageSize);
        int offset = (page - 1) * pageSize;
        String cleanedSearch = clean(search);
        return pageResult(
                mapper.getReviews(cleanedSearch, active, offset, pageSize),
                mapper.countReviews(cleanedSearch, active),
                page,
                pageSize
        );
    }

    @Override
    public int updateReviewActive(long id, boolean active) {
        return mapper.updateReviewActive(id, active);
    }

    @Override
    public Map<String, Object> getQuestions(String search, String status, int page, int pageSize) {
        page = page(page);
        pageSize = size(pageSize);
        int offset = (page - 1) * pageSize;
        String cleanedSearch = clean(search);
        String cleanedStatus = clean(status);
        return pageResult(
                mapper.getQuestions(cleanedSearch, cleanedStatus, offset, pageSize),
                mapper.countQuestions(cleanedSearch, cleanedStatus),
                page,
                pageSize
        );
    }

    @Override
    public int answerQuestion(long id, String answer) {
        return mapper.answerQuestion(id, clean(answer));
    }

    @Override
    public int updateQuestionActive(long id, boolean active) {
        return mapper.updateQuestionActive(id, active);
    }

    @Override
    public List<CamelCaseMap> getPromotions() {
        return mapper.getPromotions();
    }

    @Override
    public int createPromotion(Map<String, Object> body) {
        return mapper.insertPromotion(body);
    }

    @Override
    public int updatePromotion(long id, Map<String, Object> body) {
        body.put("id", id);
        return mapper.updatePromotion(body);
    }

    @Override
    public List<CamelCaseMap> getBanners() {
        return mapper.getBanners();
    }

    @Override
    @Transactional
    public int replaceBanners(List<Map<String, Object>> body) {
        mapper.clearBanners();
        int changed = 0;
        if (body != null) {
            for (Map<String, Object> item : body) {
                Number productId = (Number) item.get("product_id");
                Number sortOrder = (Number) item.get("sort_order");
                if (productId != null) {
                    changed += mapper.insertBanner(productId.longValue(), sortOrder == null ? changed + 1 : sortOrder.intValue());
                }
            }
        }
        return changed;
    }

    @Override
    public List<CamelCaseMap> getCoupons() {
        return mapper.getCoupons();
    }

    @Override
    public int createCoupon(Map<String, Object> body) {
        return mapper.insertCoupon(body);
    }

    @Override
    public int updateCoupon(long id, Map<String, Object> body) {
        body.put("id", id);
        return mapper.updateCoupon(body);
    }

    @Override
    public CamelCaseMap getSettings() {
        return mapper.getSettings();
    }

    @Override
    public int updateSettings(Map<String, Object> body) {
        return mapper.updateSettings(body);
    }

    @Override
    public List<CamelCaseMap> getAdmins() {
        return mapper.getAdmins();
    }

    @Override
    public int updateAdminStatus(long id, String status) {
        return mapper.updateAdminStatus(id, clean(status));
    }
}
