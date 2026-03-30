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
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.*;
import javafx.scene.text.*;
import javafx.stage.Stage;
import javafx.scene.image.Image;

// IMPORT THƯ VIỆN ÂM THANH
import javafx.scene.media.*;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class PCGameClient extends Application {

    private static final int WIDTH = 1125, HEIGHT = 750;
    private static final int LEVEL_DURATION_SECONDS = 180;
    private static final long BUILD_COOLDOWN_MS = 1500;

    private static final Color BG_DARK = Color.web("#0b0b1a"), BG_LIGHT = Color.web("#1a1a2e");
    private static final Color NEON_RED = Color.web("#ff2e63"), NEON_GREEN = Color.web("#08d9d6");
    private static final Color NEON_CYAN = Color.web("#00fff5"), NEON_PURPLE = Color.web("#aa2ee6");
    private static final Color NEON_ORANGE = Color.web("#ff9a3c"), ROAD_COLOR = Color.web("#2a2a40"), ROAD_BORDER = Color.web("#00fff5");

    private GameState state = new GameState();
    private WaveManager waveManager = new WaveManager();
    private NetworkManager network;
    private GraphicsContext gc;

    private StackPane rootPane;
    private HBox topHud, bottomPanel;
    private VBox menuOverlay;
    private ProgressBar buildProgressBar;
    private Map<MonsterType, Button> attackerButtons = new HashMap<>();

    private double animationTime = 0;
    private double mouseX = -1, mouseY = -1;

    private List<DamageText> damageTexts = new ArrayList<>();
    private List<Particle> particles = new ArrayList<>();
    private double bombFlashAlpha = 0;
    private double screenShake = 0;


    // --- QUẢN LÝ NHẠC NỀN (BGM) ---
    private MediaPlayer homeBgmPlayer;
    private MediaPlayer gameBgmPlayer;
    private MediaPlayer endgameBgmPlayer;
    private MediaPlayer victoryBgmPlayer;
    private String currentBgm = "";


    private class SpriteSheet {
        Image sheet;
        int frameCount;
        public SpriteSheet(Image sheet, int frameCount) {
            this.sheet = sheet;
            this.frameCount = frameCount;
        }
    }
    // Bộ nhớ đệm cho Animation
    private Map<String, SpriteSheet> animCache = new HashMap<>();


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
            if (isRipple) {
                this.size = 5; this.maxLife = this.life = 0.5; this.vx = 0; this.vy = 0;
            } else {
                double angle = Math.random() * Math.PI * 2;
                double speed = Math.random() * 4 + 1;
                this.vx = Math.cos(angle) * speed; this.vy = Math.sin(angle) * speed;
                this.maxLife = this.life = Math.random() * 0.4 + 0.2;
                this.size = Math.random() * 4 + 2;
            }
        }
    }

    private Map<String, Image> imageCache = new HashMap<>();
    private Map<String, AudioClip> soundCache = new HashMap<>();

    public static void main(String[] args) { launch(args); }
    // Load các tài nguyên trong game
    private void loadResources() {
        String[] imgNames = { "tower_archer", "tower_mage", "tower_barracks", "tower_cannon", "monster_goblin", "monster_orc", "monster_shaman", "monster_boss", "base_core", "bg_neon" , "map_1", "soldier"};
        for (String name : imgNames) {
            try {
                Image img = new Image(getClass().getResourceAsStream("/static/images/" + name + ".png"));
                if (!img.isError()) imageCache.put(name, img);
            } catch (Exception e) {}
        }

        // LOAD ÂM THANH
        String[] soundNames = { "build", "shoot", "laser", "cannon", "coin", "base_hit", "freeze", "bomb" };
        for (String name : soundNames) {
            try {
                AudioClip clip = new AudioClip(getClass().getResource("/static/sounds/" + name + ".wav").toExternalForm());
                soundCache.put(name, clip);
            } catch (Exception e) {}
        }

        // LOAD NHẠC NỀN (MEDIA PLAYER)
        try {
            homeBgmPlayer = new MediaPlayer(new Media(getClass().getResource("/static/sounds/homesound.wav").toExternalForm()));
            homeBgmPlayer.setCycleCount(MediaPlayer.INDEFINITE); // Lặp vô hạn
            homeBgmPlayer.setVolume(0.3); // Âm lượng 30%
        } catch (Exception e) { System.out.println("Thiếu file homesound.wav"); }
        try {
            gameBgmPlayer = new MediaPlayer(new Media(getClass().getResource("/static/sounds/gamesound.wav").toExternalForm()));
            gameBgmPlayer.setCycleCount(MediaPlayer.INDEFINITE); // Lặp vô hạn
            gameBgmPlayer.setVolume(0.25);
        } catch (Exception e) { System.out.println("Thiếu file gamesound.wav"); }
        try {
            endgameBgmPlayer = new MediaPlayer(new Media(getClass().getResource("/static/sounds/untilendgame.wav").toExternalForm()));
            endgameBgmPlayer.setCycleCount(MediaPlayer.INDEFINITE); // Lặp vô hạn
            endgameBgmPlayer.setVolume(0.25);
        } catch (Exception e) { System.out.println("Thiếu file untilendgame.wav"); }

        try {
            victoryBgmPlayer = new MediaPlayer(new Media(getClass().getResource("/static/sounds/victory.wav").toExternalForm()));
            victoryBgmPlayer.setCycleCount(1);
            victoryBgmPlayer.setVolume(0.4);
        } catch (Exception e) { System.out.println("Thiếu file victory.wav"); }
        try {
            animCache.put("archer_idle", new SpriteSheet(new Image(getClass().getResourceAsStream("/static/images/Archer_Idle.png")), 6));
            animCache.put("archer_run", new SpriteSheet(new Image(getClass().getResourceAsStream("/static/images/Archer_Run.png")), 6));
            animCache.put("archer_shoot", new SpriteSheet(new Image(getClass().getResourceAsStream("/static/images/Archer_Shoot.png")), 8));
            imageCache.put("arrow", new Image(getClass().getResourceAsStream("/static/images/Arrow.png")));
        } catch (Exception e) { System.out.println("Thiếu file ảnh Archer hoặc Arrow"); }
    }
    // HÀM ĐIỀU KHIỂN CHUYỂN BÀI NHẠC NỀN MƯỢT MÀ
    private void playBgm(String type) {
        if (currentBgm.equals(type)) return;
        currentBgm = type;

        // Dừng tất cả nhạc đang phát
        if (homeBgmPlayer != null) homeBgmPlayer.stop();
        if (gameBgmPlayer != null) gameBgmPlayer.stop();
        if (endgameBgmPlayer != null) endgameBgmPlayer.stop();
        if (victoryBgmPlayer != null) victoryBgmPlayer.stop();

        // Bật đúng bài được yêu cầu
        if ("HOME".equals(type) && homeBgmPlayer != null) homeBgmPlayer.play();
        else if ("GAME".equals(type) && gameBgmPlayer != null) gameBgmPlayer.play();
        else if ("ENDGAME".equals(type) && endgameBgmPlayer != null) endgameBgmPlayer.play();
        else if ("VICTORY".equals(type) && victoryBgmPlayer != null) victoryBgmPlayer.play();
    }
    // HÀM PHÁT ÂM THANH
    private void playSound(String name) {
        AudioClip clip = soundCache.get(name);
        if (clip != null) clip.play(0.4); // Phát âm thanh 40% để không chói tai
    }

    private void spawnExplosion(double x, double y, Color c, int count) {
        particles.add(new Particle(x, y, Color.WHITE, true));
        for (int i = 0; i < count; i++) particles.add(new Particle(x, y, c, false));
    }

    @Override
    public void start(Stage stage) {
        loadResources();
        rootPane = new StackPane();
        rootPane.setBackground(new Background(new BackgroundFill(BG_DARK, CornerRadii.EMPTY, Insets.EMPTY)));
        Canvas canvas = new Canvas(WIDTH, HEIGHT);
        gc = canvas.getGraphicsContext2D();

        canvas.setOnMouseClicked(e -> {
            //Test lấy tọa độ
            //System.out.println("state.currentPath.add(new Point2D(" + (int)e.getX() + ", " + (int)e.getY() + "));");
            if ("DEFENDER".equals(state.myRole) && !state.isGameOver && !state.isPaused && !state.isVictory) {
                if (e.getButton() == MouseButton.PRIMARY) {
                    Tower existing = state.towers.stream().filter(t -> t.dist(e.getX(), e.getY()) < 40).findFirst().orElse(null);
                    if (existing != null || isValidBuildSpot(e.getX(), e.getY())) handleBuild(e.getX(), e.getY());
                    else state.message = "VỊ TRÍ KHÔNG HỢP LỆ!";
                }
                else if (e.getButton() == MouseButton.SECONDARY) {
                    state.selectedTower = state.selectedTower==TowerType.ARCHER ? TowerType.MAGE : state.selectedTower==TowerType.MAGE ? TowerType.BARRACKS : state.selectedTower==TowerType.BARRACKS ? TowerType.CANNON : TowerType.ARCHER;
                }
            }
        });

        canvas.setOnMouseMoved(e -> { mouseX = e.getX(); mouseY = e.getY(); });
        canvas.setOnMouseExited(e -> { mouseX = -1; mouseY = -1; });

        Scene scene = new Scene(rootPane, WIDTH, HEIGHT);
        scene.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ESCAPE && state.levelStarted && !state.isGameOver && !state.isVictory) togglePause(); });

        rootPane.getChildren().add(canvas);
        showMainMenu();

        network = new NetworkManager(this::processAction, msg -> {
            Platform.runLater(() -> {
                state.message = msg;
                if (msg.equals("Đã kết nối Server!")) {
                    if ("ATTACKER".equals(state.myRole)) network.sendAction(state.myRole, "REQUEST_MAP", "");
                    else if ("DEFENDER".equals(state.myRole)) syncMapToAttacker();
                }
            });
        }, () -> { if (!state.isGameOver && !state.isVictory) togglePause(); });

        new AnimationTimer() {
            public void handle(long now) {
                if (!state.isPaused) {
                    animationTime += 0.05;
                    if (state.levelStarted && !state.isGameOver && !state.isVictory) {
                        updateLogic(now);
                        if (state.isOfflineMode && "DEFENDER".equals(state.myRole)) waveManager.update(state, network, System.currentTimeMillis());
                        else if (state.isOfflineMode && "ATTACKER".equals(state.myRole)) updateAI(now);
                    }
                }
                render(); updateInterface();
            }
        }.start();

        stage.setTitle("KINGDOM");
        stage.setScene(scene); stage.show();
    }

    // ================= MENUS =================
    private void showMainMenu() {
        playBgm("HOME"); // NHẠC MENU
        rootPane.getChildren().removeIf(node -> node instanceof VBox || node instanceof HBox);
        VBox menu = new VBox(30); menu.setAlignment(Pos.CENTER); menu.setStyle("-fx-background-color: rgba(0,0,0,0.95);");
        Label title = new Label("NEON KINGDOM"); title.setTextFill(NEON_CYAN); title.setFont(Font.font("Impact", 80)); title.setEffect(new DropShadow(20, NEON_CYAN));
        Button btnStart = createStyledButton("START GAME", NEON_GREEN); btnStart.setPrefWidth(300); btnStart.setPrefHeight(50);
        btnStart.setOnAction(e -> showRoleSelection());
        Button btnExit = createStyledButton("EXIT GAME", NEON_RED); btnExit.setPrefWidth(300); btnExit.setPrefHeight(50);
        btnExit.setOnAction(e -> Platform.exit());
        menu.getChildren().addAll(title, btnStart, btnExit); rootPane.getChildren().add(menu);
    }

    private void showRoleSelection() {
        rootPane.getChildren().removeIf(node -> node instanceof VBox || node instanceof HBox);
        VBox menu = new VBox(20); menu.setAlignment(Pos.CENTER); menu.setStyle("-fx-background-color: rgba(0,0,0,0.9);");
        Label title = new Label("CHOOSE YOUR PATH"); title.setTextFill(NEON_CYAN); title.setFont(Font.font("Impact", 50));
        HBox onlineBox = new HBox(20); onlineBox.setAlignment(Pos.CENTER);
        Button btnDef = createStyledButton("ONLINE: DEFENDER", NEON_PURPLE); btnDef.setOnAction(e -> startGame(false, "DEFENDER"));
        Button btnAtk = createStyledButton("ONLINE: ATTACKER", NEON_RED); btnAtk.setOnAction(e -> startGame(false, "ATTACKER"));
        onlineBox.getChildren().addAll(btnDef, btnAtk);
        HBox offlineBox = new HBox(20); offlineBox.setAlignment(Pos.CENTER);
        Button btnOffDef = createStyledButton("OFFLINE: DEFENDER (Wave PvE)", NEON_GREEN); btnOffDef.setOnAction(e -> startGame(true, "DEFENDER"));
        Button btnOffAtk = createStyledButton("OFFLINE: ATTACKER (Time PvE)", NEON_ORANGE); btnOffAtk.setOnAction(e -> startGame(true, "ATTACKER"));
        offlineBox.getChildren().addAll(btnOffDef, btnOffAtk);
        Button btnBack = createStyledButton("<< BACK TO MAIN MENU", Color.GRAY); btnBack.setOnAction(e -> showMainMenu());
        menu.getChildren().addAll(title, new Label("--- MULTIPLAYER ---"), onlineBox, new Label("--- SOLO PLAY ---"), offlineBox, new Label(""), btnBack);
        rootPane.getChildren().add(menu);
    }

    private void startGame(boolean offline, String role) {
        playBgm("GAME"); //BẬT NHẠC GAME
        state.isOfflineMode = offline; state.myRole = role;
        state.resetLevelData(); state.gold = 500; waveManager.initWaves(); state.currentLevel = 1;
        damageTexts.clear(); particles.clear(); screenShake = 0; bombFlashAlpha = 0;

        rootPane.getChildren().removeIf(node -> node instanceof VBox || node instanceof HBox);
        setupGameHUD();

        if (!offline) {
            network.connect();
            if ("ATTACKER".equals(role)) state.message = "Đang xin dữ liệu từ Host...";
            else { generateMap(); new Timer().schedule(new TimerTask() { public void run() { Platform.runLater(()->startLevel()); }}, 1000); }
        } else { generateMap(); startLevel(); }

        if ("ATTACKER".equals(role)) createBottomAttackerPanel();
        else if ("DEFENDER".equals(role)) createBottomShop();
    }

    private int getMaxHp(MonsterType type, int level) { return (int)(type.hp * (1 + (level - 1) * 0.4)); }
    private double getScaledSpeed(MonsterType type, int level) { return type.speed * (1 + (level - 1) * 0.15); }

    private void generateMap() {
        state.currentPath.clear();
        state.currentPath.add(new Point2D(99, 654));
        state.currentPath.add(new Point2D(70, 592));
        state.currentPath.add(new Point2D(35, 563));
        state.currentPath.add(new Point2D(11, 518));
        state.currentPath.add(new Point2D(5, 478));
        state.currentPath.add(new Point2D(21, 428));
        state.currentPath.add(new Point2D(75, 410));
        state.currentPath.add(new Point2D(138, 417));
        state.currentPath.add(new Point2D(186, 440));
        state.currentPath.add(new Point2D(233, 472));
        state.currentPath.add(new Point2D(293, 493));
        state.currentPath.add(new Point2D(376, 494));
        state.currentPath.add(new Point2D(438, 467));
        state.currentPath.add(new Point2D(469, 412));
        state.currentPath.add(new Point2D(448, 348));
        state.currentPath.add(new Point2D(388, 317));
        state.currentPath.add(new Point2D(329, 269));
        state.currentPath.add(new Point2D(327, 214));
        state.currentPath.add(new Point2D(390, 162));
        state.currentPath.add(new Point2D(489, 151));
        state.currentPath.add(new Point2D(590, 150));
        state.currentPath.add(new Point2D(663, 172));
        state.currentPath.add(new Point2D(693, 208));
        state.currentPath.add(new Point2D(706, 256));
        state.currentPath.add(new Point2D(689, 293));
        state.currentPath.add(new Point2D(668, 340));
        state.currentPath.add(new Point2D(640, 399));
        state.currentPath.add(new Point2D(638, 443));
        state.currentPath.add(new Point2D(707, 456));
        state.currentPath.add(new Point2D(807, 442));
        state.currentPath.add(new Point2D(879, 411));
        state.currentPath.add(new Point2D(959, 412));
        state.currentPath.add(new Point2D(1028, 469));
        state.currentPath.add(new Point2D(1008, 550));
        state.currentPath.add(new Point2D(948, 620));
        state.currentPath.add(new Point2D(945, 664));
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
        for (Tower t : state.towers) { if (t.dist(x, y) > 0 && t.dist(x, y) < 40) return false; } // Cấm đè lên trụ khác
        if (y > HEIGHT - 90) return false; // Cấm đè lên thanh Shop

        if (state.selectedTower == TowerType.BARRACKS) return true; // Trụ lính (Barracks) được phép thả lính ra đường

        // KIỂM TRA CẤM XÂY TRÊN ĐƯỜNG ĐẤT (Bán kính 85px)
        for (int i = 0; i < state.currentPath.size() - 1; i++) {
            Point2D p1 = state.currentPath.get(i), p2 = state.currentPath.get(i+1);
            if (distToSegment(x, y, p1.getX(), p1.getY(), p2.getX(), p2.getY()) < 85) return false;
        }
        return true;
    }

    // --- GAME LOGIC ---
    private void updateLogic(long now) {
        if (state.isVictory) { handleVictory(); return; }
        if (state.isGameOver) { handleDefeat(); return; }
        // Wave 4 trở đi, đổi sang nhạc Dồn dập
        if (state.currentLevel >= 4 && !currentBgm.equals("ENDGAME")) {
            playBgm("ENDGAME");
        }
        if (!(state.isOfflineMode && "DEFENDER".equals(state.myRole))) {
            long elapsed = (System.currentTimeMillis() - state.levelStartTime) / 1000;
            if (LEVEL_DURATION_SECONDS - elapsed <= 0) {
                if ("DEFENDER".equals(state.myRole)) { if (state.baseHp > 0) state.isVictory = true; }
                else if ("ATTACKER".equals(state.myRole)) { if (state.baseHp > 0) state.isGameOver = true; }
                return;
            }
        }

        damageTexts.removeIf(dt -> { dt.y -= 0.5; dt.life -= 0.02; return dt.life <= 0; });
        particles.removeIf(p -> {
            if(p.isRipple) p.size += 2;
            else { p.x += p.vx; p.y += p.vy; }
            p.life -= 0.02; return p.life <= 0;
        });
        if (screenShake > 0) screenShake -= 0.5;

        for(Soldier s : state.soldiers) {
            if (s.engaging==null && s.hp<100 && now%20==0) s.hp++;
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

            double baseSpeed = getScaledSpeed(m.type, state.currentLevel);
            double currentSpeed = m.frozenTimer > 0 ? baseSpeed * 0.1 : baseSpeed;

            if (m.type == MonsterType.SHAMAN && Math.random() < 0.02) {
                for(Monster ally : state.monsters) {
                    if (ally != m && ally.dist(m.x, m.y) < 80) {
                        ally.hp = Math.min(ally.hp + 20, getMaxHp(ally.type, state.currentLevel));
                        damageTexts.add(new DamageText(ally.x, ally.y, "+20", NEON_GREEN));
                    }
                }
            }
            if (m.type == MonsterType.BOSS && Math.random() < 0.005) {
                Monster minion = new Monster(MonsterType.GOBLIN, state.currentPath);
                minion.x = m.x + ThreadLocalRandom.current().nextInt(-20, 20); minion.y = m.y + ThreadLocalRandom.current().nextInt(-20, 20);
                minion.hp = getMaxHp(MonsterType.GOBLIN, state.currentLevel); minion.pathIdx = m.pathIdx; spawnedThisFrame.add(minion);
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

                    playSound("base_hit"); // ÂM THANH NHÀ CHÍNH BỊ CẮN
                    screenShake = (m.type == MonsterType.BOSS) ? 15 : 5;
                    damageTexts.add(new DamageText(WIDTH-120, HEIGHT/2, "-" + damageToCore, NEON_RED));
                    spawnExplosion(WIDTH-120, HEIGHT/2, NEON_RED, 15);

                    if (state.baseHp <= 0) {
                        state.baseHp = 0;
                        if ("DEFENDER".equals(state.myRole)) handleDefeat(); else if ("ATTACKER".equals(state.myRole)) handleVictory();
                    }
                    if(!state.isOfflineMode) network.sendAction(state.myRole, "SYNC_HP", state.baseHp+"");
                }
                it.remove();
                if (state.baseHp <= 0) return;
                continue;
            }
            if(m.hp <= 0) {
                state.gold += m.type.reward;
                damageTexts.add(new DamageText(m.x, m.y, "+$" + m.type.reward, Color.YELLOW));
                spawnExplosion(m.x, m.y, m.type.color, 10);
                playSound("coin"); // ÂM THANH RỚT TIỀN
                it.remove();
            }
        }
        state.monsters.addAll(spawnedThisFrame);

        state.projectiles.clear();
        for(Tower t : state.towers) {
            if(t.type==TowerType.BARRACKS) continue;
            if(t.target==null || t.target.hp<=0 || t.dist(t.target.x, t.target.y)>t.getRange()) t.target = state.monsters.stream().filter(m->t.dist(m.x,m.y)<=t.getRange()).min(Comparator.comparingDouble(m->t.dist(m.x,m.y))).orElse(null);

            if(t.target!=null && now-t.lastAtk > t.getCooldown()*1e9) {
                state.projectiles.add(new Projectile(t.x, t.y-20, t.target.x, t.target.y, t.type.color));

                if (t.type == TowerType.CANNON) {
                    playSound("cannon"); // ÂM THANH PHÁO
                    spawnExplosion(t.target.x, t.target.y, NEON_ORANGE, 12);
                    for(Monster m2 : state.monsters) {
                        if (m2.dist(t.target.x, t.target.y) < 60) {
                            m2.takeDamage(t.getDamage(), "PHYSICAL");
                            damageTexts.add(new DamageText(m2.x, m2.y, "-" + t.getDamage(), t.type.color));
                        }
                    }
                } else {
                    playSound(t.type == TowerType.MAGE ? "laser" : "shoot"); // ÂM THANH BẮN THƯỜNG / LASER
                    t.target.takeDamage(t.getDamage(), t.type==TowerType.MAGE?"MAGIC":"PHYSICAL");
                    damageTexts.add(new DamageText(t.target.x, t.target.y, "-" + t.getDamage(), t.type.color));
                    spawnExplosion(t.target.x, t.target.y, t.type.color, 5);
                }

                if (t.type == TowerType.MAGE) {
                    int chance = ThreadLocalRandom.current().nextInt(100);
                    if (chance < 20) t.target.frozenTimer = 60;
                    else if (chance < 40) t.target.burnTimer = 60;
                }
                t.lastAtk=now;
            }
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
                    playSound("build"); // ÂM THANH XÂY DỰNG
                    state.gold -= upgradeCost; existingTower.upgrade();
                    state.message = "UPGRADED TO LV." + existingTower.level; state.lastBuildTime = now;
                    damageTexts.add(new DamageText(x, y, "-$" + upgradeCost, Color.YELLOW));
                    if(!state.isOfflineMode) network.sendAction(state.myRole, "UPGRADE", existingTower.x+","+existingTower.y);
                } else state.message = "NOT ENOUGH GOLD OR MAX LEVEL!";
            } else {
                if (state.gold >= state.selectedTower.cost) {
                    playSound("build");
                    int refundAmount = existingTower.type.cost / 2;
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
                playSound("build");
                state.gold -= state.selectedTower.cost;
                Tower t = new Tower(state.selectedTower,x,y); state.towers.add(t);
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

    // --- PAUSE & ENDGAME MENUS ---
    private void togglePause() {
        state.isPaused = !state.isPaused;
        if (state.isPaused) showPauseMenu(); else if (menuOverlay != null) rootPane.getChildren().remove(menuOverlay);
    }
    private void handleVictory() {
        state.isVictory = true;
        playBgm("VICTORY"); // <--- THÊM DÒNG NÀY ĐỂ ĐỔI NHẠC KHI WIN
        if (!state.isOfflineMode) network.sendAction(state.myRole, "VICTORY", "");
        Platform.runLater(() -> showEndGameMenu(true));
    }
    private void handleDefeat() {
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

    private void showEndGameMenu(boolean victory) {
        if (menuOverlay != null && rootPane.getChildren().contains(menuOverlay)) rootPane.getChildren().remove(menuOverlay);
        menuOverlay = new VBox(20); menuOverlay.setAlignment(Pos.CENTER); menuOverlay.setStyle("-fx-background-color: rgba(0,0,0,0.85);");
        Label lbl = createHUDLabel(victory ? "VICTORY!" : "DEFEAT", victory ? NEON_GREEN : NEON_RED); lbl.setFont(Font.font("Impact", 80));
        HBox buttons = new HBox(20); buttons.setAlignment(Pos.CENTER);
        Button btnReplay = createStyledButton("REPLAY", NEON_ORANGE);
        btnReplay.setOnAction(e -> { rootPane.getChildren().remove(menuOverlay); state.resetLevelData(); state.gold = 500; waveManager.initWaves(); generateMap(); startLevel(); });
        Button btnQuit = createStyledButton("MAIN MENU", NEON_RED); btnQuit.setOnAction(e -> returnToMainMenu());
        buttons.getChildren().addAll(btnReplay, btnQuit);
        if (victory && (state.isOfflineMode && "DEFENDER".equals(state.myRole))) {
            Button btnNext = createStyledButton("NEXT MAP >>", NEON_CYAN);
            btnNext.setOnAction(e -> { rootPane.getChildren().remove(menuOverlay); state.currentLevel++; state.resetLevelData(); state.gold = 500; waveManager.initWaves(); generateMap(); startLevel(); });
            buttons.getChildren().add(btnNext);
        }
        menuOverlay.getChildren().addAll(lbl, buttons); rootPane.getChildren().add(menuOverlay);
    }

    private void returnToMainMenu() { state.levelStarted = false; state.resetLevelData(); showMainMenu(); }

    // --- RENDER ---
    private void render() {
        gc.save();
        if (screenShake > 0) {
            double dx = (Math.random() - 0.5) * screenShake; double dy = (Math.random() - 0.5) * screenShake; gc.translate(dx, dy);
        }

        Image bgImg = imageCache.get("map_1");
        if(bgImg != null) gc.drawImage(bgImg, 0, 0, WIDTH, HEIGHT);
        else {
            gc.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE, new Stop(0, BG_DARK), new Stop(1, BG_LIGHT))); gc.fillRect(0, 0, WIDTH, HEIGHT);
            gc.setStroke(Color.rgb(255,255,255,0.02)); gc.setLineWidth(1);
            for(int i=0;i<WIDTH;i+=40) gc.strokeLine(i,0,i,HEIGHT); for(int i=0;i<HEIGHT;i+=40) gc.strokeLine(0,i,WIDTH,i);
        }

        if (state.currentPath.isEmpty()) { gc.restore(); return; }

        Color[] stageColors = {NEON_CYAN, NEON_PURPLE, NEON_ORANGE, NEON_GREEN, NEON_RED};
        Color currentMapColor = stageColors[(state.currentLevel - 1) % stageColors.length];

//        gc.setLineCap(StrokeLineCap.ROUND); gc.setLineJoin(StrokeLineJoin.ROUND); gc.setEffect(new DropShadow(20, currentMapColor));
//        gc.setStroke(currentMapColor); gc.setLineWidth(60); gc.beginPath(); gc.moveTo(state.currentPath.get(0).getX(), state.currentPath.get(0).getY()); for(Point2D p : state.currentPath) gc.lineTo(p.getX(), p.getY()); gc.stroke();
//        gc.setEffect(null); gc.setStroke(ROAD_COLOR); gc.setLineWidth(50); gc.beginPath(); gc.moveTo(state.currentPath.get(0).getX(), state.currentPath.get(0).getY()); for(Point2D p : state.currentPath) gc.lineTo(p.getX(), p.getY()); gc.stroke();

        Point2D end = state.currentPath.get(state.currentPath.size()-1); drawBase(end.getX(), end.getY());

        for(Tower t : state.towers) drawTower(t);
        for(Soldier s : state.soldiers) { drawSoldier(s.x, s.y); renderModernBar(s.x, s.y-25, s.hp, 100, 30, NEON_CYAN); }

        try {
            for(Monster m : state.monsters) {
                drawMonster(m); double size = m.type==MonsterType.BOSS?60:40;
                renderModernBar(m.x, m.y - size/2 - 15, m.hp, getMaxHp(m.type, state.currentLevel), size, NEON_RED);
                if (m.frozenTimer > 0) { gc.setEffect(new DropShadow(10, NEON_CYAN)); gc.setStroke(NEON_CYAN); gc.setLineWidth(2); gc.strokeOval(m.x - size/2 - 5, m.y - size/2 - 5, size + 10, size + 10); gc.setEffect(null); }
                else if (m.burnTimer > 0) { gc.setEffect(new DropShadow(10, Color.ORANGE)); gc.setStroke(Color.ORANGE); gc.setLineWidth(2); gc.strokeOval(m.x - size/2 - 5, m.y - size/2 - 5, size + 10, size + 10); gc.setEffect(null); }
            }
        } catch(ConcurrentModificationException ignored) {}

        for (Particle p : particles) {
            gc.setGlobalAlpha(p.life / p.maxLife);
            if (p.isRipple) { gc.setStroke(p.color); gc.setLineWidth(2); gc.strokeOval(p.x - p.size/2, p.y - p.size/2, p.size, p.size); }
            else { gc.setFill(p.color); gc.fillOval(p.x, p.y, p.size, p.size); }
        }
        gc.setGlobalAlpha(1.0);

        gc.setEffect(new Glow(1.0));
        Image arrowImg = imageCache.get("arrow");
        for(Projectile p : state.projectiles) {
            gc.setStroke(p.c);
            if(p.c.equals(TowerType.MAGE.color)) {
                gc.setLineWidth(6); gc.strokeLine(p.sx, p.sy, p.ex, p.ey); gc.setStroke(Color.WHITE); gc.setLineWidth(2); gc.strokeLine(p.sx, p.sy, p.ex, p.ey);
            } else if(p.c.equals(TowerType.CANNON.color)) {
                gc.setFill(p.c); gc.fillOval(p.sx + (p.ex-p.sx)*0.5 - 8, p.sy + (p.ey-p.sy)*0.5 - 8, 16, 16);
            } else {
                // Cung Thủ: Nếu có ảnh, vẽ ảnh mũi tên bay có xoay góc cực đẹp
                if (arrowImg != null) {
                    double angle = Math.atan2(p.ey - p.sy, p.ex - p.sx) * 180 / Math.PI;
                    gc.save();
                    gc.translate(p.sx + (p.ex-p.sx)*0.5, p.sy + (p.ey-p.sy)*0.5); // Canh ở giữa đường đạn
                    gc.rotate(angle);
                    gc.drawImage(arrowImg, -15, -5, 30, 10);
                    gc.restore();
                } else {
                    // Nếu thiếu ảnh, vẽ tạm một đường line to cho dễ nhìn
                    gc.setLineWidth(4); gc.strokeLine(p.sx, p.sy, p.ex, p.ey);
                }
            }
        }
        gc.setEffect(null);

        gc.setFont(Font.font("Consolas", FontWeight.BOLD, 18));
        for (DamageText dt : damageTexts) {
            gc.setGlobalAlpha(dt.life); gc.setFill(dt.color); gc.setEffect(new DropShadow(3, Color.BLACK)); gc.fillText(dt.text, dt.x - 10, dt.y - 10); gc.setEffect(null);
        }
        gc.setGlobalAlpha(1.0);

        if (mouseX >= 0 && mouseY >= 0 && "DEFENDER".equals(state.myRole) && !state.isPaused && !state.isGameOver && !state.isVictory && state.levelStarted) {
            Tower existing = state.towers.stream().filter(t -> t.dist(mouseX, mouseY) < 40).findFirst().orElse(null);
            if (existing != null) {
                gc.setStroke(Color.rgb(255, 255, 0, 0.5)); gc.setLineWidth(2); gc.strokeOval(existing.x - existing.getRange(), existing.y - existing.getRange(), existing.getRange() * 2, existing.getRange() * 2);
            } else {
                boolean isValid = isValidBuildSpot(mouseX, mouseY);
                Color previewColor = isValid ? Color.rgb(0, 255, 0, 0.3) : Color.rgb(255, 0, 0, 0.5);
                gc.setFill(Color.rgb(255, 255, 255, 0.05)); gc.fillOval(mouseX - state.selectedTower.range, mouseY - state.selectedTower.range, state.selectedTower.range * 2, state.selectedTower.range * 2);
                gc.setStroke(previewColor); gc.setLineWidth(2); gc.strokeOval(mouseX - state.selectedTower.range, mouseY - state.selectedTower.range, state.selectedTower.range * 2, state.selectedTower.range * 2);

                Image previewImg = imageCache.get("tower_" + state.selectedTower.name().toLowerCase());
                if (previewImg != null) { gc.setGlobalAlpha(0.5); gc.drawImage(previewImg, mouseX - 30, mouseY - 40, 60, 80); gc.setGlobalAlpha(1.0); }
                else { gc.setFill(previewColor); gc.fillOval(mouseX-25, mouseY-20, 50, 30); }
            }
        }
        gc.restore();

        if (bombFlashAlpha > 0) {
            gc.setFill(Color.rgb(255, 255, 255, bombFlashAlpha)); gc.fillRect(0, 0, WIDTH, HEIGHT);
            bombFlashAlpha -= 0.05;
        }

        if (state.isPaused) { gc.setFill(Color.rgb(0,0,0,0.5)); gc.fillRect(0,0,WIDTH,HEIGHT); }
    }

    private void drawBase(double x, double y) {
        Image img = imageCache.get("base_core");
        if (img != null) {
            gc.setEffect(new DropShadow(20, NEON_CYAN));
            // Tăng chiều cao và dời tâm lên trên để Tower không bị lún xuống đất
            gc.drawImage(img, x - 45, y - 100, 90, 130);
            gc.setEffect(null);
        }
        else {
            gc.setEffect(new DropShadow(15, NEON_CYAN)); gc.setFill(Color.web("#333")); gc.fillRect(x-50, y-40, 100, 80);
            gc.setFill(new LinearGradient(0,0,0,1, true, CycleMethod.NO_CYCLE, new Stop(0, NEON_CYAN), new Stop(1, Color.TRANSPARENT))); gc.fillOval(x-30, y-20, 60, 60);
            gc.save(); gc.translate(x, y+10); gc.rotate(animationTime * 50); gc.setStroke(Color.WHITE); gc.setLineWidth(3); gc.strokeRect(-15, -15, 30, 30); gc.restore(); gc.setEffect(null);
            gc.setFill(Color.WHITE); gc.setFont(Font.font("Impact", 18)); gc.fillText("CORE", x-18, y-45);
        }
        // Đẩy thanh HP lên cao một chút cho khỏi vướng nhà chính
        renderModernBar(x, y - 45, state.baseHp, 2000, 80, state.baseHp < 500 ? NEON_RED : NEON_GREEN);
    }

    private void drawTower(Tower t) {
        double x = t.x, y = t.y;
        if (t.type == TowerType.ARCHER) {
            long elapsedNanos = System.nanoTime() - t.lastAtk;
            boolean isShooting = elapsedNanos < 400_000_000L;

            SpriteSheet anim = isShooting ? animCache.get("archer_shoot") : animCache.get("archer_idle");

            if (anim != null && anim.sheet != null) {
                int frameIndex;
                if (isShooting) {
                    double progress = elapsedNanos / 400_000_000.0;
                    frameIndex = Math.max(0, Math.min(anim.frameCount - 1, (int)(progress * anim.frameCount)));
                } else {
                    frameIndex = (int)(animationTime * 8) % anim.frameCount;
                }

                double frameWidth = anim.sheet.getWidth() / anim.frameCount;
                double frameHeight = anim.sheet.getHeight();
                double sx = frameIndex * frameWidth;
                boolean flip = t.target != null && t.target.x < t.x;

                gc.save();
                gc.translate(x, y - 20);
                if (flip) gc.scale(-1, 1);
                gc.drawImage(anim.sheet, sx, 0, frameWidth, frameHeight, -90, -90, 180, 180);
                gc.restore();

                gc.setFill(Color.WHITE); gc.setFont(Font.font("Consolas", FontWeight.BOLD, 14)); gc.fillText("Lv." + t.level, x-15, y+20);
                return;
            }
        }

        // --- CÁC TRỤ KHÁC ---
        Image img = imageCache.get("tower_" + t.type.name().toLowerCase());

        if (System.nanoTime() - t.lastAtk < 100_000_000L) gc.setEffect(new Glow(0.8));
        else gc.setEffect(new DropShadow(15, t.type.color));

        if (img != null) {
            // ĐÃ THÊM: Nếu là Trại lính (BARRACKS) thì vẽ to hơn và vuông hơn
            if (t.type == TowerType.BARRACKS) {
                gc.drawImage(img, x - 45, y - 45, 90, 90);
            } else {
                gc.drawImage(img, x - 30, y - 40, 60, 80);
            }

            gc.setEffect(null);
            gc.setFill(Color.WHITE); gc.setFont(Font.font("Consolas", FontWeight.BOLD, 14)); gc.fillText("Lv." + t.level, x-15, y+20);
            return;
        }

        gc.setFill(Color.web("#222")); gc.fillOval(x-25, y-20, 50, 30); gc.setFill(t.type.color.darker()); gc.fillRect(x-20, y-25, 40, 15);
        if (t.type == TowerType.MAGE) { gc.setFill(t.type.color.darker()); gc.fillPolygon(new double[]{x-15, x+15, x}, new double[]{y-10, y-10, y-60}, 3); gc.setFill(t.type.color); gc.fillOval(x-12, y-72 + Math.sin(animationTime*2)*5, 24, 24); }
        else if (t.type == TowerType.BARRACKS) { gc.setFill(t.type.color.darker()); gc.fillRect(x-25, y-30, 50, 25); gc.setFill(t.type.color); gc.fillArc(x-20, y-45, 40, 30, 0, 180, ArcType.ROUND); gc.setFill(Color.BLACK); gc.fillRect(x-8, y-25, 16, 15); }
        else if (t.type == TowerType.CANNON) { gc.setFill(t.type.color.darker()); gc.fillRect(x-15, y-40, 30, 20); gc.setFill(t.type.color); gc.fillOval(x-12, y-45, 24, 24); gc.setStroke(Color.BLACK); gc.setLineWidth(6); gc.strokeLine(x, y-35, x+15, y-50); }
        gc.setEffect(null); gc.setFill(Color.WHITE); gc.setFont(Font.font("Consolas", FontWeight.BOLD, 12)); gc.fillText("Lv." + t.level, x-12, y+5);
    }
    private void drawMonster(Monster m) {
        double x = m.x, y = m.y;
        Image img = imageCache.get("monster_" + m.type.name().toLowerCase());
        if (img != null) {
            double size = m.type == MonsterType.BOSS ? 80 : 40;
            gc.drawImage(img, x - size/2, y - size/2, size, size);
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
        topHud = new HBox(30); topHud.setPadding(new Insets(15)); topHud.setAlignment(Pos.CENTER); topHud.setStyle("-fx-background-color: rgba(20,20,30,0.8); -fx-background-radius: 0 0 20 20;"); topHud.setMaxHeight(60); StackPane.setAlignment(topHud, Pos.TOP_CENTER);
        Button btnPause = createStyledButton("||", Color.YELLOW); btnPause.setPrefWidth(40); btnPause.setOnAction(e -> togglePause());
        rootPane.getChildren().addAll(topHud, btnPause); StackPane.setAlignment(btnPause, Pos.TOP_RIGHT); StackPane.setMargin(btnPause, new Insets(10));
    }

    private void createBottomShop() {
        bottomPanel = new HBox(20); bottomPanel.setPadding(new Insets(10, 20, 10, 20)); bottomPanel.setAlignment(Pos.CENTER); bottomPanel.setMaxHeight(80);
        bottomPanel.setStyle("-fx-background-color: rgba(20,20,30,0.9); -fx-background-radius: 20 20 0 0; -fx-border-color: #00fff5; -fx-border-width: 2 2 0 2;");
        StackPane.setAlignment(bottomPanel, Pos.BOTTOM_CENTER);

        Label lbl = new Label("SHOP"); lbl.setTextFill(NEON_CYAN); lbl.setFont(Font.font("Impact", 24)); bottomPanel.getChildren().add(lbl);
        for(TowerType t : TowerType.values()) {
            Button b = createStyledButton(t.name() + " ($" + t.cost + ")", t.color); b.setPrefWidth(120); b.setPrefHeight(40);
            b.setOnAction(e -> state.selectedTower = t); bottomPanel.getChildren().add(b);
        }
        Label lblSkill = new Label("  SKILLS"); lblSkill.setTextFill(NEON_ORANGE); lblSkill.setFont(Font.font("Impact", 24)); bottomPanel.getChildren().add(lblSkill);
        Button btnFreeze = createStyledButton("❄ FREEZE ($100)", NEON_CYAN); btnFreeze.setPrefWidth(140); btnFreeze.setPrefHeight(40); btnFreeze.setOnAction(e -> activateSkill("FREEZE", 100));
        Button btnBomb = createStyledButton("💣 BOMB ($150)", NEON_ORANGE); btnBomb.setPrefWidth(140); btnBomb.setPrefHeight(40); btnBomb.setOnAction(e -> activateSkill("BOMB", 150));
        bottomPanel.getChildren().addAll(btnFreeze, btnBomb); rootPane.getChildren().add(bottomPanel);
    }

    private void createBottomAttackerPanel() {
        bottomPanel = new HBox(20); bottomPanel.setPadding(new Insets(10, 20, 10, 20)); bottomPanel.setAlignment(Pos.CENTER); bottomPanel.setMaxHeight(80);
        bottomPanel.setStyle("-fx-background-color: rgba(20,20,30,0.9); -fx-background-radius: 20 20 0 0; -fx-border-color: #e94560; -fx-border-width: 2 2 0 2;");
        StackPane.setAlignment(bottomPanel, Pos.BOTTOM_CENTER);
        Label lbl = new Label("UNITS"); lbl.setTextFill(NEON_RED); lbl.setFont(Font.font("Impact", 24)); bottomPanel.getChildren().add(lbl);
        for(MonsterType t : MonsterType.values()) {
            Button b = createStyledButton(t.name() + " (" + t.cooldown + "s)", t.color); b.setPrefWidth(140); b.setPrefHeight(40);
            b.setOnAction(e -> spawnMonster(t)); attackerButtons.put(t, b); bottomPanel.getChildren().add(b);
        }
        rootPane.getChildren().add(bottomPanel);
    }

    private void updateInterface() {
        if (topHud != null) {
            topHud.getChildren().clear();
            if (state.isOfflineMode && "DEFENDER".equals(state.myRole)) {
                int currentWaveIdx = (waveManager.currentWave != null) ? waveManager.currentWave.waveIndex : state.totalWaves;
                String waveStr = state.restTimeRemaining > 0 ? "REST: " + state.restTimeRemaining + "s" : "MAP " + state.currentLevel + " - WAVE " + currentWaveIdx + "/5";
                topHud.getChildren().add(createHUDLabel(waveStr, state.restTimeRemaining > 0 ? NEON_ORANGE : NEON_GREEN));
            } else {
                long elapsed = (System.currentTimeMillis() - state.levelStartTime) / 1000;
                long remaining = Math.max(0, LEVEL_DURATION_SECONDS - elapsed);
                String timeStr = String.format("TIME: %02d:%02d", remaining / 60, remaining % 60);
                topHud.getChildren().add(createHUDLabel(timeStr, remaining < 30 ? NEON_RED : NEON_GREEN));
            }
            topHud.getChildren().addAll(createHUDLabel("CORE: " + state.baseHp, state.baseHp<500?NEON_RED:NEON_GREEN), createHUDLabel("GOLD: " + state.gold, Color.YELLOW));
            if ("DEFENDER".equals(state.myRole)) {
                Label lblBuild = createHUDLabel("BUILD: " + state.selectedTower.name, state.selectedTower.color);
                buildProgressBar = new ProgressBar(1.0); buildProgressBar.setPrefWidth(100); buildProgressBar.setStyle("-fx-accent: #00fff5;");
                buildProgressBar.setProgress(Math.min(1.0, (double)(System.currentTimeMillis() - state.lastBuildTime) / BUILD_COOLDOWN_MS));
                topHud.getChildren().addAll(lblBuild, buildProgressBar);
            }
            topHud.getChildren().add(createHUDLabel(state.message, Color.WHITE));
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
        String style = "-fx-background-color:rgba(0,0,0,0.5);-fx-border-color:"+hex+";-fx-border-width:2;-fx-border-radius:10;-fx-text-fill:white;-fx-font-family:'Consolas';-fx-font-weight:bold;-fx-font-size:13px;-fx-cursor:hand;";
        btn.setStyle(style); btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color:"+hex+";-fx-background-radius:10;-fx-text-fill:black;-fx-font-family:'Consolas';-fx-font-weight:bold;-fx-font-size:13px;-fx-cursor:hand;")); btn.setOnMouseExited(e -> btn.setStyle(style)); return btn;
    }
    private Label createHUDLabel(String text, Color color) { Label l = new Label(text); l.setTextFill(color); l.setFont(Font.font("Consolas", FontWeight.BOLD, 16)); l.setEffect(new DropShadow(5, color)); return l; }

    // --- NETWORK & SKILLS ---
    private void activateSkill(String skillName, int cost) {
        if (state.gold >= cost) {
            state.gold -= cost;
            if (!state.isOfflineMode) network.sendAction(state.myRole, "SKILL", skillName);
            else processAction(new GameAction(state.myRole, "SKILL", skillName));
            playSound(skillName.toLowerCase()); // PHÁT Âm thanh Freeze / Bomb
        } else { state.message = "NOT ENOUGH GOLD FOR SKILL!"; }
    }

    private void spawnMonster(MonsterType t) {
        long now = System.currentTimeMillis();
        if (state.isOfflineMode && "DEFENDER".equals(state.myRole)) { state.monsters.add(new Monster(t, state.currentPath)); return; }
        if(now - state.cooldowns.getOrDefault(t,0L) > t.cooldown*1000L) {
            state.cooldowns.put(t,now);
            if (state.isOfflineMode) { Monster m = new Monster(t, state.currentPath); m.hp = getMaxHp(t, state.currentLevel); state.monsters.add(m); }
            else network.sendAction(state.myRole, "SPAWN", t.name());
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

    private void processAction(GameAction a) {
        String[] d = a.getData().split(",");
        switch(a.getActionType()) {
            case "REQUEST_MAP": if ("DEFENDER".equals(state.myRole)) syncMapToAttacker(); break;
            case "SYNC_MAP":
                state.currentPath.clear(); state.monsters.clear(); state.towers.clear(); state.projectiles.clear(); state.soldiers.clear();
                for(String s : a.getData().split(",")) { String[] xy = s.split(":"); if(xy.length==2) state.currentPath.add(new Point2D(Double.parseDouble(xy[0]), Double.parseDouble(xy[1]))); }
                break;
            case "NEW_LEVEL": state.levelStarted = true; state.levelStartTime = System.currentTimeMillis(); break;
            case "SPAWN":
                if(!state.currentPath.isEmpty()) { MonsterType type = MonsterType.valueOf(a.getData()); Monster m = new Monster(type, state.currentPath); m.hp = getMaxHp(type, state.currentLevel); state.monsters.add(m); } break;
            case "BUILD": TowerType t = TowerType.valueOf(d[0]); double x = Double.parseDouble(d[1]), y = Double.parseDouble(d[2]); Tower newT = new Tower(t,x,y); state.towers.add(newT); if(t==TowerType.BARRACKS) spawnSoldiers(newT); playSound("build"); break;
            case "UPGRADE": double ux = Double.parseDouble(d[0]), uy = Double.parseDouble(d[1]); state.towers.stream().filter(tow->tow.dist(ux,uy)<10).forEach(Tower::upgrade); playSound("build"); break;
            case "REPLACE": TowerType rt = TowerType.valueOf(d[0]); double rx = Double.parseDouble(d[1]), ry = Double.parseDouble(d[2]); Tower oldTower = state.towers.stream().filter(tow->tow.dist(rx,ry)<10).findFirst().orElse(null); if(oldTower!=null) { state.towers.remove(oldTower); if(oldTower.type==TowerType.BARRACKS) state.soldiers.removeIf(s->s.owner==oldTower); } Tower repT = new Tower(rt,rx,ry); state.towers.add(repT); if(rt==TowerType.BARRACKS) spawnSoldiers(repT); playSound("build"); break;
            case "SYNC_HP": state.baseHp = Integer.parseInt(a.getData()); if(state.baseHp <= 0) { state.baseHp = 0; if ("DEFENDER".equals(state.myRole)) handleDefeat(); else if ("ATTACKER".equals(state.myRole)) handleVictory(); } break;
            case "GAME_OVER": handleDefeat(); break;
            case "VICTORY": handleVictory(); break;
            case "SKILL":
                if (a.getData().equals("FREEZE")) { state.monsters.forEach(m -> m.frozenTimer = 100); state.message = "FREEZE ACTIVATED!"; playSound("freeze"); }
                else if (a.getData().equals("BOMB")) {
                    bombFlashAlpha = 1.0; screenShake = 20; state.message = "BOMB ACTIVATED!"; playSound("bomb");
                    state.monsters.forEach(m -> { m.takeDamage(500, "MAGIC"); damageTexts.add(new DamageText(m.x, m.y, "-500", Color.RED)); spawnExplosion(m.x, m.y, Color.RED, 15); });
                }
                break;
        }
    }
}