package com.example.minicommerce.customer.service;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.example.minicommerce.customer.entity.Customer;
import com.example.minicommerce.customer.mapper.CustomerMapper;
import org.springframework.stereotype.Service;

@Service
public class CustomerService extends ServiceImpl<CustomerMapper, Customer> { }
