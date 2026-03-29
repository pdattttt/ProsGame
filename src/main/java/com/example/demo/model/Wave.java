package com.example.demo.model;

import java.util.LinkedList;
import java.util.Queue;

public class Wave {
    public int waveIndex;
    public Queue<MonsterType> monstersToSpawn = new LinkedList<>();
    public long spawnIntervalMs; // Thời gian giãn cách giữa 2 con quái (milliseconds)

    public Wave(int index, long interval) {
        this.waveIndex = index;
        this.spawnIntervalMs = interval;
    }

    // Hàm tiện ích để thêm nhiều quái cùng lúc vào hàng đợi
    public void addMonsters(MonsterType type, int count) {
        for (int i = 0; i < count; i++) {
            monstersToSpawn.add(type);
        }
    }
}