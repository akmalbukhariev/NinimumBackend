package com.ninimum.api.payment.refund;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ninimum.api.dto.payme.PaymeRequest;
import com.ninimum.api.payment.service.IPaymeService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.math.BigDecimal;
import java.util.Map;

@Service
public class RefundMonitor {
    private final JdbcTemplate jdbc;
    private final PaymeReceiptClient client;
    private final IPaymeService payme;
    private final ObjectMapper json;
    private final TransactionTemplate transaction;
    public RefundMonitor(JdbcTemplate jdbc, PaymeReceiptClient client, IPaymeService payme,
                         ObjectMapper json, PlatformTransactionManager manager) {
        this.jdbc=jdbc;this.client=client;this.payme=payme;this.json=json;
        this.transaction=new TransactionTemplate(manager);
    }
    @Scheduled(fixedDelayString="${payme.refund-monitor.interval-ms:300000}",initialDelayString="${payme.refund-monitor.initial-delay-ms:60000}")
    public void poll() {
        if(!client.available()) return;
        // Oldest check first: errors on one receipt cannot starve the remaining orders.
        var payments=jdbc.queryForList("SELECT p.id,p.order_id,p.provider_transaction_id FROM payments p " +
                "JOIN orders o ON o.id=p.order_id LEFT JOIN payment_refund_checks c ON c.payment_id=p.id " +
                "WHERE p.provider='PAYME' AND p.status='PAID' AND o.payment_status='PAID' " +
                "AND o.status IN ('CANCELLED','RETURNING','RETURNED') AND p.provider_transaction_id IS NOT NULL " +
                "ORDER BY COALESCE(c.checked_at,'1970-01-01'),p.id LIMIT 20");
        for(var payment:payments) {
            long id=((Number)payment.get("id")).longValue();
            try {
                JsonNode receipt=client.receipt(String.valueOf(payment.get("provider_transaction_id")));
                validateReceipt(receipt,String.valueOf(payment.get("provider_transaction_id")),
                        ((Number)payment.get("order_id")).longValue());
                int state=receipt.path("state").intValue();
                transaction.executeWithoutResult(tx -> {
                    // Same payment-first lock order as the authenticated Payme callback.
                    var current=jdbc.queryForMap("SELECT * FROM payments WHERE id=? FOR UPDATE",id);
                    if(state==50 && "PAID".equals(current.get("status"))) {
                        validateRefund(receipt,current);
                        PaymeRequest request=new PaymeRequest();request.setId(id);request.setMethod("CancelTransaction");
                        request.setParams(json.valueToTree(Map.of("id",String.valueOf(current.get("provider_transaction_id")))));
                        var result=payme.handleRequest(request);
                        if(result.getError()!=null) throw new IllegalStateException("RECONCILIATION_FAILED");
                        jdbc.update("UPDATE payments SET payme_cancel_time=? WHERE id=?",receipt.path("cancel_time").longValue(),id);
                    }
                    jdbc.update("INSERT INTO payment_refund_checks(payment_id,checked_at,last_success_at,receipt_state,error_code) " +
                            "VALUES (?,UTC_TIMESTAMP(),UTC_TIMESTAMP(),?,NULL) ON DUPLICATE KEY UPDATE " +
                            "checked_at=UTC_TIMESTAMP(),last_success_at=UTC_TIMESTAMP(),receipt_state=VALUES(receipt_state),error_code=NULL",id,state);
                });
            } catch(Exception ex) {
                if(ex instanceof InterruptedException) { Thread.currentThread().interrupt(); return; }
                // No response bodies, payment details or credentials are logged or exposed.
                jdbc.update("INSERT INTO payment_refund_checks(payment_id,checked_at,error_code) VALUES (?,UTC_TIMESTAMP(),'CHECK_FAILED') " +
                        "ON DUPLICATE KEY UPDATE checked_at=UTC_TIMESTAMP(),error_code='CHECK_FAILED'",id);
            }
        }
    }
    static void validateReceipt(JsonNode receipt,String receiptId,long orderId) {
        if(!receiptId.equals(receipt.path("_id").asText()) ||
                !String.valueOf(orderId).equals(receipt.path("account").path("order_id").asText()))
            throw new IllegalStateException("RECEIPT_MISMATCH");
    }
    static void validateRefund(JsonNode receipt,Map<String,Object> payment) {
        if(!receipt.path("amount").isNumber() || payment.get("amount_tiyin")==null ||
                new BigDecimal(payment.get("amount_tiyin").toString()).compareTo(receipt.path("amount").decimalValue())!=0 ||
                !receipt.path("cancel_time").isIntegralNumber() || receipt.path("cancel_time").longValue()<=0 ||
                !receipt.path("pay_time").isIntegralNumber() || receipt.path("pay_time").longValue()<=0)
            throw new IllegalStateException("REFUND_MISMATCH");
    }
}
