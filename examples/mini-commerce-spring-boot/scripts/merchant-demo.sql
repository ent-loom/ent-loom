-- 应用启动并自动建表后，在本地演示库中执行；只补充缺失记录，不重置商家商品。
-- 每条商品都属于一个商家，名称、价格和销售状态由商家独立维护。
INSERT IGNORE INTO merchant_product (id, merchant_id, name, price, active, version) VALUES
    (101, 1001, '商家一商品一', 10.00, true, 0),
    (102, 1001, '商家一商品二', 20.00, true, 0),
    (201, 2001, '商家二商品一', 30.00, true, 0);

SELECT id, merchant_id, name, price, active, version FROM merchant_product ORDER BY id;
