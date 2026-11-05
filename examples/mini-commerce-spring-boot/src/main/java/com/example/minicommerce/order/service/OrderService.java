package com.example.minicommerce.order.service;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.example.minicommerce.order.entity.Order;
import com.example.minicommerce.order.mapper.OrderMapper;
import org.springframework.stereotype.Service;

@Service
public class OrderService extends ServiceImpl<OrderMapper, Order> { }
