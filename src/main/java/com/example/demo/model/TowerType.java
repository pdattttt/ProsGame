package com.example.demo.model;

import javafx.scene.paint.Color;

public enum TowerType {
    ARCHER("Archer", 100, 150, 1.0, 15, Color.web("#00fff5")),
    MAGE("Mage", 150, 120, 2.0, 40, Color.web("#aa2ee6")),
    BARRACKS("Barracks", 120, 100, 0, 0, Color.web("#ff9a3c")),
    CANNON("Cannon", 200, 110, 2.5, 30, Color.web("#ff2e63")); // TRỤ MỚI: Bắn chậm, Dame to, Nổ lan

    public final String name;
    public final int cost;
    public final double range;
    public final double cooldown;
    public final int damage;
    public final Color color;

    TowerType(String name, int cost, double range, double cooldown, int damage, Color color) {
        this.name = name;
        this.cost = cost;
        this.range = range;
        this.cooldown = cooldown;
        this.damage = damage;
        this.color = color;
    }
}