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

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class PCGameClient extends Application {

    private static final int WIDTH = 1200, HEIGHT = 750;
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
    private HBox topHud;
    private VBox menuOverlay, attackerPanel;
    private ProgressBar buildProgressBar;
    private Map<MonsterType, Button> attackerButtons = new HashMap<>();
    private double animationTime = 0;
    private double mouseX = -1, mouseY = -1;

    public static void main(String[] args) { launch(args); }

    @Override
    public void start(Stage stage) {
        rootPane = new StackPane();
        rootPane.setBackground(new Background(new BackgroundFill(BG_DARK, CornerRadii.EMPTY, Insets.EMPTY)));
        Canvas canvas = new Canvas(WIDTH, HEIGHT);
        gc = canvas.getGraphicsContext2D();

        canvas.setOnMouseClicked(e -> {
            if ("DEFENDER".equals(state.myRole) && !state.isGameOver && !state.isPaused && !state.isVictory) {
                if (e.getButton() == MouseButton.PRIMARY) {
                    Tower existing = state.towers.stream().filter(t -> t.dist(e.getX(), e.getY()) < 40).findFirst().orElse(null);
                    if (existing != null || isValidBuildSpot(e.getX(), e.getY())) {
                        handleBuild(e.getX(), e.getY());
                    } else {
                        state.message = "VỊ TRÍ KHÔNG HỢP LỆ!";
                    }
                }
                else if (e.getButton() == MouseButton.SECONDARY) {
                    // Đã update xoay vòng 4 loại trụ
                    state.selectedTower = state.selectedTower==TowerType.ARCHER ? TowerType.MAGE :
                            state.selectedTower==TowerType.MAGE ? TowerType.BARRACKS :
                                    state.selectedTower==TowerType.BARRACKS ? TowerType.CANNON : TowerType.ARCHER;
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

                        if (state.isOfflineMode && "DEFENDER".equals(state.myRole)) {
                            waveManager.update(state, network, System.currentTimeMillis());
                        } else if (state.isOfflineMode && "ATTACKER".equals(state.myRole)) {
                            updateAI(now);
                        }
                    }
                }
                render(); updateInterface();
            }
        }.start();

        stage.setTitle("Kingdom Rush: Ultimate Edition");
        stage.setScene(scene); stage.show();
    }

    // ================= MENUS =================
    private void showMainMenu() {
        rootPane.getChildren().removeIf(node -> node instanceof VBox || node instanceof HBox);
        VBox menu = new VBox(30); menu.setAlignment(Pos.CENTER); menu.setStyle("-fx-background-color: rgba(0,0,0,0.95);");

        Label title = new Label("NEON KINGDOM"); title.setTextFill(NEON_CYAN); title.setFont(Font.font("Impact", 80)); title.setEffect(new DropShadow(20, NEON_CYAN));

        Button btnStart = createStyledButton("START GAME", NEON_GREEN); btnStart.setPrefWidth(300); btnStart.setPrefHeight(50);
        btnStart.setOnAction(e -> showRoleSelection());

        Button btnExit = createStyledButton("EXIT GAME", NEON_RED); btnExit.setPrefWidth(300); btnExit.setPrefHeight(50);
        btnExit.setOnAction(e -> Platform.exit());

        menu.getChildren().addAll(title, btnStart, btnExit);
        rootPane.getChildren().add(menu);
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
        state.isOfflineMode = offline; state.myRole = role;
        state.resetLevelData(); waveManager.initWaves(); state.currentLevel = 1;
        rootPane.getChildren().removeIf(node -> node instanceof VBox || node instanceof HBox);
        setupGameHUD();

        if (!offline) {
            network.connect();
            if ("ATTACKER".equals(role)) state.message = "Đang xin dữ liệu từ Host...";
            else { generateMap(); new Timer().schedule(new TimerTask() { public void run() { Platform.runLater(()->startLevel()); }}, 1000); }
        } else { generateMap(); startLevel(); }

        if ("ATTACKER".equals(role)) createAttackerPanel();
    }

    private void generateMap() {
        state.currentPath.clear();
        state.currentPath.add(new Point2D(0, ThreadLocalRandom.current().nextDouble(150, HEIGHT-150)));
        for(int i=1; i<5; i++) state.currentPath.add(new Point2D((WIDTH/5.0)*i, ThreadLocalRandom.current().nextDouble(150, HEIGHT-150)));
        state.currentPath.add(new Point2D(WIDTH-120, ThreadLocalRandom.current().nextDouble(150, HEIGHT-150)));
    }

    private void syncMapToAttacker() {
        if (state.isOfflineMode || state.currentPath.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for(Point2D p : state.currentPath) sb.append((int)p.getX()).append(":").append((int)p.getY()).append(",");
        network.sendAction(state.myRole, "SYNC_MAP", sb.toString());
        network.sendAction(state.myRole, "SYNC_HP", state.baseHp + "");
        network.sendAction(state.myRole, "NEW_LEVEL", state.currentLevel + "");
    }

    private void startLevel() {
        state.levelStarted = true; state.levelStartTime = System.currentTimeMillis();
    }

    private double distToSegment(double px, double py, double x1, double y1, double x2, double y2) {
        double l2 = Math.pow(x1 - x2, 2) + Math.pow(y1 - y2, 2);
        if (l2 == 0) return Math.sqrt(Math.pow(px - x1, 2) + Math.pow(py - y1, 2));
        double t = Math.max(0, Math.min(1, ((px - x1) * (x2 - x1) + (py - y1) * (y2 - y1)) / l2));
        return Math.sqrt(Math.pow(px - (x1 + t * (x2 - x1)), 2) + Math.pow(py - (y1 + t * (y2 - y1)), 2));
    }

    private boolean isValidBuildSpot(double x, double y) {
        for (Tower t : state.towers) { if (t.dist(x, y) > 0 && t.dist(x, y) < 40) return false; }
        if (state.selectedTower == TowerType.BARRACKS) return true;
        for (int i = 0; i < state.currentPath.size() - 1; i++) {
            Point2D p1 = state.currentPath.get(i), p2 = state.currentPath.get(i+1);
            if (distToSegment(x, y, p1.getX(), p1.getY(), p2.getX(), p2.getY()) < 45) return false;
        }
        return true;
    }

    // --- GAME LOGIC ---
    private void updateLogic(long now) {
        if (state.isVictory || state.isGameOver) return;

        if (!(state.isOfflineMode && "DEFENDER".equals(state.myRole))) {
            long elapsed = (System.currentTimeMillis() - state.levelStartTime) / 1000;
            if (LEVEL_DURATION_SECONDS - elapsed <= 0) {
                if ("DEFENDER".equals(state.myRole)) { if (state.baseHp > 0) handleVictory(); }
                else if ("ATTACKER".equals(state.myRole)) { if (state.baseHp > 0) handleDefeat(); }
                return;
            }
        }

        for(Soldier s : state.soldiers) {
            if (s.engaging==null && s.hp<100 && now%20==0) s.hp++;
            if (s.engaging!=null && now-s.lastAtk > 1e9) { if(s.engaging.hp>0) s.engaging.takeDamage(15 + (s.level*5), "PHYSICAL"); s.lastAtk=now; }
            if (s.engaging!=null && s.engaging.hp<=0) s.engaging=null;
        }
        state.soldiers.removeIf(s -> s.hp<=0);

        List<Monster> spawnedThisFrame = new ArrayList<>(); // Danh sách quái do Boss đẻ ra

        Iterator<Monster> it = state.monsters.iterator();
        while(it.hasNext()) {
            Monster m = it.next();
            if (m.frozenTimer > 0) m.frozenTimer--;
            if (m.burnTimer > 0) { if (m.burnTimer % 10 == 0) m.takeDamage(5, "MAGIC"); m.burnTimer--; }
            double currentSpeed = m.frozenTimer > 0 ? m.type.speed * 0.4 : m.type.speed;

            // QUÁI SKILL: Shaman Hồi máu (2% cơ hội mỗi frame)
            if (m.type == MonsterType.SHAMAN && Math.random() < 0.02) {
                for(Monster ally : state.monsters) {
                    if (ally != m && ally.dist(m.x, m.y) < 80) ally.hp = Math.min(ally.hp + 20, ally.type.hp);
                }
            }
            // QUÁI SKILL: Boss đẻ Goblin con (0.5% cơ hội mỗi frame)
            if (m.type == MonsterType.BOSS && Math.random() < 0.005) {
                Monster minion = new Monster(MonsterType.GOBLIN, state.currentPath);
                minion.x = m.x + ThreadLocalRandom.current().nextInt(-20, 20);
                minion.y = m.y + ThreadLocalRandom.current().nextInt(-20, 20);
                minion.pathIdx = m.pathIdx;
                spawnedThisFrame.add(minion);
            }

            for(Soldier s : state.soldiers) {
                if (s.engaging==null && m.engaging==null && m.dist(s.x,s.y)<30) { s.engaging=m; m.engaging=s; break; }
            }
            if (m.engaging!=null) {
                if(m.engaging.hp<=0) m.engaging=null;
                else if(now-m.lastAtk > 1e9) { m.engaging.hp-=25; m.lastAtk=now; }
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
                    if (state.baseHp <= 0) {
                        state.baseHp = 0;
                        if ("DEFENDER".equals(state.myRole)) handleDefeat();
                        else if ("ATTACKER".equals(state.myRole)) handleVictory();
                    }
                    if(!state.isOfflineMode) network.sendAction(state.myRole, "SYNC_HP", state.baseHp+"");
                }
                it.remove();
                if (state.baseHp <= 0) return;
                continue;
            }
            if(m.hp <= 0) { state.gold += m.type.reward; it.remove(); }
        }
        // Thêm quái đẻ ra vào mảng chính
        state.monsters.addAll(spawnedThisFrame);

        state.projectiles.clear();
        for(Tower t : state.towers) {
            if(t.type==TowerType.BARRACKS) continue;
            if(t.target==null || t.target.hp<=0 || t.dist(t.target.x, t.target.y)>t.getRange()) t.target = state.monsters.stream().filter(m->t.dist(m.x,m.y)<=t.getRange()).min(Comparator.comparingDouble(m->t.dist(m.x,m.y))).orElse(null);

            if(t.target!=null && now-t.lastAtk > t.getCooldown()*1e9) {
                state.projectiles.add(new Projectile(t.x,t.y,t.target.x,t.target.y,t.type.color));

                // SKILL TRỤ CANNON: Bắn nổ lan xung quanh mục tiêu
                if (t.type == TowerType.CANNON) {
                    for(Monster m2 : state.monsters) {
                        if (m2.dist(t.target.x, t.target.y) < 60) m2.takeDamage(t.getDamage(), "PHYSICAL"); // Splash radius 60
                    }
                } else {
                    t.target.takeDamage(t.getDamage(), t.type==TowerType.MAGE?"MAGIC":"PHYSICAL");
                }

                // SKILL TRỤ MAGE: Đóng băng / Thiêu đốt
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
                    state.gold -= upgradeCost; existingTower.upgrade();
                    state.message = "UPGRADED TO LV." + existingTower.level; state.lastBuildTime = now;
                    if(!state.isOfflineMode) network.sendAction(state.myRole, "UPGRADE", existingTower.x+","+existingTower.y);
                } else state.message = "NOT ENOUGH GOLD OR MAX LEVEL!";
            } else {
                if (state.gold >= state.selectedTower.cost) {
                    int refundAmount = existingTower.type.cost / 2;
                    state.gold = state.gold - state.selectedTower.cost + refundAmount;
                    state.towers.remove(existingTower);
                    if (existingTower.type == TowerType.BARRACKS) state.soldiers.removeIf(s -> s.owner == existingTower);
                    Tower t = new Tower(state.selectedTower, existingTower.x, existingTower.y); state.towers.add(t);
                    if(state.selectedTower==TowerType.BARRACKS) spawnSoldiers(t);
                    state.message = "REPLACED! (Refund +$" + refundAmount + ")"; state.lastBuildTime = now;
                    if(!state.isOfflineMode) network.sendAction(state.myRole, "REPLACE", state.selectedTower.name()+","+existingTower.x+","+existingTower.y);
                } else state.message = "NOT ENOUGH GOLD!";
            }
        } else {
            if (state.gold >= state.selectedTower.cost) {
                state.gold -= state.selectedTower.cost;
                Tower t = new Tower(state.selectedTower,x,y); state.towers.add(t);
                if(state.selectedTower==TowerType.BARRACKS) spawnSoldiers(t);
                state.lastBuildTime = now;
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

    // ĐÃ FIX HOÀN TOÀN: XÓA CHẶN if (menuOverlay != null) ĐỂ ĐẢM BẢO LUÔN HIỆN MENU
    private void handleVictory() {
        state.isVictory = true;
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
        btnReplay.setOnAction(e -> { rootPane.getChildren().remove(menuOverlay); state.resetLevelData(); waveManager.initWaves(); generateMap(); startLevel(); });
        Button btnQuit = createStyledButton("MAIN MENU", NEON_RED); btnQuit.setOnAction(e -> returnToMainMenu());

        buttons.getChildren().addAll(btnReplay, btnQuit);
        if (victory && (state.isOfflineMode && "DEFENDER".equals(state.myRole))) {
            Button btnNext = createStyledButton("NEXT MAP >>", NEON_CYAN);
            btnNext.setOnAction(e -> { rootPane.getChildren().remove(menuOverlay); state.currentLevel++; state.resetLevelData(); waveManager.initWaves(); generateMap(); startLevel(); });
            buttons.getChildren().add(btnNext);
        }
        menuOverlay.getChildren().addAll(lbl, buttons); rootPane.getChildren().add(menuOverlay);
    }

    private void returnToMainMenu() {
        state.levelStarted = false; state.resetLevelData();
        showMainMenu();
    }

    // --- RENDER ---
    private void render() {
        gc.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE, new Stop(0, BG_DARK), new Stop(1, BG_LIGHT))); gc.fillRect(0, 0, WIDTH, HEIGHT);
        gc.setStroke(Color.rgb(255,255,255,0.02)); gc.setLineWidth(1);
        for(int i=0;i<WIDTH;i+=40) gc.strokeLine(i,0,i,HEIGHT); for(int i=0;i<HEIGHT;i+=40) gc.strokeLine(0,i,WIDTH,i);
        if (state.currentPath.isEmpty()) return;

        Color[] stageColors = {NEON_CYAN, NEON_PURPLE, NEON_ORANGE, NEON_GREEN, NEON_RED};
        Color currentMapColor = stageColors[(state.currentLevel - 1) % stageColors.length];

        gc.setLineCap(StrokeLineCap.ROUND); gc.setLineJoin(StrokeLineJoin.ROUND); gc.setEffect(new DropShadow(20, currentMapColor));
        gc.setStroke(currentMapColor); gc.setLineWidth(60); gc.beginPath(); gc.moveTo(state.currentPath.get(0).getX(), state.currentPath.get(0).getY()); for(Point2D p : state.currentPath) gc.lineTo(p.getX(), p.getY()); gc.stroke();
        gc.setEffect(null); gc.setStroke(ROAD_COLOR); gc.setLineWidth(50); gc.beginPath(); gc.moveTo(state.currentPath.get(0).getX(), state.currentPath.get(0).getY()); for(Point2D p : state.currentPath) gc.lineTo(p.getX(), p.getY()); gc.stroke();

        Point2D end = state.currentPath.get(state.currentPath.size()-1); drawBase(end.getX(), end.getY());

        for(Tower t : state.towers) drawTower(t);
        for(Soldier s : state.soldiers) { drawSoldier(s.x, s.y); renderModernBar(s.x, s.y-25, s.hp, 100, 30, NEON_CYAN); }
        try {
            for(Monster m : state.monsters) {
                drawMonster(m); double size = m.type==MonsterType.BOSS?60:40; renderModernBar(m.x, m.y - size/2 - 15, m.hp, m.type.hp, size, NEON_RED);
                if (m.frozenTimer > 0) { gc.setEffect(new DropShadow(10, NEON_CYAN)); gc.setStroke(NEON_CYAN); gc.setLineWidth(2); gc.strokeOval(m.x - 20, m.y - 20, 40, 40); gc.setEffect(null); }
                else if (m.burnTimer > 0) { gc.setEffect(new DropShadow(10, Color.ORANGE)); gc.setStroke(Color.ORANGE); gc.setLineWidth(2); gc.strokeOval(m.x - 20, m.y - 20, 40, 40); gc.setEffect(null); }
            }
        } catch(ConcurrentModificationException ignored) {}

        gc.setEffect(new Glow(1.0)); gc.setLineWidth(3);
        for(Projectile p : state.projectiles) { gc.setStroke(p.c); gc.strokeLine(p.sx, p.sy, p.ex, p.ey); }
        gc.setEffect(null);

        if (mouseX >= 0 && mouseY >= 0 && "DEFENDER".equals(state.myRole) && !state.isPaused && !state.isGameOver && !state.isVictory && state.levelStarted) {
            Tower existing = state.towers.stream().filter(t -> t.dist(mouseX, mouseY) < 40).findFirst().orElse(null);
            if (existing != null) {
                gc.setStroke(Color.rgb(255, 255, 0, 0.5)); gc.setLineWidth(2);
                gc.strokeOval(existing.x - existing.getRange(), existing.y - existing.getRange(), existing.getRange() * 2, existing.getRange() * 2);
            } else {
                boolean isValid = isValidBuildSpot(mouseX, mouseY);
                Color previewColor = isValid ? Color.rgb(0, 255, 0, 0.3) : Color.rgb(255, 0, 0, 0.5);
                gc.setFill(Color.rgb(255, 255, 255, 0.05)); gc.fillOval(mouseX - state.selectedTower.range, mouseY - state.selectedTower.range, state.selectedTower.range * 2, state.selectedTower.range * 2);
                gc.setStroke(previewColor); gc.setLineWidth(2); gc.strokeOval(mouseX - state.selectedTower.range, mouseY - state.selectedTower.range, state.selectedTower.range * 2, state.selectedTower.range * 2);
                gc.setFill(previewColor); gc.fillOval(mouseX-25, mouseY-20, 50, 30);
            }
        }

        if (state.isPaused) { gc.setFill(Color.rgb(0,0,0,0.5)); gc.fillRect(0,0,WIDTH,HEIGHT); }
    }

    private void drawBase(double x, double y) {
        gc.setEffect(new DropShadow(15, NEON_CYAN)); gc.setFill(Color.web("#333")); gc.fillRect(x-50, y-40, 100, 80);
        gc.setFill(new LinearGradient(0,0,0,1, true, CycleMethod.NO_CYCLE, new Stop(0, NEON_CYAN), new Stop(1, Color.TRANSPARENT))); gc.fillOval(x-30, y-20, 60, 60);
        gc.save(); gc.translate(x, y+10); gc.rotate(animationTime * 50); gc.setStroke(Color.WHITE); gc.setLineWidth(3); gc.strokeRect(-15, -15, 30, 30); gc.restore(); gc.setEffect(null);

        gc.setFill(Color.WHITE); gc.setFont(Font.font("Impact", 18)); gc.fillText("CORE", x-18, y-45);
        renderModernBar(x, y-40, state.baseHp, 2000, 80, state.baseHp < 500 ? NEON_RED : NEON_GREEN);
    }

    private void drawTower(Tower t) {
        double x = t.x, y = t.y; gc.setEffect(new DropShadow(10, t.type.color.darker()));
        gc.setFill(Color.web("#222")); gc.fillOval(x-25, y-20, 50, 30); gc.setFill(t.type.color.darker()); gc.fillRect(x-20, y-25, 40, 15);
        switch (t.type) {
            case ARCHER: gc.setFill(new LinearGradient(0,0,1,0, true, CycleMethod.NO_CYCLE, new Stop(0, t.type.color), new Stop(1, t.type.color.brighter()))); gc.fillRect(x-10, y-55, 20, 40); gc.setStroke(Color.WHITE); gc.setLineWidth(3); gc.strokeArc(x-20, y-65, 40, 30, 0, 180, ArcType.OPEN); break;
            case MAGE: gc.setFill(t.type.color.darker()); gc.fillPolygon(new double[]{x-15, x+15, x}, new double[]{y-10, y-10, y-60}, 3); gc.setEffect(new Glow(0.8)); gc.setFill(t.type.color); gc.fillOval(x-12, y-72 + Math.sin(animationTime*2)*5, 24, 24); break;
            case BARRACKS: gc.setFill(t.type.color.darker()); gc.fillRect(x-25, y-30, 50, 25); gc.setFill(t.type.color); gc.fillArc(x-20, y-45, 40, 30, 0, 180, ArcType.ROUND); gc.setFill(Color.BLACK); gc.fillRect(x-8, y-25, 16, 15); break;
            // VẼ TRỤ CANNON MỚI THÊM
            case CANNON: gc.setFill(t.type.color.darker()); gc.fillRect(x-15, y-40, 30, 20); gc.setFill(t.type.color); gc.fillOval(x-12, y-45, 24, 24); gc.setStroke(Color.BLACK); gc.setLineWidth(6); gc.strokeLine(x, y-35, x+15, y-50); break;
        }
        gc.setEffect(null); gc.setFill(Color.WHITE); gc.setFont(Font.font("Consolas", FontWeight.BOLD, 12)); gc.fillText("Lv." + t.level, x-12, y+5);
    }

    private void drawMonster(Monster m) {
        double x = m.x, y = m.y; Color c = m.type.color;
        switch (m.type) {
            case GOBLIN: gc.setFill(c); gc.fillOval(x-15, y-15, 30, 30); gc.fillPolygon(new double[]{x-15, x-25, x-15}, new double[]{y-5, y-15, y-25}, 3); gc.fillPolygon(new double[]{x+15, x+25, x+15}, new double[]{y-5, y-15, y-25}, 3); break;
            case ORC: gc.setFill(c); gc.fillRect(x-20, y-25, 40, 50); gc.setFill(Color.GRAY); gc.fillRect(x-25, y-30, 15, 20); gc.fillRect(x+10, y-30, 15, 20); break;
            case SHAMAN: gc.setFill(c.darker()); gc.fillPolygon(new double[]{x-20, x+20, x}, new double[]{y+20, y+20, y-30}, 3); gc.setStroke(c); gc.setLineWidth(3); gc.strokeLine(x+15, y+20, x+25, y-20); gc.setEffect(new Glow(1)); gc.setFill(c.brighter()); gc.fillOval(x+20, y-25, 10, 10); gc.setEffect(null); break;
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

    // --- UI HELPERS ---
    private void setupGameHUD() {
        topHud = new HBox(30); topHud.setPadding(new Insets(15)); topHud.setAlignment(Pos.CENTER); topHud.setStyle("-fx-background-color: rgba(20,20,30,0.8); -fx-background-radius: 0 0 20 20;"); topHud.setMaxHeight(60); StackPane.setAlignment(topHud, Pos.TOP_CENTER);
        Button btnPause = createStyledButton("||", Color.YELLOW); btnPause.setPrefWidth(40); btnPause.setOnAction(e -> togglePause());
        rootPane.getChildren().addAll(topHud, btnPause); StackPane.setAlignment(btnPause, Pos.TOP_RIGHT); StackPane.setMargin(btnPause, new Insets(10));
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
                Label lblBuild = createHUDLabel("BUILD: " + state.selectedTower.name + " ($" + state.selectedTower.cost + ")", state.selectedTower.color);
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
                    if (now < cooldownEnd) { btn.setText(t.name() + "\n" + (cooldownEnd - now) / 1000 + "s"); btn.setDisable(true); btn.setOpacity(0.5); }
                    else { btn.setText(t.name() + "\n(" + t.cooldown + "s)"); btn.setDisable(false); btn.setOpacity(1.0); }
                }
            }
        }
    }
    private void createAttackerPanel() {
        attackerPanel = new VBox(15); attackerPanel.setPadding(new Insets(20)); attackerPanel.setAlignment(Pos.CENTER_LEFT); attackerPanel.setMaxWidth(160); attackerPanel.setStyle("-fx-background-color: rgba(20,20,30,0.9); -fx-background-radius: 0 20 20 0; -fx-border-color: #e94560; -fx-border-width: 0 2 2 0;"); StackPane.setAlignment(attackerPanel, Pos.CENTER_LEFT);
        Label lbl = new Label("UNITS"); lbl.setTextFill(NEON_RED); lbl.setFont(Font.font("Impact", 20)); attackerPanel.getChildren().add(lbl);
        for(MonsterType t : MonsterType.values()) {
            Button b = createStyledButton(t.name() + "\n(" + t.cooldown + "s)", t.color); b.setPrefWidth(120); b.setFont(Font.font("Consolas", FontWeight.BOLD, 12));
            b.setOnAction(e -> spawnMonster(t)); attackerButtons.put(t, b); attackerPanel.getChildren().add(b);
        }
        rootPane.getChildren().add(attackerPanel);
    }
    private Button createStyledButton(String text, Color c) {
        Button btn = new Button(text); String hex = String.format("#%02X%02X%02X", (int)(c.getRed()*255),(int)(c.getGreen()*255),(int)(c.getBlue()*255));
        String style = "-fx-background-color:rgba(0,0,0,0.5);-fx-border-color:"+hex+";-fx-border-width:2;-fx-border-radius:10;-fx-text-fill:white;-fx-font-family:'Consolas';-fx-font-weight:bold;-fx-font-size:13px;-fx-cursor:hand;";
        btn.setStyle(style); btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color:"+hex+";-fx-background-radius:10;-fx-text-fill:black;-fx-font-family:'Consolas';-fx-font-weight:bold;-fx-font-size:13px;-fx-cursor:hand;")); btn.setOnMouseExited(e -> btn.setStyle(style)); return btn;
    }
    private Label createHUDLabel(String text, Color color) { Label l = new Label(text); l.setTextFill(color); l.setFont(Font.font("Consolas", FontWeight.BOLD, 16)); l.setEffect(new DropShadow(5, color)); return l; }

    // --- NETWORK & AI ---
    private void spawnMonster(MonsterType t) {
        long now = System.currentTimeMillis();
        if (state.isOfflineMode && "DEFENDER".equals(state.myRole)) { state.monsters.add(new Monster(t, state.currentPath)); return; }
        if(now - state.cooldowns.getOrDefault(t,0L) > t.cooldown*1000L) {
            state.cooldowns.put(t,now);
            if (state.isOfflineMode) state.monsters.add(new Monster(t, state.currentPath)); else network.sendAction(state.myRole, "SPAWN", t.name());
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
            case "REQUEST_MAP":
                if ("DEFENDER".equals(state.myRole)) syncMapToAttacker();
                break;
            case "SYNC_MAP":
                state.currentPath.clear(); state.monsters.clear(); state.towers.clear(); state.projectiles.clear(); state.soldiers.clear();
                for(String s : a.getData().split(",")) { String[] xy = s.split(":"); if(xy.length==2) state.currentPath.add(new Point2D(Double.parseDouble(xy[0]), Double.parseDouble(xy[1]))); }
                break;
            case "NEW_LEVEL": state.levelStarted = true; state.levelStartTime = System.currentTimeMillis(); break;
            case "SPAWN": if(!state.currentPath.isEmpty()) state.monsters.add(new Monster(MonsterType.valueOf(a.getData()), state.currentPath)); break;
            case "BUILD": TowerType t = TowerType.valueOf(d[0]); double x = Double.parseDouble(d[1]), y = Double.parseDouble(d[2]); Tower newT = new Tower(t,x,y); state.towers.add(newT); if(t==TowerType.BARRACKS) spawnSoldiers(newT); break;
            case "UPGRADE": double ux = Double.parseDouble(d[0]), uy = Double.parseDouble(d[1]); state.towers.stream().filter(tow->tow.dist(ux,uy)<10).forEach(Tower::upgrade); break;
            case "REPLACE": TowerType rt = TowerType.valueOf(d[0]); double rx = Double.parseDouble(d[1]), ry = Double.parseDouble(d[2]); Tower oldTower = state.towers.stream().filter(tow->tow.dist(rx,ry)<10).findFirst().orElse(null); if(oldTower!=null) { state.towers.remove(oldTower); if(oldTower.type==TowerType.BARRACKS) state.soldiers.removeIf(s->s.owner==oldTower); } Tower repT = new Tower(rt,rx,ry); state.towers.add(repT); if(rt==TowerType.BARRACKS) spawnSoldiers(repT); break;

            case "SYNC_HP":
                state.baseHp = Integer.parseInt(a.getData());
                if(state.baseHp <= 0) {
                    state.baseHp = 0;
                    if ("DEFENDER".equals(state.myRole)) handleDefeat();
                    else if ("ATTACKER".equals(state.myRole)) handleVictory();
                }
                break;

            case "GAME_OVER": handleDefeat(); break;
            case "VICTORY": handleVictory(); break;
        }
    }
}