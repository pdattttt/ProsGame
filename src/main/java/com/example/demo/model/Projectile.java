package com.example.demo.model;

import javafx.scene.paint.Color;

public class Projectile {
    public double sx, sy, ex, ey;
    public Color c;

    // BIẾN NÀY LÀ BẮT BUỘC PHẢI CÓ ĐỂ FIX LỖI CHỚP NHÁY VÀ TREO GAME
    public double life = 1.0;

    public Projectile(double sx, double sy, double ex, double ey, Color c) {
        this.sx = sx;
        this.sy = sy;
        this.ex = ex;
        this.ey = ey;
        this.c = c;
    }
}