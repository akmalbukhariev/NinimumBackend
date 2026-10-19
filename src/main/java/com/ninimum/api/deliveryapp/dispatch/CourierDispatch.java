package com.ninimum.api.deliveryapp.dispatch;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;

@Service
@lombok.extern.slf4j.Slf4j
public class CourierDispatch {
 private final JdbcTemplate jdbc;private final TransactionTemplate tx;private boolean warned;
 public CourierDispatch(JdbcTemplate jdbc,PlatformTransactionManager manager){this.jdbc=jdbc;tx=new TransactionTemplate(manager);tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);}
 public Map<String,Object> settings(){return jdbc.queryForMap("SELECT enabled,accept_minutes,updated_by,updated_at FROM courier_dispatch_settings WHERE id=1");}
 public boolean offersEnabled(){return enabled();}
 private boolean enabled(){return jdbc.queryForObject("SELECT enabled FROM courier_dispatch_settings WHERE id=1",Integer.class)==1;}
 public void save(Map<String,Object> body){
  int minutes;try{minutes=new java.math.BigDecimal(String.valueOf(body.get("accept_minutes"))).intValueExact();}catch(Exception ex){throw new IllegalArgumentException("Invalid acceptance timeout");}
  if(minutes<1||minutes>60)throw new IllegalArgumentException("Acceptance timeout must be 1–60 minutes");
  boolean on=Boolean.TRUE.equals(body.get("enabled"))||"1".equals(String.valueOf(body.get("enabled")));
  tx.executeWithoutResult(t->{jdbc.queryForMap("SELECT id FROM courier_dispatch_settings WHERE id=1 FOR UPDATE");jdbc.update("UPDATE courier_dispatch_settings SET enabled=?,accept_minutes=?,updated_by=?,updated_at=UTC_TIMESTAMP() WHERE id=1",on,minutes,actor());});
 }
 public boolean visible(long job,long worker){
  if(!enabled())return true;
  return jdbc.queryForObject("SELECT COUNT(*) FROM courier_dispatch_offers WHERE job_id=? AND worker_id=? AND status='OFFERED' AND expires_at>UTC_TIMESTAMP()",Integer.class,job,worker)==1;
 }
 /** Caller must already hold the delivery job lock; accepted jobs still require physical handover. */
 public void requireOffer(long job,long worker){
  if(!enabled())return;
  jdbc.queryForMap("SELECT id FROM delivery_jobs WHERE id=? FOR UPDATE",job);
  var rows=jdbc.queryForList("SELECT worker_id,status,expires_at>UTC_TIMESTAMP() valid FROM courier_dispatch_offers WHERE job_id=? FOR UPDATE",job);
  if(rows.size()!=1||!"OFFERED".equals(rows.get(0).get("status"))||!Objects.equals(((Number)rows.get(0).get("worker_id")).longValue(),worker)||!truth(rows.get(0).get("valid")))throw new AccessDeniedException("This offer expired or belongs to another courier");
 }
 /** The courier turns the offer down; the next scan offers the job to another courier. */
 public void declined(long job,long worker){
  var rows=jdbc.queryForList("SELECT attempt FROM courier_dispatch_offers WHERE job_id=? AND worker_id=? AND status='OFFERED' AND expires_at>UTC_TIMESTAMP() FOR UPDATE",job,worker);
  if(rows.isEmpty())throw new AccessDeniedException("This offer expired or belongs to another courier");
  jdbc.update("UPDATE courier_dispatch_offers SET status='EXPIRED',expires_at=UTC_TIMESTAMP(),updated_at=UTC_TIMESTAMP() WHERE job_id=?",job);
  event(job,((Number)rows.get(0).get("attempt")).intValue(),worker,"DECLINED",actor(),"Courier declined the offer");
 }
 public void accepted(long job,long worker){
  var rows=jdbc.queryForList("SELECT attempt FROM courier_dispatch_offers WHERE job_id=? AND worker_id=? AND status='OFFERED' FOR UPDATE",job,worker);
  if(!rows.isEmpty()){jdbc.update("UPDATE courier_dispatch_offers SET status='ACCEPTED',updated_at=UTC_TIMESTAMP() WHERE job_id=?",job);event(job,((Number)rows.get(0).get("attempt")).intValue(),worker,"ACCEPTED",actor(),null);}
 }
 @Scheduled(fixedDelayString="${courier.dispatch.interval-ms:15000}",initialDelayString="${courier.dispatch.initial-delay-ms:60000}")
 public void tick(){try{scan();warned=false;}catch(org.springframework.dao.DataAccessException ex){if(!warned)log.warn("Courier dispatch unavailable; check dispatch migration and connectivity.");warned=true;}}
 private void scan(){tx.executeWithoutResult(t->{
  var config=jdbc.queryForMap("SELECT * FROM courier_dispatch_settings WHERE id=1 FOR UPDATE");if(!truth(config.get("enabled")))return;
  int minutes=((Number)config.get("accept_minutes")).intValue();long cursor=((Number)config.get("last_worker_id")).longValue();
  var jobs=jdbc.queryForList("SELECT j.id FROM delivery_jobs j JOIN orders o ON o.id=j.order_id WHERE j.delivery_worker_id IS NULL AND j.status='WAITING_ASSIGNMENT' AND o.payment_status='PAID' AND o.status='READY' ORDER BY COALESCE((SELECT MIN(COALESCE(FROM_UNIXTIME(NULLIF(p.payme_perform_time,0)/1000),p.updated_at)) FROM payments p WHERE p.order_id=o.id AND p.status='PAID'),o.ordered_at),j.id LIMIT 100 FOR UPDATE");
  for(var job:jobs){long id=((Number)job.get("id")).longValue();var offers=jdbc.queryForList("SELECT *,expires_at>UTC_TIMESTAMP() valid FROM courier_dispatch_offers WHERE job_id=? FOR UPDATE",id);int attempt=0;long lastWorker=0;
   if(!offers.isEmpty()){
    var offer=offers.get(0);lastWorker=offer.get("worker_id")==null?0:((Number)offer.get("worker_id")).longValue();attempt=((Number)offer.get("attempt")).intValue();String state=String.valueOf(offer.get("status"));
    if("ESCALATED".equals(state)||"ACCEPTED".equals(state))continue;
    if("OFFERED".equals(state)&&truth(offer.get("valid")))continue;
    if("OFFERED".equals(state)){event(id,attempt,((Number)offer.get("worker_id")).longValue(),"EXPIRED","SYSTEM","Courier did not accept before deadline");jdbc.update("UPDATE courier_dispatch_offers SET status='EXPIRED',updated_at=UTC_TIMESTAMP() WHERE job_id=?",id);}
   }
   if(attempt>=3){jdbc.update("UPDATE courier_dispatch_offers SET status='ESCALATED',updated_at=UTC_TIMESTAMP() WHERE job_id=?",id);event(id,attempt,null,"ESCALATED","SYSTEM","Three acceptance deadlines missed; dispatcher review required");continue;}
   var candidates=jdbc.queryForList("SELECT w.id,((SELECT COUNT(*) FROM delivery_jobs a JOIN orders ao ON ao.id=a.order_id WHERE a.delivery_worker_id=w.id AND (a.status IN ('ACCEPTED','ON_THE_WAY') OR ao.status='RETURNING' OR EXISTS(SELECT 1 FROM delivery_partial_returns pr WHERE pr.delivery_job_id=a.id AND pr.status='RETURNING')))+(SELECT COUNT(*) FROM courier_dispatch_offers f WHERE f.worker_id=w.id AND f.status='OFFERED' AND f.expires_at>UTC_TIMESTAMP())) workload FROM delivery_app_workers w WHERE w.status='ACTIVE' AND w.is_online=1 AND (w.id<>? OR (SELECT COUNT(*) FROM delivery_app_workers active WHERE active.status='ACTIVE' AND active.is_online=1)=1) ORDER BY workload,(w.id>?) DESC,w.id LIMIT 1",lastWorker,cursor);
   if(candidates.isEmpty())continue;
   long worker=((Number)candidates.get(0).get("id")).longValue();attempt++;
   jdbc.update("INSERT INTO courier_dispatch_offers(job_id,worker_id,status,attempt,offered_at,expires_at,updated_at) VALUES(?,?,'OFFERED',?,UTC_TIMESTAMP(),TIMESTAMPADD(MINUTE,?,UTC_TIMESTAMP()),UTC_TIMESTAMP()) ON DUPLICATE KEY UPDATE worker_id=VALUES(worker_id),status='OFFERED',attempt=VALUES(attempt),offered_at=VALUES(offered_at),expires_at=VALUES(expires_at),updated_at=UTC_TIMESTAMP()",id,worker,attempt,minutes);
   event(id,attempt,worker,"OFFERED","SYSTEM",null);cursor=worker;
  }
  jdbc.update("UPDATE courier_dispatch_settings SET last_worker_id=? WHERE id=1",cursor);
  jdbc.update("UPDATE courier_dispatch_offers f JOIN delivery_jobs j ON j.id=f.job_id JOIN orders o ON o.id=j.order_id SET f.status='CANCELLED',f.updated_at=UTC_TIMESTAMP() WHERE f.status IN ('OFFERED','EXPIRED','ESCALATED') AND (j.delivery_worker_id IS NOT NULL OR j.status<>'WAITING_ASSIGNMENT' OR o.payment_status<>'PAID' OR o.status<>'READY')");
 });}
 public Map<String,Object> history(int page){int p=Math.max(1,Math.min(page,1000000));return Map.of("items",jdbc.queryForList("SELECT h.*,j.order_id,w.worker_code,DATE_FORMAT(h.occurred_at,'%Y-%m-%dT%H:%i:%sZ') occurred_at FROM courier_dispatch_history h LEFT JOIN delivery_jobs j ON j.id=h.job_id LEFT JOIN delivery_app_workers w ON w.id=h.worker_id ORDER BY h.id DESC LIMIT 50 OFFSET ?",(p-1)*50),"total",jdbc.queryForObject("SELECT COUNT(*) FROM courier_dispatch_history",Long.class),"page",p,"page_size",50);}
 public List<Map<String,Object>> escalated(){return jdbc.queryForList("SELECT f.job_id,j.order_id,f.attempt FROM courier_dispatch_offers f JOIN delivery_jobs j ON j.id=f.job_id JOIN orders o ON o.id=j.order_id WHERE f.status='ESCALATED' AND j.delivery_worker_id IS NULL AND o.status='READY' AND o.payment_status='PAID'");}
 public void retry(long job,String reason){
  if(reason==null||reason.isBlank()||reason.length()>500)throw new IllegalArgumentException("A review reason is required");
  tx.executeWithoutResult(t->{
   jdbc.queryForMap("SELECT id FROM courier_dispatch_settings WHERE id=1 FOR UPDATE");
   var j=jdbc.queryForMap("SELECT j.delivery_worker_id,j.status,o.status order_status,o.payment_status FROM delivery_jobs j JOIN orders o ON o.id=j.order_id WHERE j.id=? FOR UPDATE",job);
   if(j.get("delivery_worker_id")!=null||!"WAITING_ASSIGNMENT".equals(j.get("status"))||!"READY".equals(j.get("order_status"))||!"PAID".equals(j.get("payment_status")))throw new IllegalArgumentException("Job is not awaiting dispatch");
   var f=jdbc.queryForMap("SELECT status,attempt FROM courier_dispatch_offers WHERE job_id=? FOR UPDATE",job);
   if(!"ESCALATED".equals(f.get("status")))throw new IllegalArgumentException("Only escalated offers can be reviewed");
   event(job,((Number)f.get("attempt")).intValue(),null,"REVIEW_RETRY",actor(),reason.trim());
   jdbc.update("UPDATE courier_dispatch_offers SET attempt=0,worker_id=NULL,status='EXPIRED',updated_at=UTC_TIMESTAMP() WHERE job_id=?",job);
  });
 }
 private void event(long job,int attempt,Long worker,String event,String actor,String note){jdbc.update("INSERT INTO courier_dispatch_history(job_id,attempt,worker_id,event,actor,occurred_at,note) VALUES(?,?,?,?,?,UTC_TIMESTAMP(),?)",job,attempt,worker,event,actor,note);}
 private static boolean truth(Object v){return Boolean.TRUE.equals(v)||(v instanceof Number&&((Number)v).intValue()==1);}
 private static String actor(){var a=SecurityContextHolder.getContext().getAuthentication();return a==null?"SYSTEM":a.getName();}
}
