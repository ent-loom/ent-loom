-- H2 业务测试固定表结构，不冒充 MySQL 字符集及 DDL 验收。
CREATE TABLE IF NOT EXISTS customer (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    display_name VARCHAR(200),
    email VARCHAR(200)
);
CREATE TABLE IF NOT EXISTS customer_bank_card (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT,
    bank_name VARCHAR(200),
    card_last_four VARCHAR(200),
    binding_token VARCHAR(200)
);
CREATE TABLE IF NOT EXISTS customer_level (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    merchant_id BIGINT,
    customer_id BIGINT,
    level VARCHAR(200)
);
CREATE TABLE IF NOT EXISTS merchant_article (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    merchant_id BIGINT,
    title VARCHAR(200),
    content CLOB
);
CREATE TABLE IF NOT EXISTS merchant_product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    merchant_id BIGINT,
    name VARCHAR(200),
    price DECIMAL(20,6),
    active BOOLEAN,
    version BIGINT
);
CREATE TABLE IF NOT EXISTS `order` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT,
    merchant_id BIGINT,
    status VARCHAR(64),
    total_amount DECIMAL(20,6),
    created_at TIMESTAMP
);
CREATE TABLE IF NOT EXISTS order_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT,
    merchant_product_id BIGINT,
    product_name VARCHAR(200),
    unit_price DECIMAL(20,6),
    quantity INT,
    line_amount DECIMAL(20,6)
);
