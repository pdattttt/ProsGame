package com.example.demo.client;

import com.example.demo.model.*;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.canvas.*;
import javafx.scene.control.*;
import javafx.scene.effect.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.*;
import javafx.stage.Stage;
import javafx.scene.media.*;
import javafx.util.Duration;

import java.io.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class PCGameClient extends Application {

    private static final int WIDTH = 1125, HEIGHT = 750;
    private static final int LEVEL_DURATION_SECONDS = 180;
    private static final long BUILD_COOLDOWN_MS = 1500;

    private static final Color BG_DARK = Color.web("#0b0b1a"), BG_LIGHT = Color.web("#1a1a2e");
    private static final Color NEON_RED = Color.web("#ff2e63"), NEON_GREEN = Color.web("#08d9d6");
    private static final Color NEON_CYAN = Color.web("#00fff5"), NEON_PURPLE = Color.web("#aa2ee6");
    private static final Color NEON_ORANGE = Color.web("#ff9a3c");

    private GameState state = new GameState();
    private WaveManager waveManager = new WaveManager();
    private NetworkManager network;
    private GraphicsContext gc;

    private StackPane rootPane;
    private HBox topHud, bottomPanel;
    private VBox menuOverlay;

    private Label lblTime, lblCore, lblGold, lblMessage, lblQuest;
    private ProgressBar buildBar;
    private Map<TowerType, Button> shopButtons = new HashMap<>();
    private Map<MonsterType, Button> attackerButtons = new HashMap<>();
    private Button btnSell;

    private double animationTime = 0;
    private double mouseX = -1, mouseY = -1;

    private List<DamageText> damageTexts = new ArrayList<>();
    private List<Particle> particles = new ArrayList<>();
    private double bombFlashAlpha = 0, screenShake = 0;

    private MediaPlayer homeBgmPlayer, gameBgmPlayer, endgameBgmPlayer, victoryBgmPlayer;
    private String currentBgm = "";

    private Map<String, Image> imageCache = new HashMap<>();
    private Map<String, AudioClip> soundCache = new HashMap<>();
    private Map<String, Integer> spriteFrames = new HashMap<>();
    // --- HỆ THỐNG SAVE / LOAD HIGH SCORE ---
    private int bestEndlessWave = 0;
    private int maxKillsRecord = 0;
    private long fastestClearTime = 999999;
    private final String SCORE_FILE = "highscore.properties";

    private class DamageText {
        double x, y; String text; Color color; double life = 1.0;
        DamageText(double x, double y, String text, Color color) {
            this.x = x + ThreadLocalRandom.current().nextDouble(-15, 15);
            this.y = y + ThreadLocalRandom.current().nextDouble(-15, 0);
            this.text = text; this.color = color;
        }
    }

    private class Particle {
        double x, y, vx, vy, life, maxLife, size; Color color; boolean isRipple;
        Particle(double x, double y, Color c, boolean isRipple) {
            this.x = x; this.y = y; this.color = c; this.isRipple = isRipple;
            if (isRipple) { this.size = 5; this.maxLife = this.life = 0.5; this.vx = 0; this.vy = 0; }
            else {
                double angle = Math.random() * Math.PI * 2; double speed = Math.random() * 4 + 1;
                this.vx = Math.cos(angle) * speed; this.vy = Math.sin(angle) * speed;
                this.maxLife = this.life = Math.random() * 0.4 + 0.2; this.size = Math.random() * 4 + 2;
            }
        }
    }

    public static void main(String[] args) { launch(args); }

    private void loadScores() {
        try (FileReader reader = new FileReader(SCORE_FILE)) {
            Properties p = new Properties(); p.load(reader);
            bestEndlessWave = Integer.parseInt(p.getProperty("bestEndlessWave", "0"));
            maxKillsRecord = Integer.parseInt(p.getProperty("maxKillsRecord", "0"));
            fastestClearTime = Long.parseLong(p.getProperty("fastestClearTime", "999999"));
        } catch (Exception e) { System.out.println("Tạo file HighScore mới."); }
    }

    private void saveScores() {
        try (FileWriter writer = new FileWriter(SCORE_FILE)) {
            Properties p = new Properties();
            p.setProperty("bestEndlessWave", String.valueOf(bestEndlessWave));
            p.setProperty("maxKillsRecord", String.valueOf(maxKillsRecord));
            p.setProperty("fastestClearTime", String.valueOf(fastestClearTime));
            p.store(writer, "Neon Defense High Scores");
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void loadResources() {

        String[] imgNames = { "tower_archer", "archer_shoot",
                "tower_mage", "mage_shoot",
                "tower_barracks",
                "tower_cannon", "cannon_shoot",
                "monster_goblin", "monster_orc", "monster_shaman", "monster_boss",
                "base_core", "bg_neon", "map_1", "map_2", "map_3", "Arrow", "map_2", "map_3" };
        spriteFrames.put("tower_mage", 6);
        spriteFrames.put("mage_shoot", 11);
        spriteFrames.put("tower_archer", 6);
        spriteFrames.put("archer_shoot", 8);
        spriteFrames.put("tower_barracks", 1);
        spriteFrames.put("tower_cannon", 8);
        spriteFrames.put("cannon_shoot", 4);
        spriteFrames.put("monster_goblin", 6);
        spriteFrames.put("monster_orc", 6);
        spriteFrames.put("monster_shaman", 4);
        spriteFrames.put("monster_boss", 6);
        spriteFrames.put("base_core", 1);
        spriteFrames.put("bg_neon", 1);
        spriteFrames.put("arrow", 1);
        for (String name : imgNames) {
            try {
                Image img = new Image(getClass().getResourceAsStream("/static/images/" + name + ".png"));
                if (img != null && !img.isError()) imageCache.put(name, img);
            } catch (Exception e) {}
        }
        try { Image mapImg = new Image(getClass().getResourceAsStream("/static/images/map_1.jpg")); if (!mapImg.isError()) imageCache.put("map_1", mapImg); } catch (Exception e) {}

        String[] soundNames = { "build", "shoot", "laser", "cannon", "coin", "base_hit", "freeze", "bomb" };
        for (String name : soundNames) {
            try { AudioClip clip = new AudioClip(getClass().getResource("/static/sounds/" + name + ".wav").toExternalForm()); soundCache.put(name, clip); } catch (Exception e) {}
        }
        try { homeBgmPlayer = new MediaPlayer(new Media(getClass().getResource("/static/sounds/homesound.wav").toExternalForm())); homeBgmPlayer.setCycleCount(MediaPlayer.INDEFINITE); homeBgmPlayer.setVolume(0.3); } catch (Exception e) {}
        try { gameBgmPlayer = new MediaPlayer(new Media(getClass().getResource("/static/sounds/gamesound.wav").toExternalForm())); gameBgmPlayer.setCycleCount(MediaPlayer.INDEFINITE); gameBgmPlayer.setVolume(0.25); } catch (Exception e) {}
        try { endgameBgmPlayer = new MediaPlayer(new Media(getClass().getResource("/static/sounds/untilendgame.wav").toExternalForm())); endgameBgmPlayer.setCycleCount(MediaPlayer.INDEFINITE); endgameBgmPlayer.setVolume(0.25); } catch (Exception e) {}
        try { victoryBgmPlayer = new MediaPlayer(new Media(getClass().getResource("/static/sounds/victory.wav").toExternalForm())); victoryBgmPlayer.setCycleCount(1); victoryBgmPlayer.setVolume(0.4); } catch (Exception e) {}
    }

    private void playBgm(String type) {
        if (currentBgm.equals(type)) return; currentBgm = type;
        if (homeBgmPlayer != null) homeBgmPlayer.stop(); if (gameBgmPlayer != null) gameBgmPlayer.stop();
        if (endgameBgmPlayer != null) endgameBgmPlayer.stop(); if (victoryBgmPlayer != null) victoryBgmPlayer.stop();
        if ("HOME".equals(type) && homeBgmPlayer != null) homeBgmPlayer.play();
        else if ("GAME".equals(type) && gameBgmPlayer != null) gameBgmPlayer.play();
        else if ("ENDGAME".equals(type) && endgameBgmPlayer != null) endgameBgmPlayer.play();
        else if ("VICTORY".equals(type) && victoryBgmPlayer != null) victoryBgmPlayer.play();
    }
    private void playSound(String name) { AudioClip clip = soundCache.get(name); if (clip != null) clip.play(0.4); }

    private void spawnExplosion(double x, double y, Color c, int count) {
        particles.add(new Particle(x, y, Color.WHITE, true));
        for (int i = 0; i < count; i++) particles.add(new Particle(x, y, c, false));
    }

    @Override
    public void start(Stage stage) {
        loadScores();
        loadResources();
        rootPane = new StackPane();
        Canvas canvas = new Canvas(WIDTH, HEIGHT); gc = canvas.getGraphicsContext2D();

        canvas.setOnMouseClicked(e -> {
            if ("DEFENDER".equals(state.myRole) && !state.isGameOver && !state.isPaused && !state.isVictory) {
                if (e.getButton() == MouseButton.PRIMARY) {
                    Tower existing = state.towers.stream().filter(t -> t.dist(e.getX(), e.getY()) < 40).findFirst().orElse(null);

                    if (state.isSellMode) {
                        if (existing != null) sellTower(existing);
                        else { state.isSellMode = false; state.message = "Đã thoát chế độ Bán Trụ."; }
                    } else {
                        if (existing != null || isValidBuildSpot(e.getX(), e.getY())) handleBuild(e.getX(), e.getY());
                    }
                } else if (e.getButton() == MouseButton.SECONDARY) {
                    state.isSellMode = false;
                    state.selectedTower = state.selectedTower==TowerType.ARCHER ? TowerType.MAGE : state.selectedTower==TowerType.MAGE ? TowerType.BARRACKS : state.selectedTower==TowerType.BARRACKS ? TowerType.CANNON : TowerType.ARCHER;
                }
            }
        });

        canvas.setOnMouseMoved(e -> { mouseX = e.getX(); mouseY = e.getY(); });
        canvas.setOnMouseExited(e -> { mouseX = -1; mouseY = -1; });

        rootPane.getChildren().add(canvas);
        setupGameHUD(); showMainMenu();

        network = new NetworkManager(this::processAction, msg -> Platform.runLater(() -> state.message = msg), () -> { if (!state.isGameOver && !state.isVictory) togglePause(); });

        new AnimationTimer() {
            public void handle(long now) {
                if (!state.isPaused) {
                    animationTime += 0.05;
                    if (state.levelStarted && !state.isGameOver && !state.isVictory) {
                        updateLogic(now);
                        if (state.isOfflineMode && "DEFENDER".equals(state.myRole)) waveManager.update(state, network, System.currentTimeMillis());
                        else if (state.isOfflineMode && "ATTACKER".equals(state.myRole)) updateAI(now);

                        // CHẶN TREO GAME: Gọi bảng tổng kết ngay khi win/thua
                        if (state.isVictory) handleVictory();
                        else if (state.isGameOver) handleDefeat();
                    }
                }
                render(); updateInterface();
            }
        }.start();

        stage.setTitle("NEON DEFENSE PRO"); stage.setScene(new Scene(rootPane, WIDTH, HEIGHT)); stage.show();
    }
    private void drawAnimatedSprite(Image img, int totalFrames, double x, double y, double size, double speedModifier, boolean isFlipped) {
        if (totalFrames <= 0) totalFrames = 1;

        // Chiều rộng thực của MỘT khung hình đơn lẻ
        double frameWidth = img.getWidth() / totalFrames;
        double frameHeight = img.getHeight();

        // Tính toán khung hình hiện tại dựa trên thời gian
        int currentFrame = (int) ((animationTime * speedModifier) % totalFrames);
        double sourceX = currentFrame * frameWidth;

        // Tính toán kích thước vẽ để giữ đúng tỉ lệ ảnh (không bị bóp méo)
        double aspectRatio = frameWidth / frameHeight;
        double drawW = size;
        double drawH = size / aspectRatio;

        gc.save();
        if (isFlipped) {
            gc.translate(x, y);
            gc.scale(-1, 1);
            gc.translate(-x, -y);
        }

        // Vẽ chính xác khung hình đã cắt
        gc.drawImage(img, sourceX, 0, frameWidth, frameHeight, x - drawW / 2, y - drawH / 2, drawW, drawH);
        gc.restore();
    }
    private void sellTower(Tower t) {
        int refund = (t.type.cost + (t.level-1)*50) / 2;
        state.gold += refund;
        state.towers.remove(t);
        if (t.type == TowerType.BARRACKS) state.soldiers.removeIf(s -> s.owner == t);
        playSound("coin");
        damageTexts.add(new DamageText(t.x, t.y, "+$" + refund, Color.YELLOW));
        state.message = "Đã bán " + t.type.name() + " (+$" + refund + ")";
        state.isSellMode = false;
        if (!state.isOfflineMode) network.sendAction(state.myRole, "SELL", t.x + "," + t.y);
    }

    private void repairCore() {
        if (state.gold >= 200 && state.baseHp < GameState.INITIAL_BASE_HP) {
            state.gold -= 200;
            int heal = Math.min(500, GameState.INITIAL_BASE_HP - state.baseHp);
            state.baseHp += heal;
            state.skillsUsed++;
            playSound("build");
            damageTexts.add(new DamageText(WIDTH-120, HEIGHT/2, "+" + heal + " HP", NEON_GREEN));
            if (!state.isOfflineMode) {
                network.sendAction(state.myRole, "SYNC_HP", String.valueOf(state.baseHp));
                network.sendAction(state.myRole, "SYNC_GOLD", String.valueOf(state.gold));
            }
        } else if (state.baseHp >= GameState.INITIAL_BASE_HP) {
            state.message = "NHÀ CHÍNH ĐANG ĐẦY MÁU!";
        } else {
            state.message = "KHÔNG ĐỦ TIỀN ĐỂ SỬA CHỮA!";
        }
    }

    // ================= MENUS =================
    private void showMainMenu() {
        playBgm("HOME");
        if (topHud != null) topHud.setVisible(false);
        rootPane.getChildren().removeIf(node -> node instanceof VBox || (node instanceof HBox && node != topHud));
        VBox menu = new VBox(20); menu.setAlignment(Pos.CENTER); menu.setStyle("-fx-background-color: rgba(0,0,0,0.95);");
        Label title = new Label("NEON KINGDOM"); title.setTextFill(NEON_CYAN); title.setFont(Font.font("Impact", 80)); title.setEffect(new DropShadow(20, NEON_CYAN));

        Button btnStart = createStyledButton("▶ START GAME", NEON_GREEN); btnStart.setPrefWidth(300); btnStart.setPrefHeight(50);
        btnStart.setOnAction(e -> showRoleSelection());

        Button btnTutorial = createStyledButton("📖 HOW TO PLAY", NEON_ORANGE); btnTutorial.setPrefWidth(300); btnTutorial.setPrefHeight(50);
        btnTutorial.setOnAction(e -> showTutorialMenu());

        Button btnExit = createStyledButton("✖ EXIT GAME", NEON_RED); btnExit.setPrefWidth(300); btnExit.setPrefHeight(50);
        btnExit.setOnAction(e -> Platform.exit());

        menu.getChildren().addAll(title, btnStart, btnTutorial, btnExit); rootPane.getChildren().add(menu);
    }

    private void showTutorialMenu() {
        rootPane.getChildren().removeIf(node -> node instanceof VBox || (node instanceof HBox && node != topHud));
        VBox menu = new VBox(15); menu.setAlignment(Pos.CENTER); menu.setStyle("-fx-background-color: rgba(0,0,0,0.95);");
        Label title = new Label("HƯỚNG DẪN CHƠI"); title.setTextFill(NEON_CYAN); title.setFont(Font.font("Impact", 50));

        Label t1 = createHUDLabel("🎯 MỤC TIÊU: Bảo vệ Nhà Chính (Core) khỏi quái vật.", Color.WHITE);
        Label t2 = createHUDLabel("🏗️ XÂY TRỤ: Chọn trụ ở Shop bên dưới, click vào bản đồ để xây.", Color.WHITE);
        Label t3 = createHUDLabel("⭐ NÂNG CẤP: Click vào trụ đã xây để nâng cấp (Max Lv.3).", Color.WHITE);
        Label t4 = createHUDLabel("💰 BÁN TRỤ: Click nút SELL, sau đó click vào trụ để gỡ vốn 50%.", Color.WHITE);
        Label t5 = createHUDLabel("⚡ KỸ NĂNG: Mua Freeze/Bomb trong Shop khi nguy cấp.", Color.WHITE);

        Button btnBack = createStyledButton("<< BACK TO MENU", Color.GRAY); btnBack.setOnAction(e -> showMainMenu());
        menu.getChildren().addAll(title, t1, t2, t3, t4, t5, new Label(""), btnBack); rootPane.getChildren().add(menu);
    }

    private void showRoleSelection() {
        rootPane.getChildren().removeIf(node -> node instanceof VBox || (node instanceof HBox && node != topHud));
        VBox menu = new VBox(20); menu.setAlignment(Pos.CENTER); menu.setStyle("-fx-background-color: rgba(0,0,0,0.9);");
        Label title = new Label("CHOOSE YOUR PATH"); title.setTextFill(NEON_CYAN); title.setFont(Font.font("Impact", 50));

        HBox offlineBox = new HBox(20); offlineBox.setAlignment(Pos.CENTER);
        Button btnOffDef = createStyledButton("OFFLINE: DEFENDER", NEON_GREEN);
        btnOffDef.setOnAction(e -> showDifficultySelection());
        Button btnOffAtk = createStyledButton("OFFLINE: ATTACKER", NEON_ORANGE);
        btnOffAtk.setOnAction(e -> { state.difficulty = "NORMAL"; startGame(true, "ATTACKER"); });
        offlineBox.getChildren().addAll(btnOffDef, btnOffAtk);

        HBox onlineBox = new HBox(20); onlineBox.setAlignment(Pos.CENTER);
        Button btnDef = createStyledButton("ONLINE: DEFENDER", NEON_PURPLE);
        btnDef.setOnAction(e -> { state.difficulty = "NORMAL"; startGame(false, "DEFENDER"); });
        Button btnAtk = createStyledButton("ONLINE: ATTACKER", NEON_RED);
        btnAtk.setOnAction(e -> { state.difficulty = "NORMAL"; startGame(false, "ATTACKER"); });
        onlineBox.getChildren().addAll(btnDef, btnAtk);

        Button btnBack = createStyledButton("<< BACK TO MAIN MENU", Color.GRAY); btnBack.setOnAction(e -> showMainMenu());
        menu.getChildren().addAll(title, new Label("--- SOLO PLAY ---"), offlineBox, new Label("--- MULTIPLAYER ---"), onlineBox, new Label(""), btnBack);
        rootPane.getChildren().add(menu);
    }

    private void showDifficultySelection() {
        rootPane.getChildren().removeIf(node -> node instanceof VBox || (node instanceof HBox && node != topHud));
        VBox menu = new VBox(20); menu.setAlignment(Pos.CENTER); menu.setStyle("-fx-background-color: rgba(0,0,0,0.9);");
        Label title = new Label("SELECT DIFFICULTY"); title.setTextFill(NEON_CYAN); title.setFont(Font.font("Impact", 50));

        Button btnNorm = createStyledButton("NORMAL (800G, Easy Mobs)", NEON_GREEN); btnNorm.setPrefWidth(300);
        Button btnHard = createStyledButton("HARD (350G, Fast Mobs)", NEON_ORANGE); btnHard.setPrefWidth(300);
        Button btnEndless = createStyledButton("ENDLESS (Infinite Waves)", NEON_RED); btnEndless.setPrefWidth(300);

        btnNorm.setOnAction(e -> { state.difficulty = "NORMAL"; startGame(true, "DEFENDER"); });
        btnHard.setOnAction(e -> { state.difficulty = "HARD"; startGame(true, "DEFENDER"); });
        btnEndless.setOnAction(e -> { state.difficulty = "ENDLESS"; startGame(true, "DEFENDER"); });

        Button btnBack = createStyledButton("<< BACK", Color.GRAY); btnBack.setPrefWidth(300);
        btnBack.setOnAction(e -> showRoleSelection());

        menu.getChildren().addAll(title, btnNorm, btnHard, btnEndless, new Label(""), btnBack);
        rootPane.getChildren().add(menu);
    }

    private void startGame(boolean offline, String role) {
        playBgm("GAME");
        state.isOfflineMode = offline; state.myRole = role;
        state.resetLevelData();
        waveManager.initWaves(state.difficulty.equals("ENDLESS") ? 1 : 5);
        damageTexts.clear(); particles.clear(); screenShake = 0; bombFlashAlpha = 0;

        rootPane.getChildren().removeIf(node -> node instanceof VBox || (node instanceof HBox && node != topHud));
        if (topHud != null) topHud.setVisible(true);

        if (!offline) {
            network.connect();
            if ("ATTACKER".equals(role)) state.message = "Đang xin dữ liệu từ Host...";
            else { generateMap(); new Timer().schedule(new TimerTask() { public void run() { Platform.runLater(()->startLevel()); }}, 1000); }
        } else { generateMap(); startLevel(); }

        if ("ATTACKER".equals(role)) createBottomAttackerPanel();
        else if ("DEFENDER".equals(role)) createBottomShop();
    }

    private int getMaxHp(MonsterType type, int level) {
        double multi = state.difficulty.equals("NORMAL") ? 0.05 : (state.difficulty.equals("HARD") ? 0.4 : 0.3);
        return (int)(type.hp * (1 + (level - 1) * multi));
    }
    private double getScaledSpeed(MonsterType type, int level) {
        double multi = state.difficulty.equals("NORMAL") ? 0.0 : (state.difficulty.equals("HARD") ? 0.1 : 0.05);
        return type.speed * (1 + (level - 1) * multi);
    }

    private void generateMap() {
        state.currentPath.clear();
        int mapType = (state.currentLevel - 1) % 3;
        if (mapType == 0) {
            state.currentPath.addAll(Arrays.asList(new Point2D(99, 654), new Point2D(70, 592), new Point2D(35, 563), new Point2D(11, 518), new Point2D(5, 478), new Point2D(21, 428), new Point2D(75, 410), new Point2D(138, 417), new Point2D(186, 440), new Point2D(233, 472), new Point2D(293, 493), new Point2D(376, 494), new Point2D(438, 467), new Point2D(469, 412), new Point2D(448, 348), new Point2D(388, 317), new Point2D(329, 269), new Point2D(327, 214), new Point2D(390, 162), new Point2D(489, 151), new Point2D(590, 150), new Point2D(663, 172), new Point2D(693, 208), new Point2D(706, 256), new Point2D(689, 293), new Point2D(668, 340), new Point2D(640, 399), new Point2D(638, 443), new Point2D(707, 456), new Point2D(807, 442), new Point2D(879, 411), new Point2D(959, 412), new Point2D(1028, 469), new Point2D(1008, 550), new Point2D(948, 620), new Point2D(945, 664)));
        } else if (mapType == 1) {
            state.currentPath.addAll(Arrays.asList(new Point2D(0, 200), new Point2D(300, 200), new Point2D(500, 500), new Point2D(800, 500), new Point2D(950, 250), new Point2D(WIDTH, 250)));
        } else {
            state.currentPath.addAll(Arrays.asList(new Point2D(100, HEIGHT), new Point2D(100, 200), new Point2D(WIDTH/2, 100), new Point2D(WIDTH-150, 200), new Point2D(WIDTH-150, HEIGHT)));
        }
    }

    private void syncMapToAttacker() {
        if (state.isOfflineMode || state.currentPath.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for(Point2D p : state.currentPath) sb.append((int)p.getX()).append(":").append((int)p.getY()).append(",");
        network.sendAction(state.myRole, "SYNC_MAP", sb.toString());
        network.sendAction(state.myRole, "SYNC_HP", state.baseHp + "");
        network.sendAction(state.myRole, "NEW_LEVEL", state.currentLevel + "");
    }

    private void startLevel() { state.levelStarted = true; state.levelStartTime = System.currentTimeMillis(); }

    private double distToSegment(double px, double py, double x1, double y1, double x2, double y2) {
        double l2 = Math.pow(x1 - x2, 2) + Math.pow(y1 - y2, 2);
        if (l2 == 0) return Math.sqrt(Math.pow(px - x1, 2) + Math.pow(py - y1, 2));
        double t = Math.max(0, Math.min(1, ((px - x1) * (x2 - x1) + (py - y1) * (y2 - y1)) / l2));
        return Math.sqrt(Math.pow(px - (x1 + t * (x2 - x1)), 2) + Math.pow(py - (y1 + t * (y2 - y1)), 2));
    }

    private boolean isValidBuildSpot(double x, double y) {
        for (Tower t : state.towers) { if (t.dist(x, y) > 0 && t.dist(x, y) < 40) return false; }
        if (y > HEIGHT - 100) return false;
        if (state.selectedTower == TowerType.BARRACKS) return true;
        for (int i = 0; i < state.currentPath.size() - 1; i++) {
            Point2D p1 = state.currentPath.get(i), p2 = state.currentPath.get(i+1);
            if (distToSegment(x, y, p1.getX(), p1.getY(), p2.getX(), p2.getY()) < 50) return false;
        }
        return true;
    }

    // --- GAME LOGIC ---
    private long lastRegenTime = 0;

    private void updateLogic(long now) {
        if (state.isVictory || state.isGameOver) return;
        long currentTime = System.currentTimeMillis();

        if (state.currentLevel >= 4 && !currentBgm.equals("ENDGAME")) playBgm("ENDGAME");

        if (!(state.isOfflineMode && "DEFENDER".equals(state.myRole))) {
            long elapsed = (currentTime - state.levelStartTime) / 1000;
            if (LEVEL_DURATION_SECONDS - elapsed <= 0) {
                if ("DEFENDER".equals(state.myRole)) { if (state.baseHp > 0) handleVictory(); }
                else if ("ATTACKER".equals(state.myRole)) { if (state.baseHp > 0) handleDefeat(); }
                return;
            }
        }

        if (currentTime % 5000 < 20 && !state.isOfflineMode && "DEFENDER".equals(state.myRole)) {
            StringBuilder sb = new StringBuilder();
            state.towers.forEach(t -> sb.append(t.type.name()).append(":").append((int)t.x).append(":").append((int)t.y).append(":").append(t.level).append("|"));
            network.sendAction(state.myRole, "FULL_SYNC_TOWERS", sb.toString());
            network.sendAction(state.myRole, "SYNC_GOLD", String.valueOf(state.gold));
        }

        damageTexts.removeIf(dt -> { dt.y -= 0.5; dt.life -= 0.02; return dt.life <= 0; });
        particles.removeIf(p -> { if(p.isRipple) p.size += 2; else { p.x += p.vx; p.y += p.vy; } p.life -= 0.02; return p.life <= 0; });
        if (screenShake > 0) screenShake -= 0.5;

        boolean canRegen = (currentTime - lastRegenTime > 500);
        if (canRegen) lastRegenTime = currentTime;

        for(Soldier s : state.soldiers) {
            if (s.engaging==null && s.hp<100 && canRegen) s.hp++;
            if (s.engaging!=null && now-s.lastAtk > 1e9) {
                if(s.engaging.hp>0) {
                    int dmg = 15 + (s.level*5); s.engaging.takeDamage(dmg, "PHYSICAL");
                    damageTexts.add(new DamageText(s.engaging.x, s.engaging.y, "-" + dmg, NEON_CYAN));
                    spawnExplosion(s.engaging.x, s.engaging.y, NEON_CYAN, 3);
                }
                s.lastAtk=now;
            }
            if (s.engaging!=null && s.engaging.hp<=0) s.engaging=null;
        }
        state.soldiers.removeIf(s -> s.hp<=0);

        List<Monster> spawnedThisFrame = new ArrayList<>();
        Iterator<Monster> it = state.monsters.iterator();
        while(it.hasNext()) {
            Monster m = it.next();
            if (m.frozenTimer > 0) m.frozenTimer--;
            if (m.burnTimer > 0) {
                if (m.burnTimer % 10 == 0) {
                    m.takeDamage(5, "MAGIC"); damageTexts.add(new DamageText(m.x, m.y, "-5", Color.ORANGE));
                    spawnExplosion(m.x, m.y, Color.ORANGE, 2);
                }
                m.burnTimer--;
            }

            double currentSpeed = m.frozenTimer > 0 ? m.currentSpeed * 0.1 : m.currentSpeed;

            if (m.type == MonsterType.SHAMAN && Math.random() < 0.02) {
                for(Monster ally : state.monsters) {
                    if (ally != m && ally.dist(m.x, m.y) < 80) { ally.hp = Math.min(ally.hp + 20, ally.maxHp); damageTexts.add(new DamageText(ally.x, ally.y, "+20", NEON_GREEN)); }
                }
            }
            if (m.type == MonsterType.BOSS && Math.random() < 0.005) {
                Monster minion = new Monster(MonsterType.GOBLIN, state.currentPath, state.currentLevel);
                minion.x = m.x + ThreadLocalRandom.current().nextInt(-20, 20); minion.y = m.y + ThreadLocalRandom.current().nextInt(-20, 20);
                minion.pathIdx = m.pathIdx; spawnedThisFrame.add(minion);
            }

            for(Soldier s : state.soldiers) { if (s.engaging==null && m.engaging==null && m.dist(s.x,s.y)<30) { s.engaging=m; m.engaging=s; break; } }
            if (m.engaging!=null) {
                if(m.engaging.hp<=0) m.engaging=null;
                else if(now-m.lastAtk > 1e9) {
                    m.engaging.hp-=25; m.lastAtk=now;
                    damageTexts.add(new DamageText(m.engaging.x, m.engaging.y, "-25", NEON_RED));
                    spawnExplosion(m.engaging.x, m.engaging.y, NEON_RED, 3);
                }
            } else {
                if(m.pathIdx>=state.currentPath.size()-1) m.finished=true;
                else {
                    Point2D t = state.currentPath.get(m.pathIdx+1); double d = m.dist(t.getX(), t.getY());
                    if(d<=currentSpeed) { m.x=t.getX(); m.y=t.getY(); m.pathIdx++; }
                    else { m.x+=(t.getX()-m.x)/d*currentSpeed; m.y+=(t.getY()-m.y)/d*currentSpeed; }
                }
            }

            if(m.finished) {
                if (state.isOfflineMode || "DEFENDER".equals(state.myRole)) {
                    int damageToCore = (m.type == MonsterType.BOSS) ? 500 : 100;
                    state.baseHp -= damageToCore;
                    state.baseDamaged = true;
                    playSound("base_hit");
                    screenShake = (m.type == MonsterType.BOSS) ? 15 : 5;
                    damageTexts.add(new DamageText(WIDTH-120, HEIGHT/2, "-" + damageToCore, NEON_RED));
                    spawnExplosion(WIDTH-120, HEIGHT/2, NEON_RED, 15);

                    if (state.baseHp <= 0) { state.baseHp = 0; handleDefeat(); }
                    if(!state.isOfflineMode) network.sendAction(state.myRole, "SYNC_HP", state.baseHp+"");
                }
                it.remove();
                if (state.baseHp <= 0) return;
                continue;
            }
            if(m.hp <= 0) {
                state.gold += m.type.reward;
                state.enemiesKilled++;
                if (m.type == MonsterType.BOSS) state.bossesKilled++;
                state.totalGoldEarned += m.type.reward;
                damageTexts.add(new DamageText(m.x, m.y, "+$" + m.type.reward, Color.YELLOW));
                spawnExplosion(m.x, m.y, m.type.color, 10);
                playSound("coin");
                it.remove();
            }
        }
        state.monsters.addAll(spawnedThisFrame);

        state.projectiles.clear();
        for(Tower t : state.towers) {
            if(t.type==TowerType.BARRACKS) continue;
            if(t.target==null || t.target.hp<=0 || t.dist(t.target.x, t.target.y)>t.getRange()) t.target = state.monsters.stream().filter(m->t.dist(m.x,m.y)<=t.getRange()).min(Comparator.comparingDouble(m->t.dist(m.x,m.y))).orElse(null);

            if(t.target!=null && currentTime - t.lastAtk > t.getCooldown() * 1000) {
                state.projectiles.add(new Projectile(t.x, t.y-20, t.target.x, t.target.y, t.type.color));

                if (t.type == TowerType.CANNON) {
                    playSound("cannon"); spawnExplosion(t.target.x, t.target.y, NEON_ORANGE, 12);
                    for(Monster m2 : state.monsters) {
                        if (m2.dist(t.target.x, t.target.y) < 60) {
                            m2.takeDamage(t.getDamage(), "PHYSICAL");
                            damageTexts.add(new DamageText(m2.x, m2.y, "-" + t.getDamage(), t.type.color));
                        }
                    }
                } else {
                    playSound(t.type == TowerType.MAGE ? "laser" : "shoot");
                    t.target.takeDamage(t.getDamage(), t.type==TowerType.MAGE?"MAGIC":"PHYSICAL");
                    damageTexts.add(new DamageText(t.target.x, t.target.y, "-" + t.getDamage(), t.type.color));
                    spawnExplosion(t.target.x, t.target.y, t.type.color, 5);
                }

                if (t.type == TowerType.MAGE) {
                    if (Math.random() < 0.2) t.target.frozenTimer = 60;
                    else if (Math.random() < 0.4) t.target.burnTimer = 60;
                }
                t.lastAtk=currentTime;
            }
        }
    }

    private void updateAI(long now) {
        if (!state.levelStarted) return;
        if ("ATTACKER".equals(state.myRole) && System.currentTimeMillis() - state.lastAiActionTime > 2500) {
            int pathIndex = ThreadLocalRandom.current().nextInt(state.currentPath.size() - 1);
            Point2D p1 = state.currentPath.get(pathIndex), p2 = state.currentPath.get(pathIndex + 1);
            double t = ThreadLocalRandom.current().nextDouble(), midX = p1.getX() + t * (p2.getX() - p1.getX()), midY = p1.getY() + t * (p2.getY() - p1.getY());
            TowerType randomTower = TowerType.values()[ThreadLocalRandom.current().nextInt(TowerType.values().length)];
            Tower tow = new Tower(randomTower, midX, midY + (80 * (ThreadLocalRandom.current().nextBoolean() ? 1 : -1))); state.towers.add(tow);
            if (randomTower == TowerType.BARRACKS) spawnSoldiers(tow); state.lastAiActionTime = System.currentTimeMillis();
        }
    }

    private void handleBuild(double x, double y) {
        long now = System.currentTimeMillis();
        if (now - state.lastBuildTime < BUILD_COOLDOWN_MS) { state.message = "Building Cooldown!"; return; }

        Tower existingTower = state.towers.stream().filter(t -> t.dist(x, y) < 40).findFirst().orElse(null);

        if (existingTower != null) {
            if (existingTower.type == state.selectedTower) {
                int upgradeCost = existingTower.type.cost + (existingTower.level * 50);
                if (state.gold >= upgradeCost && existingTower.level < 3) {
                    playSound("build"); state.gold -= upgradeCost; existingTower.upgrade();
                    state.message = "UPGRADED TO LV." + existingTower.level; state.lastBuildTime = now;
                    damageTexts.add(new DamageText(x, y, "-$" + upgradeCost, Color.YELLOW));
                    if(!state.isOfflineMode) network.sendAction(state.myRole, "UPGRADE", existingTower.x+","+existingTower.y);
                } else state.message = "NOT ENOUGH GOLD OR MAX LEVEL!";
            } else {
                if (state.gold >= state.selectedTower.cost) {
                    playSound("build"); int refundAmount = existingTower.type.cost / 2;
                    state.gold = state.gold - state.selectedTower.cost + refundAmount;
                    state.towers.remove(existingTower);
                    if (existingTower.type == TowerType.BARRACKS) state.soldiers.removeIf(s -> s.owner == existingTower);
                    Tower t = new Tower(state.selectedTower, existingTower.x, existingTower.y); state.towers.add(t);
                    if(state.selectedTower==TowerType.BARRACKS) spawnSoldiers(t);
                    state.message = "REPLACED! (Refund +$" + refundAmount + ")"; state.lastBuildTime = now;
                    damageTexts.add(new DamageText(x, y, "-$" + (state.selectedTower.cost - refundAmount), Color.YELLOW));
                    if(!state.isOfflineMode) network.sendAction(state.myRole, "REPLACE", state.selectedTower.name()+","+existingTower.x+","+existingTower.y);
                } else state.message = "NOT ENOUGH GOLD!";
            }
        } else {
            if (state.gold >= state.selectedTower.cost) {
                playSound("build"); state.gold -= state.selectedTower.cost;
                Tower t = new Tower(state.selectedTower,x,y); state.towers.add(t);
                state.towersBuilt++;
                if(state.towers.size() > state.maxTowersActive) state.maxTowersActive = state.towers.size();
                if(state.selectedTower==TowerType.BARRACKS) spawnSoldiers(t);
                state.lastBuildTime = now;
                damageTexts.add(new DamageText(x, y, "-$" + state.selectedTower.cost, Color.YELLOW));
                if(!state.isOfflineMode) network.sendAction(state.myRole, "BUILD", state.selectedTower.name()+","+x+","+y);
            } else state.message = "NOT ENOUGH GOLD!";
        }
    }

    private void spawnSoldiers(Tower t) {
        state.soldiers.add(new Soldier(t.x+20, t.y, t)); state.soldiers.add(new Soldier(t.x-20, t.y, t)); state.soldiers.add(new Soldier(t.x, t.y+20, t));
    }

    private void togglePause() {
        state.isPaused = !state.isPaused;
        if (state.isPaused) showPauseMenu(); else if (menuOverlay != null) rootPane.getChildren().remove(menuOverlay);
    }

    private void handleVictory() {
        if (!state.levelStarted) return; state.levelStarted = false;
        state.isVictory = true; playBgm("VICTORY");
        if (!state.isOfflineMode) network.sendAction(state.myRole, "VICTORY", "");
        Platform.runLater(() -> showEndGameMenu(true));
    }
    private void handleDefeat() {
        if (!state.levelStarted) return; state.levelStarted = false;
        state.isGameOver = true;
        if (!state.isOfflineMode) network.sendAction(state.myRole, "GAME_OVER", "");
        Platform.runLater(() -> showEndGameMenu(false));
    }

    private void showPauseMenu() {
        if (menuOverlay != null && rootPane.getChildren().contains(menuOverlay)) rootPane.getChildren().remove(menuOverlay);
        menuOverlay = new VBox(20); menuOverlay.setAlignment(Pos.CENTER); menuOverlay.setStyle("-fx-background-color: rgba(0,0,0,0.8);");
        Label lbl = createHUDLabel("PAUSED", NEON_CYAN); lbl.setFont(Font.font("Impact", 60));
        Button btnResume = createStyledButton("RESUME", NEON_GREEN); btnResume.setOnAction(e -> togglePause());
        Button btnRestart = createStyledButton("RESTART LEVEL", NEON_ORANGE); btnRestart.setOnAction(e -> { togglePause(); startGame(state.isOfflineMode, state.myRole); });
        Button btnQuit = createStyledButton("MAIN MENU", NEON_RED); btnQuit.setOnAction(e -> returnToMainMenu());
        menuOverlay.getChildren().addAll(lbl, btnResume, btnRestart, btnQuit); rootPane.getChildren().add(menuOverlay);
    }

    // --- CẢI TIẾN: BẢNG THỐNG KÊ CHI TIẾT & GHI FILE SCORE ---
    private void showEndGameMenu(boolean victory) {
        if (menuOverlay != null && rootPane.getChildren().contains(menuOverlay)) rootPane.getChildren().remove(menuOverlay);

        // TÍNH TOÁN VÀ GHI HIGH SCORE
        state.survivalTimeSeconds = (System.currentTimeMillis() - state.levelStartTime) / 1000;
        boolean newRecord = false;
        if (state.difficulty.equals("ENDLESS") && state.currentLevel > bestEndlessWave) { bestEndlessWave = state.currentLevel; newRecord = true; }
        if (state.enemiesKilled > maxKillsRecord) { maxKillsRecord = state.enemiesKilled; newRecord = true; }
        if (victory && !state.difficulty.equals("ENDLESS") && state.survivalTimeSeconds < fastestClearTime) { fastestClearTime = state.survivalTimeSeconds; newRecord = true; }
        if (newRecord) saveScores();

        menuOverlay = new VBox(15); menuOverlay.setAlignment(Pos.CENTER); menuOverlay.setStyle("-fx-background-color: rgba(0,0,0,0.95);");
        String titleStr = state.difficulty.equals("ENDLESS") ? "GAME OVER" : (victory ? "VICTORY!" : "DEFEAT");
        Label lbl = createHUDLabel(titleStr, victory ? NEON_GREEN : NEON_RED); lbl.setFont(Font.font("Impact", 70));

        VBox statsBox = new VBox(10); statsBox.setAlignment(Pos.CENTER_LEFT);
        statsBox.setStyle("-fx-border-color: #00fff5; -fx-border-width: 2; -fx-padding: 20; -fx-background-color: rgba(0,0,0,0.5);");
        statsBox.setMaxWidth(400);
        statsBox.getChildren().addAll(
                createHUDLabel("📊 THỐNG KÊ TRẬN ĐẤU", NEON_CYAN),
                createHUDLabel("⏱️ Thời gian sống sót: " + String.format("%02d:%02d", state.survivalTimeSeconds/60, state.survivalTimeSeconds%60), Color.WHITE),
                createHUDLabel("🗡️ Kẻ địch đã diệt: " + state.enemiesKilled, Color.WHITE),
                createHUDLabel("👹 Số Boss hạ gục: " + state.bossesKilled, Color.WHITE),
                createHUDLabel("💰 Tổng vàng kiếm được: " + state.totalGoldEarned, Color.YELLOW),
                createHUDLabel("⚡ Kỹ năng đã dùng: " + state.skillsUsed, NEON_ORANGE),
                createHUDLabel("🌊 Wave kết thúc: " + state.currentLevel, NEON_ORANGE)
        );

        VBox scoreBox = new VBox(10); scoreBox.setAlignment(Pos.CENTER_LEFT);
        scoreBox.setStyle("-fx-border-color: #ff9a3c; -fx-border-width: 2; -fx-padding: 20; -fx-background-color: rgba(0,0,0,0.5);");
        scoreBox.setMaxWidth(400);
        scoreBox.getChildren().addAll(
                createHUDLabel("🏆 KỶ LỤC CÁ NHÂN", NEON_ORANGE),
                createHUDLabel("🌊 Wave Endless Cao Nhất: " + bestEndlessWave, NEON_GREEN),
                createHUDLabel("💀 Diệt nhiều nhất: " + maxKillsRecord + " mạng", NEON_GREEN),
                createHUDLabel("⏱️ Clear nhanh nhất: " + (fastestClearTime == 999999 ? "N/A" : String.format("%02d:%02d", fastestClearTime/60, fastestClearTime%60)), NEON_GREEN)
        );

        HBox panels = new HBox(20); panels.setAlignment(Pos.CENTER); panels.getChildren().addAll(statsBox, scoreBox);

        HBox buttons = new HBox(20); buttons.setAlignment(Pos.CENTER);
        Button btnReplay = createStyledButton("REPLAY", NEON_ORANGE);
        btnReplay.setOnAction(e -> { rootPane.getChildren().remove(menuOverlay); startGame(state.isOfflineMode, state.myRole); });
        Button btnQuit = createStyledButton("MAIN MENU", NEON_RED); btnQuit.setOnAction(e -> returnToMainMenu());
        buttons.getChildren().addAll(btnReplay, btnQuit);

        if (victory && (state.isOfflineMode && "DEFENDER".equals(state.myRole)) && !state.difficulty.equals("ENDLESS")) {
            Button btnNext = createStyledButton("NEXT MAP >>", NEON_CYAN);
            btnNext.setOnAction(e -> {
                rootPane.getChildren().remove(menuOverlay); state.currentLevel++;
                state.gold = state.difficulty.equals("HARD") ? 350 : 800;
                state.isVictory = false; waveManager.initWaves(5); generateMap(); startLevel();
            });
            buttons.getChildren().add(btnNext);
        }

        menuOverlay.getChildren().addAll(lbl, panels, buttons); rootPane.getChildren().add(menuOverlay);
    }

    private void returnToMainMenu() { state.levelStarted = false; state.resetLevelData(); showMainMenu(); }

    private void render() {
        gc.save();
        // 1. HIỆU ỨNG RUNG MÀN HÌNH (SCREEN SHAKE)
        if (screenShake > 0) {
            double dx = (Math.random() - 0.5) * screenShake;
            double dy = (Math.random() - 0.5) * screenShake;
            gc.translate(dx, dy);
        }

        // 2. VẼ BACKGROUND (MAP)
        int mapIdx = ((state.currentLevel - 1) % 3) + 1;
        Image bgImg = imageCache.get("map_" + mapIdx);
        if(bgImg != null) {
            gc.drawImage(bgImg, 0, 0, WIDTH, HEIGHT);
        } else {
            gc.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE, new Stop(0, BG_DARK), new Stop(1, BG_LIGHT)));
            gc.fillRect(0, 0, WIDTH, HEIGHT);
        }

        if (state.currentPath.isEmpty()) { gc.restore(); return; }

        // 3. VẼ NHÀ CHÍNH (BASE CORE)
        Point2D end = state.currentPath.get(state.currentPath.size()-1);
        drawBase(end.getX(), end.getY());

        // 4. VẼ CÁC ĐƠN VỊ (TOWERS, SOLDIERS, MONSTERS)
        List<Tower> towerSnapshot = new ArrayList<>(state.towers);
        List<Monster> monsterSnapshot = new ArrayList<>(state.monsters);

        for(Tower t : towerSnapshot) drawTower(t);

        for(Soldier s : state.soldiers) {
            drawSoldier(s.x, s.y);
            // Thanh máu lính: to hơn và cao hơn để không đè lên hình lính lớn
            renderModernBar(s.x, s.y - 40, s.hp, 100, 50, NEON_CYAN);
        }

        for(Monster m : monsterSnapshot) {
            drawMonster(m);
            // Kích thước thanh máu quái khớp với kích thước mới (Boss 160, Quái thường 75-80)
            double barSize = (m.type == MonsterType.BOSS) ? 100 : 50;
            double barYOffset = (m.type == MonsterType.BOSS) ? 85 : 45;
            renderModernBar(m.x, m.y - barYOffset, m.hp, m.maxHp, barSize, NEON_RED);

            // Hiệu ứng dính bùa (Đóng băng/Đốt cháy) bao quanh ảnh
            if (m.frozenTimer > 0) {
                gc.setStroke(NEON_CYAN); gc.setLineWidth(3);
                gc.strokeOval(m.x - barSize/2, m.y - barSize/2, barSize, barSize);
            } else if (m.burnTimer > 0) {
                gc.setStroke(Color.ORANGE); gc.setLineWidth(3);
                gc.strokeOval(m.x - barSize/2, m.y - barSize/2, barSize, barSize);
            }
        }

        // 5. VẼ HIỆU ỨNG HẠT (PARTICLES)
        for (Particle p : particles) {
            gc.setGlobalAlpha(p.life / p.maxLife);
            if (p.isRipple) {
                gc.setStroke(p.color); gc.setLineWidth(2);
                gc.strokeOval(p.x - p.size/2, p.y - p.size/2, p.size, p.size);
            } else {
                gc.setFill(p.color); gc.fillOval(p.x, p.y, p.size, p.size);
            }
        }
        gc.setGlobalAlpha(1.0);

        // 6. VẼ ĐẠN BAY (PROJECTILES)
        Image arrowImg = imageCache.get("Arrow");
        gc.setEffect(new Glow(1.0));
        for(Projectile p : state.projectiles) {
            gc.setStroke(p.c);
            if(p.c.equals(TowerType.ARCHER.color) && arrowImg != null) {
                double angle = Math.toDegrees(Math.atan2(p.ey - p.sy, p.ex - p.sx));
                gc.save();
                gc.translate(p.sx + (p.ex-p.sx)*0.5, p.sy + (p.ey-p.sy)*0.5);
                gc.rotate(angle);
                gc.drawImage(arrowImg, -20, -10, 40, 20); // Tăng size đạn cung
                gc.restore();
            } else if(p.c.equals(TowerType.MAGE.color)) {
                gc.setLineWidth(6); gc.strokeLine(p.sx, p.sy, p.ex, p.ey);
                gc.setStroke(Color.WHITE); gc.setLineWidth(2); gc.strokeLine(p.sx, p.sy, p.ex, p.ey);
            } else if(p.c.equals(TowerType.CANNON.color)) {
                gc.setFill(p.c); gc.fillOval(p.sx + (p.ex-p.sx)*0.5 - 10, p.sy + (p.ey-p.sy)*0.5 - 10, 20, 20); // Đạn pháo to hơn
            } else {
                gc.setLineWidth(3); gc.strokeLine(p.sx, p.sy, p.ex, p.ey);
            }
        }
        gc.setEffect(null);

        // 7. VẼ SÁT THƯƠNG NHẢY SỐ (DAMAGE TEXT)
        gc.setFont(Font.font("Consolas", FontWeight.BOLD, 20)); // Chữ to hơn
        for (DamageText dt : damageTexts) {
            gc.setGlobalAlpha(dt.life);
            gc.setFill(dt.color);
            gc.setEffect(new DropShadow(3, Color.BLACK));
            gc.fillText(dt.text, dt.x - 10, dt.y - 10);
            gc.setEffect(null);
        }
        gc.setGlobalAlpha(1.0);

        // 8. VẼ TOOLTIP VÀ PREVIEW KHI XÂY TRỤ
        if (mouseX >= 0 && mouseY >= 0 && !state.isPaused && !state.isGameOver && !state.isVictory && state.levelStarted) {
            // ... (Giữ nguyên logic vẽ Tooltip cũ của bạn tại đây) ...

            if ("DEFENDER".equals(state.myRole)) {
                Tower existing = towerSnapshot.stream().filter(t -> t.dist(mouseX, mouseY) < 40).findFirst().orElse(null);
                if (existing != null) {
                    gc.setStroke(Color.rgb(255, 255, 0, 0.5)); gc.setLineWidth(2);
                    gc.strokeOval(existing.x - existing.getRange(), existing.y - existing.getRange(), existing.getRange() * 2, existing.getRange() * 2);
                } else if (!state.isSellMode) {
                    boolean isValid = isValidBuildSpot(mouseX, mouseY);
                    Color previewColor = isValid ? Color.rgb(0, 255, 0, 0.3) : Color.rgb(255, 0, 0, 0.5);
                    gc.setStroke(previewColor); gc.setLineWidth(2);
                    gc.strokeOval(mouseX - state.selectedTower.range, mouseY - state.selectedTower.range, state.selectedTower.range * 2, state.selectedTower.range * 2);

                    Image previewImg = imageCache.get("tower_" + state.selectedTower.name().toLowerCase());
                    if (previewImg != null) {
                        gc.setGlobalAlpha(0.5);
                        // Preview to hơn theo kích thước trụ mới (130)
                        gc.drawImage(previewImg, 0, 0, previewImg.getWidth() / spriteFrames.getOrDefault("tower_"+state.selectedTower.name().toLowerCase(), 1), previewImg.getHeight(), mouseX - 65, mouseY - 75, 130, 130);
                        gc.setGlobalAlpha(1.0);
                    }
                }
            }
        }
        gc.restore(); // KẾT THÚC SCREEN SHAKE

        // 9. HIỆU ỨNG CHỚP SÁNG KHI THẢ BOM
        if (bombFlashAlpha > 0) {
            gc.setFill(Color.rgb(255, 255, 255, bombFlashAlpha)); gc.fillRect(0, 0, WIDTH, HEIGHT);
            bombFlashAlpha -= 0.05;
        }

        // 10. LỚP PHỦ KHI PAUSE
        if (state.isPaused) { gc.setFill(Color.rgb(0,0,0,0.5)); gc.fillRect(0,0,WIDTH,HEIGHT); }
    }

    private void drawCanvasTooltip(double x, double y, String text, Color borderColor) {
        gc.setFill(Color.rgb(10, 10, 20, 0.9)); gc.setStroke(borderColor); gc.setLineWidth(2);
        int lines = text.split("\n").length; double boxW = 140, boxH = lines * 20 + 10;
        double drawX = x + 15, drawY = y - boxH - 10;
        gc.fillRoundRect(drawX, drawY, boxW, boxH, 10, 10); gc.strokeRoundRect(drawX, drawY, boxW, boxH, 10, 10);
        gc.setFill(Color.WHITE); gc.setFont(Font.font("Consolas", 14)); gc.fillText(text, drawX + 5, drawY + 20);
    }

    private void drawBase(double x, double y) {
        Image img = imageCache.get("base_core");
        if (img != null) { gc.setEffect(new DropShadow(20, NEON_CYAN)); gc.drawImage(img, x - 60, y - 60, 120, 120); gc.setEffect(null); }
        else {
            gc.setEffect(new DropShadow(15, NEON_CYAN)); gc.setFill(Color.web("#333")); gc.fillRect(x-50, y-40, 100, 80);
            gc.setFill(new LinearGradient(0,0,0,1, true, CycleMethod.NO_CYCLE, new Stop(0, NEON_CYAN), new Stop(1, Color.TRANSPARENT))); gc.fillOval(x-30, y-20, 60, 60);
            gc.save(); gc.translate(x, y+10); gc.rotate(animationTime * 50); gc.setStroke(Color.WHITE); gc.setLineWidth(3); gc.strokeRect(-15, -15, 30, 30); gc.restore(); gc.setEffect(null);
            gc.setFill(Color.WHITE); gc.setFont(Font.font("Impact", 18)); gc.fillText("CORE", x-18, y-45);
        }
        renderModernBar(x, y-40, state.baseHp, 2000, 80, state.baseHp < 500 ? NEON_RED : NEON_GREEN);
    }

    private void drawTower(Tower t) {
        double x = t.x, y = t.y;
        long now = System.currentTimeMillis();

        // Fix: Hoạt ảnh bắn sẽ chiếm 60% thời gian của tốc độ bắn (Cooldown)
        // Giúp hoạt ảnh luôn hiển thị đầy đủ dù bắn nhanh hay chậm
        double attackDurationMs = t.getCooldown() * 1000 * 0.6;
        boolean isShooting = (now - t.lastAtk < attackDurationMs);

        String stateStr = isShooting ? "shoot" : "idle";
        String towerNameStr = t.type.name().toLowerCase();

        String imageKey = towerNameStr + "_" + stateStr;
        Image img = imageCache.get(imageKey);
        Integer framesObj = spriteFrames.get(imageKey);

        if (img == null) {
            imageKey = "tower_" + towerNameStr;
            img = imageCache.get(imageKey);
            framesObj = spriteFrames.get(imageKey);
        }

        if (img != null) {
            int frames = (framesObj != null) ? framesObj : 1;
            double towerSize = 130;

            if (frames > 1) {
                boolean flip = t.target != null && t.target.x < t.x;

                // Tính toán speedModifier để hoạt ảnh chạy hết số khung hình vừa đúng lúc hồi chiêu xong
                // Công thức: Speed = (Số khung hình) / (Thời gian diễn hoạt tính bằng giây của code máy)
                double animSpeed = isShooting ? (frames / (attackDurationMs / 100)) : 8;

                drawAnimatedSprite(img, frames, x, y - 20, towerSize, animSpeed, flip);
            } else {
                gc.drawImage(img, x - towerSize/2, y - towerSize/2 - 10, towerSize, towerSize);
            }

            gc.setFill(Color.WHITE);
            gc.setFont(Font.font("Consolas", FontWeight.BOLD, 14));
            gc.fillText("Lv." + t.level, x-15, y+45);
            return;
        }


        // --- FALLBACK VẼ BẰNG CODE DỰ PHÒNG CHỐNG CRASH (BỎ GLOW) ---
        gc.setFill(Color.web("#222")); gc.fillOval(x-25, y-20, 50, 30);
        gc.setFill(t.type.color.darker()); gc.fillRect(x-20, y-25, 40, 15);
        switch (t.type) {
            case ARCHER: gc.setFill(new LinearGradient(0,0,1,0, true, CycleMethod.NO_CYCLE, new Stop(0, t.type.color), new Stop(1, t.type.color.brighter()))); gc.fillRect(x-10, y-55, 20, 40); gc.setStroke(Color.WHITE); gc.setLineWidth(3); gc.strokeArc(x-20, y-65, 40, 30, 0, 180, ArcType.OPEN); break;
            case MAGE: gc.setFill(t.type.color.darker()); gc.fillPolygon(new double[]{x-15, x+15, x}, new double[]{y-10, y-10, y-60}, 3); gc.setFill(t.type.color); gc.fillOval(x-12, y-72 + Math.sin(animationTime*2)*5, 24, 24); break;
            case BARRACKS: gc.setFill(t.type.color.darker()); gc.fillRect(x-25, y-30, 50, 25); gc.setFill(t.type.color); gc.fillArc(x-20, y-45, 40, 30, 0, 180, ArcType.ROUND); gc.setFill(Color.BLACK); gc.fillRect(x-8, y-25, 16, 15); break;
            case CANNON: gc.setFill(t.type.color.darker()); gc.fillRect(x-15, y-40, 30, 20); gc.setFill(t.type.color); gc.fillOval(x-12, y-45, 24, 24); gc.setStroke(Color.BLACK); gc.setLineWidth(6); gc.strokeLine(x, y-35, x+15, y-50); break;
        }
        gc.setEffect(null); gc.setFill(Color.WHITE); gc.setFont(Font.font("Consolas", FontWeight.BOLD, 12)); gc.fillText("Lv." + t.level, x-12, y+5);
    }

    private void drawMonster(Monster m) {
        double x = m.x, y = m.y;
        String monsterNameStr = m.type.name().toLowerCase();
        String imageKey = "monster_" + monsterNameStr;
        Image img = imageCache.get(imageKey);
        Integer framesObj = spriteFrames.get(imageKey);
        if (img != null) {
            double size = m.type == MonsterType.BOSS ? 160 : 75;
            int frames = (framesObj != null) ? framesObj : 1;

            if (frames > 1) {
                boolean flip = false;
                if (m.pathIdx < state.currentPath.size() - 1) {
                    Point2D next = state.currentPath.get(m.pathIdx + 1);
                    flip = next.getX() < m.x;
                }
                drawAnimatedSprite(img, frames, x, y, size, 12, flip);
            } else {
                gc.drawImage(img, x - size/2, y - size/2, size, size);
            }
            return;
        }
        Color c = m.type.color;
        switch (m.type) {
            case GOBLIN: gc.setFill(c); gc.fillOval(x-15, y-15, 30, 30); gc.fillPolygon(new double[]{x-15, x-25, x-15}, new double[]{y-5, y-15, y-25}, 3); gc.fillPolygon(new double[]{x+15, x+25, x+15}, new double[]{y-5, y-15, y-25}, 3); break;
            case ORC: gc.setFill(c); gc.fillRect(x-20, y-25, 40, 50); gc.setFill(Color.GRAY); gc.fillRect(x-25, y-30, 15, 20); gc.fillRect(x+10, y-30, 15, 20); break;
            case SHAMAN: gc.setFill(c.darker()); gc.fillPolygon(new double[]{x-20, x+20, x}, new double[]{y+20, y+20, y-30}, 3); gc.setStroke(c); gc.setLineWidth(3); gc.strokeLine(x+15, y+20, x+25, y-20); gc.setFill(c.brighter()); gc.fillOval(x+20, y-25, 10, 10); break;
            case BOSS: gc.setFill(c.darker()); gc.fillOval(x-30, y-40, 60, 80); gc.setFill(Color.BLACK); gc.fillPolygon(new double[]{x-10, x+10, x}, new double[]{y-40, y-40, y-60}, 3); gc.fillPolygon(new double[]{x-30, x-10, x-20}, new double[]{y-20, y-20, y-40}, 3); gc.fillPolygon(new double[]{x+10, x+30, x+20}, new double[]{y-20, y-20, y-40}, 3); gc.setFill(Color.YELLOW); gc.fillOval(x-15, y-20, 10, 5); gc.fillOval(x+5, y-20, 10, 5); break;
        }
    }
    private void drawSoldier(double x, double y) {
        gc.setFill(NEON_CYAN.darker()); gc.fillArc(x-10, y-15, 20, 20, 0, 180, ArcType.ROUND); gc.setFill(NEON_CYAN); gc.fillRect(x-12, y-5, 24, 15); gc.setStroke(Color.WHITE); gc.setLineWidth(2); gc.strokeLine(x+5, y-10, x+15, y+5);
    }
    private void renderModernBar(double x, double y, int cur, int max, double w, Color c) {
        if (cur < 0) cur = 0;
        gc.setFill(Color.rgb(0,0,0,0.7)); gc.fillRoundRect(x-w/2, y, w, 5, 2, 2); gc.setFill(c); gc.fillRoundRect(x-w/2, y, w * Math.max(0, (double)cur/max), 5, 2, 2);
    }


    // --- UI PANELS ---
    private void setupGameHUD() {
        topHud = new HBox(25); topHud.setPadding(new Insets(15));
        topHud.setAlignment(Pos.CENTER);
        topHud.setStyle("-fx-background-color: rgba(20,20,30,0.8);");
        topHud.setMaxHeight(60);
        StackPane.setAlignment(topHud, Pos.TOP_CENTER);

        lblTime = createHUDLabel("00:00", NEON_GREEN);
        lblCore = createHUDLabel("HP: 0", NEON_RED);
        lblGold = createHUDLabel("GOLD: 0", Color.YELLOW);
        lblMessage = createHUDLabel("", Color.WHITE);
        lblQuest = createHUDLabel("🏆 QUÉT NHIỆM VỤ...", NEON_ORANGE);

        Button btnPause = createStyledButton("||", Color.YELLOW);
        btnPause.setPrefWidth(40);
        btnPause.setOnAction(e -> togglePause());

        topHud.getChildren().addAll(lblTime, lblCore, lblGold, lblQuest, lblMessage, btnPause);
        topHud.setVisible(false);
        rootPane.getChildren().add(topHud);
    }

    // --- CẢI TIẾN: GIAO DIỆN SHOP CÓ ICON & NÚT REPAIR/SELL ---
    private void createBottomShop() {

        bottomPanel = new HBox(15); bottomPanel.setPadding(new Insets(10, 20, 10, 20)); bottomPanel.setAlignment(Pos.CENTER); bottomPanel.setMaxHeight(90);
        bottomPanel.setStyle("-fx-background-color: rgba(20,20,30,0.9); -fx-background-radius: 20 20 0 0; -fx-border-color: #00fff5; -fx-border-width: 2 2 0 2;");
        StackPane.setAlignment(bottomPanel, Pos.BOTTOM_CENTER);

        shopButtons.clear();
        for(TowerType t : TowerType.values()) {
            Button b = createStyledButton(t.name() + "\n$" + t.cost + "\nD:" + t.damage, t.color);
            b.setPrefWidth(120);
            b.setPrefHeight(80);

            String towerName = t.name().toLowerCase();
            Image iconImg = imageCache.get(towerName + "_idle");
            if (iconImg == null) iconImg = imageCache.get("tower_" + towerName);
            if (iconImg != null) {
                ImageView iv = new ImageView(iconImg);
                String searchKey = (imageCache.containsKey(towerName + "_idle")) ? (towerName + "_idle") : ("tower_" + towerName);
                int frames = spriteFrames.getOrDefault(searchKey, 1);

                if (frames > 1) {
                    double frameWidth = iconImg.getWidth() / frames;
                    iv.setViewport(new Rectangle2D(0, 0, frameWidth, iconImg.getHeight()));
                }

                iv.setFitWidth(45);  // Tăng kích thước icon trong Shop
                iv.setFitHeight(45);
                iv.setPreserveRatio(true);

                b.setGraphic(iv);
                b.setContentDisplay(ContentDisplay.TOP); // Để hình ảnh nằm TRÊN chữ
            }

            b.setOnAction(e -> { state.selectedTower = t; state.isSellMode = false; });
            shopButtons.put(t, b);
            bottomPanel.getChildren().add(b);
        }

        // Thêm các nút Actions (Sell, Repair, Skills) vào cuối
        Label lblActs = new Label(" ACTIONS ");
        lblActs.setTextFill(NEON_ORANGE);
        lblActs.setFont(Font.font("Impact", 20));

        btnSell = createStyledButton("💰\nSELL", Color.GRAY); btnSell.setPrefWidth(70); btnSell.setPrefHeight(80);
        btnSell.setOnAction(e -> { state.isSellMode = !state.isSellMode; state.message = state.isSellMode ? "CHẾ ĐỘ BÁN: Chọn trụ trên bản đồ!" : "Đã hủy Bán."; });

        Button btnRepair = createStyledButton("🛠️\nREPAIR", NEON_GREEN); btnRepair.setPrefWidth(80); btnRepair.setPrefHeight(80);
        btnRepair.setOnAction(e -> repairCore());

        Button btnFreeze = createStyledButton("❄️\nFREEZE", NEON_CYAN); btnFreeze.setPrefWidth(80); btnFreeze.setPrefHeight(80);
        btnFreeze.setOnAction(e -> activateSkill("FREEZE", 100));

        Button btnBomb = createStyledButton("💣\nBOMB", NEON_ORANGE); btnBomb.setPrefWidth(80); btnBomb.setPrefHeight(80);
        btnBomb.setOnAction(e -> activateSkill("BOMB", 150));

        bottomPanel.getChildren().addAll(lblActs, btnSell, btnRepair, btnFreeze, btnBomb);
        rootPane.getChildren().add(bottomPanel);
    }

    private void createBottomAttackerPanel() {
        if (bottomPanel != null && rootPane.getChildren().contains(bottomPanel)) rootPane.getChildren().remove(bottomPanel);
        bottomPanel = new HBox(20); bottomPanel.setPadding(new Insets(10, 20, 10, 20)); bottomPanel.setAlignment(Pos.CENTER); bottomPanel.setMaxHeight(90); // Tăng chiều cao lên 90
        bottomPanel.setStyle("-fx-background-color: rgba(20,20,30,0.9); -fx-background-radius: 20 20 0 0; -fx-border-color: #e94560; -fx-border-width: 2 2 0 2;");
        StackPane.setAlignment(bottomPanel, Pos.BOTTOM_CENTER);
        Label lbl = new Label("UNITS"); lbl.setTextFill(NEON_RED); lbl.setFont(Font.font("Impact", 24)); bottomPanel.getChildren().add(lbl);
        for(MonsterType t : MonsterType.values()) {
            Button b = createStyledButton(t.name() + "\n(" + t.cooldown + "s)", t.color);
            b.setPrefWidth(120); b.setPrefHeight(60);

            Image icon = imageCache.get("monster_" + t.name().toLowerCase());
            if (icon != null) {
                ImageView iv = new ImageView(icon);
                Integer framesObj = spriteFrames.get("monster_" + t.name().toLowerCase());
                int frames = (framesObj != null) ? framesObj : 1;

                if (frames > 1) {
                    double frameWidth = icon.getWidth() / frames;
                    iv.setViewport(new Rectangle2D(0, 0, frameWidth, icon.getHeight()));
                }

                iv.setFitWidth(30); iv.setFitHeight(30);
                b.setGraphic(iv); b.setContentDisplay(ContentDisplay.LEFT);
            }

            Tooltip tip = new Tooltip("HP: " + t.hp + "\nSpeed: " + t.speed + "\nReward: $" + t.reward);
            tip.setShowDelay(Duration.millis(100)); tip.setFont(Font.font("Consolas", 14));
            Tooltip.install(b, tip);

            b.setOnAction(e -> spawnMonster(t)); attackerButtons.put(t, b); bottomPanel.getChildren().add(b);
        }
        rootPane.getChildren().add(bottomPanel);
    }

    private void updateInterface() {
        if (!state.levelStarted) return;
        if (topHud != null) topHud.setVisible(true);

        if (state.isOfflineMode && "DEFENDER".equals(state.myRole)) {
            int currentWaveIdx = (waveManager.currentWave != null) ? waveManager.currentWave.waveIndex : state.totalWaves;
            String waveStr = state.restTimeRemaining > 0 ? "REST: " + state.restTimeRemaining + "s" : "MAP " + state.currentLevel + " - WAVE " + currentWaveIdx + "/5";
            lblTime.setText(waveStr);
            lblTime.setTextFill(state.restTimeRemaining > 0 ? NEON_ORANGE : NEON_GREEN);
        } else {
            long elapsed = (System.currentTimeMillis() - state.levelStartTime) / 1000;
            long remaining = Math.max(0, LEVEL_DURATION_SECONDS - elapsed);
            lblTime.setText(String.format("TIME: %02d:%02d", remaining / 60, remaining % 60));
            lblTime.setTextFill(remaining < 30 ? NEON_RED : NEON_GREEN);
        }

        lblCore.setText("CORE: " + state.baseHp);
        lblGold.setText("GOLD: " + state.gold);

        String q1 = (state.enemiesKilled >= 50) ? "✔️ 50 Kills" : "⚔️ " + state.enemiesKilled + "/50";
        String q2 = (state.maxTowersActive <= 3) ? "⭐ " + state.maxTowersActive + "/3 Trụ" : "❌ >3 trụ";
        String q3 = (!state.baseDamaged) ? "🛡️ 100% HP" : "❌ Mất HP";
        lblQuest.setText("🏆 " + q1 + " | " + q2 + " | " + q3);

        lblMessage.setText(state.message);

        // HIGHLIGHT NÚT ĐANG CHỌN TRONG SHOP
        if ("DEFENDER".equals(state.myRole) && bottomPanel != null) {
            for (TowerType t : TowerType.values()) {
                Button b = shopButtons.get(t);
                if (b != null) {
                    if (!state.isSellMode && state.selectedTower == t) b.setStyle("-fx-background-color:rgba(255,255,0,0.3); -fx-border-color:yellow; -fx-border-width:2; -fx-border-radius:10; -fx-text-fill:white; -fx-font-family:'Consolas'; -fx-font-weight:bold; -fx-font-size:12px;");
                    else b.setStyle("-fx-background-color:rgba(0,0,0,0.5); -fx-border-color:" + String.format("#%02X%02X%02X", (int)(t.color.getRed()*255),(int)(t.color.getGreen()*255),(int)(t.color.getBlue()*255)) + "; -fx-border-width:2; -fx-border-radius:10; -fx-text-fill:white; -fx-font-family:'Consolas'; -fx-font-weight:bold; -fx-font-size:12px;");
                }
            }
            if (btnSell != null) {
                if (state.isSellMode) btnSell.setStyle("-fx-background-color:rgba(255,0,0,0.5); -fx-border-color:red; -fx-border-width:2; -fx-border-radius:10; -fx-text-fill:white; -fx-font-family:'Consolas'; -fx-font-weight:bold; -fx-font-size:13px;");
                else btnSell.setStyle("-fx-background-color:rgba(0,0,0,0.5); -fx-border-color:gray; -fx-border-width:2; -fx-border-radius:10; -fx-text-fill:white; -fx-font-family:'Consolas'; -fx-font-weight:bold; -fx-font-size:13px;");
            }
        }

        if ("ATTACKER".equals(state.myRole)) {
            long now = System.currentTimeMillis();
            for (MonsterType t : MonsterType.values()) {
                Button btn = attackerButtons.get(t);
                if (btn != null) {
                    long cooldownEnd = state.cooldowns.getOrDefault(t, 0L) + t.cooldown * 1000L;
                    if (now < cooldownEnd) { btn.setText(t.name() + " (" + (cooldownEnd - now) / 1000 + "s)"); btn.setDisable(true); btn.setOpacity(0.5); }
                    else { btn.setText(t.name() + " (" + t.cooldown + "s)"); btn.setDisable(false); btn.setOpacity(1.0); }
                }
            }
        }
    }

    private Button createStyledButton(String text, Color c) {
        Button btn = new Button(text); String hex = String.format("#%02X%02X%02X", (int)(c.getRed()*255),(int)(c.getGreen()*255),(int)(c.getBlue()*255));
        String style = "-fx-background-color:rgba(0,0,0,0.5);-fx-border-color:"+hex+";-fx-border-width:2;-fx-border-radius:10;-fx-text-fill:white;-fx-font-family:'Consolas';-fx-font-weight:bold;-fx-font-size:12px;-fx-cursor:hand;";
        btn.setStyle(style);
        btn.setOnMouseEntered(e -> { if(!btn.getStyle().contains("yellow") && !btn.getStyle().contains("red")) btn.setStyle("-fx-background-color:"+hex+";-fx-background-radius:10;-fx-text-fill:black;-fx-font-family:'Consolas';-fx-font-weight:bold;-fx-font-size:12px;-fx-cursor:hand;"); });
        btn.setOnMouseExited(e -> { if(!btn.getStyle().contains("yellow") && !btn.getStyle().contains("red")) btn.setStyle(style); });
        return btn;
    }
    private Label createHUDLabel(String text, Color color) { Label l = new Label(text); l.setTextFill(color); l.setFont(Font.font("Consolas", FontWeight.BOLD, 16)); l.setEffect(new DropShadow(5, color)); return l; }

    // --- NETWORK & SKILLS ---
    private void activateSkill(String skillName, int cost) {
        if (state.gold >= cost) {
            state.gold -= cost; state.skillsUsed++;
            if (!state.isOfflineMode) network.sendAction(state.myRole, "SKILL", skillName);
            else processAction(new GameAction(state.myRole, "SKILL", skillName));
            playSound(skillName.toLowerCase());
        } else { state.message = "NOT ENOUGH GOLD FOR SKILL!"; }
    }

    private void spawnMonster(MonsterType t) {
        long now = System.currentTimeMillis();
        if (state.isOfflineMode && "DEFENDER".equals(state.myRole)) { state.monsters.add(new Monster(t, state.currentPath, state.currentLevel)); return; }
        if(now - state.cooldowns.getOrDefault(t,0L) > t.cooldown*1000L) {
            state.cooldowns.put(t,now);
            if (state.isOfflineMode) { Monster m = new Monster(t, state.currentPath, state.currentLevel); state.monsters.add(m); }
            else network.sendAction(state.myRole, "SPAWN", t.name());
        }
    }

    private void processAction(GameAction a) {
        try {
            String type = a.getActionType(); String data = a.getData();
            if (data == null || data.isEmpty()) return;
            switch (type) {
                case "REQUEST_MAP": if ("DEFENDER".equals(state.myRole)) syncMapToAttacker(); break;
                case "SYNC_MAP":
                    state.currentPath.clear(); state.monsters.clear(); state.towers.clear(); state.projectiles.clear(); state.soldiers.clear();
                    for(String s : data.split(",")) { String[] xy = s.split(":"); if(xy.length==2) state.currentPath.add(new Point2D(Double.parseDouble(xy[0]), Double.parseDouble(xy[1]))); } break;
                case "FULL_SYNC_TOWERS":
                    if ("ATTACKER".equals(state.myRole)) {
                        state.towers.clear();
                        for (String tStr : data.split("\\|")) {
                            String[] p = tStr.split(":");
                            if (p.length == 4) {
                                Tower tw = new Tower(TowerType.valueOf(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]));
                                tw.level = Integer.parseInt(p[3]);
                                state.towers.add(tw);
                            }
                        }
                    }
                    break;
                case "SYNC_GOLD": state.gold = Integer.parseInt(data); break;
                case "NEW_LEVEL": state.levelStarted = true; state.levelStartTime = System.currentTimeMillis(); break;
                case "SPAWN":
                    if(!state.currentPath.isEmpty()) { MonsterType mtype = MonsterType.valueOf(data); Monster m = new Monster(mtype, state.currentPath, state.currentLevel); state.monsters.add(m); } break;
                case "BUILD":
                    String[] bd = data.split(","); TowerType bt = TowerType.valueOf(bd[0]); double bx = Double.parseDouble(bd[1]), by = Double.parseDouble(bd[2]); Tower newT = new Tower(bt,bx,by); state.towers.add(newT); if(bt==TowerType.BARRACKS) spawnSoldiers(newT); playSound("build"); break;
                case "UPGRADE":
                    String[] ud = data.split(","); double ux = Double.parseDouble(ud[0]), uy = Double.parseDouble(ud[1]); state.towers.stream().filter(tow->tow.dist(ux,uy)<10).forEach(Tower::upgrade); playSound("build"); break;
                case "REPLACE":
                    String[] rd = data.split(","); TowerType rt = TowerType.valueOf(rd[0]); double rx = Double.parseDouble(rd[1]), ry = Double.parseDouble(rd[2]); Tower oldTower = state.towers.stream().filter(tow->tow.dist(rx,ry)<10).findFirst().orElse(null); if(oldTower!=null) { state.towers.remove(oldTower); if(oldTower.type==TowerType.BARRACKS) state.soldiers.removeIf(s->s.owner==oldTower); } Tower repT = new Tower(rt,rx,ry); state.towers.add(repT); if(rt==TowerType.BARRACKS) spawnSoldiers(repT); playSound("build"); break;
                case "SYNC_HP": state.baseHp = Integer.parseInt(data); if(state.baseHp <= 0) { state.baseHp = 0; if ("DEFENDER".equals(state.myRole)) handleDefeat(); else if ("ATTACKER".equals(state.myRole)) handleVictory(); } break;
                case "GAME_OVER": handleDefeat(); break;
                case "VICTORY": handleVictory(); break;
                case "SELL":
                    String[] sd = data.split(","); double slx = Double.parseDouble(sd[0]), sly = Double.parseDouble(sd[1]);
                    Tower sellTower = state.towers.stream().filter(tow->tow.dist(slx,sly)<10).findFirst().orElse(null);
                    if(sellTower!=null) { state.towers.remove(sellTower); if(sellTower.type==TowerType.BARRACKS) state.soldiers.removeIf(s->s.owner==sellTower); playSound("coin"); }
                    break;
                case "SKILL":
                    if (data.equals("FREEZE")) { state.monsters.forEach(m -> m.frozenTimer = 100); state.message = "FREEZE ACTIVATED!"; playSound("freeze"); }
                    else if (data.equals("BOMB")) {
                        bombFlashAlpha = 1.0; screenShake = 20; state.message = "BOMB ACTIVATED!"; playSound("bomb");
                        state.monsters.forEach(m -> { m.takeDamage(500, "MAGIC"); damageTexts.add(new DamageText(m.x, m.y, "-500", Color.RED)); spawnExplosion(m.x, m.y, Color.RED, 15); });
                    }
                    break;
            }
        } catch (Exception e) {
            System.err.println("Gói tin lỗi từ role " + a.getRole() + ": " + e.getMessage());
        }
    }
}