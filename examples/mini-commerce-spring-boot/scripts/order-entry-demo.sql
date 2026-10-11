-- 启动应用建表后执行：按用户、商家两个方向观察同一批商家销售订单。
-- 仅补充缺失数据，不重置已有结算单或销售订单；销售订单按商家独立保存。
INSERT IGNORE INTO customer (id, display_name, email) VALUES
    (301, '演示用户甲', 'customer-a@example.com'),
    (302, '演示用户乙', 'customer-b@example.com');

INSERT IGNORE INTO `order` (id, customer_id, status, total_amount, created_at) VALUES
    (9001, 301, 'CREATED', 30.00, '2026-10-07 10:00:00'),
    (9002, 302, 'CREATED', 30.00, '2026-10-07 10:01:00'),
    (9003, 301, 'CREATED', 40.00, '2026-10-07 10:02:00'),
    (9004, 302, 'CREATED', 50.00, '2026-10-07 10:03:00');

INSERT IGNORE INTO merchant_order (id, order_id, customer_id, merchant_id, status, total_amount, created_at) VALUES
    (501, 9001, 301, 1001, 'CREATED', 10.00, '2026-10-07 10:00:00'),
    (502, 9001, 301, 2001, 'CREATED', 20.00, '2026-10-07 10:00:00'),
    (503, 9002, 302, 1001, 'CREATED', 30.00, '2026-10-07 10:01:00'),
    (504, 9003, 301, 1001, 'CREATED', 40.00, '2026-10-07 10:02:00'),
    (505, 9004, 302, 2001, 'CREATED', 50.00, '2026-10-07 10:03:00');

SELECT id, order_id, customer_id, merchant_id, total_amount FROM merchant_order ORDER BY id;
