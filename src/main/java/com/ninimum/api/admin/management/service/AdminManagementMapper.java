package com.ninimum.api.admin.management.service;

import com.ninimum.api.camelcase.CamelCaseMap;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AdminManagementMapper {
    List<CamelCaseMap> getOrders(@Param("search") String search, @Param("status") String status, @Param("offset") int offset, @Param("pageSize") int pageSize);
    int countOrders(@Param("search") String search, @Param("status") String status);
    CamelCaseMap getOrder(@Param("id") long id);
    List<CamelCaseMap> getOrderItems(@Param("id") long id);
    int updateOrderStatus(@Param("id") long id, @Param("status") String status, @Param("paymentStatus") String paymentStatus);
    int syncDeliveryJobFromOrder(@Param("id") long id, @Param("status") String status);

    List<CamelCaseMap> getProducts(@Param("search") String search, @Param("categoryId") Long categoryId, @Param("active") Boolean active, @Param("offset") int offset, @Param("pageSize") int pageSize);
    int countProducts(@Param("search") String search, @Param("categoryId") Long categoryId, @Param("active") Boolean active);
    CamelCaseMap getProduct(@Param("id") long id);
    List<CamelCaseMap> getProductImages(@Param("id") long id);
    int updateProduct(Map<String, Object> data);
    int getMaxProductImageSortOrder(@Param("productId") long productId);
    int insertProductImage(@Param("productId") long productId, @Param("imageUrl") String imageUrl, @Param("sortOrder") int sortOrder);
    CamelCaseMap getProductImage(@Param("productId") long productId, @Param("imageId") long imageId);
    int deleteProductImage(@Param("productId") long productId, @Param("imageId") long imageId);
    int countProductOrderReferences(@Param("productId") long productId);
    int countProductReviewReferences(@Param("productId") long productId);
    int countProductQuestionReferences(@Param("productId") long productId);
    int deleteProductCartItems(@Param("productId") long productId);
    int deleteProductFavorites(@Param("productId") long productId);
    int deleteProductRecentlyViewed(@Param("productId") long productId);
    int deleteProductBanners(@Param("productId") long productId);
    int deleteProductImages(@Param("productId") long productId);
    int deleteProduct(@Param("productId") long productId);

    List<CamelCaseMap> getCategories();
    int insertCategory(Map<String, Object> data);
    int updateCategory(Map<String, Object> data);

    List<CamelCaseMap> getCustomers(@Param("search") String search, @Param("active") Boolean active, @Param("offset") int offset, @Param("pageSize") int pageSize);
    int countCustomers(@Param("search") String search, @Param("active") Boolean active);
    int updateCustomerStatus(@Param("id") long id, @Param("active") boolean active);

    List<CamelCaseMap> getSubscriptions(@Param("search") String search, @Param("status") String status, @Param("offset") int offset, @Param("pageSize") int pageSize);
    int countSubscriptions(@Param("search") String search, @Param("status") String status);
    int updateSubscriptionStatus(@Param("id") long id, @Param("status") String status);
    List<CamelCaseMap> getTariffs();
    int insertTariff(Map<String, Object> data);
    int updateTariff(Map<String, Object> data);

    int syncPaidOrders();
    List<CamelCaseMap> getDeliveryJobs(@Param("status") String status, @Param("offset") int offset, @Param("pageSize") int pageSize);
    int countDeliveryJobs(@Param("status") String status);
    List<CamelCaseMap> getDeliveryWorkers();
    int updateDeliveryWorkerStatus(@Param("id") long id, @Param("status") String status);
    int updateDeliveryJob(Map<String, Object> data);
    int syncOrderFromDeliveryJob(@Param("id") long id);

    List<CamelCaseMap> getReviews(@Param("search") String search, @Param("active") Boolean active, @Param("offset") int offset, @Param("pageSize") int pageSize);
    int countReviews(@Param("search") String search, @Param("active") Boolean active);
    int updateReviewActive(@Param("id") long id, @Param("active") boolean active);
    List<CamelCaseMap> getQuestions(@Param("search") String search, @Param("status") String status, @Param("offset") int offset, @Param("pageSize") int pageSize);
    int countQuestions(@Param("search") String search, @Param("status") String status);
    int answerQuestion(@Param("id") long id, @Param("answer") String answer);
    int updateQuestionActive(@Param("id") long id, @Param("active") boolean active);

    List<CamelCaseMap> getPromotions();
    int insertPromotion(Map<String, Object> data);
    int updatePromotion(Map<String, Object> data);
    List<CamelCaseMap> getBanners();
    int clearBanners();
    int insertBanner(@Param("productId") long productId, @Param("sortOrder") int sortOrder);
    List<CamelCaseMap> getCoupons();
    int insertCoupon(Map<String, Object> data);
    int updateCoupon(Map<String, Object> data);

    CamelCaseMap getSettings();
    int updateSettings(Map<String, Object> data);
    List<CamelCaseMap> getAdmins();
    int updateAdminStatus(@Param("id") long id, @Param("status") String status);
}
