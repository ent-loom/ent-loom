-- 启动应用建表后执行：按用户、商家两个方向观察同一批商家销售订单。
-- 仅补充缺失数据，不重置已有订单；每笔订单对应一个客户和一个商家。
INSERT IGNORE INTO customer (id, display_name, email) VALUES
    (301, '演示用户甲', 'customer-a@example.com'),
    (302, '演示用户乙', 'customer-b@example.com');

INSERT IGNORE INTO `order` (id, customer_id, merchant_id, status, total_amount, created_at) VALUES
    (501, 301, 1001, 'CREATED', 10.00, '2026-10-07 10:00:00'),
    (502, 301, 2001, 'CREATED', 20.00, '2026-10-07 10:00:00'),
    (503, 302, 1001, 'CREATED', 30.00, '2026-10-07 10:01:00'),
    (504, 301, 1001, 'CREATED', 40.00, '2026-10-07 10:02:00'),
    (505, 302, 2001, 'CREATED', 50.00, '2026-10-07 10:03:00');

-- 为订单详情和一对多分页提供可观察的成交快照。
INSERT IGNORE INTO order_item (id, order_id, merchant_product_id, product_name, unit_price, quantity, line_amount) VALUES
    (601, 501, 101, '快照商品甲', 2.00, 2, 4.00),
    (602, 501, 102, '快照商品乙', 3.00, 2, 6.00),
    (603, 502, 201, '另一商家商品', 20.00, 1, 20.00),
    (604, 503, 103, '另一客户商品', 30.00, 1, 30.00);

SELECT id, customer_id, merchant_id, total_amount FROM `order` ORDER BY id;
