package com.ninimum.api.admin.management.service.impl;

import com.ninimum.api.admin.management.service.AdminManagementMapper;
import com.ninimum.api.admin.management.service.IAdminManagementService;
import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.file.service.impl.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminManagementService implements IAdminManagementService {
    private final AdminManagementMapper mapper;
    @org.springframework.beans.factory.annotation.Autowired
    private com.ninimum.api.payment.refund.PaymeReceiptClient refundClient;
    private final FileService fileService;
    private final com.ninimum.api.warehouse.WarehouseAppService warehouseApp;
    private final com.ninimum.api.warehouse.WarehouseService warehouse;
    private final com.ninimum.api.deliveryapp.service.ReturnPhotoService returnPhotos;
    private final com.ninimum.api.deliveryapp.service.PartialReturnService partialReturns;
    @org.springframework.beans.factory.annotation.Autowired private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

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
        return getFilteredOrders(search,status,null,null,null,page,pageSize);
    }

    @Override
    public Map<String,Object> getFilteredOrders(String search,String status,String paymentStatus,String from,String to,int page,int pageSize) {
        var filters=com.ninimum.api.admin.management.service.OrderListFilters.parse(paymentStatus,from,to);
        page = page(page);
        pageSize = size(pageSize);
        int offset = (page - 1) * pageSize;
        String cleanedSearch = clean(search);
        String cleanedStatus = clean(status);
        return pageResult(
                mapper.getOrders(cleanedSearch, cleanedStatus, filters.paymentStatus(), filters.fromUtc(), filters.toUtc(), offset, pageSize),
                mapper.countOrders(cleanedSearch, cleanedStatus, filters.paymentStatus(), filters.fromUtc(), filters.toUtc()),
                page,
                pageSize
        );
    }

    @Override
    public Map<String, Object> getOrder(long id) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("order", mapper.getOrder(id));
        result.put("refund_monitor_enabled", refundClient != null && refundClient.available());
        result.put("items", mapper.getOrderItems(id));
        result.put("payments", mapper.getOrderPayments(id));
        result.put("status_history", mapper.getOrderStatusHistory(id));
        result.put("step_history", mapper.getOrderStepHistory(id));
        if(result.get("order")!=null) result.put("partial_return",partialReturns.details(id));
        if (result.get("order") != null) result.put("return_photo", returnPhotos.forOrder(id));
        return result;
    }

    @Override
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public int updateOrderStatus(long id, Map<String, Object> body) {
        String status = clean((String) body.get("status"));
        String paymentStatus = clean((String) body.get("payment_status"));
        if ("CANCELLED".equals(status)) {
            String reason = clean((String) body.get("cancel_reason"));
            // Q-9: cancellation reason (switchable for staff and owner).
            if ((reason == null || reason.isBlank()) && com.ninimum.api.admin.access.StrictRules.on("Q-9")) throw new IllegalArgumentException("Cancellation reason is required");
            if (reason == null) reason = "";
            int changed = mapper.cancelOrder(id, reason);
            if (changed != 1) throw new IllegalArgumentException("This order cannot be cancelled");
            mapper.syncDeliveryJobFromOrder(id, "CANCELLED");
            return changed;
        }
        // Operational progress belongs to the warehouse/courier actions. This editor cannot
        // invent a preparation, dispatch, delivery, or payment event.
        if (status == null) throw new IllegalArgumentException("Order status is required");
        var current = mapper.lockOrderState(id);
        if (current == null) throw new IllegalArgumentException("Order not found");
        if (status.equals(current.get("status"))) return 1;
        throw new IllegalArgumentException("Use the warehouse and courier apps to advance the order. Only cancellation is allowed here.");
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

    @Override public java.util.List<CamelCaseMap> getProductPriceHistory(long id){return mapper.getPriceHistory(id);}
    @Override
    @Transactional
    public int updateProduct(long id, Map<String, Object> body) {
        body=new LinkedHashMap<>(body);
        com.ninimum.api.product.validation.ProductMetadata.normalize(body);
        warehouse.requireDocumentStock(body);
        var before=mapper.lockProductPrices(id);
        if(before==null)return 0;
        var changes=new java.util.ArrayList<Map<String,Object>>();
        String reason=Objects.toString(body.get("price_change_reason"),"").trim();
        if(reason.length()>500)throw new IllegalArgumentException("Price reason is too long");
        for(String field:List.of("price","subscription_price","cost_price"))if(body.containsKey(field)) {
            java.math.BigDecimal oldAmount=before.get(field)==null?null:new java.math.BigDecimal(before.get(field).toString());
            java.math.BigDecimal newAmount=body.get(field)==null?null:new java.math.BigDecimal(body.get(field).toString()).setScale(2,java.math.RoundingMode.UNNECESSARY);
            if(!field.equals("cost_price")&&(newAmount==null?field.equals("price"):newAmount.signum()<=0||newAmount.compareTo(new java.math.BigDecimal("9999999999.99"))>0))throw new IllegalArgumentException("Invalid product price");
            body.put(field,newAmount);
            boolean same=oldAmount==null?newAmount==null:newAmount!=null&&oldAmount.compareTo(newAmount)==0;
            if(!same){var change=new LinkedHashMap<String,Object>();change.put("product_id",id);change.put("field_name",field);change.put("old_amount",oldAmount);change.put("new_amount",newAmount);changes.add(change);}
        }
        var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        boolean staff=auth!=null&&auth.getAuthorities().stream().noneMatch(a->"SUPER_ADMIN".equals(a.getAuthority()));
        // M14: a signed-in staff member needs the price permission; system calls (no sign-in) behave as before.
        if(!changes.isEmpty()&&auth!=null&&!com.ninimum.api.admin.access.StaffAccess.can(com.ninimum.api.admin.access.StaffAccess.PRICES))throw new com.ninimum.api.warehouse.WarehouseException("ADMIN_PERMISSION_DENIED");
        // Q-10: reason for a price change (staff on, owner off by default).
        if(!changes.isEmpty()&&auth!=null&&com.ninimum.api.admin.access.StrictRules.on("Q-10")&&reason.isEmpty())throw new com.ninimum.api.warehouse.WarehouseException("PRICE_REASON_REQUIRED");
        body.put("id", id);int changed=mapper.updateProduct(body);
        if(changed>0)for(var change:changes){change.put("reason",reason);change.put("changed_by",com.ninimum.api.warehouse.WarehouseService.actor());mapper.insertPriceHistory(change);}
        return changed;
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
    public int addProductImageData(long id, Map<String, Object> body) throws Exception {
        if (body == null) throw new IllegalArgumentException("Image data is required.");
        String fileName = body.get("file_name") == null ? "image" : String.valueOf(body.get("file_name"));
        String contentType = body.get("content_type") == null ? "" : String.valueOf(body.get("content_type"));
        String base64 = body.get("base64") == null ? null : String.valueOf(body.get("base64"));
        if (base64 == null || base64.isBlank()) throw new IllegalArgumentException("Image data is required.");
        if (!contentType.isBlank() && !contentType.toLowerCase().startsWith("image/")) {
            throw new IllegalArgumentException("Only image files can be uploaded.");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid image data.");
        }
        String path = fileService.saveProductImage(bytes, fileName);
        int sortOrder = mapper.getMaxProductImageSortOrder(id) + 1;
        return mapper.insertProductImage(id, path, sortOrder);
    }

    @Override
    @Transactional
    public int addProductImageBytes(long id, byte[] bytes, String fileName, String contentType) throws Exception {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Product image is empty.");
        }
        if (contentType != null && !contentType.isBlank()
                && !contentType.toLowerCase().startsWith("image/")
                && !"application/octet-stream".equalsIgnoreCase(contentType)) {
            throw new IllegalArgumentException("Only image files can be uploaded.");
        }

        String normalizedName = (fileName == null || fileName.isBlank()) ? "product-image" : fileName;
        String path = fileService.saveProductImage(bytes, normalizedName);
        int sortOrder = mapper.getMaxProductImageSortOrder(id) + 1;
        return mapper.insertProductImage(id, path, sortOrder);
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
    @Transactional
    public int deleteProduct(long id) {
        // Preserve catalog identifiers used by warehouse documents and stock history.
        if (warehouse.isEnabled()) return mapper.updateProduct(new java.util.HashMap<>(java.util.Map.of("id",id,"is_active",false)));
        CamelCaseMap product = mapper.getProduct(id);
        if (product == null || product.isEmpty()) {
            return 0;
        }

        List<CamelCaseMap> images = mapper.getProductImages(id);

        // Catalog/customer convenience relations must not make product deletion fragile.
        // Some deployed databases may not contain every optional table, so clean each
        // relation independently and let the final DELETE decide whether real history
        // still references this product.
        deleteProductRelationQuietly(() -> mapper.deleteProductBanners(id), "banners", id);
        deleteProductRelationQuietly(() -> mapper.deleteProductCartItems(id), "cart_items", id);
        deleteProductRelationQuietly(() -> mapper.deleteProductFavorites(id), "favorites", id);
        deleteProductRelationQuietly(() -> mapper.deleteProductRecentlyViewed(id), "recently_viewed_products", id);

        // product_images is part of the product itself. Keep this inside the same
        // transaction so it is restored automatically if the product DELETE is blocked.
        mapper.deleteProductImages(id);

        final int changed;
        try {
            changed = mapper.deleteProduct(id);
        } catch (RuntimeException ex) {
            // Orders, reviews, questions, or any other FK/reference must protect
            // historical data. Do not expose a generic SQL/server error to Admin.
            throw new IllegalStateException("PRODUCT_IS_IN_USE", ex);
        }

        if (changed > 0 && images != null) {
            for (CamelCaseMap image : images) {
                Object imageUrl = image.get("imageUrl");
                if (imageUrl == null) imageUrl = image.get("image_url");
                if (imageUrl != null) fileService.deleteProductImage(String.valueOf(imageUrl));
            }
        }
        return changed;
    }

    private void deleteProductRelationQuietly(Runnable action, String relation, long productId) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            log.warn("AdminManagementService => deleteProduct: could not clean {} for product {}: {}",
                    relation, productId, ex.getMessage());
        }
    }

    @Override
    public List<CamelCaseMap> getCategories() {
        return mapper.getCategories();
    }

    @Override
    @Transactional
    public int createCategory(Map<String, Object> body) {
        var data = new LinkedHashMap<>(body);
        validateCategory(null, data);
        return mapper.insertCategory(data);
    }

    @Override
    @Transactional
    public int updateCategory(long id, Map<String, Object> body) {
        var data = new LinkedHashMap<>(body);
        data.put("id", id);
        validateCategory(id, data);
        return mapper.updateCategory(data);
    }

    @Override
    public String uploadCategoryImage(Map<String,Object> body) throws Exception {
        String encoded=Objects.toString(body.get("base64"), "");
        if(encoded.isBlank() || encoded.length()>2800000) throw new IllegalArgumentException("Image must be at most 2 MB");
        byte[] bytes=Base64.getDecoder().decode(encoded);
        if(bytes.length>2*1024*1024) throw new IllegalArgumentException("Image must be at most 2 MB");
        try(var input=javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(bytes))) {
            var readers=javax.imageio.ImageIO.getImageReaders(input);
            if(!readers.hasNext()) throw new IllegalArgumentException("Unsupported image");
            var reader=readers.next();
            try {
                reader.setInput(input);
                if((long)reader.getWidth(0)*reader.getHeight(0)>16000000) throw new IllegalArgumentException("Image dimensions too large");
                var image=reader.read(0);
                var output=new java.io.ByteArrayOutputStream();
                try { if(!javax.imageio.ImageIO.write(image,"png",output)) throw new IllegalArgumentException("Unsupported image"); }
                finally { image.flush(); }
                return fileService.saveProductImage(output.toByteArray(),"category.png");
            } finally { reader.dispose(); }
        }
    }

    private void validateCategory(Long id, Map<String, Object> data) {
        for(String key:List.of("name_uz","name_ru")) if(data.containsKey(key)) {
            String value=clean(data.get(key)==null?null:data.get(key).toString());
            if(value!=null && value.length()>255) throw new IllegalArgumentException("Category name too long");
            data.put(key,value);
        }
        if(data.containsKey("image_url")) {
            String image=Objects.toString(data.get("image_url"),"").trim();
            if(!image.isEmpty() && !image.matches("products/[A-Za-z0-9._-]+")) {
                var uri=java.net.URI.create(image);
                if(!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) || uri.getHost()==null || uri.getUserInfo()!=null)
                    throw new IllegalArgumentException("Invalid category image URL");
            }
            if(image.length()>2000) throw new IllegalArgumentException("Image URL too long");
            data.put("image_url",image);
        }

        if (id == null || data.containsKey("name")) {
            String name = clean(data.get("name") == null ? null : data.get("name").toString());
            if (name == null) throw new IllegalArgumentException("Category name is required");
            if(name.length()>255) throw new IllegalArgumentException("Category name too long");
            data.put("name", name);
        }
        var parents = new java.util.HashMap<Long, Long>();
        for (var row : mapper.lockCategories())
            parents.put(((Number) row.get("id")).longValue(),
                    row.get("parent_id") == null ? null : ((Number) row.get("parent_id")).longValue());
        if (id != null && !parents.containsKey(id))
            throw new IllegalArgumentException("Category does not exist");
        if (!data.containsKey("parent_id") || data.get("parent_id") == null) return;
        Long parent;
        try { parent = Long.valueOf(data.get("parent_id").toString()); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException("Invalid parent category"); }
        var visited = new java.util.HashSet<Long>();
        for (Long current = parent; current != null; current = parents.get(current)) {
            if (current.equals(id) || !visited.add(current))
                throw new IllegalArgumentException("Category hierarchy cannot contain a cycle");
            if (!parents.containsKey(current))
                throw new IllegalArgumentException("Parent category does not exist");
        }
        data.put("parent_id", parent);
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
    public int resetDeliveryWorkerPassword(long id, String password) {
        return mapper.updateDeliveryWorkerPassword(id, passwordEncoder.encode(com.ninimum.api.security.login.StaffPasswordPolicy.require(password)));
    }

    @Override
    @Transactional
    public int deleteDeliveryWorker(long id) {
        if (mapper.countDeliveryJobsForWorker(id) > 0 || mapper.countDeliveryTrackingForWorker(id) > 0) {
            throw new IllegalStateException("COURIER_IS_IN_USE");
        }
        try {
            return mapper.deleteDeliveryWorker(id);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("COURIER_IS_IN_USE", ex);
        }
    }

    @Override
    @Transactional(rollbackFor=Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public int updateDeliveryJob(long id, Map<String, Object> body) {
        if ("FAILED".equals(body.get("status"))) {
            String reason = java.util.Objects.toString(body.get("note"), "").trim();
            if (reason.isEmpty() || reason.length() > 500) throw new IllegalArgumentException("RETURN_REASON_REQUIRED");
            body.put("note", reason);
        }
        if (body.get("delivery_worker_id")!=null || java.util.Set.of("ACCEPTED","ON_THE_WAY","DELIVERED").contains(java.util.Objects.toString(body.get("status"),"")))
            warehouseApp.requireReadyJob(id);
        body.put("id", id);
        int result = mapper.updateDeliveryJob(body);
        if(result>0&&"ON_THE_WAY".equals(body.get("status")))warehouseApp.issueInventoryForJob(id);
        if (result == 0 && "FAILED".equals(body.get("status")))
            throw new IllegalArgumentException("Delivery changed; refresh the order");
        if (result > 0) {
            if ("FAILED".equals(body.get("status"))) {
                try {
                    returnPhotos.save(id, com.ninimum.api.audit.OrderAuditActor.current(),
                            (String) body.get("note"), java.util.Objects.toString(body.get("photo_base64"), null));
                } catch (RuntimeException ex) { throw ex; }
                catch (Exception ex) { throw new IllegalStateException("RETURN_PHOTO_INVALID", ex); }
            }
            mapper.syncOrderFromDeliveryJob(id);
            String step = body.get("status") == null ? "ACCEPTED" : body.get("status").toString();
            String message = "Changed by admin " + com.ninimum.api.audit.OrderAuditActor.current()
                    + (body.get("note") == null ? "" : ": " + body.get("note"));
            mapper.addDeliveryTracking(id, step, message.length() > 500 ? message.substring(0, 500) : message);
        }
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
        return getFilteredQuestions(search,status,null,page,pageSize);
    }
    @Override
    public Map<String,Object> getFilteredQuestions(String search,String status,String kind,int page,int pageSize) {
        kind=clean(kind);
        if(kind!=null&&!java.util.Set.of("QUESTION","COMPLAINT").contains(kind))throw new IllegalArgumentException("Invalid question type");
        page = page(page);
        pageSize = size(pageSize);
        int offset = (page - 1) * pageSize;
        String cleanedSearch = clean(search);
        String cleanedStatus = clean(status);
        return pageResult(
                mapper.getQuestions(cleanedSearch, cleanedStatus, kind, offset, pageSize),
                mapper.countQuestions(cleanedSearch, cleanedStatus, kind),
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
        String value = clean(status);
        if (!"ACTIVE".equals(value) && !"INACTIVE".equals(value))
            throw new IllegalArgumentException("Invalid administrator status");
        int changed = mapper.updateAdminStatus(id, value);
        if (changed == 0) throw new IllegalArgumentException("Administrator not found or owner account is protected");
        return changed;
    }
}
