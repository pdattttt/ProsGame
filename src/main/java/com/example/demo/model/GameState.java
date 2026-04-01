package com.example.demo.model;

import javafx.geometry.Point2D;
import java.util.*;

public class GameState {
    public static final int INITIAL_BASE_HP = 2000;
    public int totalWaves = 5;
    public int baseHp = INITIAL_BASE_HP;
    public int gold = 500;
    public int currentLevel = 1;
    public long restTimeRemaining = 0;

    public String difficulty = "NORMAL";
    public int enemiesKilled = 0;
    public int totalGoldEarned = 500;
    public int towersBuilt = 0;
    public int maxTowersActive = 0;
    public boolean baseDamaged = false;

    // --- BIẾN MỚI CHO ENDGAME & SHOP ---
    public int bossesKilled = 0;
    public int skillsUsed = 0;
    public long survivalTimeSeconds = 0;
    public boolean isSellMode = false; // Cờ theo dõi chế độ Bán Trụ

    public boolean isGameOver = false, isVictory = false, isPaused = false, levelStarted = false;
    public long levelStartTime = 0, lastBuildTime = 0, lastAiActionTime = 0;

    public List<Point2D> currentPath = new ArrayList<>();
    public List<Monster> monsters = new ArrayList<>();
    public List<Tower> towers = new ArrayList<>();
    public List<Projectile> projectiles = new ArrayList<>();
    public List<Soldier> soldiers = new ArrayList<>();
    public Map<MonsterType, Long> cooldowns = new HashMap<>();

    public String message = "Chọn chế độ chơi...", myRole = "NONE";
    public boolean isOfflineMode = false;
    public TowerType selectedTower = TowerType.ARCHER;

    public void resetLevelData() {
        monsters.clear(); projectiles.clear(); soldiers.clear(); towers.clear(); cooldowns.clear(); currentPath.clear();
        baseHp = INITIAL_BASE_HP;
        gold = difficulty.equals("HARD") ? 350 : (difficulty.equals("NORMAL") ? 800 : 500);

        enemiesKilled = 0; totalGoldEarned = gold; towersBuilt = 0; maxTowersActive = 0; baseDamaged = false;
        bossesKilled = 0; skillsUsed = 0; survivalTimeSeconds = 0; isSellMode = false;

        isGameOver = false; isVictory = false; isPaused = false; levelStarted = false;
        restTimeRemaining = 0; message = "Đang sẵn sàng..."; levelStartTime = System.currentTimeMillis();
    }
}