package com.ninimum.api.order.recurring;
import com.ninimum.api.order.service.IOrderService;
import com.ninimum.api.param.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;
import java.util.*;

@Service
@lombok.extern.slf4j.Slf4j
public class RecurringOrders {
 private final JdbcTemplate jdbc;private final IOrderService orders;private final TransactionTemplate tx;private boolean warned;
 public RecurringOrders(JdbcTemplate jdbc,IOrderService orders,PlatformTransactionManager manager){this.jdbc=jdbc;this.orders=orders;tx=new TransactionTemplate(manager);tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);}
 private CreateOrderParam template(long user,long source){
  var rows=jdbc.queryForList("SELECT delivery_address,delivery_latitude,delivery_longitude FROM orders WHERE id=? AND user_id=? AND status='DELIVERED' AND payment_status='PAID'",source,user);
  if(rows.size()!=1)throw new IllegalArgumentException("Choose your own paid delivered order");
  var r=rows.get(0);var p=new CreateOrderParam();p.setUserId(user);p.setDeliveryAddress(Objects.toString(r.get("delivery_address"),""));
  if(p.getDeliveryAddress().isBlank()||r.get("delivery_latitude")==null||r.get("delivery_longitude")==null)throw new IllegalArgumentException("Order has no complete delivery address");
  p.setDeliveryLatitude(((Number)r.get("delivery_latitude")).doubleValue());p.setDeliveryLongitude(((Number)r.get("delivery_longitude")).doubleValue());
  if(p.getDeliveryLatitude()<39.0||p.getDeliveryLatitude()>39.15||p.getDeliveryLongitude()<66.75||p.getDeliveryLongitude()>66.95)throw new IllegalArgumentException("Choose an order with a valid Shahrisabz address");
  var items=jdbc.queryForList("SELECT product_id,quantity FROM order_items WHERE order_id=? ORDER BY id",source);
  if(items.isEmpty()||items.size()>100)throw new IllegalArgumentException("Invalid order items");
  var products=new ArrayList<CreateOrderProductParam>();for(var item:items){var line=new CreateOrderProductParam();line.setProductId(((Number)item.get("product_id")).longValue());line.setQuantity(((Number)item.get("quantity")).intValue());products.add(line);}p.setProducts(products);return p;
 }
 private int interval(long user){
  var rows=jdbc.queryForList("SELECT t.recurring_order_days FROM user_tariffs u JOIN tariff_plans t ON t.id=u.tariff_plan_id WHERE u.user_id=? AND u.status='ACTIVE' AND NOW() BETWEEN u.start_date AND u.end_date ORDER BY u.id DESC LIMIT 1",user);
  if(rows.isEmpty()||rows.get(0).get("recurring_order_days")==null)throw new IllegalArgumentException("Active tariff does not include recurring orders");
  int n=((Number)rows.get(0).get("recurring_order_days")).intValue();if(n<1||n>365)throw new IllegalArgumentException("Invalid recurring interval");return n;
 }
 public Map<String,Object> preview(long user,long source)throws Exception{
  int days=interval(user);var p=template(user,source);var quote=orders.quote(p);
  return Map.of("source_order_id",source,"interval_days",days,"delivery_address",p.getDeliveryAddress(),"total",p.getTotalPrice(),"quote",quote,"automatic_payment",false);
 }
 public void save(long user,long source,long maximum,boolean consent)throws Exception{
  if(!consent||maximum<1||maximum>Integer.MAX_VALUE)throw new IllegalArgumentException("Explicit consent and maximum total are required");
  int days=interval(user);var p=template(user,source);orders.quote(p);if(p.getTotalPrice()>maximum)throw new IllegalArgumentException("Current total exceeds your maximum");
  tx.executeWithoutResult(t->{
   jdbc.update("INSERT INTO recurring_orders(user_id,source_order_id,max_total,interval_days,next_run_at,consented_at,updated_at) VALUES(?,?,?,?,TIMESTAMPADD(DAY,?,UTC_TIMESTAMP()),UTC_TIMESTAMP(),UTC_TIMESTAMP()) ON DUPLICATE KEY UPDATE max_total=VALUES(max_total),interval_days=VALUES(interval_days),status='ACTIVE',next_run_at=VALUES(next_run_at),consented_at=UTC_TIMESTAMP(),updated_at=UTC_TIMESTAMP()",user,source,maximum,days,days);
   long id=jdbc.queryForObject("SELECT id FROM recurring_orders WHERE user_id=? AND source_order_id=?",Long.class,user,source);event(id,"CONSENT",null,"Unpaid drafts only; customer must confirm each payment");
  });
 }
 public List<Map<String,Object>> list(long user){return jdbc.queryForList("SELECT r.id,r.source_order_id,r.max_total,r.interval_days,r.status,r.last_order_id,o.status last_order_status,o.payment_status last_payment_status,o.total_price last_total,DATE_FORMAT(r.next_run_at,'%Y-%m-%dT%H:%i:%sZ') next_run_at,(SELECT e.note FROM recurring_order_events e WHERE e.recurring_id=r.id ORDER BY e.id DESC LIMIT 1) last_note FROM recurring_orders r LEFT JOIN orders o ON o.id=r.last_order_id AND o.user_id=r.user_id WHERE r.user_id=? ORDER BY r.id DESC",user);}
 public void pause(long user,long id){tx.executeWithoutResult(t->{if(jdbc.update("UPDATE recurring_orders SET status='PAUSED',updated_at=UTC_TIMESTAMP() WHERE id=? AND user_id=?",id,user)!=1)throw new IllegalArgumentException("Recurring order not found");event(id,"PAUSED",null,"Customer paused recurrence");});}
 /** Restarts a paused plan; the next draft is due one full interval from now, under the current tariff. */
 public void resume(long user,long id){int days=interval(user);tx.executeWithoutResult(t->{var rows=jdbc.queryForList("SELECT status FROM recurring_orders WHERE id=? AND user_id=? FOR UPDATE",id,user);
  if(rows.isEmpty())throw new IllegalArgumentException("Recurring order not found");
  if(!List.of("PAUSED","NEEDS_REVIEW").contains(String.valueOf(rows.get(0).get("status"))))throw new IllegalArgumentException("Recurring order is not paused");
  jdbc.update("UPDATE recurring_orders SET status='ACTIVE',interval_days=?,next_run_at=TIMESTAMPADD(DAY,?,UTC_TIMESTAMP()),updated_at=UTC_TIMESTAMP() WHERE id=?",days,days,id);event(id,"RESUMED",null,"Customer resumed recurrence");});}
 @Scheduled(fixedDelayString="${orders.recurring.interval-ms:60000}",initialDelayString="${orders.recurring.initial-delay-ms:120000}")
 public void tick(){try{
  for(Long id:jdbc.queryForList("SELECT id FROM recurring_orders WHERE status='ACTIVE' AND next_run_at<=UTC_TIMESTAMP() ORDER BY next_run_at,id LIMIT 50",Long.class))process(id);
  warned=false;
 }catch(org.springframework.dao.DataAccessException ex){if(!warned)log.warn("Recurring orders unavailable; check migration and connectivity.");warned=true;}}
 private void process(long id){
  try{tx.executeWithoutResult(t->{
   var r=jdbc.queryForMap("SELECT *,next_run_at<=UTC_TIMESTAMP() due FROM recurring_orders WHERE id=? FOR UPDATE",id);
   if(!"ACTIVE".equals(r.get("status"))||!truth(r.get("due")))return;
   long user=((Number)r.get("user_id")).longValue();
   if(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id=? AND is_active=1 AND status='ACTIVE'",Integer.class,user)!=1)throw new IllegalArgumentException("Account unavailable");
   int days=interval(user);if(days!=((Number)r.get("interval_days")).intValue())throw new IllegalArgumentException("Tariff interval changed; review and consent again");
   if(r.get("last_order_id")!=null&&jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE id=? AND status='PENDING' AND payment_status='PENDING'",Integer.class,r.get("last_order_id"))>0)throw new IllegalArgumentException("Previous recurring draft still awaits payment");
   var p=template(user,((Number)r.get("source_order_id")).longValue());
   try{orders.quote(p);if(p.getTotalPrice()>((Number)r.get("max_total")).longValue())throw new IllegalArgumentException("Price exceeds your maximum; review required");p.setQuotedTotal(p.getTotalPrice());orders.createOrder(p);}catch(RuntimeException ex){throw ex;}catch(Exception ex){throw new IllegalArgumentException("Cannot create recurring draft; review stock, address and tariff",ex);}
   jdbc.update("UPDATE recurring_orders SET last_order_id=?,next_run_at=TIMESTAMPADD(DAY,interval_days,UTC_TIMESTAMP()),updated_at=UTC_TIMESTAMP() WHERE id=?",p.getOrderId(),id);event(id,"DRAFT_CREATED",p.getOrderId(),"Awaiting customer payment; no automatic charge");
  });}catch(RuntimeException ex){
   if(ex instanceof org.springframework.dao.DataAccessException){log.warn("Recurring order {} temporarily unavailable",id);return;}
   tx.executeWithoutResult(t->{var r=jdbc.queryForMap("SELECT status,next_run_at<=UTC_TIMESTAMP() due FROM recurring_orders WHERE id=? FOR UPDATE",id);if("ACTIVE".equals(r.get("status"))&&truth(r.get("due"))){jdbc.update("UPDATE recurring_orders SET status='NEEDS_REVIEW',updated_at=UTC_TIMESTAMP() WHERE id=?",id);event(id,"NEEDS_REVIEW",null,ex instanceof IllegalArgumentException?Objects.toString(ex.getMessage(),"Review required").substring(0,Math.min(500,Objects.toString(ex.getMessage(),"Review required").length())):"Review required");}});
  }
 }
 private void event(long id,String event,Long order,String note){jdbc.update("INSERT INTO recurring_order_events(recurring_id,event,order_id,note,occurred_at) VALUES(?,?,?,?,UTC_TIMESTAMP())",id,event,order,note);}
 private static boolean truth(Object value){return Boolean.TRUE.equals(value)||(value instanceof Number&&((Number)value).intValue()==1);}
}
