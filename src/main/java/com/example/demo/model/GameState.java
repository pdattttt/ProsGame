package com.example.demo.model;

import javafx.geometry.Point2D;
import java.util.*;

public class GameState {
    public static final int INITIAL_BASE_HP = 2000;
    public static final int STARTING_GOLD = 300;
    public long restTimeRemaining = 0;
    public int totalWaves = 5;
    public int baseHp = INITIAL_BASE_HP;
    public int gold = STARTING_GOLD;
    public int currentLevel = 1;

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
        monsters.clear(); projectiles.clear(); soldiers.clear(); towers.clear();
        baseHp = INITIAL_BASE_HP; gold = STARTING_GOLD;
        isGameOver = false; isVictory = false; isPaused = false;
    }
}