package com.example.minicommerce.order.service;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.example.minicommerce.order.entity.CommerceOrderItem;
import com.example.minicommerce.order.mapper.CommerceOrderItemMapper;
import org.springframework.stereotype.Service;

@Service
public class CommerceOrderItemService extends ServiceImpl<CommerceOrderItemMapper, CommerceOrderItem> { }
