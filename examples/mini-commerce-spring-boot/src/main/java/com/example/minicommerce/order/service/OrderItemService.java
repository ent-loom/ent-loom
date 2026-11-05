package com.example.minicommerce.order.service;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.example.minicommerce.order.entity.OrderItem;
import com.example.minicommerce.order.mapper.OrderItemMapper;
import org.springframework.stereotype.Service;

@Service
public class OrderItemService extends ServiceImpl<OrderItemMapper, OrderItem> { }
