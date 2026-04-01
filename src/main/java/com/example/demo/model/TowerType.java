package com.example.demo.model;

import javafx.scene.paint.Color;

public enum TowerType {
    // BUFF: Tăng sát thương (damage) và Tầm bắn (range) cơ bản
    ARCHER("Archer", 100, 180, 0.5, 25, Color.web("#00fff5")),   // Sát thương 15 -> 25, Tầm bắn 150 -> 180
    MAGE("Mage", 150, 150, 1.2, 65, Color.web("#aa2ee6")),       // Sát thương 40 -> 65, Tầm bắn 120 -> 150
    BARRACKS("Barracks", 120, 100, 0, 0, Color.web("#ff9a3c")),
    CANNON("Cannon", 200, 140, 1.5, 55, Color.web("#ff2e63"));   // Sát thương 30 -> 55, Tầm bắn 110 -> 140

    public final String name;
    public final int cost;
    public final double range;
    public final double cooldown;
    public final int damage;
    public final Color color;

    TowerType(String name, int cost, double range, double cooldown, int damage, Color color) {
        this.name = name; this.cost = cost; this.range = range;
        this.cooldown = cooldown; this.damage = damage; this.color = color;
    }
}