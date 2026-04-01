package com.example.demo.model;

import com.example.demo.client.NetworkManager;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class WaveManager {
    private Queue<Wave> waves = new LinkedList<>();
    public Wave currentWave;
    private long lastSpawnTime = 0;
    private long restStartTime = 0;
    public boolean isResting = true;
    public long restDurationMs = 10000;

    public WaveManager() { initWaves(5); }

    // Hỗ trợ tạo linh hoạt số lượng Wave
    public void initWaves(int totalWaves) {
        waves.clear();
        for (int i = 1; i <= totalWaves; i++) {
            generateSingleWave(i);
        }
        currentWave = waves.poll();
        isResting = true;
        restStartTime = System.currentTimeMillis();
    }

    private void generateSingleWave(int i) {
        long interval = Math.max(400, 2000 - (i * 250)); // Càng về sau ra càng nhanh
        Wave w = new Wave(i, interval);
        int numGoblins = ThreadLocalRandom.current().nextInt(5, 10 + (i * 5));
        int numOrcs = (i > 1) ? ThreadLocalRandom.current().nextInt(2, 5 + (i * 3)) : 0;
        int numShamans = (i > 2) ? ThreadLocalRandom.current().nextInt(1, 3 + (i * 2)) : 0;

        w.addMonsters(MonsterType.GOBLIN, numGoblins);
        if (numOrcs > 0) w.addMonsters(MonsterType.ORC, numOrcs);
        if (numShamans > 0) w.addMonsters(MonsterType.SHAMAN, numShamans);
        if (i % 4 == 0) w.addMonsters(MonsterType.BOSS, 1);
        if (i % 5 == 0) {
            w.addMonsters(MonsterType.BOSS, ThreadLocalRandom.current().nextInt(1, 1 + i/2));
            w.addMonsters(MonsterType.GOBLIN, 15);
        }
        List<MonsterType> tempList = new ArrayList<>(w.monstersToSpawn);
        Collections.shuffle(tempList);
        w.monstersToSpawn.clear();
        w.monstersToSpawn.addAll(tempList);
        waves.add(w);
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
            if (!currentWave.monstersToSpawn.isEmpty()) {
                if (nowMs - lastSpawnTime >= currentWave.spawnIntervalMs) {
                    MonsterType type = currentWave.monstersToSpawn.poll();
                    if (state.isOfflineMode) state.monsters.add(new Monster(type, state.currentPath, state.currentLevel));
                    else network.sendAction(state.myRole, "SPAWN", type.name());
                    lastSpawnTime = nowMs;
                }
            } else {
                if (state.monsters.isEmpty()) {
                    if (waves.isEmpty()) {
                        // CHẾ ĐỘ ENDLESS: Sinh tiếp Wave vô tận
                        if (state.difficulty.equals("ENDLESS")) {
                            generateSingleWave(state.currentLevel + 1);
                            currentWave = waves.poll();
                            isResting = true;
                            restStartTime = nowMs;
                            state.gold += ThreadLocalRandom.current().nextInt(150, 400);
                        } else {
                            state.isVictory = true;
                        }
                    } else {
                        currentWave = waves.poll();
                        isResting = true;
                        restStartTime = nowMs;
                        state.gold += ThreadLocalRandom.current().nextInt(100, 251);
                    }
                }
            }
        }
    }
}