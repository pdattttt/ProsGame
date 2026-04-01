package com.example.demo.model;

public class Tower {
    public TowerType type; public double x, y; public long lastAtk=0; public Monster target; public int level=1;
    public Tower(TowerType t, double x, double y){this.type=t; this.x=x; this.y=y;}
    public double dist(double ox, double oy){return Math.sqrt(Math.pow(x-ox,2)+Math.pow(y-oy,2));}
    public void upgrade() { if(level < 3) level++; }

    // BUFF: Tăng 20 sát thương mỗi cấp (cũ là 10)
    public int getDamage() {
        return type.damage + (level * 20);
    }

    // BUFF: Tăng 40 tầm bắn mỗi cấp (cũ là 20) giúp trụ bao quát bản đồ tốt hơn
    public double getRange() {
        return type.range + (level * 40);
    }

    // BUFF: Giảm hồi chiêu 0.2s mỗi cấp (cũ là 0.15s) giúp tốc độ xả đạn cực nhanh
    public double getCooldown() {
        return Math.max(0.1, type.cooldown - (level * 0.2));
    }
}