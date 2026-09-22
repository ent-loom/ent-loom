create table if not exists product (
    id bigint not null auto_increment primary key,
    name varchar(128) not null,
    price decimal(10, 2) not null,
    active boolean not null
);

create table if not exists customer (
    id bigint not null auto_increment primary key,
    display_name varchar(128) not null,
    email varchar(255) not null
);

create table if not exists commerce_order (
    id bigint not null auto_increment primary key,
    customer_id bigint not null,
    status varchar(32) not null,
    total_amount decimal(12, 2) not null,
    created_at datetime not null,
    constraint fk_commerce_order_customer foreign key (customer_id) references customer (id)
);

create table if not exists commerce_order_item (
    id bigint not null auto_increment primary key,
    order_id bigint not null,
    product_id bigint not null,
    product_name varchar(128) not null,
    unit_price decimal(10, 2) not null,
    quantity int not null,
    line_amount decimal(12, 2) not null,
    constraint fk_commerce_order_item_order foreign key (order_id) references commerce_order (id)
);
