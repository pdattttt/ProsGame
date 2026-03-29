package com.example.demo.model;

import javafx.geometry.Point2D;
import java.util.List;

public class Monster {
    // --- THÔNG SỐ CƠ BẢN CỦA QUÁI ---
    public MonsterType type;
    public double x, y;
    public int hp, pathIdx = 0;
    public boolean finished = false;
    public long lastAtk = 0;
    public Soldier engaging = null;

    // --- THÔNG SỐ HIỆU ỨNG (MỚI THÊM) ---
    public int frozenTimer = 0;
    public int burnTimer = 0;

    // --- HÀM KHỞI TẠO ---
    public Monster(MonsterType t, List<Point2D> p) {
        this.type = t;
        this.hp = t.hp;
        this.x = p.get(0).getX();
        this.y = p.get(0).getY();
    }

    // --- HÀM TÍNH KHOẢNG CÁCH ---
    public double dist(double ox, double oy) {
        return Math.sqrt(Math.pow(x - ox, 2) + Math.pow(y - oy, 2));
    }

    // --- HÀM NHẬN SÁT THƯƠNG ---
    public void takeDamage(int d, String dmgType) {
        double m = 1.0;
        if (dmgType.equals("PHYSICAL")) m = (100 - this.type.armor) / 100.0;
        if (dmgType.equals("MAGIC")) m = (100 - this.type.magicResist) / 100.0;
        this.hp -= d * m;
    }
}