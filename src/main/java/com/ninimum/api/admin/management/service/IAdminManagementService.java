package com.ninimum.api.admin.management.service;

import com.ninimum.api.camelcase.CamelCaseMap;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface IAdminManagementService {
    Map<String, Object> getOrders(String search, String status, int page, int pageSize);
    Map<String, Object> getOrder(long id);
    int updateOrderStatus(long id, Map<String, Object> body);

    Map<String, Object> getProducts(String search, Long categoryId, Boolean active, int page, int pageSize);
    Map<String, Object> getProduct(long id);
    int updateProduct(long id, Map<String, Object> body);
    int addProductImages(long id, List<MultipartFile> images) throws Exception;
    int deleteProductImage(long productId, long imageId);

    List<CamelCaseMap> getCategories();
    int createCategory(Map<String, Object> body);
    int updateCategory(long id, Map<String, Object> body);

    Map<String, Object> getCustomers(String search, Boolean active, int page, int pageSize);
    int updateCustomerStatus(long id, boolean active);

    Map<String, Object> getSubscriptions(String search, String status, int page, int pageSize);
    int updateSubscriptionStatus(long id, String status);
    List<CamelCaseMap> getTariffs();
    int createTariff(Map<String, Object> body);
    int updateTariff(long id, Map<String, Object> body);

    Map<String, Object> getDeliveryJobs(String status, int page, int pageSize);
    List<CamelCaseMap> getDeliveryWorkers();
    int updateDeliveryWorkerStatus(long id, String status);
    int updateDeliveryJob(long id, Map<String, Object> body);

    Map<String, Object> getReviews(String search, Boolean active, int page, int pageSize);
    int updateReviewActive(long id, boolean active);
    Map<String, Object> getQuestions(String search, String status, int page, int pageSize);
    int answerQuestion(long id, String answer);
    int updateQuestionActive(long id, boolean active);

    List<CamelCaseMap> getPromotions();
    int createPromotion(Map<String, Object> body);
    int updatePromotion(long id, Map<String, Object> body);
    List<CamelCaseMap> getBanners();
    int replaceBanners(List<Map<String, Object>> body);
    List<CamelCaseMap> getCoupons();
    int createCoupon(Map<String, Object> body);
    int updateCoupon(long id, Map<String, Object> body);

    CamelCaseMap getSettings();
    int updateSettings(Map<String, Object> body);
    List<CamelCaseMap> getAdmins();
    int updateAdminStatus(long id, String status);
}
