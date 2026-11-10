package com.gba.merchantbao.config;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@MapperScan(basePackages = {"com.gba.merchantbao.legacy.mapper", "com.gba.merchantbao.auth.mapper", "com.gba.merchantbao.chat.mapper", "com.gba.merchantbao.product.mapper"},
        annotationClass = Mapper.class)
public class MyBatisConfiguration {
}
