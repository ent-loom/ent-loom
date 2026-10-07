-- 应用启动并自动建表后，在本地演示库中执行；只补充缺失记录，不重置已有价格或版本。
-- 主数据价格与商家报价独立；原下单演示仍使用 product.price。
INSERT IGNORE INTO product (id, name, price, active) VALUES
    (900010, '商家演示商品一', 10.00, true),
    (900020, '商家演示商品二', 20.00, true);

INSERT IGNORE INTO merchant_product (id, product_id, merchant_id, price, version) VALUES
    (101, 900010, 1001, 10.00, 0),
    (102, 900020, 1001, 20.00, 0),
    (201, 900010, 2001, 30.00, 0);

SELECT id, product_id, merchant_id, price, version FROM merchant_product ORDER BY id;
