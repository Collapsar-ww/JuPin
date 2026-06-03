-- JuPin business consistency validation.
-- Run after HTTP pressure/scenario tests:
-- mysql -h127.0.0.1 -uroot -p jupin < Test/sql/validate_consistency.sql

SELECT 'duplicate_order_by_user_idempotent_key' AS check_name, COUNT(*) AS anomaly_count
FROM (
  SELECT user_id, idempotent_key
  FROM `order`
  WHERE idempotent_key IS NOT NULL AND idempotent_key <> ''
  GROUP BY user_id, idempotent_key
  HAVING COUNT(*) > 1
) t;

SELECT 'duplicate_order_by_user_pool_type' AS check_name, COUNT(*) AS anomaly_count
FROM (
  SELECT user_id, pool_id, type
  FROM `order`
  GROUP BY user_id, pool_id, type
  HAVING COUNT(*) > 1
) t;

SELECT 'duplicate_pool_member_by_pool_user' AS check_name, COUNT(*) AS anomaly_count
FROM (
  SELECT pool_id, user_id
  FROM pool_member
  GROUP BY pool_id, user_id
  HAVING COUNT(*) > 1
) t;

SELECT 'paid_order_member_not_joined' AS check_name, COUNT(*) AS anomaly_count
FROM `order` o
LEFT JOIN pool_member pm
  ON pm.pool_id = o.pool_id
 AND pm.user_id = o.user_id
WHERE o.type = 0
  AND o.status = 1
  AND (pm.id IS NULL OR pm.status <> 2);

SELECT 'joined_member_without_paid_deposit' AS check_name, COUNT(*) AS anomaly_count
FROM pool_member pm
LEFT JOIN `order` o
  ON o.pool_id = pm.pool_id
 AND o.user_id = pm.user_id
 AND o.type = 0
 AND o.status = 1
WHERE pm.status = 2
  AND o.id IS NULL;

SELECT 'pool_current_members_drift' AS check_name, COUNT(*) AS anomaly_count
FROM car_pool cp
LEFT JOIN (
  SELECT pool_id, COUNT(*) AS joined_count
  FROM pool_member
  WHERE status = 2
  GROUP BY pool_id
) joined ON joined.pool_id = cp.id
WHERE cp.current_members <> COALESCE(joined.joined_count, 0);

SELECT 'overdue_order_member_still_pending_payment' AS check_name, COUNT(*) AS anomaly_count
FROM `order` o
JOIN pool_member pm
  ON pm.pool_id = o.pool_id
 AND pm.user_id = o.user_id
WHERE o.type = 0
  AND o.status = 4
  AND pm.status = 1;

SELECT 'late_callback_rollback_overdue_order' AS check_name, COUNT(*) AS anomaly_count
FROM `order` o
JOIN payment_event pe
  ON pe.order_no = o.order_no
WHERE o.status = 1
  AND pe.status = 2
  AND pe.event_type = 'PAY_CALLBACK';

SELECT 'duplicate_payment_event_key' AS check_name, COUNT(*) AS anomaly_count
FROM (
  SELECT event_key
  FROM payment_event
  GROUP BY event_key
  HAVING COUNT(*) > 1
) t;

SELECT 'duplicate_payment_request_no' AS check_name, COUNT(*) AS anomaly_count
FROM (
  SELECT request_no
  FROM payment_event
  WHERE request_no IS NOT NULL AND request_no <> ''
  GROUP BY request_no
  HAVING COUNT(*) > 1
) t;

SELECT 'duplicate_payment_channel_txn_id' AS check_name, COUNT(*) AS anomaly_count
FROM (
  SELECT channel_txn_id
  FROM payment_event
  WHERE channel_txn_id IS NOT NULL AND channel_txn_id <> ''
  GROUP BY channel_txn_id
  HAVING COUNT(*) > 1
) t;

SELECT 'paid_order_without_success_payment_event' AS check_name, COUNT(*) AS anomaly_count
FROM `order` o
LEFT JOIN payment_event pe
  ON pe.order_no = o.order_no
 AND pe.event_type = 'PAY_CALLBACK'
 AND pe.status = 1
WHERE o.status = 1
  AND o.channel_txn_id IS NOT NULL
  AND pe.id IS NULL;
