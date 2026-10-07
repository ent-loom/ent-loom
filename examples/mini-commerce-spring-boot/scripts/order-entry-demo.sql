-- 启动应用建表后执行：按用户、商家两个方向观察同一批商家子订单。
-- 仅补充缺失数据，不重置已有主订单或子订单；当前下单 Handler 尚未自动拆单。
INSERT IGNORE INTO customer (id, display_name, email) VALUES
    (301, '演示用户甲', 'customer-a@example.com'),
    (302, '演示用户乙', 'customer-b@example.com');

INSERT IGNORE INTO `order` (id, customer_id, status, total_amount, created_at) VALUES
    (9001, 301, 'CREATED', 30.00, '2026-10-07 10:00:00'),
    (9002, 302, 'CREATED', 30.00, '2026-10-07 10:01:00'),
    (9003, 301, 'CREATED', 40.00, '2026-10-07 10:02:00'),
    (9004, 302, 'CREATED', 50.00, '2026-10-07 10:03:00');

INSERT IGNORE INTO merchant_order (id, order_id, customer_id, merchant_id, total_amount) VALUES
    (501, 9001, 301, 1001, 10.00),
    (502, 9001, 301, 2001, 20.00),
    (503, 9002, 302, 1001, 30.00),
    (504, 9003, 301, 1001, 40.00),
    (505, 9004, 302, 2001, 50.00);

SELECT id, order_id, customer_id, merchant_id, total_amount FROM merchant_order ORDER BY id;
