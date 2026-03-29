package com.example.demo.model;

public class Tower {
    public TowerType type; public double x, y; public long lastAtk=0; public Monster target; public int level=1;
    public Tower(TowerType t, double x, double y){this.type=t; this.x=x; this.y=y;}
    public double dist(double ox, double oy){return Math.sqrt(Math.pow(x-ox,2)+Math.pow(y-oy,2));}
    public void upgrade() { if(level<3) level++; }
    public int getDamage() { return type.damage + (level * 10); }
    public double getRange() { return type.range + (level * 20); }
    public double getCooldown() { return Math.max(0.1, type.cooldown - (level * 0.1)); }
}