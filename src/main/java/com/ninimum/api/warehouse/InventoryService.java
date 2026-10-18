package com.ninimum.api.warehouse;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import java.math.BigDecimal;

@Service
@lombok.RequiredArgsConstructor
public class InventoryService {
 private final JdbcTemplate jdbc;private final WarehouseService warehouse;
 private final ObjectMapper json=new ObjectMapper();
 private BatchStock stock(){return new BatchStock(jdbc);}
 private void enabled(){if(!warehouse.isInventoryEnabled())throw new WarehouseException("INVENTORY_NOT_ENABLED");}
 private String actor(){return WarehouseService.actor();}
 private boolean owner(){var a=SecurityContextHolder.getContext().getAuthentication();return a!=null&&a.getAuthorities().stream().anyMatch(r->"SUPER_ADMIN".equals(r.getAuthority()));}
 private long positive(Object v){return WarehouseService.positive(v);}
 private long nonnegative(Object v){try{long n=new BigDecimal(String.valueOf(v)).longValueExact();if(n<0||n>Integer.MAX_VALUE)throw new ArithmeticException();return n;}catch(Exception ex){throw new WarehouseException("WAREHOUSE_INVALID_INPUT");}}
 private String reason(Object v){return WarehouseService.text(v,500,true);}
 private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception ex){throw new WarehouseException("WAREHOUSE_INVALID_INPUT");}}
 private Map<String,Object> decode(Object value){try{return json.readValue(String.valueOf(value),new TypeReference<Map<String,Object>>(){});}catch(Exception ex){throw new IllegalStateException("Invalid stored inventory document",ex);}}
 public Map<String,Object> status(){if(!warehouse.isInventoryEnabled())return Map.of("enabled",false);return Map.of("enabled",true,"tracked_products",jdbc.queryForObject("SELECT COUNT(*) FROM inventory_products",Long.class),"legacy_products",jdbc.queryForObject("SELECT COUNT(*) FROM products p WHERE NOT EXISTS(SELECT 1 FROM inventory_products i WHERE i.product_id=p.id)",Long.class));}
 public Map<String,Object> balances(Long product,Long location,int page){
  enabled();int p=Math.max(1,Math.min(page,1000000));var args=new ArrayList<Object>();String where=" WHERE b.physical_quantity>0";
  if(product!=null){where+=" AND b.product_id=?";args.add(product);}if(location!=null){where+=" AND b.location_id=?";args.add(location);}
  String from=" FROM inventory_batches b JOIN products p ON p.id=b.product_id JOIN warehouse_locations l ON l.id=b.location_id LEFT JOIN inventory_bin_locks k ON k.location_id=l.id";
  Long total=jdbc.queryForObject("SELECT COUNT(*)"+from+where,Long.class,args.toArray());args.add((p-1)*50);
  return Map.of("items",jdbc.queryForList("SELECT b.id,b.product_id,p.name product_name,p.sku,p.barcode,b.location_id,l.code location_code,l.type location_type,b.batch_number,b.expiry_date,b.manufacture_date,b.unit_cost,b.physical_quantity,b.reserved_quantity,CASE WHEN "+BatchStock.ELIGIBLE+" AND k.location_id IS NULL THEN b.physical_quantity-b.reserved_quantity ELSE 0 END available_quantity,b.quarantined,k.document_id count_document,b.reference"+from+where+" ORDER BY p.name,l.code,b.expiry_date,b.id LIMIT 50 OFFSET ?",args.toArray()),"total",total,"page",p,"page_size",50);
 }
 public Map<String,Object> movements(Long product,int page){enabled();int p=Math.max(1,Math.min(page,1000000));String where=product==null?" WHERE 1=1":" WHERE m.product_id=?";if(!owner())where+=" AND NOT EXISTS(SELECT 1 FROM inventory_bin_locks k WHERE k.location_id=m.location_id)";Object[] count=product==null?new Object[]{}:new Object[]{product};Object[] list=product==null?new Object[]{(p-1)*50}:new Object[]{product,(p-1)*50};
  return Map.of("items",jdbc.queryForList("SELECT m.*,DATE_FORMAT(m.created_at,'%Y-%m-%dT%H:%i:%sZ') created_at FROM inventory_movements m"+where+" ORDER BY m.id DESC LIMIT 50 OFFSET ?",list),"total",jdbc.queryForObject("SELECT COUNT(*) FROM inventory_movements m"+where,Long.class,count),"page",p,"page_size",50);
 }
 public Map<String,Object> documents(int page){enabled();int p=Math.max(1,Math.min(page,1000000));return Map.of("items",jdbc.queryForList("SELECT id,kind,status,product_id,location_id,reason,created_by,approved_by,cost_total,DATE_FORMAT(created_at,'%Y-%m-%dT%H:%i:%sZ') created_at FROM inventory_documents ORDER BY id DESC LIMIT 50 OFFSET ?",(p-1)*50),"total",jdbc.queryForObject("SELECT COUNT(*) FROM inventory_documents",Long.class),"page",p,"page_size",50);}
 public Map<String,Object> document(long id){enabled();var r=new LinkedHashMap<>(jdbc.queryForMap("SELECT * FROM inventory_documents WHERE id=?",id));var payload=decode(r.remove("payload"));if(!owner()&&Set.of("OPENING","RELEASE").contains(String.valueOf(r.get("kind"))))throw new org.springframework.security.access.AccessDeniedException("Owner only");
  if("COUNT".equals(r.get("kind"))&&("DRAFT".equals(r.get("status"))||(!owner()&&"COUNTED".equals(r.get("status")))))for(Object item:(List<?>)payload.get("lines")){((Map<?,?>)item).remove("expected_quantity");}
  r.put("payload",payload);return r;
 }
 @Transactional public long create(Map<String,Object> body){
  enabled();String kind=WarehouseService.text(body.get("kind"),30,true),why=reason(body.get("reason"));var payload=new LinkedHashMap<String,Object>(body);payload.remove("reason");
  if(!owner()&&Set.of("OPENING","RELEASE").contains(kind))throw new org.springframework.security.access.AccessDeniedException("Owner approval required");
  if("WRITEOFF".equals(kind)){
   String code=WarehouseService.text(body.get("reason_code"),30,true);if(!Set.of("EXPIRED","WAREHOUSE_DAMAGE","DELIVERY_DAMAGE","LOST","SAMPLE","INTERNAL","OTHER").contains(code))throw new WarehouseException("WAREHOUSE_INVALID_INPUT");
   byte[] photo=ReceiptImage.decode(body.get("photo_base64"));if(photo==null)throw new WarehouseException("INVENTORY_PHOTO_REQUIRED");payload.remove("photo_base64");payload.put("photo_data","data:image/png;base64,"+Base64.getEncoder().encodeToString(photo));
  }
  Long product=null,location=null,expected=null;
  if("OPENING".equals(kind)){
   product=positive(body.get("product_id"));stock().lockProduct(product);if(stock().active(product))throw new WarehouseException("INVENTORY_ALREADY_OPENED");
   expected=jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id=?",Long.class,product);openingLines(body);noLegacyWork(product);
  }else if(Set.of("TRANSFER","WRITEOFF","RELEASE").contains(kind)){
   long batch=positive(body.get("batch_id"));var b=jdbc.queryForMap("SELECT product_id,location_id FROM inventory_batches WHERE id=?",batch);product=BatchStock.n(b,"product_id");location=BatchStock.n(b,"location_id");stock().lockProduct(product);stock().requireBin(location);
   if(!"RELEASE".equals(kind))positive(body.get("quantity"));
   if(!"WRITEOFF".equals(kind))stock().requireBin(positive(body.get("destination_id")));
   if("TRANSFER".equals(kind)){scanBin(location,body.get("source_code"));scanProduct(product,Objects.toString(body.get("scanned_barcode"),""),body.get("scan_override_reason"));scanBin(positive(body.get("destination_id")),body.get("destination_code"));}
   if("RELEASE".equals(kind))metadata(body,1);
  }else if("COUNT".equals(kind)){
   location=positive(body.get("location_id"));var products=jdbc.queryForList("SELECT DISTINCT product_id FROM inventory_batches WHERE location_id=? AND physical_quantity>0 ORDER BY product_id",Long.class,location);for(long id:products)stock().lockProduct(id);
   stock().requireBin(location);scanBin(location,body.get("source_code"));var lines=jdbc.queryForList("SELECT b.id batch_id,b.product_id,p.name product_name,b.batch_number,b.expiry_date,b.physical_quantity expected_quantity,b.reserved_quantity,p.barcode FROM inventory_batches b JOIN products p ON p.id=b.product_id WHERE b.location_id=? AND b.physical_quantity>0 ORDER BY b.product_id,b.id FOR UPDATE",location);
   if(lines.stream().anyMatch(b->BatchStock.n(b,"reserved_quantity")>0))throw new WarehouseException("INVENTORY_RESERVED_STOCK");payload.put("lines",lines);
  }else throw new WarehouseException("WAREHOUSE_INVALID_INPUT");
  jdbc.update("INSERT INTO inventory_documents(kind,product_id,location_id,reason,payload,expected_stock,created_by,created_at) VALUES(?,?,?,?,?,?,?,UTC_TIMESTAMP())",kind,product,location,why,encode(payload),expected,actor());long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
  if("COUNT".equals(kind))jdbc.update("INSERT INTO inventory_bin_locks(location_id,document_id,created_at) VALUES(?,?,UTC_TIMESTAMP())",location,id);return id;
 }
 private ReceiptMetadata metadata(Map<?,?> line,long qty){var m=new LinkedHashMap<Object,Object>(line);m.put("quantity",qty);m.put("input_unit","PIECE");return ReceiptMetadata.parse(m,java.time.Clock.systemUTC());}
 private List<Map<String,Object>> openingLines(Map<String,Object> payload){
  Object raw=payload.get("lines");if(!(raw instanceof List<?> list)||list.size()>100)throw new WarehouseException("WAREHOUSE_INVALID_INPUT");var result=new ArrayList<Map<String,Object>>();var seen=new HashSet<String>();
  for(Object item:list){if(!(item instanceof Map<?,?> m))throw new WarehouseException("WAREHOUSE_INVALID_INPUT");var line=new LinkedHashMap<String,Object>();m.forEach((k,v)->line.put(String.valueOf(k),v));long qty=positive(line.get("quantity"));long location=positive(line.get("location_id"));var meta=metadata(line,qty);if(!seen.add(location+"|"+meta.batch()+"|"+meta.manufactured()+"|"+meta.expiry()+"|"+meta.unitPrice()))throw new WarehouseException("INVENTORY_DUPLICATE_BATCH");result.add(line);}return result;
 }
 private void noLegacyWork(long product){
  int active=jdbc.queryForObject("SELECT COUNT(*) FROM order_items i JOIN orders o ON o.id=i.order_id WHERE i.product_id=? AND (o.status IN ('ON_THE_WAY','RETURNING') OR (o.payment_status='PAID' AND o.status IN ('PENDING','CONFIRMED','PREPARING','READY')) OR EXISTS(SELECT 1 FROM delivery_partial_returns r WHERE r.order_id=o.id AND r.status='RETURNING'))",Integer.class,product);
  if(active>0)throw new WarehouseException("INVENTORY_LEGACY_ORDERS_ACTIVE");
 }
 @Transactional public Object approve(long id,Map<String,Object> confirmation){
  enabled();var d=jdbc.queryForMap("SELECT * FROM inventory_documents WHERE id=? FOR UPDATE",id);if("POSTED".equals(d.get("status")))return document(id);if(!Set.of("DRAFT","COUNTED").contains(String.valueOf(d.get("status"))))throw new WarehouseException("WAREHOUSE_INVALID_STATE");
  if(!owner()&&actor().equals(d.get("created_by"))&&!"TRANSFER".equals(d.get("kind")))throw new WarehouseException("WAREHOUSE_SEPARATE_APPROVER_REQUIRED");
  var p=decode(d.get("payload"));String kind=String.valueOf(d.get("kind")),reference="INV-"+id,why=String.valueOf(d.get("reason"));BigDecimal cost=BigDecimal.ZERO;
  if(!owner()&&Set.of("OPENING","WRITEOFF","RELEASE","COUNT").contains(kind))throw new org.springframework.security.access.AccessDeniedException("Owner approval required");
  if("OPENING".equals(kind)){
   long product=BatchStock.n(d,"product_id");stock().lockProduct(product);if(stock().active(product))throw new WarehouseException("INVENTORY_ALREADY_OPENED");noLegacyWork(product);
   long before=jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id=?",Long.class,product);if(before!=BatchStock.n(d,"expected_stock"))throw new WarehouseException("INVENTORY_STOCK_CHANGED");
   long free=0,total=0;for(var line:openingLines(p)){
    long qty=positive(line.get("quantity")),location=positive(line.get("location_id"));stock().requireBin(location);var m=metadata(line,qty);total=Math.addExact(total,qty);if(total>Integer.MAX_VALUE)throw new WarehouseException("WAREHOUSE_INVALID_INPUT");
    long batch=stock().insert(product,location,null,m.batch(),m.manufactured(),m.expiry(),m.unitPrice(),qty,false,reference,actor());if(stock().eligible(batch))free+=qty;cost=cost.add(m.unitPrice().multiply(BigDecimal.valueOf(qty)));
   }
   jdbc.update("INSERT INTO inventory_products(product_id,opened_by,opened_at) VALUES(?,?,UTC_TIMESTAMP())",product,actor());stock().adjustFree(product,free-before);warehouse.recordInventoryAdjustment(product,free-before,"OPENING",reference,why);
  }else if("COUNT".equals(kind)){
   if("COUNTED".equals(d.get("status"))){confirmation=new LinkedHashMap<>();confirmation.put("counts",p.get("counts"));confirmation.put("variance_reason",p.get("variance_reason"));confirmation.put("scan_override_reason",p.get("scan_override_reason"));}
   if(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_bin_locks WHERE location_id=? AND document_id=?",Integer.class,d.get("location_id"),id)!=1)throw new WarehouseException("INVENTORY_BIN_LOCKED");
   var scanCodes=new HashMap<Long,String>();var counts=new HashMap<Long,Long>();if(!(confirmation.get("counts") instanceof List<?> list))throw new WarehouseException("WAREHOUSE_BLIND_COUNT_REQUIRED");
   for(Object item:list){if(!(item instanceof Map<?,?> m))throw new WarehouseException("WAREHOUSE_INVALID_INPUT");long batchId=positive(m.get("batch_id"));scanCodes.put(batchId,Objects.toString(m.get("scanned_barcode"),""));if(counts.put(batchId,nonnegative(m.get("quantity")))!=null)throw new WarehouseException("WAREHOUSE_INVALID_INPUT");}
   var lines=(List<Map<String,Object>>)p.get("lines");if(counts.size()!=lines.size())throw new WarehouseException("WAREHOUSE_BLIND_COUNT_REQUIRED");
   for(var line:lines){long batch=positive(line.get("batch_id")),product=positive(line.get("product_id"));stock().lockProduct(product);stock().refresh(product);var b=jdbc.queryForMap("SELECT * FROM inventory_batches WHERE id=? FOR UPDATE",batch);
    if(!counts.containsKey(batch)||BatchStock.n(b,"reserved_quantity")!=0||BatchStock.n(b,"physical_quantity")!=nonnegative(line.get("expected_quantity")))throw new WarehouseException("INVENTORY_STOCK_CHANGED");
    scanProduct(product,scanCodes.get(batch),confirmation.get("scan_override_reason"));long delta=counts.get(batch)-BatchStock.n(b,"physical_quantity");if(delta!=0){reason(confirmation.get("variance_reason"));boolean saleable=stock().eligible(batch);jdbc.update("UPDATE inventory_batches SET physical_quantity=?,accounted_quantity=? WHERE id=?",counts.get(batch),saleable?counts.get(batch):0,batch);if(saleable){stock().adjustFree(product,delta);warehouse.recordInventoryAdjustment(product,delta,"COUNT",reference,why);}stock().event(batch,delta,0,"COUNT",reference,actor(),reason(confirmation.get("variance_reason")),device(p));}
    line.put("counted_quantity",counts.get(batch));if(b.get("unit_cost")==null)cost=null;else if(cost!=null)cost=cost.add(new BigDecimal(String.valueOf(b.get("unit_cost"))).multiply(BigDecimal.valueOf(delta)));
   }
   p.put("variance_reason",confirmation.get("variance_reason"));jdbc.update("DELETE FROM inventory_bin_locks WHERE location_id=? AND document_id=?",d.get("location_id"),id);
  }else{
   long batch=positive(p.get("batch_id"));var initial=jdbc.queryForMap("SELECT product_id FROM inventory_batches WHERE id=?",batch);long product=BatchStock.n(initial,"product_id");stock().lockProduct(product);stock().refresh(product);
   var b=jdbc.queryForMap("SELECT * FROM inventory_batches WHERE id=? FOR UPDATE",batch);stock().requireBin(BatchStock.n(b,"location_id"));long qty="RELEASE".equals(kind)?BatchStock.n(b,"physical_quantity"):positive(p.get("quantity"));
   if(qty<=0||qty>BatchStock.n(b,"physical_quantity")-BatchStock.n(b,"reserved_quantity"))throw new WarehouseException("WAREHOUSE_INSUFFICIENT_STOCK");boolean wasSellable=stock().eligible(batch);
   if("RELEASE".equals(kind)){
    if(!BatchStock.truth(b.get("quarantined"))||BatchStock.n(b,"reserved_quantity")!=0)throw new WarehouseException("WAREHOUSE_INVALID_STATE");long destination=positive(p.get("destination_id"));stock().requireBin(destination);var m=metadata(p,qty);
    if(b.get("expiry_date")!=null&&!String.valueOf(b.get("expiry_date")).equals(String.valueOf(m.expiry())))throw new WarehouseException("INVENTORY_EXPIRY_IMMUTABLE");p.put("before",b);
    stock().event(batch,0,0,"INSPECTION",reference,actor(),why,device(p));jdbc.update("UPDATE inventory_batches SET location_id=?,manufacture_date=?,expiry_date=?,unit_cost=?,batch_number=?,quarantined=0 WHERE id=?",destination,m.manufactured(),m.expiry(),m.unitPrice(),m.batch(),batch);
    if(stock().eligible(batch)){jdbc.update("UPDATE inventory_batches SET accounted_quantity=? WHERE id=?",qty,batch);stock().adjustFree(product,qty);warehouse.recordInventoryAdjustment(product,qty,"INSPECTION",reference,why);}stock().event(batch,0,0,"RELEASE",reference,actor(),why,device(p),stock().eligible(batch)?qty:0);cost=m.unitPrice().multiply(BigDecimal.valueOf(qty));
   }else{
    jdbc.update("UPDATE inventory_batches SET physical_quantity=physical_quantity-?,accounted_quantity=accounted_quantity-? WHERE id=?",qty,wasSellable?qty:0,batch);stock().event(batch,-qty,0,kind,reference,actor(),why,device(p));long freeDelta=wasSellable?-qty:0;
    if("TRANSFER".equals(kind)){
     scanBin(BatchStock.n(b,"location_id"),confirmation.get("source_code"));scanProduct(product,Objects.toString(confirmation.get("scanned_barcode"),""),confirmation.get("scan_override_reason"));
     long destination=positive(p.get("destination_id"));if(destination==BatchStock.n(b,"location_id"))throw new WarehouseException("WAREHOUSE_INVALID_INPUT");stock().requireBin(destination);scanBin(destination,confirmation.get("destination_code"));
     long moved=stock().insert(product,destination,b.get("receipt_id")==null?null:BatchStock.n(b,"receipt_id"),Objects.toString(b.get("batch_number"),""),b.get("manufacture_date"),b.get("expiry_date"),b.get("unit_cost"),qty,BatchStock.truth(b.get("quarantined")),reference,actor());
     if(stock().eligible(moved))freeDelta+=qty;stock().event(moved,0,0,"TRANSFER",reference,actor(),why,device(p));
    }
    stock().adjustFree(product,freeDelta);warehouse.recordInventoryAdjustment(product,freeDelta,kind,reference,why);cost=b.get("unit_cost")==null?null:new BigDecimal(String.valueOf(b.get("unit_cost"))).multiply(BigDecimal.valueOf(qty));
   }
  }
  jdbc.update("UPDATE inventory_documents SET status='POSTED',approved_by=?,approved_at=UTC_TIMESTAMP(),payload=?,cost_total=? WHERE id=?",actor(),encode(p),cost,id);return document(id);
 }
 private void scanBin(long location,Object scanned){String code=WarehouseService.text(scanned,40,true).toUpperCase(Locale.ROOT);String expected=jdbc.queryForObject("SELECT code FROM warehouse_locations WHERE id=?",String.class,location);if(!Objects.equals(expected,code))throw new WarehouseException("INVENTORY_BIN_MISMATCH");}
 private void scanProduct(long product,String scanned,Object override){String code=jdbc.queryForObject("SELECT barcode FROM products WHERE id=?",String.class,product);if(code==null||code.isBlank()){reason(override);return;}if(!code.equals(scanned==null?"":scanned.trim()))throw new WarehouseException("WAREHOUSE_BARCODE_MISMATCH");}
 @Transactional public Object submitCount(long id,Map<String,Object> body){
  enabled();var d=jdbc.queryForMap("SELECT * FROM inventory_documents WHERE id=? FOR UPDATE",id);if(!"COUNT".equals(d.get("kind"))||!"DRAFT".equals(d.get("status")))throw new WarehouseException("WAREHOUSE_INVALID_STATE");
  var p=decode(d.get("payload"));var lines=(List<Map<String,Object>>)p.get("lines");if(!(body.get("counts") instanceof List<?> counts)||counts.size()!=lines.size())throw new WarehouseException("WAREHOUSE_BLIND_COUNT_REQUIRED");
  var seen=new HashSet<Long>();boolean variance=false;
  for(Object item:counts){if(!(item instanceof Map<?,?> m))throw new WarehouseException("WAREHOUSE_INVALID_INPUT");long batch=positive(m.get("batch_id")),qty=nonnegative(m.get("quantity"));if(!seen.add(batch))throw new WarehouseException("WAREHOUSE_INVALID_INPUT");var line=lines.stream().filter(r->positive(r.get("batch_id"))==batch).findFirst().orElseThrow(()->new WarehouseException("WAREHOUSE_INVALID_INPUT"));scanProduct(positive(line.get("product_id")),Objects.toString(m.get("scanned_barcode"),""),body.get("scan_override_reason"));line.put("counted_quantity",qty);if(qty!=nonnegative(line.get("expected_quantity")))variance=true;}
  if(variance)reason(body.get("variance_reason"));p.put("counts",counts);p.put("variance_reason",body.get("variance_reason"));p.put("scan_override_reason",body.get("scan_override_reason"));
  jdbc.update("UPDATE inventory_documents SET status='COUNTED',payload=?,counted_by=?,counted_at=UTC_TIMESTAMP() WHERE id=?",encode(p),actor(),id);return document(id);
 }
 public Map<String,Object> settings(){enabled();return jdbc.queryForMap("SELECT * FROM inventory_settings WHERE id=1");}
 @Transactional public void saveSettings(Map<String,Object> body){enabled();if(!owner())throw new org.springframework.security.access.AccessDeniedException("Owner only");
  BigDecimal percent;int days;try{percent=new BigDecimal(String.valueOf(body.get("writeoff_warning_percent"))).setScale(2,java.math.RoundingMode.UNNECESSARY);days=new BigDecimal(String.valueOf(body.get("expiry_warning_days"))).intValueExact();}catch(Exception ex){throw new WarehouseException("WAREHOUSE_INVALID_INPUT");}
  if(percent.signum()<0||percent.compareTo(new BigDecimal("100"))>0||days<1||days>365)throw new WarehouseException("WAREHOUSE_INVALID_INPUT");
  jdbc.update("UPDATE inventory_settings SET writeoff_warning_percent=?,expiry_warning_days=?,updated_by=?,updated_at=UTC_TIMESTAMP() WHERE id=1",percent,days,actor());
 }
 public Map<String,Object> writeoffSummary(){enabled();if(!owner())throw new org.springframework.security.access.AccessDeniedException("Owner only");
  String start="CONVERT_TZ(DATE_FORMAT(CONVERT_TZ(UTC_TIMESTAMP(),'+00:00','+05:00'),'%Y-%m-01'),'+05:00','+00:00')";
  BigDecimal written=jdbc.queryForObject("SELECT COALESCE(SUM(cost_total),0) FROM inventory_documents WHERE kind='WRITEOFF' AND status='POSTED' AND approved_at>="+start,BigDecimal.class);
  BigDecimal sales=jdbc.queryForObject("SELECT COALESCE(SUM(GREATEST(0,o.total_price-COALESCE((SELECT r.refund_amount FROM delivery_partial_returns r WHERE r.order_id=o.id),0))),0) FROM orders o WHERE o.status='DELIVERED' AND o.payment_status='PAID' AND o.delivered_at>="+start,BigDecimal.class);
  BigDecimal threshold=jdbc.queryForObject("SELECT writeoff_warning_percent FROM inventory_settings WHERE id=1",BigDecimal.class);var result=new LinkedHashMap<String,Object>();result.put("writeoff_cost",written);result.put("delivered_turnover",sales);result.put("threshold_percent",threshold);result.put("percent",sales.signum()==0?null:written.multiply(new BigDecimal("100")).divide(sales,4,java.math.RoundingMode.HALF_UP));result.put("warning",threshold.signum()>0&&written.signum()>0&&(sales.signum()==0||written.multiply(new BigDecimal("100")).compareTo(sales.multiply(threshold))>0));return result;
 }
 @org.springframework.scheduling.annotation.Scheduled(fixedDelayString="${warehouse.inventory.scan-ms:60000}",initialDelayString="${warehouse.inventory.scan-initial-ms:120000}")
 @Transactional public void refreshExpiry(){
  if(!warehouse.isInventoryEnabled())return;
  for(long product:jdbc.queryForList("SELECT DISTINCT b.product_id FROM inventory_batches b JOIN inventory_products i ON i.product_id=b.product_id WHERE b.accounted_quantity>0 AND b.expiry_date<=DATE(CONVERT_TZ(UTC_TIMESTAMP(),'+00:00','+05:00')) ORDER BY b.product_id LIMIT 100",Long.class)){stock().lockProduct(product);stock().refresh(product);}
 }
 public List<Map<String,Object>> expiry(int days){enabled();if(days<0||days>365)throw new WarehouseException("WAREHOUSE_INVALID_INPUT");return jdbc.queryForList("SELECT b.id,b.product_id,p.name product_name,l.code location_code,b.batch_number,b.expiry_date,b.physical_quantity,b.reserved_quantity,k.document_id count_document,DATEDIFF(b.expiry_date,DATE(CONVERT_TZ(UTC_TIMESTAMP(),'+00:00','+05:00'))) days_remaining FROM inventory_batches b JOIN products p ON p.id=b.product_id JOIN warehouse_locations l ON l.id=b.location_id LEFT JOIN inventory_bin_locks k ON k.location_id=b.location_id WHERE b.physical_quantity>0 AND (b.expiry_date IS NULL OR b.expiry_date<=DATE(CONVERT_TZ(UTC_TIMESTAMP(),'+00:00','+05:00'))+INTERVAL ? DAY) ORDER BY b.expiry_date IS NOT NULL,b.expiry_date,b.id LIMIT 1000",days);}
 private String device(Map<String,Object> p){return WarehouseService.text(p.get("device_code"),100,false);}
 @Transactional public void cancel(long id,String why){enabled();why=reason(why);var d=jdbc.queryForMap("SELECT status,created_by FROM inventory_documents WHERE id=? FOR UPDATE",id);if(!owner()&&!actor().equals(d.get("created_by")))throw new WarehouseException("WAREHOUSE_NOT_OWNER");if(!Set.of("DRAFT","COUNTED").contains(String.valueOf(d.get("status"))))throw new WarehouseException("WAREHOUSE_INVALID_STATE");jdbc.update("DELETE FROM inventory_bin_locks WHERE document_id=?",id);jdbc.update("UPDATE inventory_documents SET status='VOID',approved_by=?,approved_at=UTC_TIMESTAMP(),reason=CONCAT(LEFT(reason,240),' / ',?) WHERE id=?",actor(),why,id);}
}
