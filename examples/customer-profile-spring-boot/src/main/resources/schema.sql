create table if not exists customer_profile (
    id bigint not null primary key,
    display_name varchar(128) not null,
    email varchar(255) not null
);
