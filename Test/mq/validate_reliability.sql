-- JuPin MQ/reliable-event validation.
-- Run after timeout, duplicate callback, or MQ tests:
-- mysql -h127.0.0.1 -uroot -p jupin < Test/mq/validate_reliability.sql
--
-- Current project has payment_event but does not yet have an Outbox table.
-- The Outbox checks below are guarded by information_schema so this script can
-- run before and after the Outbox upgrade.

SELECT 'payment_event_processing_stuck_over_5_min' AS check_name, COUNT(*) AS anomaly_count
FROM payment_event
WHERE status = 0
  AND create_time < DATE_SUB(NOW(), INTERVAL 5 MINUTE);

SELECT 'payment_event_success_order_not_paid' AS check_name, COUNT(*) AS anomaly_count
FROM payment_event pe
JOIN `order` o ON o.order_no = pe.order_no
WHERE pe.event_type = 'PAY_CALLBACK'
  AND pe.status = 1
  AND o.status <> 1;

SELECT 'payment_event_ignored_order_paid' AS check_name, COUNT(*) AS anomaly_count
FROM payment_event pe
JOIN `order` o ON o.order_no = pe.order_no
WHERE pe.event_type = 'PAY_CALLBACK'
  AND pe.status = 2
  AND o.status = 1;

SELECT 'overdue_deposit_member_not_released' AS check_name, COUNT(*) AS anomaly_count
FROM `order` o
JOIN pool_member pm
  ON pm.pool_id = o.pool_id
 AND pm.user_id = o.user_id
WHERE o.type = 0
  AND o.status = 4
  AND pm.status IN (1, 2);

SET @outbox_exists := (
  SELECT COUNT(*)
  FROM information_schema.tables
  WHERE table_schema = DATABASE()
    AND table_name = 'outbox_event'
);

SET @sql := IF(
  @outbox_exists > 0,
  'SELECT ''outbox_pending_over_5_min'' AS check_name, COUNT(*) AS anomaly_count
   FROM outbox_event
   WHERE status = 0 AND create_time < DATE_SUB(NOW(), INTERVAL 5 MINUTE)',
  'SELECT ''outbox_pending_over_5_min'' AS check_name, 0 AS anomaly_count'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  @outbox_exists > 0,
  'SELECT ''outbox_publish_failed'' AS check_name, COUNT(*) AS anomaly_count
   FROM outbox_event
   WHERE status IN (2, 3)',
  'SELECT ''outbox_publish_failed'' AS check_name, 0 AS anomaly_count'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  @outbox_exists > 0,
  'SELECT ''outbox_compensation_failed'' AS check_name, COUNT(*) AS anomaly_count
   FROM outbox_event
   WHERE status = 4',
  'SELECT ''outbox_compensation_failed'' AS check_name, 0 AS anomaly_count'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  @outbox_exists > 0,
  'SELECT ''duplicate_outbox_event_key'' AS check_name, COUNT(*) AS anomaly_count
   FROM (
     SELECT event_key
     FROM outbox_event
     WHERE event_key IS NOT NULL AND event_key <> ''''
     GROUP BY event_key
     HAVING COUNT(*) > 1
   ) t',
  'SELECT ''duplicate_outbox_event_key'' AS check_name, 0 AS anomaly_count'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
