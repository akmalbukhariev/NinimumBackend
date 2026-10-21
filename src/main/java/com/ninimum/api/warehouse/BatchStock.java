package com.ninimum.api.warehouse;

import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** All mutations run inside the caller's transaction while holding the product row lock. */
public final class BatchStock {
 final JdbcTemplate jdbc;
 public BatchStock(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public boolean active(long product){return jdbc.queryForObject("SELECT COUNT(*) FROM inventory_products WHERE product_id=?",Integer.class,product)==1;}
 static final String ELIGIBLE="b.quarantined=0 AND b.expiry_date>DATE(CONVERT_TZ(UTC_TIMESTAMP(),'+00:00','+05:00')) AND b.unit_cost IS NOT NULL AND l.is_active=1 AND l.hierarchy_level='BIN' AND l.type IN ('SHELF','COLD_STORAGE','PICKING')";
 static final String UNLOCKED=" AND NOT EXISTS(SELECT 1 FROM inventory_bin_locks k WHERE k.location_id=b.location_id)";
 public long available(long product){return jdbc.queryForObject("SELECT COALESCE(SUM(b.physical_quantity-b.reserved_quantity),0) FROM inventory_batches b JOIN warehouse_locations l ON l.id=b.location_id WHERE b.product_id=? AND "+ELIGIBLE+UNLOCKED,Long.class,product);}
 public boolean eligible(long batch){return jdbc.queryForObject("SELECT COUNT(*) FROM inventory_batches b JOIN warehouse_locations l ON l.id=b.location_id WHERE b.id=? AND "+ELIGIBLE,Integer.class,batch)==1;}
 long insert(long product,long location,Long receipt,String lot,Object manufacture,Object expiry,Object cost,long quantity,boolean quarantine,String reference,String actor){
  jdbc.update("INSERT INTO inventory_batches(product_id,location_id,receipt_id,batch_number,manufacture_date,expiry_date,unit_cost,physical_quantity,quarantined,reference,created_by,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP())",product,location,receipt,lot,manufacture,expiry,cost,quantity,quarantine,reference,actor);
  long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);jdbc.update("UPDATE inventory_batches SET accounted_quantity=? WHERE id=?",eligible(id)?quantity:0,id);event(id,quantity,0,"RECEIPT",reference,actor,null,null);return id;
 }
 /** Returns actual sellable change, after correcting the caller's preliminary aggregate update. */
 public long reconcile(long product,long delta,String kind,String reference,String actor){
  if(!active(product))return delta;
  refresh(product);
  if("SALE".equals(kind)){reserve(product,-delta,order(reference),actor);return delta;}
  if("PAYMENT_REFUND".equals(kind)){long restored=release(product,delta,order(reference),actor);adjustFree(product,restored-delta);return restored;}
  if("RECEIPT".equals(kind)&&reference.startsWith("KR-")){
   long receipt=Long.parseLong(reference.substring(3));
   var item=jdbc.queryForMap("SELECT location_id,batch_number,manufacture_date,expiry_date,unit_price FROM warehouse_receipt_items WHERE receipt_id=? AND product_id=?",receipt,product);
   requireBin(((Number)item.get("location_id")).longValue());
   long batch=insert(product,((Number)item.get("location_id")).longValue(),receipt,Objects.toString(item.get("batch_number"),""),item.get("manufacture_date"),item.get("expiry_date"),item.get("unit_price"),delta,false,reference,actor);
   long free=eligible(batch)?delta:0;adjustFree(product,free-delta);return free;
  }
  if("RECEIPT".equals(kind)&&reference.startsWith("ORDER-RETURN-")){
   // A partial return does not prove which physical batch returned. Inspect before selling it again.
   var locations=jdbc.queryForList("SELECT id FROM warehouse_locations WHERE is_active=1 AND hierarchy_level='BIN' AND type='RETURNS' ORDER BY id LIMIT 1 FOR UPDATE");
   if(locations.isEmpty())throw new WarehouseException("INVENTORY_RETURN_BIN_REQUIRED");
   long location=((Number)locations.get(0).get("id")).longValue();requireBin(location);
   insert(product,location,null,null,null,null,null,delta,true,reference,actor);adjustFree(product,-delta);return 0;
  }
  return delta;
 }
 private static long order(String reference){if(reference==null||!reference.matches("ORDER-[0-9]+"))throw new WarehouseException("INVENTORY_ORDER_REFERENCE_REQUIRED");return Long.parseLong(reference.substring(6));}
 private void reserve(long product,long quantity,long order,String actor){
  if(quantity<=0)throw new WarehouseException("WAREHOUSE_INVALID_INPUT");
  if(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_allocations WHERE order_id=? AND product_id=?",Integer.class,order,product)>0)throw new WarehouseException("INVENTORY_ALREADY_RESERVED");
  var batches=jdbc.queryForList("SELECT b.* FROM inventory_batches b JOIN warehouse_locations l ON l.id=b.location_id WHERE b.product_id=? AND b.physical_quantity>b.reserved_quantity AND "+ELIGIBLE+UNLOCKED+" ORDER BY b.expiry_date,b.id FOR UPDATE",product);
  long remaining=quantity;BigDecimal cost=unitCost(product);
  for(var b:batches){long id=n(b,"id"),take=Math.min(remaining,n(b,"physical_quantity")-n(b,"reserved_quantity"));if(take<=0)continue;
   jdbc.update("UPDATE inventory_batches SET reserved_quantity=reserved_quantity+?,accounted_quantity=accounted_quantity-? WHERE id=?",take,take,id);
   jdbc.update("INSERT INTO inventory_allocations(order_id,batch_id,product_id,quantity,unit_cost,created_at) VALUES(?,?,?,?,?,UTC_TIMESTAMP())",order,id,product,take,cost);
   event(id,0,take,"RESERVE","ORDER-"+order,actor,null,null);remaining-=take;if(remaining==0)break;
  }
  if(remaining!=0)throw new WarehouseException("WAREHOUSE_INSUFFICIENT_STOCK");
  jdbc.update("UPDATE order_items SET unit_cost_snapshot=?,cost_recorded_at=UTC_TIMESTAMP() WHERE order_id=? AND product_id=? AND unit_cost_snapshot IS NULL",cost,order,product);
 }
 private long release(long product,long quantity,long order,String actor){
  long free=0,remaining=quantity;
  for(var a:jdbc.queryForList("SELECT a.*,b.location_id FROM inventory_allocations a JOIN inventory_batches b ON b.id=a.batch_id WHERE a.order_id=? AND a.product_id=? ORDER BY a.batch_id FOR UPDATE",order,product)){
   long held=n(a,"quantity")-n(a,"issued_quantity")-n(a,"released_quantity"),take=Math.min(remaining,held);if(take<=0)continue;
   long batch=n(a,"batch_id");boolean saleable=eligible(batch);jdbc.update("UPDATE inventory_batches SET reserved_quantity=reserved_quantity-?,accounted_quantity=accounted_quantity+? WHERE id=? AND reserved_quantity>=?",take,saleable?take:0,batch,take);
   jdbc.update("UPDATE inventory_allocations SET released_quantity=released_quantity+? WHERE order_id=? AND batch_id=?",take,order,batch);
   event(batch,0,-take,"RELEASE","ORDER-"+order,actor,null,null);if(saleable)free+=take;remaining-=take;if(remaining==0)break;
  }
  // Already issued goods are not physically returned by a refund callback.
  return free;
 }
 public void issue(long order,String actor){
  var products=jdbc.queryForList("SELECT DISTINCT product_id FROM inventory_allocations WHERE order_id=? ORDER BY product_id",Long.class,order);
  for(long product:products){lockProduct(product);refresh(product);
   for(var a:jdbc.queryForList("SELECT * FROM inventory_allocations WHERE order_id=? AND product_id=? ORDER BY batch_id FOR UPDATE",order,product)){
    long qty=n(a,"quantity")-n(a,"issued_quantity")-n(a,"released_quantity");if(qty==0)continue;long id=n(a,"batch_id");
    if(!eligible(id))throw new WarehouseException("INVENTORY_BATCH_NOT_SELLABLE");
    requireBin(((Number)jdbc.queryForMap("SELECT location_id FROM inventory_batches WHERE id=? FOR UPDATE",id).get("location_id")).longValue());
    if(jdbc.update("UPDATE inventory_batches SET physical_quantity=physical_quantity-?,reserved_quantity=reserved_quantity-? WHERE id=? AND physical_quantity>=? AND reserved_quantity>=?",qty,qty,id,qty,qty)!=1)throw new WarehouseException("INVENTORY_BALANCE_MISMATCH");
    jdbc.update("UPDATE inventory_allocations SET issued_quantity=issued_quantity+? WHERE order_id=? AND batch_id=?",qty,order,id);event(id,-qty,-qty,"ISSUE","ORDER-"+order,actor,null,null);
   }
  }
 }
 /** Owner correction back before pickup: issued goods are in the warehouse again and stay reserved for the order. */
 public void unissue(long order,String actor){
  var products=jdbc.queryForList("SELECT DISTINCT product_id FROM inventory_allocations WHERE order_id=? AND issued_quantity>0 ORDER BY product_id",Long.class,order);
  for(long product:products){lockProduct(product);
   for(var a:jdbc.queryForList("SELECT * FROM inventory_allocations WHERE order_id=? AND product_id=? AND issued_quantity>0 ORDER BY batch_id FOR UPDATE",order,product)){
    long qty=n(a,"issued_quantity"),id=n(a,"batch_id");
    requireBin(((Number)jdbc.queryForMap("SELECT location_id FROM inventory_batches WHERE id=? FOR UPDATE",id).get("location_id")).longValue());
    jdbc.update("UPDATE inventory_batches SET physical_quantity=physical_quantity+?,reserved_quantity=reserved_quantity+? WHERE id=?",qty,qty,id);
    jdbc.update("UPDATE inventory_allocations SET issued_quantity=0 WHERE order_id=? AND batch_id=?",order,id);event(id,qty,qty,"UNISSUE","ORDER-"+order,actor,"Owner correction",null);
   }
  }
 }
 public void requireFresh(long order){
  if(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_allocations a JOIN inventory_batches b ON b.id=a.batch_id JOIN warehouse_locations l ON l.id=b.location_id WHERE a.order_id=? AND a.quantity>a.issued_quantity+a.released_quantity AND NOT("+ELIGIBLE+")",Integer.class,order)>0)throw new WarehouseException("INVENTORY_BATCH_NOT_SELLABLE");
 }
 public long reverse(long product,long receipt,long expected,String actor,String reason){
  var batches=jdbc.queryForList("SELECT * FROM inventory_batches WHERE product_id=? AND receipt_id=? ORDER BY id FOR UPDATE",product,receipt);
  long physical=batches.stream().mapToLong(b->n(b,"physical_quantity")).sum();
  if(physical!=expected||batches.stream().anyMatch(b->n(b,"reserved_quantity")!=0))throw new WarehouseException("INVENTORY_BATCH_IN_USE");
  long free=0;for(var b:batches){long id=n(b,"id"),qty=n(b,"physical_quantity");requireBin(n(b,"location_id"));if(eligible(id))free+=qty;jdbc.update("UPDATE inventory_batches SET physical_quantity=0,accounted_quantity=0 WHERE id=?",id);event(id,-qty,0,"RECEIPT_REVERSAL","KR-"+receipt,actor,reason,null);}
  adjustFree(product,-free);return -free;
 }
 public void refresh(long product){
  if(!active(product))return;
  for(var b:jdbc.queryForList("SELECT b.*,CASE WHEN "+ELIGIBLE+" THEN b.physical_quantity-b.reserved_quantity ELSE 0 END expected_available FROM inventory_batches b JOIN warehouse_locations l ON l.id=b.location_id WHERE b.product_id=? ORDER BY b.id FOR UPDATE",product)){
   long expected=n(b,"expected_available"),previous=n(b,"accounted_quantity");if(expected==previous)continue;
   adjustFree(product,expected-previous);jdbc.update("UPDATE inventory_batches SET accounted_quantity=? WHERE id=?",expected,b.get("id"));
   event(n(b,"id"),0,0,"AVAILABILITY","BATCH-"+b.get("id"),"SYSTEM","Sellable availability changed; physical stock preserved",null,expected-previous);
   jdbc.update("INSERT INTO warehouse_stock_movements(product_id,product_name,kind,quantity_change,stock_after,reference,actor,note,event_key,created_at) SELECT id,name,'AVAILABILITY',?,stock_quantity,?,'SYSTEM','Batch expiry or location availability changed',?,UTC_TIMESTAMP() FROM products WHERE id=?",expected-previous,"BATCH-"+b.get("id"),"inventory-availability:"+UUID.randomUUID(),product);
  }
 }
 public BigDecimal unitCost(long product){
  BigDecimal value=jdbc.queryForObject("SELECT SUM(b.unit_cost*b.physical_quantity)/NULLIF(SUM(b.physical_quantity),0) FROM inventory_batches b JOIN warehouse_locations l ON l.id=b.location_id WHERE b.product_id=? AND "+ELIGIBLE,BigDecimal.class,product);
  if(value==null)throw new WarehouseException("INVENTORY_COST_REQUIRED");return value.setScale(4,RoundingMode.HALF_UP);
 }
 public void requireBin(long id){
  var b=jdbc.queryForMap("SELECT is_active,hierarchy_level FROM warehouse_locations WHERE id=? FOR UPDATE",id);
  if(!truth(b.get("is_active"))||!"BIN".equals(b.get("hierarchy_level")))throw new WarehouseException("WAREHOUSE_INVALID_INPUT");
  if(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_bin_locks WHERE location_id=?",Integer.class,id)>0)throw new WarehouseException("INVENTORY_BIN_LOCKED");
 }
 public void lockProduct(long product){jdbc.queryForMap("SELECT id FROM products WHERE id=? FOR UPDATE",product);}
 public void adjustFree(long product,long delta){if(delta!=0&&jdbc.update("UPDATE products SET stock_quantity=stock_quantity+?,updated_at=UTC_TIMESTAMP() WHERE id=? AND stock_quantity+? BETWEEN 0 AND 2147483647",delta,product,delta)!=1)throw new WarehouseException("INVENTORY_BALANCE_MISMATCH");}
 public void event(long batch,long physical,long reserved,String kind,String reference,String actor,String reason,String device){event(batch,physical,reserved,kind,reference,actor,reason,device,eligible(batch)?physical-reserved:0);}
 public void event(long batch,long physical,long reserved,String kind,String reference,String actor,String reason,String device,long available){
  var b=jdbc.queryForMap("SELECT b.product_id,b.location_id,b.physical_quantity,b.reserved_quantity,b.accounted_quantity,p.name,l.code FROM inventory_batches b JOIN products p ON p.id=b.product_id JOIN warehouse_locations l ON l.id=b.location_id WHERE b.id=?",batch);
  if(n(b,"physical_quantity")<n(b,"reserved_quantity")||n(b,"reserved_quantity")<0||n(b,"accounted_quantity")<0)throw new WarehouseException("INVENTORY_BALANCE_MISMATCH");
  jdbc.update("INSERT INTO inventory_movements(batch_id,product_id,location_id,product_name,location_code,physical_change,reserved_change,available_change,available_after,physical_after,reserved_after,kind,reference,actor,reason,device_code,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP())",batch,b.get("product_id"),b.get("location_id"),b.get("name"),b.get("code"),physical,reserved,available,b.get("accounted_quantity"),b.get("physical_quantity"),b.get("reserved_quantity"),kind,reference,actor,reason,device);
 }
 static long n(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
 static boolean truth(Object value){return Boolean.TRUE.equals(value)||(value instanceof Number&&((Number)value).intValue()==1);}
}
