package com.example.demo.model;

import javafx.geometry.Point2D;
import java.util.List;

public class Monster {
    public MonsterType type;
    public double x, y;
    public int hp, maxHp, pathIdx = 0; // Thêm maxHp để thanh máu vẽ chuẩn
    public double currentSpeed;
    public boolean finished = false;
    public long lastAtk = 0;
    public Soldier engaging = null;

    public int frozenTimer = 0;
    public int burnTimer = 0;

    // Cập nhật hàm khởi tạo nhận thêm 'level'
    public Monster(MonsterType t, List<Point2D> p, int level) {
        this.type = t;
        // GIẢM SỨC MẠNH: Chỉ buff 20% máu mỗi level (bản cũ là 40%)
        this.maxHp = (int)(t.hp * (1 + (level - 1) * 0.2));
        this.hp = this.maxHp; // Máu hiện tại = Máu max -> HẾT LỖI TỤT MÁU

        // GIẢM SỨC MẠNH: Chỉ buff 5% tốc độ mỗi level (bản cũ là 15%)
        this.currentSpeed = t.speed * (1 + (level - 1) * 0.05);

        this.x = p.get(0).getX();
        this.y = p.get(0).getY();
    }

    public double dist(double ox, double oy) {
        return Math.sqrt(Math.pow(x - ox, 2) + Math.pow(y - oy, 2));
    }

    public void takeDamage(int d, String dmgType) {
        double m = 1.0;
        if (dmgType.equals("PHYSICAL")) m = (100 - this.type.armor) / 100.0;
        if (dmgType.equals("MAGIC")) m = (100 - this.type.magicResist) / 100.0;
        this.hp -= d * m;
    }
}