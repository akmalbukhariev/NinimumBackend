-- K3 read-only check: orders whose warehouse step or delivery job disagrees with orders.status.
-- Changes nothing. Each row is one order and the reason it looks inconsistent.
SELECT o.id order_id,o.status order_status,o.payment_status,wp.status warehouse_step,dj.status delivery_step,
  CONCAT_WS('; ',
    CASE WHEN o.status='CANCELLED' AND dj.status IS NOT NULL AND dj.status NOT IN ('CANCELLED','FAILED','DELIVERED') THEN 'cancelled order has an open delivery job' END,
    CASE WHEN o.status IN ('PENDING','CONFIRMED','PREPARING') AND dj.status IN ('ACCEPTED','ON_THE_WAY','DELIVERED') THEN 'courier step is ahead of the order' END,
    CASE WHEN o.status='READY' AND dj.status IN ('ON_THE_WAY','DELIVERED') THEN 'courier step is ahead of the order' END,
    CASE WHEN o.status='ON_THE_WAY' AND dj.status IN ('WAITING_ASSIGNMENT','CANCELLED') THEN 'order is on the way without an active courier' END,
    CASE WHEN o.status='DELIVERED' AND dj.status IS NOT NULL AND dj.status<>'DELIVERED' THEN 'delivered order, delivery job not delivered' END,
    CASE WHEN o.status IN ('PENDING','CONFIRMED') AND wp.status IN ('PICKING','BLOCKED','READY') THEN 'warehouse step is ahead of the order' END,
    CASE WHEN o.status='PREPARING' AND wp.status='READY' THEN 'warehouse is ready but the order is still preparing' END,
    CASE WHEN o.status IN ('READY','ON_THE_WAY') AND wp.status IS NOT NULL AND wp.status<>'READY' THEN 'order is ready but the warehouse step is not' END,
    CASE WHEN o.payment_status<>'PAID' AND o.status IN ('CONFIRMED','PREPARING','READY','ON_THE_WAY') THEN 'order moves without payment' END
  ) problem
FROM orders o
LEFT JOIN warehouse_preparations wp ON wp.order_id=o.id
LEFT JOIN delivery_jobs dj ON dj.order_id=o.id
HAVING problem<>''
ORDER BY o.id;
