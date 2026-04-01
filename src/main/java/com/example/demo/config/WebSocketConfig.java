package com.example.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Cấu hình tiền tố cho tin nhắn gửi từ Server ra Client
        config.enableSimpleBroker("/topic");
        // Cấu hình tiền tố cho tin nhắn Client gửi lên Server
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Mở cổng /ws-game cho Client kết nối vào
        // setAllowedOriginPatterns("*") cực kỳ quan trọng để cho phép nhiều cửa sổ cùng kết nối
        registry.addEndpoint("/ws-game").setAllowedOriginPatterns("*");
    }
}