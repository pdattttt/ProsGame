package com.example.demo.model;

import javafx.scene.paint.Color;

public enum MonsterType {
    GOBLIN(150, 3.5, 0, 0, Color.web("#08d9d6"), 1, 15),
    ORC(500, 1.5, 60, 0, Color.web("#2e7d32"), 2, 30),
    SHAMAN(400, 1.8, 0, 70, Color.web("#00fff5"), 3, 50),
    BOSS(5000, 1.0, 40, 40, Color.web("#ff2e63"), 10, 500);

    public final int hp; public final double speed; public final int armor;
    public final int magicResist; public final Color color; public final int cooldown; public final int reward;

    MonsterType(int h, double s, int a, int m, Color c, int cd, int reward) {
        this.hp=h; this.speed=s; this.armor=a; this.magicResist=m; this.color=c; this.cooldown=cd; this.reward=reward;
    }
}