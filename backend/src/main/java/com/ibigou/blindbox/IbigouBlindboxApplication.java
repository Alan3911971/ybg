package com.ibigou.blindbox;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 宜必购盲盒优惠券 & 余额系统 启动类。
 *
 * <p>需求规格说明书 V1.4：全部端均为 H5（顾客 / 商家 / 平台），
 * 本服务同时提供三端 API 与静态 H5 资源。</p>
 */
@SpringBootApplication
@EnableScheduling
public class IbigouBlindboxApplication {

    public static void main(String[] args) {
        SpringApplication.run(IbigouBlindboxApplication.class, args);
    }
}
