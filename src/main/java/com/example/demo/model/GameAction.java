package com.example.demo.model;

public class GameAction {
    private String role;
    private String actionType;
    private String data;

    // Bắt buộc phải có constructor rỗng để thư viện Jackson chuyển đổi JSON
    public GameAction() {
    }

    public GameAction(String role, String actionType, String data) {
        this.role = role;
        this.actionType = actionType;
        this.data = data;
    }

    // Các hàm Getter và Setter
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }

    public String getData() { return data; }
    public void setData(String data) { this.data = data; }
}