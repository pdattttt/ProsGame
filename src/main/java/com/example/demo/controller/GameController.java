package com.example.demo.controller;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class GameController {

    @MessageMapping("/action")
    @SendTo("/topic/game-progress")
    public String handleGameAction(String actionJson) {
        // Nhận trực tiếp chuỗi JSON từ Client này và quăng y nguyên sang Client kia
        return actionJson;
    }
}