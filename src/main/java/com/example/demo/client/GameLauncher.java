package com.example.demo.client;

public class GameLauncher {
    public static void main(String[] args) {
        // Gọi hàm main của PCGameClient từ đây để tránh lỗi thiếu JavaFX Module
        PCGameClient.main(args);
    }
}