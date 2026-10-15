-- 启动应用建表后执行：按用户、商家两个方向观察同一批商家销售订单。
-- 仅补充缺失数据，不重置已有订单；销售订单按商家独立保存。
INSERT IGNORE INTO customer (id, display_name, email) VALUES
    (301, '演示用户甲', 'customer-a@example.com'),
    (302, '演示用户乙', 'customer-b@example.com');

INSERT IGNORE INTO `order` (id, customer_id, merchant_id, status, total_amount, created_at) VALUES
    (501, 301, 1001, 'CREATED', 10.00, '2026-10-07 10:00:00'),
    (502, 301, 2001, 'CREATED', 20.00, '2026-10-07 10:00:00'),
    (503, 302, 1001, 'CREATED', 30.00, '2026-10-07 10:01:00'),
    (504, 301, 1001, 'CREATED', 40.00, '2026-10-07 10:02:00'),
    (505, 302, 2001, 'CREATED', 50.00, '2026-10-07 10:03:00');

SELECT id, customer_id, merchant_id, total_amount FROM `order` ORDER BY id;
