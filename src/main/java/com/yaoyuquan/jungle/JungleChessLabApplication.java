package com.yaoyuquan.jungle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 斗兽棋 AI 服务启动类。
 * <p>
 * 本服务是无状态的：对局状态、规则判定全部由前端持有，后端只负责按棋手人设调用大模型选出一步棋。
 *
 * @author yaoyuquan
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class JungleChessLabApplication {

    public static void main(String[] args) {
        SpringApplication.run(JungleChessLabApplication.class, args);
    }
}
