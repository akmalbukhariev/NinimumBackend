package com.ninimum.api.payment.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.dto.payme.*;
import com.ninimum.api.payment.service.IPaymeService;
import com.ninimum.api.payment.service.PaymeMapper;
import com.ninimum.api.response.payme.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.annotation.PostConstruct;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymeService implements IPaymeService {

    private final ObjectMapper objectMapper;
    private final PaymeMapper paymeMapper;
    private volatile String currentPaymeKey;
    @Value("${payme.merchant-id}")
    private String paymeMerchantId;

    @Value("${payme.checkout-url}")
    private String paymeCheckoutUrl;

    @Value("${payme.return-url}")
    private String paymeReturnUrl;

    @Value("${payme.tariff-account-offset:1000000000000}")
    private long tariffAccountOffset;

    @Value("${payme.key}")
    private String paymeKey;

    @PostConstruct
    public void init() {
        currentPaymeKey = paymeKey;
    }

    @Override
    public boolean isValidAuthorization(String authorization) {
        if (authorization == null || !authorization.startsWith("Basic ")) {
            return false;
        }

        String encoded = authorization.substring("Basic ".length());

        String decoded = new String(
                Base64.getDecoder().decode(encoded),
                StandardCharsets.UTF_8
        );

        String expected = "Paycom:" + currentPaymeKey;

        return expected.equals(decoded);
    }

    @Override
    @Transactional
    public PaymeResponse handleRequest(PaymeRequest request) {

        if (request == null) {
            return PaymeResponse.error(-32600, "Invalid Request", null, null);
        }

        try {
            log.info("PaymeService =>  ", request.getMethod());

            switch (request.getMethod()) {
                case "CheckPerformTransaction":
                    return checkPerformTransaction(request);

                case "CreateTransaction":
                    return createTransaction(request);

                case "PerformTransaction":
                    return performTransaction(request);

                case "CancelTransaction":
                    return cancelTransaction(request);
                case "CheckTransaction":
                    return checkTransaction(request);
                case "GetStatement":
                    return getStatement(request);
                case "ChangePassword":
                    return changePassword(request);
                default:
                    return PaymeResponse.error(-32601, "Method not found", request.getMethod(), request.getId());
            }

        } catch (Exception ex) {
            log.error("PaymeService => handleRequest: ", ex);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return PaymeResponse.error(-32400, "System error", null, request.getId());
        }
    }

    @Override
    public CreatePaymeCheckoutUrlResponse createCheckoutUrl(CreatePaymeCheckoutUrlParam param) throws Exception {

        if (param == null || param.getOrder_id() == null) {
            throw new Exception("order_id is required");
        }

        CamelCaseMap order = getOrder(param.getOrder_id());

        if (order == null) {
            throw new Exception("Order not found");
        }

        String paymentStatus = String.valueOf(order.get("payment_status"));
        String orderStatus = String.valueOf(order.get("status"));

        if ("PAID".equalsIgnoreCase(paymentStatus)) {
            throw new Exception("Order already paid");
        }

        if ("CANCELLED".equalsIgnoreCase(orderStatus)) {
            throw new Exception("Order is cancelled");
        }

        BigDecimal totalPrice = new BigDecimal(String.valueOf(order.get("total_price")));
        long amountTiyin = totalPrice.movePointRight(2).longValueExact();

        String paramsText =
                "m=" + paymeMerchantId +
                        ";ac.order_id=" + param.getOrder_id() +
                        ";a=" + amountTiyin +
                        ";l=uz" +
                        ";c=" + paymeReturnUrl;

        String encoded = Base64.getEncoder()
                .encodeToString(paramsText.getBytes(StandardCharsets.UTF_8));

        String paymentUrl = paymeCheckoutUrl + encoded;

        return new CreatePaymeCheckoutUrlResponse(paymentUrl);
    }

    private PaymeResponse checkPerformTransaction(PaymeRequest request) throws Exception {

        PaymeParams params = objectMapper.treeToValue(request.getParams(), PaymeParams.class);

        Long orderId = getOrderId(params, request.getId());
        Long subscriptionId = getSubscriptionId(params);

        if (orderId != null && subscriptionId != null) {
            return PaymeResponse.error(-32602, "Only one payment account is allowed", "account", request.getId());
        }

        if (subscriptionId != null) {
            CamelCaseMap subscription = getSubscription(subscriptionId);

            PaymeResponse validationError = validateSubscriptionAndAmount(subscription, params, request.getId());
            if (validationError != null) {
                return validationError;
            }

            Map<String, Object> tariffDetail = buildTariffFiscalDetail(subscription);

            log.info(
                    "PAYME TARIFF CHECK => subscriptionId={}, amount={}, fiscalDetail={}",
                    subscriptionId,
                    params.getAmount(),
                    tariffDetail == null ? "not configured" : objectMapper.writeValueAsString(tariffDetail)
            );

            return PaymeResponse.success(
                    tariffDetail == null
                            ? new CheckPerformTransactionResult(true)
                            : new CheckPerformTransactionResult(true, tariffDetail),
                    request.getId()
            );
        }

        if (orderId == null) {
            return PaymeResponse.error(-31050, "Payment account not found", "account", request.getId());
        }

        CamelCaseMap order = getOrder(orderId);

        PaymeResponse validationError = validateOrderAndAmount(order, params, request.getId());
        if (validationError != null) {
            return validationError;
        }

        Map<String, Object> detail = buildFiscalDetail(orderId);

        log.info(
                "PAYME FISCAL DATA => orderId={}, amount={}, detail={}",
                orderId,
                params.getAmount(),
                objectMapper.writeValueAsString(detail)
        );

        return PaymeResponse.success(
                new CheckPerformTransactionResult(true, detail),
                request.getId()
        );
    }

    private Map<String, Object> buildTariffFiscalDetail(CamelCaseMap subscription) {
        Object title = subscription.get("tariff_name");
        Object priceValue = subscription.get("price");
        Object mxikCode = subscription.get("fiscal_mxik_code");
        Object packageCode = subscription.get("fiscal_package_code");
        Object vatPercent = subscription.get("vat_percent");

        if (title == null || priceValue == null || mxikCode == null || packageCode == null || vatPercent == null) {
            return null;
        }

        BigDecimal price = new BigDecimal(String.valueOf(priceValue));

        Map<String, Object> item = new HashMap<>();
        item.put("title", String.valueOf(title));
        item.put("price", price.movePointRight(2).longValueExact());
        item.put("count", 1);
        item.put("code", String.valueOf(mxikCode));
        item.put("package_code", String.valueOf(packageCode));
        item.put("vat_percent", Integer.valueOf(String.valueOf(vatPercent)));

        List<Map<String, Object>> items = new java.util.ArrayList<>();
        items.add(item);

        Map<String, Object> detail = new HashMap<>();
        detail.put("receipt_type", 0);
        detail.put("items", items);
        return detail;
    }

    private Map<String, Object> buildFiscalDetail(Long orderId) throws Exception {

        GetPaymeOrderParam param = new GetPaymeOrderParam();
        param.setOrder_id(orderId);

        List<CamelCaseMap> rows = paymeMapper.getPaymeOrderFiscalItems(param);

        if (rows == null || rows.isEmpty()) {
            throw new Exception("Order items not found for order_id=" + orderId);
        }

        List<Map<String, Object>> items = new java.util.ArrayList<>();

        for (CamelCaseMap row : rows) {

            Object productName = row.get("product_name");
            Object unitPriceValue = row.get("unit_price");
            Object quantity = row.get("quantity");
            Object mxikCode = row.get("fiscal_mxik_code");
            Object packageCode = row.get("fiscal_package_code");
            Object vatPercent = row.get("vat_percent");

            if (productName == null
                    || unitPriceValue == null
                    || quantity == null
                    || mxikCode == null
                    || packageCode == null
                    || vatPercent == null) {

                throw new Exception(
                        "Fiscal information is incomplete for order item: "
                                + row.get("order_item_id")
                );
            }

            BigDecimal unitPrice =
                    new BigDecimal(String.valueOf(unitPriceValue));

            long priceTiyin = unitPrice
                    .movePointRight(2)
                    .longValueExact();

            Map<String, Object> item = new HashMap<>();

            item.put("title", String.valueOf(productName));
            item.put("price", priceTiyin);
            item.put(
                    "count",
                    Integer.valueOf(String.valueOf(quantity))
            );
            item.put("code", String.valueOf(mxikCode));
            item.put(
                    "package_code",
                    String.valueOf(packageCode)
            );
            item.put(
                    "vat_percent",
                    Integer.valueOf(String.valueOf(vatPercent))
            );

            items.add(item);
        }

        Map<String, Object> detail = new HashMap<>();
        detail.put("receipt_type", 0);
        detail.put("items", items);

        return detail;
    }

    @Transactional
    private PaymeResponse createTransaction(PaymeRequest request) throws Exception {

        PaymeParams params = objectMapper.treeToValue(request.getParams(), PaymeParams.class);

        if (params == null || params.getId() == null || params.getId().trim().isEmpty()) {
            return PaymeResponse.error(-32602, "Transaction id is invalid", "id", request.getId());
        }

        Long orderId = getOrderId(params, request.getId());
        Long subscriptionId = getSubscriptionId(params);

        if (orderId != null && subscriptionId != null) {
            return PaymeResponse.error(-32602, "Only one payment account is allowed", "account", request.getId());
        }

        if (orderId == null && subscriptionId == null) {
            return PaymeResponse.error(-31050, "Payment account not found", "account", request.getId());
        }

        GetPaymePaymentParam paymentParam = new GetPaymePaymentParam();
        paymentParam.setTransaction_id(params.getId());

        CamelCaseMap existingPayment = paymeMapper.getPaymePaymentByTransactionId(paymentParam);

        if (existingPayment != null) {
            return PaymeResponse.success(
                    new CreateTransactionResult(
                            Long.valueOf(String.valueOf(existingPayment.get("payme_create_time"))),
                            String.valueOf(existingPayment.get("provider_transaction_id")),
                            getPaymeTransactionState(existingPayment)
                    ),
                    request.getId()
            );
        }

        CamelCaseMap target;
        BigDecimal amount;
        Long userId;

        if (subscriptionId != null) {
            target = getSubscription(subscriptionId);

            PaymeResponse validationError = validateSubscriptionAndAmount(target, params, request.getId());
            if (validationError != null) {
                return validationError;
            }

            GetPaymeSubscriptionParam activePaymentParam = new GetPaymeSubscriptionParam();
            activePaymentParam.setSubscription_id(subscriptionId);

            CamelCaseMap activePayment = paymeMapper.getActivePaymePaymentBySubscriptionId(activePaymentParam);
            if (activePayment != null) {
                return PaymeResponse.error(
                        -31051,
                        "Subscription already has active payment transaction",
                        "account.order_id",
                        request.getId()
                );
            }

            amount = new BigDecimal(String.valueOf(target.get("price")));
            userId = target.get("user_id") == null ? null : Long.valueOf(String.valueOf(target.get("user_id")));
        } else {
            target = getOrder(orderId);

            PaymeResponse validationError = validateOrderAndAmount(target, params, request.getId());
            if (validationError != null) {
                return validationError;
            }

            GetPaymeOrderParam activePaymentParam = new GetPaymeOrderParam();
            activePaymentParam.setOrder_id(orderId);

            CamelCaseMap activePayment = paymeMapper.getActivePaymePaymentByOrderId(activePaymentParam);
            if (activePayment != null) {
                return PaymeResponse.error(
                        -31051,
                        "Order already has active payment transaction",
                        "account.order_id",
                        request.getId()
                );
            }

            amount = new BigDecimal(String.valueOf(target.get("total_price")));
            userId = target.get("user_id") == null ? null : Long.valueOf(String.valueOf(target.get("user_id")));
        }

        Long createTime = System.currentTimeMillis();

        CreatePaymePaymentParam createParam = new CreatePaymePaymentParam();
        createParam.setOrder_id(orderId);
        createParam.setSubscription_id(subscriptionId);
        createParam.setUser_id(userId);
        createParam.setProvider_transaction_id(params.getId());
        createParam.setAmount(amount);
        createParam.setAmount_tiyin(params.getAmount());
        createParam.setPayme_time(params.getTime());
        createParam.setPayme_create_time(createTime);

        paymeMapper.createPaymePayment(createParam);

        return PaymeResponse.success(
                new CreateTransactionResult(createTime, params.getId(), 1),
                request.getId()
        );
    }

    @Transactional
    private PaymeResponse performTransaction(PaymeRequest request) throws Exception {

        PaymeParams params = objectMapper.treeToValue(request.getParams(), PaymeParams.class);

        if (params == null || params.getId() == null || params.getId().trim().isEmpty()) {
            return PaymeResponse.error(-32602, "Transaction id is invalid", "id", request.getId());
        }

        GetPaymePaymentParam paymentParam = new GetPaymePaymentParam();
        paymentParam.setTransaction_id(params.getId());

        CamelCaseMap payment = paymeMapper.getPaymePaymentByTransactionId(paymentParam);

        if (payment == null) {
            return PaymeResponse.error(-31003, "Transaction not found", "id", request.getId());
        }

        String status = String.valueOf(payment.get("status"));

        if ("PAID".equalsIgnoreCase(status)) {
            return PaymeResponse.success(
                    new PerformTransactionResult(
                            String.valueOf(payment.get("provider_transaction_id")),
                            Long.valueOf(String.valueOf(payment.get("payme_perform_time"))),
                            2
                    ),
                    request.getId()
            );
        }

        if (!"CREATED".equalsIgnoreCase(status)) {
            return PaymeResponse.error(-31008, "Transaction cannot be performed", "id", request.getId());
        }

        Long orderId = toLongOrNull(payment.get("order_id"));
        Long subscriptionId = toLongOrNull(payment.get("subscription_id"));

        if (subscriptionId != null && subscriptionId > 0) {
            CamelCaseMap subscription = getSubscription(subscriptionId);

            if (subscription == null || !"PENDING".equalsIgnoreCase(String.valueOf(subscription.get("subscription_status")))) {
                return PaymeResponse.error(
                        -31008,
                        "Tariff subscription is no longer payable",
                        "account.order_id",
                        request.getId()
                );
            }
        }

        Long performTime = System.currentTimeMillis();

        PerformPaymePaymentParam performParam = new PerformPaymePaymentParam();
        performParam.setTransaction_id(params.getId());
        performParam.setPayme_perform_time(performTime);

        int updatedPayment = paymeMapper.performPaymePayment(performParam);
        if (updatedPayment == 0) {
            return PaymeResponse.error(-31008, "Transaction cannot be performed", "id", request.getId());
        }

        if (orderId != null && orderId > 0) {
            UpdateOrderPaymentStatusParam orderStatusParam = new UpdateOrderPaymentStatusParam();
            orderStatusParam.setOrder_id(orderId);
            orderStatusParam.setPayment_status("PAID");
            paymeMapper.updateOrderPaymentStatus(orderStatusParam);
        } else if (subscriptionId != null && subscriptionId > 0) {
            UpdateSubscriptionStatusParam subscriptionStatusParam = new UpdateSubscriptionStatusParam();
            subscriptionStatusParam.setSubscription_id(subscriptionId);
            subscriptionStatusParam.setSubscription_status("ACTIVE");

            int activated = paymeMapper.activateSubscription(subscriptionStatusParam);
            if (activated != 1) {
                throw new Exception("Tariff subscription activation failed. subscriptionId=" + subscriptionId);
            }
        } else {
            log.error("PaymeService => paid transaction has no order_id or subscription_id, transaction={}", params.getId());
        }

        return PaymeResponse.success(
                new PerformTransactionResult(params.getId(), performTime, 2),
                request.getId()
        );
    }

    private PaymeResponse checkTransaction(PaymeRequest request) throws Exception {

        PaymeParams params = objectMapper.treeToValue(request.getParams(), PaymeParams.class);

        if (params == null || params.getId() == null || params.getId().trim().isEmpty()) {
            return PaymeResponse.error(-32602, "Transaction id is invalid", "id", request.getId());
        }

        GetPaymePaymentParam paymentParam = new GetPaymePaymentParam();
        paymentParam.setTransaction_id(params.getId());

        CamelCaseMap payment = paymeMapper.getPaymePaymentByTransactionId(paymentParam);

        if (payment == null) {
            return PaymeResponse.error(-31003, "Transaction not found", "id", request.getId());
        }

        CheckTransactionResult result = new CheckTransactionResult(
                Long.valueOf(String.valueOf(payment.get("payme_create_time"))),
                toLongOrNull(payment.get("payme_perform_time")),
                toLongOrNull(payment.get("payme_cancel_time")),
                String.valueOf(payment.get("provider_transaction_id")),
                getPaymeTransactionState(payment),
                toIntegerOrNull(payment.get("payme_reason"))
        );

        return PaymeResponse.success(result, request.getId());
    }

    @Transactional
    private PaymeResponse cancelTransaction(PaymeRequest request) throws Exception {

        PaymeParams params = objectMapper.treeToValue(request.getParams(), PaymeParams.class);

        if (params == null || params.getId() == null || params.getId().trim().isEmpty()) {
            return PaymeResponse.error(-32602, "Transaction id is invalid", "id", request.getId());
        }

        GetPaymePaymentParam paymentParam = new GetPaymePaymentParam();
        paymentParam.setTransaction_id(params.getId());

        CamelCaseMap payment = paymeMapper.getPaymePaymentByTransactionId(paymentParam);

        if (payment == null) {
            return PaymeResponse.error(-31003, "Transaction not found", "id", request.getId());
        }

        String status = String.valueOf(payment.get("status"));

        if ("CANCELLED".equalsIgnoreCase(status)
                || "FAILED".equalsIgnoreCase(status)
                || "REFUNDED".equalsIgnoreCase(status)) {

            return PaymeResponse.success(
                    new CancelTransactionResult(
                            String.valueOf(payment.get("provider_transaction_id")),
                            toLongOrNull(payment.get("payme_cancel_time")),
                            getCancelStateByPerformTime(payment.get("payme_perform_time"))
                    ),
                    request.getId()
            );
        }

        Long cancelTime = System.currentTimeMillis();
        Integer reason = "PAID".equalsIgnoreCase(status) ? 5 : 3;

        CancelPaymePaymentParam cancelParam = new CancelPaymePaymentParam();
        cancelParam.setTransaction_id(params.getId());
        cancelParam.setPayme_cancel_time(cancelTime);
        cancelParam.setPayme_reason(reason);
        cancelParam.setStatus("CANCELLED");

        int updatedPayment = paymeMapper.cancelPaymePayment(cancelParam);
        if (updatedPayment == 0) {
            return PaymeResponse.error(-31007, "Transaction cannot be cancelled", "id", request.getId());
        }

        Long orderId = toLongOrNull(payment.get("order_id"));
        Long subscriptionId = toLongOrNull(payment.get("subscription_id"));

        if (orderId != null && orderId > 0) {
            UpdateOrderPaymentStatusParam orderStatusParam = new UpdateOrderPaymentStatusParam();
            orderStatusParam.setOrder_id(orderId);
            orderStatusParam.setPayment_status("PAID".equalsIgnoreCase(status) ? "REFUNDED" : "FAILED");
            paymeMapper.updateOrderPaymentStatus(orderStatusParam);
        } else if (subscriptionId != null && subscriptionId > 0) {
            UpdateSubscriptionStatusParam subscriptionStatusParam = new UpdateSubscriptionStatusParam();
            subscriptionStatusParam.setSubscription_id(subscriptionId);
            subscriptionStatusParam.setSubscription_status("CANCELLED");
            paymeMapper.updateSubscriptionStatus(subscriptionStatusParam);
        }

        Integer state = "PAID".equalsIgnoreCase(status) ? -2 : -1;

        return PaymeResponse.success(
                new CancelTransactionResult(params.getId(), cancelTime, state),
                request.getId()
        );
    }

    private Long getOrderId(PaymeParams params, Object requestId) {
        Long paymeAccountId = getPaymeAccountId(params);

        if (paymeAccountId == null || paymeAccountId <= 0) {
            return null;
        }

        // Values at/above the reserved offset represent tariff subscriptions, not orders.
        if (paymeAccountId >= tariffAccountOffset) {
            return null;
        }

        return paymeAccountId;
    }

    private Long getSubscriptionId(PaymeParams params) {
        Long paymeAccountId = getPaymeAccountId(params);

        if (paymeAccountId == null || paymeAccountId < tariffAccountOffset) {
            return null;
        }

        long subscriptionId = paymeAccountId - tariffAccountOffset;
        return subscriptionId > 0 ? subscriptionId : null;
    }

    private Long getPaymeAccountId(PaymeParams params) {
        if (params == null || params.getAccount() == null || !params.getAccount().has("order_id")) {
            return null;
        }

        String value = params.getAccount().get("order_id").asText();
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Long toPaymeAccountId(Long orderId, Long subscriptionId) {
        if (orderId != null && orderId > 0) {
            return orderId;
        }

        if (subscriptionId != null && subscriptionId > 0) {
            return Math.addExact(tariffAccountOffset, subscriptionId);
        }

        return null;
    }

    private CamelCaseMap getSubscription(Long subscriptionId) {
        GetPaymeSubscriptionParam param = new GetPaymeSubscriptionParam();
        param.setSubscription_id(subscriptionId);
        return paymeMapper.getPaymeSubscriptionCheckById(param);
    }

    private PaymeResponse validateSubscriptionAndAmount(CamelCaseMap subscription, PaymeParams params, Object requestId) {
        if (subscription == null) {
            return PaymeResponse.error(-31050, "Subscription not found", "account.order_id", requestId);
        }

        if (params.getAmount() == null || params.getAmount() <= 0) {
            return PaymeResponse.error(-32602, "Amount is invalid", "amount", requestId);
        }

        Object priceObj = subscription.get("price");
        if (priceObj == null) {
            return PaymeResponse.error(-32400, "Tariff amount is invalid", "amount", requestId);
        }

        BigDecimal price = new BigDecimal(String.valueOf(priceObj));
        long expectedAmountTiyin = price.movePointRight(2).longValueExact();

        if (expectedAmountTiyin != params.getAmount()) {
            return PaymeResponse.error(-31001, "Incorrect amount", "amount", requestId);
        }

        String status = String.valueOf(subscription.get("subscription_status"));

        if ("ACTIVE".equalsIgnoreCase(status)) {
            return PaymeResponse.error(-31051, "Subscription already paid", "account.order_id", requestId);
        }

        if (!"PENDING".equalsIgnoreCase(status)) {
            return PaymeResponse.error(-31050, "Subscription is not payable", "account.order_id", requestId);
        }

        return null;
    }

    private CamelCaseMap getOrder(Long orderId) {
        GetPaymeOrderParam orderParam = new GetPaymeOrderParam();
        orderParam.setOrder_id(orderId);
        return paymeMapper.getPaymeOrderCheckById(orderParam);
    }

    private PaymeResponse validateOrderAndAmount(CamelCaseMap order, PaymeParams params, Object requestId) {

        if (order == null) {
            return PaymeResponse.error(-31050, "Order not found", "account.order_id", requestId);
        }

        if (params.getAmount() == null || params.getAmount() <= 0) {
            return PaymeResponse.error(-32602, "Amount is invalid", "amount", requestId);
        }

        Object totalPriceObj = order.get("total_price");

        if (totalPriceObj == null) {
            return PaymeResponse.error(-32400, "Order amount is invalid", "amount", requestId);
        }

        BigDecimal totalPrice = new BigDecimal(String.valueOf(totalPriceObj));

        long expectedAmountTiyin = totalPrice.movePointRight(2).longValueExact();

        if (expectedAmountTiyin != params.getAmount()) {
            return PaymeResponse.error(-31001, "Incorrect amount", "amount", requestId);
        }

        String paymentStatus = String.valueOf(order.get("payment_status"));
        String orderStatus = String.valueOf(order.get("status"));

        if ("PAID".equalsIgnoreCase(paymentStatus)) {
            return PaymeResponse.error(-31051, "Order already paid", "account.order_id", requestId);
        }

        if ("CANCELLED".equalsIgnoreCase(orderStatus)) {
            return PaymeResponse.error(-31050, "Order is cancelled", "account.order_id", requestId);
        }

        return null;
    }

    private Integer getCancelStateByPerformTime(Object performTimeObj) {
        Long performTime = toLongOrNull(performTimeObj);
        return performTime != null && performTime > 0 ? -2 : -1;
    }

    private Integer getPaymeTransactionState(CamelCaseMap payment) {
        String status = String.valueOf(payment.get("status"));

        if ("PAID".equalsIgnoreCase(status)) {
            return 2;
        }

        if ("CANCELLED".equalsIgnoreCase(status)
                || "FAILED".equalsIgnoreCase(status)
                || "REFUNDED".equalsIgnoreCase(status)) {

            Object performTime = payment.get("payme_perform_time");
            if (performTime == null) {
                performTime = payment.get("perform_time");
            }

            return getCancelStateByPerformTime(performTime);
        }

        return 1;
    }

    private Long toLongOrNull(Object value) {
        if (value == null) {
            return 0L;
        }

        String text = String.valueOf(value);

        if (text.trim().isEmpty() || "null".equalsIgnoreCase(text)) {
            return 0L;
        }

        return Long.valueOf(text);
    }

    private Integer toIntegerOrNull(Object value) {
        if (value == null) {
            return null;
        }

        String text = String.valueOf(value);

        if (text.trim().isEmpty() || "null".equalsIgnoreCase(text)) {
            return null;
        }

        return Integer.valueOf(text);
    }

    private PaymeResponse changePassword(PaymeRequest request) throws Exception {
        PaymeParams params = objectMapper.treeToValue(request.getParams(), PaymeParams.class);

        if (params == null || params.getPassword() == null || params.getPassword().trim().isEmpty()) {
            return PaymeResponse.error(-32602, "Password is invalid", "password", request.getId());
        }

        currentPaymeKey = params.getPassword();

        Map<String, Boolean> result = new HashMap<>();
        result.put("success", true);

        return PaymeResponse.success(result, request.getId());
    }

    private PaymeResponse getStatement(PaymeRequest request) throws Exception {
        PaymeParams params = objectMapper.treeToValue(request.getParams(), PaymeParams.class);

        if (params == null || params.getFrom() == null || params.getTo() == null) {
            return PaymeResponse.error(-32602, "Invalid params", null, request.getId());
        }

        GetPaymeStatementParam statementParam = new GetPaymeStatementParam();
        statementParam.setFrom_time(params.getFrom());
        statementParam.setTo_time(params.getTo());

        List<CamelCaseMap> rows = paymeMapper.getPaymeStatement(statementParam);

        List<Map<String, Object>> transactions = new java.util.ArrayList<>();

        for (CamelCaseMap row : rows) {
            Map<String, Object> transaction = new HashMap<>();

            transaction.put("id", row.get("id"));
            transaction.put("time", toLongOrNull(row.get("time")));
            transaction.put("amount", toLongOrNull(row.get("amount")));

            Map<String, Object> account = new HashMap<>();
            Long statementOrderId = toLongOrNull(row.get("order_id"));
            Long statementSubscriptionId = toLongOrNull(row.get("subscription_id"));
            Long statementPaymeAccountId = toPaymeAccountId(statementOrderId, statementSubscriptionId);

            if (statementPaymeAccountId != null) {
                account.put("order_id", String.valueOf(statementPaymeAccountId));
            }

            transaction.put("account", account);

            transaction.put("create_time", toLongOrNull(row.get("create_time")));
            transaction.put("perform_time", toLongOrNull(row.get("perform_time")));
            transaction.put("cancel_time", toLongOrNull(row.get("cancel_time")));
            transaction.put("transaction", row.get("transaction"));
            transaction.put("state", getPaymeTransactionState(row));
            transaction.put("reason", toIntegerOrNull(row.get("reason")));

            transactions.add(transaction);
        }

        return PaymeResponse.success(
                new GetStatementResult(transactions),
                request.getId()
        );
    }
}