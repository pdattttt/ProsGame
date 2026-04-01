package com.example.demo.model;

public class Soldier {
    public double x,y; public int hp=100; public int level=1; public long lastAtk=0;
    public Monster engaging=null; public Tower owner;
    public Soldier(double x, double y, Tower t){this.x=x; this.y=y; this.owner=t; this.level=t.level;}
}