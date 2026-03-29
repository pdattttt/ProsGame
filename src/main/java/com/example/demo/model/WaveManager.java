package com.example.demo.model;

import com.example.demo.client.NetworkManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ThreadLocalRandom;

public class WaveManager {
    private Queue<Wave> waves = new LinkedList<>();
    public Wave currentWave;
    private long lastSpawnTime = 0;
    private long restStartTime = 0;
    public boolean isResting = true;
    public long restDurationMs = 10000; // 10 giây nghỉ để xây trụ

    public WaveManager() {
        initWaves();
    }

    public void initWaves() {
        waves.clear();

        // Vòng lặp tự động tạo kịch bản ngẫu nhiên cho 5 Wave
        for (int i = 1; i <= 5; i++) {
            // Tốc độ thả quái: Wave càng cao thả càng nhanh (random từ 800ms đến 2000ms tùy level)
            long interval = Math.max(800, 2000 - (i * ThreadLocalRandom.current().nextInt(150, 300)));
            Wave w = new Wave(i, interval);

            // Công thức Random số lượng quái tăng dần theo từng Wave
            int numGoblins = ThreadLocalRandom.current().nextInt(5, 10 + (i * 5));
            int numOrcs = (i > 1) ? ThreadLocalRandom.current().nextInt(2, 5 + (i * 3)) : 0;
            int numShamans = (i > 2) ? ThreadLocalRandom.current().nextInt(1, 3 + (i * 2)) : 0;

            w.addMonsters(MonsterType.GOBLIN, numGoblins);
            if (numOrcs > 0) w.addMonsters(MonsterType.ORC, numOrcs);
            if (numShamans > 0) w.addMonsters(MonsterType.SHAMAN, numShamans);

            // KỊCH BẢN ĐẶC BIỆT CHO WAVE 4 VÀ 5
            if (i == 4 && ThreadLocalRandom.current().nextBoolean()) {
                // Wave 4 có 50% tỷ lệ xuất hiện 1 con Mini-Boss xui xẻo
                w.addMonsters(MonsterType.BOSS, 1);
            }
            if (i == 5) {
                // Wave 5: Chắc chắn có Boss (Random từ 1 đến 3 con) + 1 bầy Goblin cảm tử
                int numBosses = ThreadLocalRandom.current().nextInt(1, 4);
                w.addMonsters(MonsterType.BOSS, numBosses);
                w.addMonsters(MonsterType.GOBLIN, 15);
            }

            // XÀO BÀI (SHUFFLE): Trộn lộn xộn các loại quái với nhau cho kịch tính
            List<MonsterType> tempList = new ArrayList<>(w.monstersToSpawn);
            Collections.shuffle(tempList); // Xáo trộn danh sách
            w.monstersToSpawn.clear();
            w.monstersToSpawn.addAll(tempList); // Nạp lại vào hàng đợi

            waves.add(w);
        }

        currentWave = waves.poll();
        isResting = true;
        restStartTime = System.currentTimeMillis();
    }

    public void update(GameState state, NetworkManager network, long nowMs) {
        if (state.isGameOver || state.isVictory) return;

        if (isResting) {
            long elapsedRest = nowMs - restStartTime;
            state.restTimeRemaining = Math.max(0, (restDurationMs - elapsedRest) / 1000);
            state.message = "Chuẩn bị Wave " + currentWave.waveIndex + " sau " + state.restTimeRemaining + "s";

            if (elapsedRest >= restDurationMs) {
                isResting = false;
                state.currentLevel = currentWave.waveIndex;
                state.message = "WAVE " + currentWave.waveIndex + " STARTED!";
            }
        } else {
            // Đang tiến công
            if (!currentWave.monstersToSpawn.isEmpty()) {
                if (nowMs - lastSpawnTime >= currentWave.spawnIntervalMs) {
                    MonsterType type = currentWave.monstersToSpawn.poll();
                    if (state.isOfflineMode) {
                        state.monsters.add(new Monster(type, state.currentPath));
                    } else {
                        network.sendAction(state.myRole, "SPAWN", type.name());
                    }
                    lastSpawnTime = nowMs;
                }
            } else {
                // Đã thả hết quái của Wave này, chờ dọn sạch bản đồ
                if (state.monsters.isEmpty()) {
                    if (waves.isEmpty()) {
                        state.isVictory = true;
                    } else {
                        currentWave = waves.poll();
                        isResting = true;
                        restStartTime = nowMs;
                        // Thưởng tiền qua ải ngẫu nhiên từ 100 đến 250 vàng
                        state.gold += ThreadLocalRandom.current().nextInt(100, 251);
                    }
                }
            }
        }
    }
}