import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.Random;

public class VelocityRivalsGUI extends JFrame {
    // ==================== DSA INSTANCES ====================
    final ArrayList<VelocityRivalsEngine.AI> cars = new ArrayList<>();
    final ArrayList<VelocityRivalsEngine.Pickup> pickups = new ArrayList<>();
    final ArrayList<VelocityRivalsEngine.Coin> coins = new ArrayList<>();
    final ArrayList<VelocityRivalsEngine.Spark> sparks = new ArrayList<>();
    final ArrayList<VelocityRivalsEngine.OilPatch> oils = new ArrayList<>();
    final ArrayList<VelocityRivalsEngine.Crate> crates = new ArrayList<>();
    final ArrayList<VelocityRivalsEngine.MissileProjectile> missiles = new ArrayList<>();
    final VelocityRivalsEngine.CustomQueue<VelocityRivalsEngine.AI> spawnQ = new VelocityRivalsEngine.CustomQueue<>(32);
    final VelocityRivalsEngine.CustomStack<String> evtStk = new VelocityRivalsEngine.CustomStack<>(20);
    final VelocityRivalsEngine.CustomHashMap<VelocityRivalsEngine.PU, Integer> p1map = new VelocityRivalsEngine.CustomHashMap<>();
    final VelocityRivalsEngine.CustomHashMap<VelocityRivalsEngine.PU, Integer> p2map = new VelocityRivalsEngine.CustomHashMap<>();
    final VelocityRivalsEngine.CustomPriorityQueue<VelocityRivalsEngine.ScoreEntry> board = new VelocityRivalsEngine.CustomPriorityQueue<>(32, (a, b) -> b.score - a.score);

    // ==================== STATE ====================
    VelocityRivalsEngine.Screen scr = VelocityRivalsEngine.Screen.MAIN_MENU;
    VelocityRivalsEngine.Theme thm = VelocityRivalsEngine.Theme.HIGHWAY;
    boolean multi = false;
    VelocityRivalsEngine.GameMode currentGameMode = VelocityRivalsEngine.GameMode.SURVIVAL;
    VelocityRivalsEngine.Car p1, p2;
    double roadOff = 0;
    int frame = 0, diff = 1, secs = 0, secTick = 0;
    int spawnCD = 60, pickCD = 140, coinCD = 80, crateCD = 90, p2CD = 0;
    int hov = -1;
    boolean started = false;
    int cdNum = 3, cdTick = 0;
    final Random rng = new Random();
    Font fT, fH, fB, fS;
    final boolean[] keys = new boolean[65536];
    GamePanel panel;
    String player1Name = "", player2Name = "";
    String winnerName = "";
    boolean raceFinished = false;
    String raceWinner = "";
    int damageFlash = 0, screenShake = 0;
    static final double RACE_DISTANCE = 5000.0;
    double raceTimeLimit = 90.0;
    boolean collisionsEnabled = false;
    int collisionDelay = 60;

    public VelocityRivalsGUI() {
        super("Velocity Rivals — Racing Championship");
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        VelocityRivalsEngine.W = Math.min(1200, screenSize.width - 50);
        VelocityRivalsEngine.H = Math.min(700, screenSize.height - 80);
        VelocityRivalsEngine.ROAD_X = VelocityRivalsEngine.W / 2 - VelocityRivalsEngine.ROAD_W / 2;
        VelocityRivalsEngine.LANE_X = new int[]{
                VelocityRivalsEngine.ROAD_X + VelocityRivalsEngine.ROAD_W / 6,
                VelocityRivalsEngine.ROAD_X + VelocityRivalsEngine.ROAD_W / 2,
                VelocityRivalsEngine.ROAD_X + 5 * VelocityRivalsEngine.ROAD_W / 6
        };
        fT = new Font("Impact", Font.PLAIN, 72);
        fH = new Font("Consolas", Font.BOLD, 18);
        fB = new Font("Arial Black", Font.BOLD, 20);
        fS = new Font("Consolas", Font.PLAIN, 13);
        panel = new GamePanel();
        add(panel);
        pack();
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(true);
        new Timer(16, e -> { update(); panel.repaint(); }).start();
        setVisible(true);
        panel.requestFocusInWindow();
    }

    void askForPlayerNames() {
        if (!multi) {
            String name = JOptionPane.showInputDialog(this, "🏁 VELOCITY RIVALS 🏁\n\nEnter your name:", "PLAYER NAME", JOptionPane.QUESTION_MESSAGE);
            if (name != null && !name.trim().isEmpty()) player1Name = name.trim().toUpperCase();
            else player1Name = "RACER";
            if (player1Name.length() > 12) player1Name = player1Name.substring(0, 10) + "..";
        } else {
            JPanel namePanel = new JPanel(new GridLayout(2, 2, 10, 10));
            namePanel.setBackground(new Color(0x0a0a1a));
            JLabel p1Label = new JLabel("PLAYER 1 (BLUE):"); p1Label.setForeground(new Color(0x00E5FF));
            JTextField p1Field = new JTextField(15); p1Field.setText("RACER1");
            JLabel p2Label = new JLabel("PLAYER 2 (RED):"); p2Label.setForeground(new Color(0xFF4757));
            JTextField p2Field = new JTextField(15); p2Field.setText("RACER2");
            namePanel.add(p1Label); namePanel.add(p1Field); namePanel.add(p2Label); namePanel.add(p2Field);
            int result = JOptionPane.showConfirmDialog(this, namePanel, "ENTER PLAYER NAMES", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (result == JOptionPane.OK_OPTION) {
                player1Name = p1Field.getText().trim().isEmpty() ? "RACER1" : p1Field.getText().trim().toUpperCase();
                player2Name = p2Field.getText().trim().isEmpty() ? "RACER2" : p2Field.getText().trim().toUpperCase();
                if (player1Name.length() > 12) player1Name = player1Name.substring(0, 10) + "..";
                if (player2Name.length() > 12) player2Name = player2Name.substring(0, 10) + "..";
            } else { player1Name = "RACER1"; player2Name = "RACER2"; }
        }
        startGame();
    }

    void startGame() {
        cars.clear(); pickups.clear(); coins.clear(); sparks.clear(); oils.clear(); crates.clear(); missiles.clear();
        p1map.clear(); p2map.clear(); evtStk.clear();
        while (!spawnQ.isEmpty()) spawnQ.dequeue();
        roadOff = 0; frame = 0; diff = 1; secs = 0; secTick = 0;
        spawnCD = 60; pickCD = 140; coinCD = 80; crateCD = 90; p2CD = 0;
        started = false; cdNum = 3; cdTick = 0; raceFinished = false;
        raceWinner = ""; winnerName = ""; damageFlash = 0; screenShake = 0;
        collisionsEnabled = false;
        collisionDelay = 60;

        if (!multi) {
            p1 = new VelocityRivalsEngine.Car(VelocityRivalsEngine.LANE_X[1], VelocityRivalsEngine.H - 160, new Color(0x00E5FF), new Color(0x0055CC), true);
        } else {
            p1 = new VelocityRivalsEngine.Car(VelocityRivalsEngine.W / 4, VelocityRivalsEngine.H - 160, new Color(0x00E5FF), new Color(0x0055CC), true);
            p2 = new VelocityRivalsEngine.Car(3 * VelocityRivalsEngine.W / 4, VelocityRivalsEngine.H - 160, new Color(0xFF4757), new Color(0xCC2200), false);
        }

        if (currentGameMode == VelocityRivalsEngine.GameMode.RACE) {
            if (p1 != null) { p1.distance = 0; p1.lastCheckpoint = 0; }
            if (p2 != null) { p2.distance = 0; p2.lastCheckpoint = 0; }
        }

        for (int i = 0; i < 6; i++) scheduleAI(false);
        if (multi) for (int i = 0; i < 6; i++) scheduleAI(true);

        scr = multi ? VelocityRivalsEngine.Screen.GAME_MULTI : VelocityRivalsEngine.Screen.GAME_SINGLE;
        evt((currentGameMode == VelocityRivalsEngine.GameMode.RACE ? "🏁 RACE MODE - " : "SURVIVAL MODE - ") + thm.label);
        evt("⚠️ ANY COLLISION = INSTANT GAME OVER ⚠️");
        if (currentGameMode == VelocityRivalsEngine.GameMode.RACE) evt("🔄 SWAP BOOSTER: Swap positions with opponent! 🔄");
    }

    void scheduleAI(boolean p2side) {
        int lane = rng.nextInt(3);
        double spd = 1.4 + rng.nextDouble() * 2.0 * (0.5 + diff * 0.25);
        Color[] cols = {new Color(0xFF6B6B), new Color(0xF7DC6F), new Color(0x82E0AA), new Color(0xF0B27A), new Color(0xAED6F1), new Color(0xD7BDE2)};
        Color col = cols[rng.nextInt(cols.length)];
        boolean agg = rng.nextFloat() < 0.12f * diff;
        double yOffset = -80 - (rng.nextInt(100) * (p2side ? 1 : -1));
        spawnQ.enqueue(new VelocityRivalsEngine.AI(getLX(lane, p2side), yOffset, spd, lane, col, agg));
    }

    double getLX(int lane, boolean p2side) {
        if (!multi) return VelocityRivalsEngine.LANE_X[lane];
        int laneWidth = VelocityRivalsEngine.ROAD_W / 3;
        int startX = p2side ? (VelocityRivalsEngine.W / 2 + (VelocityRivalsEngine.W / 2 - VelocityRivalsEngine.ROAD_W) / 2) : ((VelocityRivalsEngine.W / 2 - VelocityRivalsEngine.ROAD_W) / 2);
        return startX + laneWidth / 2 + lane * laneWidth;
    }

    int getRoadLeft(boolean p2side) {
        if (!multi) return VelocityRivalsEngine.ROAD_X;
        return p2side ? (VelocityRivalsEngine.W / 2 + (VelocityRivalsEngine.W / 2 - VelocityRivalsEngine.ROAD_W) / 2) : ((VelocityRivalsEngine.W / 2 - VelocityRivalsEngine.ROAD_W) / 2);
    }

    int getRoadRight(boolean p2side) { return getRoadLeft(p2side) + VelocityRivalsEngine.ROAD_W; }

    double[] playerBounds(VelocityRivalsEngine.Car p) {
        boolean p2s = (p2 != null && !p.isP1);
        int rl = getRoadLeft(p2s) + 18, rr = getRoadRight(p2s) - 18;
        return new double[]{rl, rr, 20, VelocityRivalsEngine.H - 60};
    }

    void evt(String s) { evtStk.push(s); }

    void swapPositions() {
        if (currentGameMode != VelocityRivalsEngine.GameMode.RACE || p1 == null || p2 == null) return;
        if (!multi) return;

        double p1x = p1.x, p1y = p1.y, p1dist = p1.distance;
        double p2x = p2.x, p2y = p2.y, p2dist = p2.distance;

        p1.x = p2x;
        p1.y = p2y;
        p2.x = p1x;
        p2.y = p1y;

        p1.distance = p2dist;
        p2.distance = p1dist;

        for (int i = 0; i < 20; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            double s = 2 + rng.nextDouble() * 5;
            sparks.add(new VelocityRivalsEngine.Spark(p1.x, p1.y, Math.cos(a) * s, Math.sin(a) * s, new Color(0x00E5FF), 30, 5f));
            sparks.add(new VelocityRivalsEngine.Spark(p2.x, p2.y, Math.cos(a + Math.PI) * s, Math.sin(a + Math.PI) * s, new Color(0xFF4757), 30, 5f));
        }

        screenShake = 20;
        evt("🔄 SWAP BOOSTER! " + player1Name + " ↔ " + player2Name + " swapped positions! 🔄");
    }

    void checkAllCollisions() {
        if (!collisionsEnabled) return;
        if (!started) return;

        if (p1 != null && p1.alive) {
            for (VelocityRivalsEngine.AI ai : cars) {
                if (ai.alive && p1.box().intersects(ai.box())) {
                    if (ai.y > 50 && ai.y < VelocityRivalsEngine.H - 50) {
                        gameOverDueToCollision(player1Name.isEmpty() ? "P1" : player1Name, "an AI car");
                        return;
                    }
                }
            }
        }

        if (multi && p2 != null && p2.alive) {
            for (VelocityRivalsEngine.AI ai : cars) {
                if (ai.alive && p2.box().intersects(ai.box())) {
                    if (ai.y > 50 && ai.y < VelocityRivalsEngine.H - 50) {
                        gameOverDueToCollision(player2Name.isEmpty() ? "P2" : player2Name, "an AI car");
                        return;
                    }
                }
            }
        }

        if (multi && p1 != null && p2 != null && p1.alive && p2.alive && p1.box().intersects(p2.box())) {
            if (p1.y > 50 && p1.y < VelocityRivalsEngine.H - 50 && p2.y > 50 && p2.y < VelocityRivalsEngine.H - 50) {
                if (p1.invincibleT == 0) {
                    p1.health = Math.max(0, p1.health - 50);
                    p1.dmgFlash = 20;
                    p1.invincibleT = 30;
                }
                if (p2.invincibleT == 0) {
                    p2.health = Math.max(0, p2.health - 50);
                    p2.dmgFlash = 20;
                    p2.invincibleT = 30;
                }

                double angle = Math.atan2(p1.y - p2.y, p1.x - p2.x);
                p1.vx = Math.cos(angle) * 8;
                p1.vy = Math.sin(angle) * 8;
                p2.vx = -Math.cos(angle) * 8;
                p2.vy = -Math.sin(angle) * 8;

                evt("💥 PLAYERS COLLIDED! -50 HP EACH! 💥");
                smoke(p1.x, p1.y);
                smoke(p2.x, p2.y);
                damageFlash = 10;
                screenShake = 12;

                if (p1.health <= 0) { p1.alive = false; explode(p1.x, p1.y); winnerName = player2Name; endGame(); return; }
                if (p2.health <= 0) { p2.alive = false; explode(p2.x, p2.y); winnerName = player1Name; endGame(); return; }
            }
        }

        if (p1 != null && p1.alive) {
            for (VelocityRivalsEngine.Crate c : crates) {
                if (!c.done && p1.box().intersects(c.box()) && c.y > 50) {
                    int dmg = currentGameMode == VelocityRivalsEngine.GameMode.RACE ? 20 : 30;
                    if (p1.invincibleT == 0) {
                        p1.health = Math.max(0, p1.health - dmg);
                        p1.dmgFlash = 20;
                        p1.invincibleT = 30;
                    }
                    c.done = true;
                    evt(p1.isP1 ? "P1 HIT CRATE! -" + dmg : "P2 HIT CRATE! -" + dmg);
                    smoke(c.x, c.y);
                    if (p1.health <= 0) { p1.alive = false; explode(p1.x, p1.y); if (multi) winnerName = player2Name; endGame(); return; }
                }
            }
        }
        if (multi && p2 != null && p2.alive) {
            for (VelocityRivalsEngine.Crate c : crates) {
                if (!c.done && p2.box().intersects(c.box()) && c.y > 50) {
                    int dmg = currentGameMode == VelocityRivalsEngine.GameMode.RACE ? 20 : 30;
                    if (p2.invincibleT == 0) {
                        p2.health = Math.max(0, p2.health - dmg);
                        p2.dmgFlash = 20;
                        p2.invincibleT = 30;
                    }
                    c.done = true;
                    evt(p2.isP1 ? "P1 HIT CRATE! -" + dmg : "P2 HIT CRATE! -" + dmg);
                    smoke(c.x, c.y);
                    if (p2.health <= 0) { p2.alive = false; explode(p2.x, p2.y); if (multi) winnerName = player1Name; endGame(); return; }
                }
            }
        }
    }

    void gameOverDueToCollision(String who, String with) {
        if (scr != VelocityRivalsEngine.Screen.GAME_SINGLE && scr != VelocityRivalsEngine.Screen.GAME_MULTI) return;
        evt("💥💥💥 GAME OVER! " + who + " collided with " + with + "! 💥💥💥");
        if (p1 != null && p1.alive) explode(p1.x, p1.y);
        if (p2 != null && p2.alive) explode(p2.x, p2.y);
        for (VelocityRivalsEngine.AI ai : cars) if (ai.alive && ai.y > 0 && ai.y < VelocityRivalsEngine.H) explode(ai.x, ai.y);
        screenShake = 30;
        damageFlash = 30;
        if (p1 != null) p1.alive = false;
        if (p2 != null) p2.alive = false;
        for (VelocityRivalsEngine.AI ai : cars) ai.alive = false;
        endGame();
    }

    void endGame() {
        if (scr == VelocityRivalsEngine.Screen.GAME_OVER) return;
        if (p1 != null && !(currentGameMode == VelocityRivalsEngine.GameMode.RACE && !multi && !raceFinished)) {
            board.offer(new VelocityRivalsEngine.ScoreEntry(player1Name.isEmpty() ? "RACER" : player1Name, p1.score, currentGameMode.name()));
        }
        if (p2 != null && multi) {
            board.offer(new VelocityRivalsEngine.ScoreEntry(player2Name.isEmpty() ? "RACER2" : player2Name, p2.score, currentGameMode.name()));
        }
        scr = VelocityRivalsEngine.Screen.GAME_OVER;
    }

    void update() {
        frame++;
        boolean inGame = scr == VelocityRivalsEngine.Screen.GAME_SINGLE || scr == VelocityRivalsEngine.Screen.GAME_MULTI;
        if (!inGame) return;

        if (!started) {
            if (++cdTick >= 60) { cdTick = 0; cdNum--; }
            if (cdNum < 0) { started = true; collisionDelay = 60; }
        }

        if (started && !collisionsEnabled) {
            collisionDelay--;
            if (collisionDelay <= 0) {
                collisionsEnabled = true;
                evt("⚔️ COLLISIONS ENABLED! Avoid all cars! ⚔️");
            }
        }

        if (++secTick >= 60) { secTick = 0; secs++; if (secs > 0 && secs % 30 == 0 && diff < 5) { diff = Math.min(5, diff + 1); evt("DIFFICULTY LV" + diff); } }

        if (currentGameMode == VelocityRivalsEngine.GameMode.RACE && !raceFinished && started && secs >= raceTimeLimit) {
            raceFinished = true;
            evt("⏰ TIME'S UP!");
            if (multi && p1 != null && p2 != null) {
                if (p1.distance > p2.distance) raceWinner = player1Name;
                else if (p2.distance > p1.distance) raceWinner = player2Name;
                else raceWinner = "TIE";
            } else if (p1 != null) {
                raceWinner = "";
            }
            endGame();
            return;
        }

        double scroll = 5.0 * thm.spd + diff * 0.25;
        if (p1 != null && p1.boosted) scroll *= 1.55;

        if (currentGameMode == VelocityRivalsEngine.GameMode.RACE && p1 != null && p1.alive) {
            double playerSpeed = (!multi && (keys[KeyEvent.VK_UP] || keys[KeyEvent.VK_W])) ? 1 : ((!multi && (keys[KeyEvent.VK_DOWN] || keys[KeyEvent.VK_S])) ? -0.5 : (multi && keys[KeyEvent.VK_W]) ? 1 : (multi && keys[KeyEvent.VK_S]) ? -0.5 : 0);
            scroll = 3.0 * thm.spd + diff * 0.2;
            scroll *= (1 + playerSpeed * 1.5);
            if (p1.boosted) scroll *= 1.55;
            p1.distance += scroll * 0.5;
            if (p1.distance >= RACE_DISTANCE && !raceFinished) {
                raceFinished = true;
                raceWinner = player1Name;
                evt("🏆 " + player1Name + " FINISHED!");
                endGame();
                return;
            }
        }

        if (currentGameMode == VelocityRivalsEngine.GameMode.RACE && p2 != null && p2.alive && multi) {
            double p2Speed = (keys[KeyEvent.VK_I] ? 1 : keys[KeyEvent.VK_K] ? -0.5 : 0);
            double p2Scroll = 3.0 * thm.spd + diff * 0.2;
            p2Scroll *= (1 + p2Speed * 1.5);
            if (p2.boosted) p2Scroll *= 1.55;
            p2.distance += p2Scroll * 0.5;
            if (p2.distance >= RACE_DISTANCE && !raceFinished) {
                raceFinished = true;
                raceWinner = player2Name;
                evt("🏆 " + player2Name + " FINISHED!");
                endGame();
                return;
            }
        }

        roadOff = (roadOff + scroll) % 120;
        input(p1, false); if (p2 != null) input(p2, true);
        physics(p1); if (p2 != null) physics(p2);

        if (p1 != null && !p1.boosted && p1.nitro < 100) p1.nitro = Math.min(100, p1.nitro + 2);
        if (p2 != null && !p2.boosted && p2.nitro < 100) p2.nitro = Math.min(100, p2.nitro + 2);

        if (--spawnCD <= 0) { spawnCD = Math.max(22, 85 - diff * 10); if (!spawnQ.isEmpty()) { cars.add(spawnQ.dequeue()); scheduleAI(false); } }
        if (multi && --p2CD <= 0) { p2CD = Math.max(22, 85 - diff * 10); if (!spawnQ.isEmpty()) { cars.add(spawnQ.dequeue()); scheduleAI(true); } else scheduleAI(true); }

        updateAI(scroll);

        if (--pickCD <= 0) { pickCD = Math.max(110, 230 - diff * 22); spawnPickup(); }
        if (--coinCD <= 0) { coinCD = 50 + rng.nextInt(35); spawnCoin(); }
        if (--crateCD <= 0) { crateCD = 70 + rng.nextInt(50); spawnCrate(); }

        vsPick(p1, p1map); if (p2 != null) vsPick(p2, p2map);
        vsCoin(p1); if (p2 != null) vsCoin(p2);
        vsOil(p1); if (p2 != null) vsOil(p2);

        p1map.tickAndPrune(); if (p2 != null) p2map.tickAndPrune();

        if (p1 != null) {
            Integer boost = p1map.get(VelocityRivalsEngine.PU.BOOST);
            Integer shield = p1map.get(VelocityRivalsEngine.PU.SHIELD);
            if (boost != null && boost > 0) { p1.boosted = true; p1.boostT = boost; }
            else if (p1.boostT > 0) p1.boostT--;
            else p1.boosted = false;
            if (shield != null && shield > 0) { p1.shielded = true; p1.shieldT = shield; }
            else if (p1.shieldT > 0) p1.shieldT--;
            else p1.shielded = false;
            if (p1.invincibleT > 0) p1.invincibleT--;
            if (p1.dmgFlash > 0) p1.dmgFlash--;
            if (p1.health > 100) p1.health = 100;
        }
        if (p2 != null) {
            Integer boost = p2map.get(VelocityRivalsEngine.PU.BOOST);
            Integer shield = p2map.get(VelocityRivalsEngine.PU.SHIELD);
            if (boost != null && boost > 0) { p2.boosted = true; p2.boostT = boost; }
            else if (p2.boostT > 0) p2.boostT--;
            else p2.boosted = false;
            if (shield != null && shield > 0) { p2.shielded = true; p2.shieldT = shield; }
            else if (p2.shieldT > 0) p2.shieldT--;
            else p2.shielded = false;
            if (p2.invincibleT > 0) p2.invincibleT--;
            if (p2.dmgFlash > 0) p2.dmgFlash--;
            if (p2.health > 100) p2.health = 100;
        }

        if (screenShake > 0) screenShake--;

        missiles.removeIf(m -> {
            m.x += m.vx; m.y += m.vy; m.life--;
            if (m.life <= 0 || m.y < -100 || m.y > VelocityRivalsEngine.H + 100) return true;
            VelocityRivalsEngine.Car target = (m.owner == p1 && p2 != null) ? p2 : (m.owner == p2 && p1 != null) ? p1 : null;
            if (target != null && target.alive && m.box().intersects(target.box()) && collisionsEnabled) {
                if (target.shielded) { evt("🛡️ SHIELD BLOCKED MISSILE!"); return true; }
                gameOverDueToCollision(target.isP1 ? player1Name : player2Name, "a MISSILE"); return true;
            }
            for (VelocityRivalsEngine.AI a : cars) {
                if (a.alive && m.box().intersects(a.box()) && collisionsEnabled) {
                    a.alive = false; explode(a.x, a.y); m.owner.score += 150; evt("🎯 MISSILE DESTROYED AI! +150"); return true;
                }
            }
            return false;
        });

        oils.removeIf(o -> { o.life--; return o.life <= 0; });
        crates.removeIf(c -> c.done);
        sparks.removeIf(s -> s.life <= 0 || s.y > VelocityRivalsEngine.H + 200);
        for (VelocityRivalsEngine.Spark s : sparks) { s.x += s.vx; s.y += s.vy; s.vy += 0.10; s.life--; }

        if (p1 != null && p1.alive) p1.score += diff;
        if (p2 != null && p2.alive) p2.score += diff;

        checkAllCollisions();
    }

    void input(VelocityRivalsEngine.Car p, boolean isP2) {
        if (p == null || !p.alive || !started) return;
        double ac = 3.2 * thm.trc, mx = 8.0;
        if (currentGameMode == VelocityRivalsEngine.GameMode.RACE) {
            if (!multi) { if (keys[KeyEvent.VK_LEFT]) p.vx = Math.max(p.vx - ac, -mx); if (keys[KeyEvent.VK_RIGHT]) p.vx = Math.min(p.vx + ac, mx); p.vy *= 0.9; }
            else if (!isP2) { if (keys[KeyEvent.VK_A]) p.vx = Math.max(p.vx - ac, -mx); if (keys[KeyEvent.VK_D]) p.vx = Math.min(p.vx + ac, mx); p.vy *= 0.9; }
            else { if (keys[KeyEvent.VK_J]) p.vx = Math.max(p.vx - ac, -mx); if (keys[KeyEvent.VK_L]) p.vx = Math.min(p.vx + ac, mx); p.vy *= 0.9; }
        } else {
            if (!multi) { if (keys[KeyEvent.VK_LEFT]) p.vx = Math.max(p.vx - ac, -mx); if (keys[KeyEvent.VK_RIGHT]) p.vx = Math.min(p.vx + ac, mx); if (keys[KeyEvent.VK_UP]) p.vy = Math.max(p.vy - ac / 1.4, -mx); if (keys[KeyEvent.VK_DOWN]) p.vy = Math.min(p.vy + ac / 1.4, mx); }
            else if (!isP2) { if (keys[KeyEvent.VK_A]) p.vx = Math.max(p.vx - ac, -mx); if (keys[KeyEvent.VK_D]) p.vx = Math.min(p.vx + ac, mx); if (keys[KeyEvent.VK_W]) p.vy = Math.max(p.vy - ac / 1.4, -mx); if (keys[KeyEvent.VK_S]) p.vy = Math.min(p.vy + ac / 1.4, mx); }
            else { if (keys[KeyEvent.VK_J]) p.vx = Math.max(p.vx - ac, -mx); if (keys[KeyEvent.VK_L]) p.vx = Math.min(p.vx + ac, mx); if (keys[KeyEvent.VK_I]) p.vy = Math.max(p.vy - ac / 1.4, -mx); if (keys[KeyEvent.VK_K]) p.vy = Math.min(p.vy + ac / 1.4, mx); }
        }
    }

    void physics(VelocityRivalsEngine.Car p) {
        if (p == null || !p.alive) return;
        double trc = thm.trc;
        if (p.oilT > 0) { trc *= 0.22; p.oilT--; }
        p.vx *= 0.82 * trc;
        p.vy *= 0.86;
        p.x += p.vx;
        p.y += p.vy;
        double[] b = playerBounds(p);
        if (p.x < b[0]) { p.x = b[0]; p.vx = 0; }
        if (p.x > b[1]) { p.x = b[1]; p.vx = 0; }
        if (p.y < b[2]) { p.y = b[2]; p.vy = 7; }
        if (p.y > b[3]) { p.y = b[3]; p.vy = 0; }
    }

    void updateAI(double scroll) {
        cars.removeIf(ai -> {
            if (!ai.alive) return true;
            ai.y += scroll - ai.spd * 0.65;
            if (ai.y > VelocityRivalsEngine.H + 100 || ai.y < -200) return true;
            for (VelocityRivalsEngine.AI other : cars) {
                if (other != ai && other.alive && Math.abs(ai.y - other.y) < 60 && Math.abs(ai.x - other.x) < 40) {
                    if (ai.x < other.x) ai.x -= 2;
                    else ai.x += 2;
                }
            }
            if (--ai.cd <= 0 && ai.agg) {
                int best = ai.lane;
                double bestSc = Double.NEGATIVE_INFINITY;
                for (int l = 0; l < 3; l++) {
                    boolean p2s = (p2 != null && ai.x > VelocityRivalsEngine.W / 2);
                    double lx = getLX(l, p2s);
                    double d1 = (p1 != null && p1.alive) ? Math.abs(p1.x - lx) : 9999;
                    double d2 = (p2 != null && p2.alive) ? Math.abs(p2.x - lx) : 9999;
                    double sc = ai.agg ? -(Math.min(d1, d2)) : Math.min(d1, d2);
                    for (VelocityRivalsEngine.AI o : cars) if (o != ai && o.lane == l && Math.abs(o.y - ai.y) < 85) sc -= 200;
                    if (sc > bestSc) { bestSc = sc; best = l; }
                }
                ai.tgtLane = best;
                ai.cd = 55 + rng.nextInt(55);
            }
            boolean p2s = (p2 != null && ai.x > VelocityRivalsEngine.W / 2);
            double tx = getLX(ai.tgtLane, p2s);
            ai.x += (tx - ai.x) * 0.09;
            ai.lane = ai.tgtLane;
            if (frame % 4 == 0) exhaust(ai.x, ai.y - 24, ai.col);
            return false;
        });
    }

    void vsPick(VelocityRivalsEngine.Car p, VelocityRivalsEngine.CustomHashMap<VelocityRivalsEngine.PU, Integer> m) {
        if (p == null || !p.alive) return;
        pickups.removeIf(pk -> {
            if (pk.done) return false;
            if (p.box().intersects(pk.box())) {
                pk.done = true;
                applyPU(p, m, pk.type);
                ring(pk.x, pk.y, puCol(pk.type));
                return true;
            }
            return false;
        });
    }

    void vsCoin(VelocityRivalsEngine.Car p) {
        if (p == null || !p.alive) return;
        coins.removeIf(c -> {
            if (c.done) return false;
            if (p.box().intersects(c.box())) {
                c.done = true;
                p.score += 10;
                p.coins++;
                evt("COIN +10!");
                ring(c.x, c.y, new Color(0xFFD700));
                return true;
            }
            return false;
        });
    }

    void vsOil(VelocityRivalsEngine.Car p) {
        if (p == null || !p.alive) return;
        for (VelocityRivalsEngine.OilPatch o : oils)
            if (!o.used && p.box().intersects(o.box())) {
                p.oilT = 130;
                o.used = true;
                evt("🛢️ OIL SLICK! SLOWING DOWN!");
            }
    }

    void applyPU(VelocityRivalsEngine.Car p, VelocityRivalsEngine.CustomHashMap<VelocityRivalsEngine.PU, Integer> m, VelocityRivalsEngine.PU t) {
        switch (t) {
            case BOOST:
                m.put(VelocityRivalsEngine.PU.BOOST, 250);
                p.boosted = true;
                p.nitro = 100;
                p.score += 50;
                evt("🚀 SPEED BOOST! NITRO FULL! 🚀");
                break;
            case SHIELD:
                m.put(VelocityRivalsEngine.PU.SHIELD, 400);
                p.shielded = true;
                p.score += 50;
                evt("🛡️ SHIELD ACTIVE! (400 frames) 🛡️");
                break;
            case MISSILE:
                m.put(VelocityRivalsEngine.PU.MISSILE, 1);
                p.score += 50;
                evt("💣 MISSILE READY! Press Z/M to fire! 💣");
                break;
            case OIL:
                m.put(VelocityRivalsEngine.PU.OIL, 1);
                p.score += 50;
                evt("🛢️ OIL SLICK READY! Press Z/M to deploy! 🛢️");
                break;
            case SWAP:
                if (currentGameMode == VelocityRivalsEngine.GameMode.RACE && multi) {
                    swapPositions();
                    p.score += 100;
                    evt("🔄 SWAP BOOSTER ACTIVATED! +100 points! 🔄");
                } else {
                    p.score += 50;
                    evt("⚠️ SWAP only works in MULTIPLAYER RACE mode! +50 points instead ⚠️");
                }
                break;
        }
    }

    void usePU(VelocityRivalsEngine.Car p, VelocityRivalsEngine.CustomHashMap<VelocityRivalsEngine.PU, Integer> m) {
        if (p == null || !p.alive) return;
        if (m.containsKey(VelocityRivalsEngine.PU.MISSILE)) {
            m.remove(VelocityRivalsEngine.PU.MISSILE);
            VelocityRivalsEngine.Car target = null;
            if (multi) target = (p.isP1 ? p2 : p1);
            else {
                double bestDist = Double.MAX_VALUE;
                for (VelocityRivalsEngine.AI a : cars) if (a.alive) {
                    double d = Math.hypot(a.x - p.x, a.y - p.y);
                    if (d < bestDist) { bestDist = d; }
                }
            }
            if (target != null && target.alive) {
                double dx = target.x - p.x, dy = target.y - p.y;
                double len = Math.hypot(dx, dy);
                if (len > 0) missiles.add(new VelocityRivalsEngine.MissileProjectile(p.x, p.y - 20, dx / len * 8, dy / len * 8, p));
            }
            else missiles.add(new VelocityRivalsEngine.MissileProjectile(p.x, p.y - 20, 0, -8, p));
            evt("🚀 MISSILE FIRED! 🚀");
            return;
        }
        if (m.containsKey(VelocityRivalsEngine.PU.OIL)) {
            m.remove(VelocityRivalsEngine.PU.OIL);
            oils.add(new VelocityRivalsEngine.OilPatch(p.x, p.y + 45));
            ring(p.x, p.y + 45, new Color(0x334455));
            evt("🛢️ OIL DEPLOYED! 🛢️");
        }
    }

    void nitro(VelocityRivalsEngine.Car p, VelocityRivalsEngine.CustomHashMap<VelocityRivalsEngine.PU, Integer> m) {
        if (p == null || !p.alive || p.nitro < 20) return;
        p.nitro -= 20;
        p.boosted = true;
        p.boostT = 100;
        m.put(VelocityRivalsEngine.PU.BOOST, 100);
        evt(p.isP1 ? "P1 NITRO! 🔥" : "P2 NITRO! 🔥");
        screenShake = 8;
    }

    void spawnPickup() {
        VelocityRivalsEngine.PU[] ts = {VelocityRivalsEngine.PU.BOOST, VelocityRivalsEngine.PU.SHIELD, VelocityRivalsEngine.PU.MISSILE, VelocityRivalsEngine.PU.OIL, VelocityRivalsEngine.PU.SWAP};
        VelocityRivalsEngine.PU t = ts[rng.nextInt(ts.length)];
        pickups.add(new VelocityRivalsEngine.Pickup(getLX(rng.nextInt(3), false), VelocityRivalsEngine.ROAD_TOP + 20, t));
        if (multi) pickups.add(new VelocityRivalsEngine.Pickup(getLX(rng.nextInt(3), true), VelocityRivalsEngine.ROAD_TOP + 20, ts[rng.nextInt(ts.length)]));
    }

    void spawnCoin() {
        coins.add(new VelocityRivalsEngine.Coin(getLX(rng.nextInt(3), false), VelocityRivalsEngine.ROAD_TOP + 20));
        if (multi) coins.add(new VelocityRivalsEngine.Coin(getLX(rng.nextInt(3), true), VelocityRivalsEngine.ROAD_TOP + 20));
    }

    void spawnCrate() {
        boolean reinforced = rng.nextBoolean();
        crates.add(new VelocityRivalsEngine.Crate(getLX(rng.nextInt(3), false), VelocityRivalsEngine.ROAD_TOP + 20, reinforced));
        if (multi) crates.add(new VelocityRivalsEngine.Crate(getLX(rng.nextInt(3), true), VelocityRivalsEngine.ROAD_TOP + 20, reinforced));
    }

    void exhaust(double x, double y, Color c) {
        if (frame % 2 != 0) return;
        sparks.add(new VelocityRivalsEngine.Spark(x, y, (rng.nextDouble() - .5) * 1.2, rng.nextDouble() * 1.2 + 0.3, c.darker().darker(), 10, 3f));
    }

    void smoke(double x, double y) {
        for (int i = 0; i < 8; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            double s = 1 + rng.nextDouble() * 2.5;
            sparks.add(new VelocityRivalsEngine.Spark(x, y, Math.cos(a) * s, Math.sin(a) * s, new Color(100, 100, 100), 20, 4f));
        }
    }

    void explode(double x, double y) {
        Color[] cs = {Color.ORANGE, Color.RED, Color.YELLOW, Color.WHITE};
        for (int i = 0; i < 35; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            double s = 1.5 + rng.nextDouble() * 6.5;
            sparks.add(new VelocityRivalsEngine.Spark(x, y, Math.cos(a) * s, Math.sin(a) * s, cs[rng.nextInt(cs.length)], 40, 7f));
        }
    }

    void ring(double x, double y, Color c) {
        for (int i = 0; i < 12; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            double s = 1 + rng.nextDouble() * 3;
            sparks.add(new VelocityRivalsEngine.Spark(x, y, Math.cos(a) * s, Math.sin(a) * s, c, 24, 3f));
        }
    }

    // DRAWING METHODS
    void drawStars(Graphics2D g) {
        rng.setSeed(77);
        for (int i = 0; i < 90; i++) {
            int sx = rng.nextInt(VelocityRivalsEngine.W), sy = rng.nextInt(VelocityRivalsEngine.H);
            float fl = (float) (0.35 + 0.65 * Math.sin(frame * 0.04 + i * 1.3));
            float alpha = Math.min(0.7f, Math.max(0.15f, 0.15f + 0.55f * fl));
            g.setColor(new Color(1f, 1f, 1f, alpha));
            g.fillOval(sx, sy, rng.nextInt(2) + 1, rng.nextInt(2) + 1);
        }
        rng.setSeed(System.nanoTime());
    }

    void drawGrid(Graphics2D g) {
        g.setColor(new Color(255, 255, 255, 7));
        int gs = 60, off = (int) (frame * 0.35) % gs;
        for (int x = 0; x <= VelocityRivalsEngine.W; x += gs) g.drawLine(x, 0, x, VelocityRivalsEngine.H);
        for (int y = -gs; y <= VelocityRivalsEngine.H; y += gs) g.drawLine(0, y + off, VelocityRivalsEngine.W, y + off);
    }

    void drawBtn(Graphics2D g, int cx, int y, int w, int h, String txt, Color col, boolean hov) {
        int x = cx - w / 2;
        if (hov) {
            g.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), 45));
            g.fillRoundRect(x - 4, y - 4, w + 8, h + 8, 12, 12);
        }
        g.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), 30));
        g.fillRoundRect(x, y, w, h, 8, 8);
        g.setColor(hov ? col : col.darker());
        g.setStroke(new BasicStroke(hov ? 2.5f : 1.5f));
        g.drawRoundRect(x, y, w, h, 8, 8);
        g.setStroke(new BasicStroke(1));
        g.setFont(fB.deriveFont(18f));
        g.setColor(hov ? Color.WHITE : col);
        cs(g, txt, cx, y + h / 2 + 7);
    }

    void backBtn(Graphics2D g) {
        g.setColor(new Color(255, 255, 255, 25));
        g.fillRoundRect(25, VelocityRivalsEngine.H - 74, 122, 47, 8, 8);
        g.setColor(new Color(0x00E5FF));
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(25, VelocityRivalsEngine.H - 74, 122, 47, 8, 8);
        g.setStroke(new BasicStroke(1));
        g.setFont(fB.deriveFont(15f));
        g.setColor(Color.WHITE);
        cs(g, "← BACK", 86, VelocityRivalsEngine.H - 74 + 29);
    }

    void cs(Graphics2D g, String s, int cx, int cy) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, cx - fm.stringWidth(s) / 2, cy);
    }

    String fmt(int s) { return String.format("%02d:%02d", s / 60, s % 60); }

    Color puCol(VelocityRivalsEngine.PU t) {
        switch (t) {
            case BOOST: return new Color(0xFF8C00);
            case SHIELD: return new Color(0x00BFFF);
            case MISSILE: return new Color(0xFF2222);
            case OIL: return new Color(0x445566);
            case SWAP: return new Color(0x9B59B6);
            default: return Color.WHITE;
        }
    }

    String puLbl(VelocityRivalsEngine.PU t) {
        switch (t) {
            case BOOST: return "NOS";
            case SHIELD: return "SHD";
            case MISSILE: return "MSL";
            case OIL: return "OIL";
            case SWAP: return "SWP";
            default: return "???";
        }
    }

    void drawDesertSides(Graphics2D g, int ox, int vw, int rl, int rr) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0xF5DEB3));
        g.fillRect(ox, VelocityRivalsEngine.H - 180, rl - ox, 180);
        g.fillRect(rr, VelocityRivalsEngine.H - 180, ox + vw - rr, 180);
        g.setColor(new Color(0xE8C99A));
        int[] hill1x = {ox - 20, ox + 40, ox + 100, ox + 160, Math.min(rl + 10, ox + vw - 10)};
        int[] hill1y = {VelocityRivalsEngine.H - 100, VelocityRivalsEngine.H - 220, VelocityRivalsEngine.H - 250, VelocityRivalsEngine.H - 200, VelocityRivalsEngine.H - 100};
        if (rl - ox > 30) g.fillPolygon(hill1x, hill1y, 5);
        g.setColor(new Color(0xD4A373));
        int[] hill2x = {ox + 30, ox + 90, ox + 150, Math.max(rl - 20, ox + 10)};
        int[] hill2y = {VelocityRivalsEngine.H - 90, VelocityRivalsEngine.H - 200, VelocityRivalsEngine.H - 220, VelocityRivalsEngine.H - 100};
        if (rl - ox > 30) g.fillPolygon(hill2x, hill2y, 4);
        g.setColor(new Color(0xE8C99A));
        int[] hill1rx = {rr - 10, Math.max(ox + vw - 150, rr + 10), Math.max(ox + vw - 90, rr + 10), Math.max(ox + vw - 40, rr + 10), ox + vw + 20};
        int[] hill1ry = {VelocityRivalsEngine.H - 100, VelocityRivalsEngine.H - 210, VelocityRivalsEngine.H - 240, VelocityRivalsEngine.H - 200, VelocityRivalsEngine.H - 100};
        if (ox + vw - rr > 30) g.fillPolygon(hill1rx, hill1ry, 5);
        g.setColor(new Color(0xFFD700));
        g.fillOval(ox + vw - 70, 25, 45, 45);
        g.setColor(new Color(0xFFF0A0));
        g.fillOval(ox + vw - 65, 30, 35, 35);
    }

    void drawNightSides(Graphics2D g, int ox, int vw, int rl, int rr) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x1A1025));
        g.fillRect(ox, VelocityRivalsEngine.H - 180, rl - ox, 180);
        g.fillRect(rr, VelocityRivalsEngine.H - 180, ox + vw - rr, 180);
        g.setColor(new Color(0xFFF5C0));
        g.fillOval(ox + vw - 90, 25, 55, 55);
        g.setColor(thm.bgTop);
        g.fillOval(ox + vw - 82, 20, 50, 50);
        for (int i = 0; i < 60; i++) {
            int sx = ox + 10 + (i * 37) % Math.max(1, vw - 70);
            int sy = 30 + (i * 23) % 140;
            float twinkle = (float) (0.4 + 0.6 * Math.sin(frame * 0.05 + i));
            float alpha = Math.min(0.8f, Math.max(0.2f, twinkle));
            int size = 2 + (i % 3);
            g.setColor(new Color(1f, 1f, 0.9f, alpha));
            g.fillOval(sx, sy, size, size);
            if (i % 7 == 0) {
                g.setColor(new Color(1f, 1f, 0.7f, alpha * 0.5f));
                g.fillOval(sx - 1, sy - 1, size + 2, size + 2);
            }
        }
    }

    void drawHighwaySides(Graphics2D g, int ox, int vw, int rl, int rr) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0xE8E8F0));
        g.fillRect(ox, VelocityRivalsEngine.H - 180, rl - ox, 180);
        g.fillRect(rr, VelocityRivalsEngine.H - 180, ox + vw - rr, 180);
        Color[] pastels = {new Color(0xFFB3BA), new Color(0xFFDFBA), new Color(0xBAFFC9), new Color(0xBAE1FF)};
        for (int i = 0; i < 4; i++) {
            int bx = ox + 15 + i * 55;
            if (bx + 25 < rl) {
                int height = 100 + (i * 15) % 80;
                g.setColor(pastels[i % pastels.length]);
                g.fillRect(bx, VelocityRivalsEngine.H - 80 - height, 22, height);
                g.setColor(new Color(255, 255, 255, 180));
                for (int wy = VelocityRivalsEngine.H - 70 - height; wy < VelocityRivalsEngine.H - 90; wy += 15) {
                    g.fillRect(bx + 4, wy, 5, 8);
                    g.fillRect(bx + 13, wy, 5, 8);
                }
                g.setColor(new Color(255, 200, 100));
                g.fillRect(bx + 8, VelocityRivalsEngine.H - 88 - height, 6, 10);
            }
        }
        for (int i = 0; i < 4; i++) {
            int bx = rr + 20 + i * 60;
            if (bx + 25 < ox + vw) {
                int height = 110 + (i * 12) % 70;
                g.setColor(pastels[(i + 2) % pastels.length]);
                g.fillRect(bx, VelocityRivalsEngine.H - 80 - height, 22, height);
                g.setColor(new Color(255, 255, 255, 180));
                for (int wy = VelocityRivalsEngine.H - 70 - height; wy < VelocityRivalsEngine.H - 90; wy += 15) {
                    g.fillRect(bx + 4, wy, 5, 8);
                    g.fillRect(bx + 13, wy, 5, 8);
                }
                g.setColor(new Color(255, 200, 100));
                g.fillRect(bx + 8, VelocityRivalsEngine.H - 88 - height, 6, 10);
            }
        }
        g.setColor(new Color(0xFFE066));
        g.fillOval(ox + vw - 80, 20, 55, 55);
        g.setColor(new Color(0xFFF0A0));
        g.fillOval(ox + vw - 75, 25, 45, 45);
    }

    void drawHalf(Graphics2D g, int ox, int vw, VelocityRivalsEngine.Car p, VelocityRivalsEngine.CustomHashMap<VelocityRivalsEngine.PU, Integer> m, boolean isP2) {
        int rl = getRoadLeft(isP2), rr = rl + VelocityRivalsEngine.ROAD_W;
        rl = Math.max(ox, Math.min(rl, ox + vw - VelocityRivalsEngine.ROAD_W));
        rr = rl + VelocityRivalsEngine.ROAD_W;
        g.setPaint(new GradientPaint(ox, 0, thm.bgTop, ox, VelocityRivalsEngine.H, thm.bgBot));
        g.fillRect(ox, 0, vw, VelocityRivalsEngine.H);
        switch (thm) {
            case DESERT: drawDesertSides(g, ox, vw, rl, rr); break;
            case NIGHT: drawNightSides(g, ox, vw, rl, rr); break;
            case HIGHWAY: drawHighwaySides(g, ox, vw, rl, rr); break;
        }
        g.setColor(thm.road);
        g.fillRect(rl, 0, VelocityRivalsEngine.ROAD_W, VelocityRivalsEngine.H);
        for (int y = 0; y < VelocityRivalsEngine.H; y += 40) {
            int mod = ((y + (int) roadOff) % 80 + 80) % 80;
            g.setColor(mod < 40 ? new Color(0xDD3333) : Color.WHITE);
            g.fillRoundRect(rl, y, 10, 35, 5, 5);
            g.fillRoundRect(rr - 10, y, 10, 35, 5, 5);
        }
        g.setColor(thm.lane);
        float[] dash = {28f, 20f};
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_MITER, 10, dash, (float) roadOff));
        for (int l = 1; l < 3; l++) {
            int lx = rl + l * (VelocityRivalsEngine.ROAD_W / 3);
            g.drawLine(lx, 0, lx, VelocityRivalsEngine.H);
        }
        g.setStroke(new BasicStroke(1));
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(3));
        g.drawLine(rl + 12, 0, rl + 12, VelocityRivalsEngine.H);
        g.drawLine(rr - 12, 0, rr - 12, VelocityRivalsEngine.H);
        g.setStroke(new BasicStroke(1));

        for (VelocityRivalsEngine.OilPatch o : oils) {
            if (!multi || (isP2 ? (o.x >= VelocityRivalsEngine.W / 2) : (o.x < VelocityRivalsEngine.W / 2))) {
                float a = (float) o.life / 360f;
                g.setColor(new Color(0.1f, 0.1f, 0.2f, 0.75f * a));
                g.fillOval((int) o.x - 24, (int) o.y - 12, 48, 24);
            }
        }

        for (VelocityRivalsEngine.MissileProjectile msl : missiles) {
            if ((!multi || (isP2 ? (msl.x >= VelocityRivalsEngine.W / 2) : (msl.x < VelocityRivalsEngine.W / 2))) && msl.owner == p) {
                g.setColor(new Color(0xFF4444));
                g.fillOval((int) msl.x - 6, (int) msl.y - 6, 12, 12);
                g.setColor(Color.WHITE);
                g.fillOval((int) msl.x - 3, (int) msl.y - 3, 6, 6);
            }
        }

        for (VelocityRivalsEngine.Crate c : crates) {
            boolean mine = !multi || (isP2 ? (c.x >= VelocityRivalsEngine.W / 2 - 5) : (c.x < VelocityRivalsEngine.W / 2 + 5));
            if (mine && !c.done) {
                c.tick++;
                c.y += 5 * thm.spd;
                if (c.y > VelocityRivalsEngine.H + 40) c.done = true;
                else {
                    int cx = (int) c.x, cy = (int) c.y;
                    Color crateColor = c.reinforced ? new Color(0x8B4513) : new Color(0xB8860B);
                    g.setColor(crateColor);
                    g.fillRect(cx - 16, cy - 16, 32, 32);
                    g.setColor(new Color(0x654321));
                    g.setStroke(new BasicStroke(2));
                    g.drawLine(cx - 16, cy, cx + 16, cy);
                    g.drawLine(cx, cy - 16, cx, cy + 16);
                    g.setStroke(new BasicStroke(1));
                    if (c.reinforced) {
                        g.setColor(new Color(0xFFD700));
                        g.drawRect(cx - 14, cy - 14, 28, 28);
                    }
                }
            }
        }

        for (VelocityRivalsEngine.Coin c : coins) {
            boolean mine = !multi || (isP2 ? (c.x >= VelocityRivalsEngine.W / 2 - 5) : (c.x < VelocityRivalsEngine.W / 2 + 5));
            if (mine && !c.done) {
                c.tick++;
                c.y += 5 * thm.spd;
                if (c.y > VelocityRivalsEngine.H + 40) c.done = true;
                else {
                    float pulse = 0.8f + 0.2f * (float) Math.sin(c.tick * 0.15);
                    int cx = (int) c.x, cy = (int) c.y;
                    g.setColor(new Color(0xFFD700));
                    g.fillOval(cx - 9, cy - 9, 18, 18);
                    g.setColor(new Color(0xFFF0C0));
                    g.fillOval(cx - 6, cy - 7, 7, 7);
                    g.setFont(fS.deriveFont(9f));
                    g.setColor(new Color(0x6B4C10));
                    cs(g, "$", cx, cy + 4);
                }
            }
        }

        for (VelocityRivalsEngine.Pickup pk : pickups) {
            boolean mine = !multi || (isP2 ? (pk.x >= VelocityRivalsEngine.W / 2 - 5) : (pk.x < VelocityRivalsEngine.W / 2 + 5));
            if (mine && !pk.done) {
                pk.tick++;
                pk.y += 5.2 * thm.spd;
                if (pk.y > VelocityRivalsEngine.H + 40) pk.done = true;
                else {
                    int px = (int) pk.x, py = (int) pk.y;
                    Color col = puCol(pk.type);
                    g.setColor(col);
                    g.fillRoundRect(px - 14, py - 14, 28, 28, 7, 7);
                    g.setColor(Color.WHITE);
                    g.setStroke(new BasicStroke(2));
                    g.drawRoundRect(px - 14, py - 14, 28, 28, 7, 7);
                    g.setStroke(new BasicStroke(1));
                    g.setFont(fS.deriveFont(10f));
                    g.setColor(Color.WHITE);
                    cs(g, puLbl(pk.type), px, py + 4);
                }
            }
        }

        for (VelocityRivalsEngine.AI ai : cars) {
            if (!ai.alive) continue;
            boolean mine = !multi || (isP2 ? (ai.x >= VelocityRivalsEngine.W / 2 - 10) : (ai.x < VelocityRivalsEngine.W / 2 + 10));
            if (mine) {
                int cx = (int) ai.x, cy = (int) ai.y;
                g.setColor(new Color(0, 0, 0, 45));
                g.fillOval(cx - 15, cy + 22, 30, 11);
                g.setColor(ai.col);
                g.fillRoundRect(cx - 15, cy - 30, 30, 62, 9, 9);
                g.setColor(ai.col.darker());
                g.fillRect(cx - 15, cy - 12, 30, 5);
                g.setColor(Color.BLACK);
                g.fillOval(cx - 12, cy + 25, 7, 5);
                g.fillOval(cx + 5, cy + 25, 7, 5);
            }
        }

        if (p != null && p.alive) {
            int cx = (int) p.x, cy = (int) p.y;
            if (p.shielded) {
                float pulse = 0.55f + 0.45f * (float) Math.sin(frame * 0.15);
                g.setColor(new Color(0f, 0.88f, 1f, 0.22f * pulse));
                g.fillOval(cx - 32, cy - 46, 64, 84);
                g.setColor(new Color(0f, 0.88f, 1f, 0.85f));
                g.setStroke(new BasicStroke(2.5f));
                g.drawOval(cx - 32, cy - 46, 64, 84);
                g.setStroke(new BasicStroke(1));
            }
            if (p.boosted && frame % 2 == 0) {
                int fh = 11 + rng.nextInt(13);
                g.setColor(new Color(255, 140, 0, 200));
                g.fillPolygon(new int[]{cx - 8, cx + 8, cx}, new int[]{cy + 32, cy + 32, cy + 32 + fh}, 3);
                g.setColor(new Color(255, 255, 0, 150));
                g.fillPolygon(new int[]{cx - 5, cx + 5, cx}, new int[]{cy + 32, cy + 32, cy + 32 + fh - 5}, 3);
            }
            boolean invFlicker = (p.invincibleT > 0 && (frame % 4 < 2));
            if (!invFlicker) {
                g.setColor(new Color(0, 0, 0, 65));
                g.fillOval(cx - 19, cy + 24, 38, 13);
                g.setColor(p.body);
                g.fillRoundRect(cx - 15, cy - 30, 30, 62, 9, 9);
                g.setColor(p.accent);
                g.fillRect(cx - 15, cy - 12, 30, 5);
                g.setColor(p.body.brighter());
                g.fillRoundRect(cx - 11, cy - 30, 22, 18, 7, 7);
                g.setColor(new Color(160, 215, 255, 172));
                g.fillRoundRect(cx - 9, cy - 26, 18, 12, 4, 4);
                g.setColor(p.boosted ? new Color(0xFFFF00) : new Color(0xFFFFCC));
                g.fillOval(cx - 13, cy - 35, 8, 6);
                g.fillOval(cx + 5, cy - 35, 8, 6);
                g.setColor(Color.BLACK);
                g.fillOval(cx - 12, cy + 25, 7, 5);
                g.fillOval(cx + 5, cy + 25, 7, 5);
            } else {
                g.setColor(new Color(0, 0, 0, 40));
                g.fillOval(cx - 19, cy + 24, 38, 13);
                g.setColor(new Color(p.body.getRed(), p.body.getGreen(), p.body.getBlue(), 100));
                g.fillRoundRect(cx - 15, cy - 30, 30, 62, 9, 9);
                g.setColor(new Color(p.accent.getRed(), p.accent.getGreen(), p.accent.getBlue(), 100));
                g.fillRect(cx - 15, cy - 12, 30, 5);
            }
            int bw = 40, bh = 6;
            g.setColor(new Color(0, 0, 0, 150));
            g.fillRect(cx - bw / 2, cy - 38, bw, bh);
            Color healthColor = p.health > 60 ? new Color(0x00FF00) : (p.health > 30 ? new Color(0xFFA500) : new Color(0xFF0000));
            g.setColor(healthColor);
            g.fillRect(cx - bw / 2, cy - 38, (int) (bw * p.health / 100.0), bh);
            g.setColor(Color.WHITE);
            g.drawRect(cx - bw / 2, cy - 38, bw, bh);
            int nbw = 36, nbh = 5;
            g.setColor(new Color(0, 0, 0, 100));
            g.fillRect(cx - nbw / 2, cy - 52, nbw, nbh);
            g.setColor(p.isP1 ? new Color(0x00E5FF) : new Color(0xFF4757));
            g.fillRect(cx - nbw / 2, cy - 52, (int) (nbw * p.nitro / 100.0), nbh);
            g.setColor(Color.WHITE);
            g.drawRect(cx - nbw / 2, cy - 52, nbw, nbh);
            if (p.dmgFlash > 0) {
                g.setColor(new Color(255, 0, 0, 100));
                g.fillRoundRect(cx - 15, cy - 30, 30, 62, 9, 9);
            }
        }
    }

    void drawSparks(Graphics2D g) {
        for (VelocityRivalsEngine.Spark s : sparks) {
            float a = Math.min(1f, (float) s.life / s.maxL);
            g.setColor(new Color(s.col.getRed(), s.col.getGreen(), s.col.getBlue(), (int) (255 * a)));
            int sz = Math.max(1, (int) (s.sz * a));
            g.fillOval((int) s.x - sz / 2, (int) s.y - sz / 2, sz, sz);
        }
    }

    void drawCountdown(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 200));
        g.fillRect(0, 0, VelocityRivalsEngine.W, VelocityRivalsEngine.H);
        String txt = cdNum > 0 ? String.valueOf(cdNum) : "GO!";
        Color col = cdNum > 0 ? new Color(0xFF4757) : new Color(0x00E676);
        for (int gw = 12; gw >= 0; gw--) {
            g.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), (int) (7 * gw)));
            g.setFont(fT.deriveFont(112 + gw * 3));
            FontMetrics fm = g.getFontMetrics();
            g.drawString(txt, (VelocityRivalsEngine.W - fm.stringWidth(txt)) / 2 - gw, VelocityRivalsEngine.H / 2 + 55 + gw);
        }
        g.setFont(fT.deriveFont(112f));
        g.setColor(col);
        cs(g, txt, VelocityRivalsEngine.W / 2, VelocityRivalsEngine.H / 2 + 55);
        g.setFont(fB.deriveFont(19f));
        g.setColor(Color.LIGHT_GRAY);
        cs(g, "GET READY!", VelocityRivalsEngine.W / 2, VelocityRivalsEngine.H / 2 - 55);
    }

    void drawHUD(Graphics2D g) {
        if (!multi && p1 != null) {
            int px = 10, py = 20;
            g.setColor(new Color(0, 0, 0, 180));
            g.fillRoundRect(px, py, 210, 150, 12, 12);
            g.setColor(new Color(0x00E5FF));
            g.setStroke(new BasicStroke(1.5f));
            g.drawRoundRect(px, py, 210, 150, 12, 12);
            g.setFont(fS.deriveFont(Font.BOLD, 13f));
            g.setColor(new Color(0x00E5FF));
            g.drawString("🏎️ " + (player1Name.isEmpty() ? "RACER" : player1Name), px + 10, py + 20);
            g.setFont(fH.deriveFont(20f));
            g.setColor(Color.WHITE);
            g.drawString(String.format("%,d", p1.score), px + 10, py + 48);
            g.setFont(fS.deriveFont(11f));
            g.setColor(new Color(255, 215, 0));
            g.drawString("🪙 " + p1.coins, px + 10, py + 70);
            g.setColor(new Color(255, 215, 0));
            g.drawString("⚡ LV." + diff, px + 10, py + 88);
            g.setColor(Color.WHITE);
            g.drawString("💨", px + 10, py + 108);
            g.setColor(new Color(0, 0, 0, 100));
            g.fillRoundRect(px + 28, py + 100, 80, 8, 4, 4);
            g.setColor(new Color(255, 140, 0));
            g.fillRoundRect(px + 28, py + 100, (int) (80 * p1.nitro / 100.0), 8, 4, 4);
            g.setColor(Color.WHITE);
            g.drawString("❤️", px + 10, py + 130);
            g.setColor(new Color(0, 0, 0, 100));
            g.fillRoundRect(px + 28, py + 122, 80, 8, 4, 4);
            Color hpColor = p1.health > 60 ? new Color(0x00FF00) : (p1.health > 30 ? new Color(0xFFA500) : new Color(0xFF0000));
            g.setColor(hpColor);
            g.fillRoundRect(px + 28, py + 122, (int) (80 * p1.health / 100.0), 8, 4, 4);
        } else if (multi && p1 != null && p2 != null) {
            int px1 = 10, py = 20;
            g.setColor(new Color(0, 0, 0, 180));
            g.fillRoundRect(px1, py, 210, 150, 12, 12);
            g.setColor(new Color(0x00E5FF));
            g.drawRoundRect(px1, py, 210, 150, 12, 12);
            g.setFont(fS.deriveFont(Font.BOLD, 13f));
            g.setColor(new Color(0x00E5FF));
            g.drawString("🏎️ " + (player1Name.isEmpty() ? "RACER1" : player1Name), px1 + 10, py + 20);
            g.setFont(fH.deriveFont(20f));
            g.setColor(Color.WHITE);
            g.drawString(String.format("%,d", p1.score), px1 + 10, py + 48);
            g.setFont(fS.deriveFont(11f));
            g.setColor(new Color(255, 215, 0));
            g.drawString("🪙 " + p1.coins, px1 + 10, py + 70);
            g.setColor(Color.WHITE);
            g.drawString("💨", px1 + 10, py + 100);
            g.setColor(new Color(0, 0, 0, 100));
            g.fillRoundRect(px1 + 28, py + 92, 80, 8, 4, 4);
            g.setColor(new Color(255, 140, 0));
            g.fillRoundRect(px1 + 28, py + 92, (int) (80 * p1.nitro / 100.0), 8, 4, 4);
            g.setColor(Color.WHITE);
            g.drawString("❤️", px1 + 10, py + 122);
            g.setColor(new Color(0, 0, 0, 100));
            g.fillRoundRect(px1 + 28, py + 114, 80, 8, 4, 4);
            Color hpColor1 = p1.health > 60 ? new Color(0x00FF00) : (p1.health > 30 ? new Color(0xFFA500) : new Color(0xFF0000));
            g.setColor(hpColor1);
            g.fillRoundRect(px1 + 28, py + 114, (int) (80 * p1.health / 100.0), 8, 4, 4);
            int px2 = VelocityRivalsEngine.W - 210;
            g.setColor(new Color(0, 0, 0, 180));
            g.fillRoundRect(px2, py, 210, 150, 12, 12);
            g.setColor(new Color(0xFF4757));
            g.drawRoundRect(px2, py, 210, 150, 12, 12);
            g.setFont(fS.deriveFont(Font.BOLD, 13f));
            g.setColor(new Color(0xFF4757));
            g.drawString("🏎️ " + (player2Name.isEmpty() ? "RACER2" : player2Name), px2 + 10, py + 20);
            g.setFont(fH.deriveFont(20f));
            g.setColor(Color.WHITE);
            g.drawString(String.format("%,d", p2.score), px2 + 10, py + 48);
            g.setFont(fS.deriveFont(11f));
            g.setColor(new Color(255, 215, 0));
            g.drawString("🪙 " + p2.coins, px2 + 10, py + 70);
            g.setColor(Color.WHITE);
            g.drawString("💨", px2 + 10, py + 100);
            g.setColor(new Color(0, 0, 0, 100));
            g.fillRoundRect(px2 + 28, py + 92, 80, 8, 4, 4);
            g.setColor(new Color(255, 140, 0));
            g.fillRoundRect(px2 + 28, py + 92, (int) (80 * p2.nitro / 100.0), 8, 4, 4);
            g.setColor(Color.WHITE);
            g.drawString("❤️", px2 + 10, py + 122);
            g.setColor(new Color(0, 0, 0, 100));
            g.fillRoundRect(px2 + 28, py + 114, 80, 8, 4, 4);
            Color hpColor2 = p2.health > 60 ? new Color(0x00FF00) : (p2.health > 30 ? new Color(0xFFA500) : new Color(0xFF0000));
            g.setColor(hpColor2);
            g.fillRoundRect(px2 + 28, py + 114, (int) (80 * p2.health / 100.0), 8, 4, 4);
        }
        if (currentGameMode != VelocityRivalsEngine.GameMode.RACE) {
            g.setColor(new Color(0, 0, 0, 150));
            g.fillRoundRect(VelocityRivalsEngine.W / 2 - 150, 8, 300, 28, 10, 10);
            g.setColor(new Color(0x00E5FF));
            g.drawRoundRect(VelocityRivalsEngine.W / 2 - 150, 8, 300, 28, 10, 10);
            g.setFont(fS.deriveFont(12f));
            String themeIcon = thm == VelocityRivalsEngine.Theme.DESERT ? "🏜️" : (thm == VelocityRivalsEngine.Theme.NIGHT ? "🌙" : "🛣️");
            g.setColor(new Color(0x00E5FF));
            g.drawString(themeIcon + " " + thm.label, VelocityRivalsEngine.W / 2 - 140, 27);
            g.setColor(Color.WHITE);
            g.drawString("⏱️ " + fmt(secs), VelocityRivalsEngine.W / 2 - 40, 27);
            g.setColor(new Color(0xFFD700));
            g.drawString("⚡" + diff, VelocityRivalsEngine.W / 2 + 80, 27);
        }
        int ex = 15, ey = VelocityRivalsEngine.H - 95;
        g.setColor(new Color(0, 0, 0, 140));
        g.fillRoundRect(ex, ey, 210, 85, 8, 8);
        g.setColor(new Color(0x00E5FF));
        g.drawRoundRect(ex, ey, 210, 85, 8, 8);
        g.setFont(fS.deriveFont(9f));
        g.setColor(new Color(0x00E5FF));
        g.drawString("EVENTS", ex + 8, ey + 14);
        for (int i = 0; i < Math.min(5, evtStk.size()); i++) {
            String event = evtStk.get(i);
            if (event != null) {
                if (event.length() > 28) event = event.substring(0, 25) + "...";
                g.drawString("• " + event, ex + 8, ey + 30 + i * 14);
            }
        }
    }

    void drawRaceProgress(Graphics2D g) {
        if (currentGameMode != VelocityRivalsEngine.GameMode.RACE) return;
        int barWidth = VelocityRivalsEngine.W - 100, barX = 50, barY = 55, barH = 20;
        g.setColor(new Color(0, 0, 0, 180));
        g.fillRoundRect(barX - 2, barY - 2, barWidth + 4, barH + 4, 10, 10);
        if (!multi && p1 != null) {
            double progress = Math.min(1.0, p1.distance / RACE_DISTANCE);
            g.setColor(new Color(0x00E5FF));
            g.fillRoundRect(barX, barY, (int) (barWidth * progress), barH, 8, 8);
            g.setColor(Color.WHITE);
            g.drawRoundRect(barX, barY, barWidth, barH, 8, 8);
            g.setFont(fB.deriveFont(14f));
            cs(g, String.format("DISTANCE: %.0fm / %.0fm", p1.distance, RACE_DISTANCE), VelocityRivalsEngine.W / 2, barY - 8);
            int timeLeft = (int) Math.max(0, raceTimeLimit - secs);
            Color timerColor = timeLeft < 30 ? Color.RED : (timeLeft < 60 ? Color.YELLOW : Color.WHITE);
            g.setColor(timerColor);
            cs(g, String.format("⏰ TIME: %02d:%02d", timeLeft / 60, timeLeft % 60), VelocityRivalsEngine.W / 2, barY + barH + 18);
            for (int i = 1; i < 5; i++) {
                int checkpointX = barX + (int) (barWidth * (i / 5.0));
                g.setColor(new Color(0xFFD700));
                g.fillRect(checkpointX - 2, barY - 5, 4, barH + 10);
            }
        } else if (multi && p1 != null && p2 != null) {
            double p1Progress = Math.min(1.0, p1.distance / RACE_DISTANCE);
            int p1BarWidth = (int) ((VelocityRivalsEngine.W / 2 - 40) * p1Progress);
            g.setColor(new Color(0x00E5FF));
            g.fillRoundRect(20, barY, p1BarWidth, barH, 8, 8);
            g.setColor(Color.WHITE);
            g.drawRoundRect(20, barY, VelocityRivalsEngine.W / 2 - 40, barH, 8, 8);
            g.setFont(fS.deriveFont(10f));
            g.setColor(new Color(0x00E5FF));
            cs(g, (player1Name.isEmpty() ? "P1" : player1Name) + ": " + (int) p1.distance + "m", VelocityRivalsEngine.W / 4, barY - 5);
            double p2Progress = Math.min(1.0, p2.distance / RACE_DISTANCE);
            int p2BarWidth = (int) ((VelocityRivalsEngine.W / 2 - 40) * p2Progress);
            g.setColor(new Color(0xFF4757));
            g.fillRoundRect(VelocityRivalsEngine.W / 2 + 20, barY, p2BarWidth, barH, 8, 8);
            g.setColor(Color.WHITE);
            g.drawRoundRect(VelocityRivalsEngine.W / 2 + 20, barY, VelocityRivalsEngine.W / 2 - 40, barH, 8, 8);
            g.setColor(new Color(0xFF4757));
            cs(g, (player2Name.isEmpty() ? "P2" : player2Name) + ": " + (int) p2.distance + "m", 3 * VelocityRivalsEngine.W / 4, barY - 5);
            if (p1.distance > p2.distance) {
                g.setColor(new Color(0x00E5FF));
                cs(g, "🏆 LEADING", VelocityRivalsEngine.W / 4, barY + barH + 15);
            }
            else if (p2.distance > p1.distance) {
                g.setColor(new Color(0xFF4757));
                cs(g, "🏆 LEADING", 3 * VelocityRivalsEngine.W / 4, barY + barH + 15);
            }
        }
    }

    void drawPause(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 200));
        g.fillRect(0, 0, VelocityRivalsEngine.W, VelocityRivalsEngine.H);
        g.setColor(new Color(0x00E5FF));
        g.setFont(fT.deriveFont(54f));
        cs(g, "PAUSED", VelocityRivalsEngine.W / 2, VelocityRivalsEngine.H / 2 - 70);
        drawBtn(g, VelocityRivalsEngine.W / 2, VelocityRivalsEngine.H / 2 + 20, 200, 50, "RESUME", new Color(0x00E5FF), false);
        drawBtn(g, VelocityRivalsEngine.W / 2, VelocityRivalsEngine.H / 2 + 90, 200, 50, "MAIN MENU", new Color(0xFF8C00), false);
        g.setFont(fS.deriveFont(12f));
        g.setColor(Color.GRAY);
        cs(g, "ESC=Resume  Q=Quit", VelocityRivalsEngine.W / 2, VelocityRivalsEngine.H / 2 + 165);
    }

    void drawBoard(Graphics2D g) {
        g.setPaint(new GradientPaint(0, 0, new Color(0x06060F), 0, VelocityRivalsEngine.H, new Color(0x101028)));
        g.fillRect(0, 0, VelocityRivalsEngine.W, VelocityRivalsEngine.H);
        drawStars(g);
        g.setColor(new Color(0xFFD700));
        g.setFont(fT.deriveFont(52f));
        cs(g, "🏆 LEADERBOARD 🏆", VelocityRivalsEngine.W / 2, 80);
        Object[] sortedObj = board.toSortedArray();
        VelocityRivalsEngine.ScoreEntry[] sorted = new VelocityRivalsEngine.ScoreEntry[sortedObj.length];
        for (int i = 0; i < sortedObj.length; i++) sorted[i] = (VelocityRivalsEngine.ScoreEntry) sortedObj[i];
        Color[] rankCols = {new Color(0xFFD700), new Color(0xC0C0C0), new Color(0xCD7F32)};
        String[] medals = {"🥇", "🥈", "🥉"};
        if (sorted.length == 0) {
            g.setFont(fH.deriveFont(20f));
            g.setColor(Color.GRAY);
            cs(g, "No scores yet! Play a game!", VelocityRivalsEngine.W / 2, VelocityRivalsEngine.H / 2);
        } else {
            for (int i = 0; i < Math.min(sorted.length, 10); i++) {
                VelocityRivalsEngine.ScoreEntry e = sorted[i];
                int ry = 140 + i * 52;
                g.setColor(new Color(255, 255, 255, i % 2 == 0 ? 12 : 6));
                g.fillRoundRect(VelocityRivalsEngine.W / 2 - 360, ry, 720, 48, 10, 10);
                g.setFont(fH.deriveFont(24f));
                if (i < 3) {
                    g.setColor(rankCols[i]);
                    g.drawString(medals[i], VelocityRivalsEngine.W / 2 - 330, ry + 33);
                } else {
                    g.setColor(Color.GRAY);
                    g.drawString("#" + (i + 1), VelocityRivalsEngine.W / 2 - 330, ry + 33);
                }
                g.setFont(fB.deriveFont(18f));
                g.setColor(Color.WHITE);
                String name = e.name.length() > 12 ? e.name.substring(0, 10) + ".." : e.name;
                g.drawString(name, VelocityRivalsEngine.W / 2 - 260, ry + 33);
                g.setFont(fH.deriveFont(23f));
                g.setColor(new Color(0x00E5FF));
                g.drawString(String.format("%,d", e.score), VelocityRivalsEngine.W / 2 + 80, ry + 33);
                g.setFont(fS.deriveFont(12f));
                g.setColor(Color.GRAY);
                g.drawString(e.mode, VelocityRivalsEngine.W / 2 + 240, ry + 33);
            }
        }
        drawBtn(g, VelocityRivalsEngine.W / 2, VelocityRivalsEngine.H - 80, 160, 50, "BACK", new Color(0x00E5FF), false);
    }

    void drawOver(Graphics2D g) {
        g.setPaint(new GradientPaint(0, 0, new Color(0x150000), 0, VelocityRivalsEngine.H, new Color(0x250808)));
        g.fillRect(0, 0, VelocityRivalsEngine.W, VelocityRivalsEngine.H);
        for (int gw = 8; gw >= 0; gw--) {
            g.setColor(new Color(1f, 0.18f, 0.18f, 0.04f * gw));
            g.setFont(fT.deriveFont(58 + gw * 2));
            FontMetrics fm = g.getFontMetrics();
            g.drawString("GAME OVER", (VelocityRivalsEngine.W - fm.stringWidth("GAME OVER")) / 2 - gw, VelocityRivalsEngine.H / 2 - 110 + gw);
        }
        g.setColor(new Color(0xFF4757));
        g.setFont(fT.deriveFont(58f));
        cs(g, "GAME OVER", VelocityRivalsEngine.W / 2, VelocityRivalsEngine.H / 2 - 110);
        if (!raceWinner.isEmpty()) {
            g.setFont(fB.deriveFont(36f));
            g.setColor(new Color(0xFFD700));
            cs(g, "🏆 WINNER: " + raceWinner + " 🏆", VelocityRivalsEngine.W / 2, VelocityRivalsEngine.H / 2 - 80);
        } else if (multi && !winnerName.isEmpty()) {
            g.setFont(fB.deriveFont(36f));
            g.setColor(new Color(0xFFD700));
            cs(g, "🏆 WINNER: " + winnerName + " 🏆", VelocityRivalsEngine.W / 2, VelocityRivalsEngine.H / 2 - 80);
        }
        if (p1 != null && !(currentGameMode == VelocityRivalsEngine.GameMode.RACE && !multi && !raceFinished)) {
            g.setFont(fH.deriveFont(22f));
            g.setColor(new Color(0x00E5FF));
            cs(g, (player1Name.isEmpty() ? "P1" : player1Name) + " → " + String.format("%,d", p1.score) + " pts (" + p1.coins + " coins)", VelocityRivalsEngine.W / 2, VelocityRivalsEngine.H / 2 - 25);
        }
        if (p2 != null && multi) {
            g.setFont(fH.deriveFont(22f));
            g.setColor(new Color(0xFF4757));
            cs(g, (player2Name.isEmpty() ? "P2" : player2Name) + " → " + String.format("%,d", p2.score) + " pts (" + p2.coins + " coins)", VelocityRivalsEngine.W / 2, VelocityRivalsEngine.H / 2 + 15);
        }
        g.setFont(fS.deriveFont(13f));
        g.setColor(Color.GRAY);
        cs(g, "Time: " + fmt(secs) + "  ·  Difficulty LV" + diff + "  ·  " + thm.label, VelocityRivalsEngine.W / 2, VelocityRivalsEngine.H / 2 + 100);
        drawBtn(g, VelocityRivalsEngine.W / 2 - 100, VelocityRivalsEngine.H / 2 + 150, 140, 50, "RETRY", new Color(0x00E5FF), false);
        drawBtn(g, VelocityRivalsEngine.W / 2 + 60, VelocityRivalsEngine.H / 2 + 150, 140, 50, "MENU", new Color(0xFF8C00), false);
    }

    void drawMenu(Graphics2D g) {
        g.setPaint(new GradientPaint(0, 0, new Color(0x06060F), 0, VelocityRivalsEngine.H, new Color(0x101028)));
        g.fillRect(0, 0, VelocityRivalsEngine.W, VelocityRivalsEngine.H);
        drawStars(g);
        drawGrid(g);
        for (int gw = 9; gw >= 0; gw--) {
            g.setColor(new Color(0f, 0.9f, 1f, 0.033f * gw));
            g.setFont(fT.deriveFont(72 + gw * 2));
            FontMetrics fm = g.getFontMetrics();
            g.drawString("VELOCITY RIVALS", (VelocityRivalsEngine.W - fm.stringWidth("VELOCITY RIVALS")) / 2 - gw, VelocityRivalsEngine.H / 2 - 190 + gw);
        }
        g.setColor(new Color(0x00E5FF));
        g.setFont(fT);
        cs(g, "VELOCITY RIVALS", VelocityRivalsEngine.W / 2, VelocityRivalsEngine.H / 2 - 190);
        g.setColor(new Color(0xFF8C00));
        g.setFont(fB.deriveFont(15f));
        cs(g, "RACING CHAMPIONSHIP", VelocityRivalsEngine.W / 2, VelocityRivalsEngine.H / 2 - 130);
        String[] bt = {"▶ START RACE", "★ LEADERBOARD", "✕ EXIT"};
        Color[] bc = {new Color(0x00E5FF), new Color(0xFFD700), new Color(0xFF4757)};
        int[] by = {VelocityRivalsEngine.H / 2, VelocityRivalsEngine.H / 2 + 70, VelocityRivalsEngine.H / 2 + 140};
        for (int i = 0; i < 3; i++) drawBtn(g, VelocityRivalsEngine.W / 2, by[i], 280, 52, bt[i], bc[i], hov == i);
        g.setFont(fS.deriveFont(11f));
        g.setColor(new Color(255, 255, 255, 60));
        cs(g, "⚠️ ANY COLLISION = INSTANT GAME OVER ⚠️", VelocityRivalsEngine.W / 2, VelocityRivalsEngine.H - 20);
        cs(g, "Single: ARROWS+SPACE+Z  Multi: P1=WASD+SPACE+Z  P2=IJKL+ENTER+M", VelocityRivalsEngine.W / 2, VelocityRivalsEngine.H - 40);
        cs(g, "🔄 SWAP BOOSTER: Purple pickup - Swap positions in Race Mode! 🔄", VelocityRivalsEngine.W / 2, VelocityRivalsEngine.H - 60);
    }

    void drawRaceModeSelect(Graphics2D g) {
        g.setPaint(new GradientPaint(0, 0, new Color(0x06060F), 0, VelocityRivalsEngine.H, new Color(0x101028)));
        g.fillRect(0, 0, VelocityRivalsEngine.W, VelocityRivalsEngine.H);
        drawStars(g);
        g.setColor(new Color(0x00E5FF));
        g.setFont(fT.deriveFont(52f));
        cs(g, "SELECT GAME MODE", VelocityRivalsEngine.W / 2, 120);
        int cardW = 350, cardH = 200;
        int survivalX = VelocityRivalsEngine.W / 2 - cardW - 30, raceX = VelocityRivalsEngine.W / 2 + 30, cardY = VelocityRivalsEngine.H / 2 - cardH / 2;
        g.setColor(new Color(0x00E5FF, true));
        g.fillRoundRect(survivalX, cardY, cardW, cardH, 12, 12);
        g.setColor(new Color(0x00E5FF));
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(survivalX, cardY, cardW, cardH, 12, 12);
        g.setFont(fB.deriveFont(24f));
        cs(g, "🏆 SURVIVAL", survivalX + cardW / 2, cardY + 50);
        g.setFont(fS.deriveFont(12f));
        g.setColor(Color.LIGHT_GRAY);
        String[] survivalDesc = {"Classic arcade mode", "Auto-scrolling road", "Collect coins & power-ups", "ANY collision = GAME OVER", "Survive as long as possible"};
        for (int i = 0; i < survivalDesc.length; i++) cs(g, survivalDesc[i], survivalX + cardW / 2, cardY + 85 + i * 18);
        g.setColor(new Color(0xFF8C00, true));
        g.fillRoundRect(raceX, cardY, cardW, cardH, 12, 12);
        g.setColor(new Color(0xFF8C00));
        g.drawRoundRect(raceX, cardY, cardW, cardH, 12, 12);
        g.setFont(fB.deriveFont(24f));
        cs(g, "🏁 RACE MODE", raceX + cardW / 2, cardY + 50);
        g.setFont(fS.deriveFont(12f));
        g.setColor(Color.LIGHT_GRAY);
        String[] raceDesc = {"Proper racing experience", "Control your speed!", "UP/W = Accelerate", "DOWN/S = Brake/Reverse", "NEW: SWAP BOOSTER Power-up!"};
        for (int i = 0; i < raceDesc.length; i++) cs(g, raceDesc[i], raceX + cardW / 2, cardY + 85 + i * 18);
        backBtn(g);
    }

    void drawMode(Graphics2D g) {
        g.setPaint(new GradientPaint(0, 0, new Color(0x06060F), 0, VelocityRivalsEngine.H, new Color(0x101028)));
        g.fillRect(0, 0, VelocityRivalsEngine.W, VelocityRivalsEngine.H);
        drawStars(g);
        g.setColor(new Color(0x00E5FF));
        g.setFont(fT.deriveFont(52f));
        cs(g, "SELECT PLAYERS", VelocityRivalsEngine.W / 2, 120);
        int cardW = 280, cardH = 160, singleX = VelocityRivalsEngine.W / 2 - cardW - 20, multiX = VelocityRivalsEngine.W / 2 + 20, cardY = VelocityRivalsEngine.H / 2 - cardH / 2;
        String modeName = (currentGameMode == VelocityRivalsEngine.GameMode.RACE) ? "RACE MODE" : "SURVIVAL MODE";
        g.setFont(fS.deriveFont(14f));
        g.setColor(new Color(0xFFD700));
        cs(g, "⚡ " + modeName + " ⚡", VelocityRivalsEngine.W / 2, 200);
        g.setColor(new Color(0x00E5FF, true));
        g.fillRoundRect(singleX, cardY, cardW, cardH, 12, 12);
        g.setColor(new Color(0x00E5FF));
        g.drawRoundRect(singleX, cardY, cardW, cardH, 12, 12);
        g.setFont(fB.deriveFont(18f));
        g.setColor(new Color(0x00E5FF));
        cs(g, "SINGLE PLAYER", singleX + cardW / 2, cardY + 40);
        g.setFont(fS.deriveFont(11f));
        g.setColor(Color.LIGHT_GRAY);
        String[] singleDesc = (currentGameMode == VelocityRivalsEngine.GameMode.RACE) ? new String[]{"Race to the finish!", "UP/DOWN = Speed Control", "Reach 5000m in 90 seconds", "ANY collision = GAME OVER"} : new String[]{"Arrow Keys to Drive", "SPACE = Nitro", "Z = Power-up", "ANY collision = GAME OVER"};
        for (int i = 0; i < singleDesc.length; i++) cs(g, singleDesc[i], singleX + cardW / 2, cardY + 70 + i * 22);
        g.setColor(new Color(0xFF4757, true));
        g.fillRoundRect(multiX, cardY, cardW, cardH, 12, 12);
        g.setColor(new Color(0xFF4757));
        g.drawRoundRect(multiX, cardY, cardW, cardH, 12, 12);
        g.setFont(fB.deriveFont(18f));
        g.setColor(new Color(0xFF4757));
        cs(g, "MULTIPLAYER", multiX + cardW / 2, cardY + 40);
        g.setFont(fS.deriveFont(11f));
        g.setColor(Color.LIGHT_GRAY);
        String[] multiDesc = (currentGameMode == VelocityRivalsEngine.GameMode.RACE) ? new String[]{"P1: WASD + SPACE + Z", "P2: IJKL + ENTER + M", "First to 5000m WINS!", "NEW: SWAP BOOSTER!", "ANY collision = GAME OVER"} : new String[]{"P1: WASD + SPACE + Z", "P2: IJKL + ENTER + M", "Last one standing wins!", "ANY collision = GAME OVER"};
        for (int i = 0; i < multiDesc.length; i++) cs(g, multiDesc[i], multiX + cardW / 2, cardY + 70 + i * 22);
        backBtn(g);
    }

    void drawTheme(Graphics2D g) {
        g.setPaint(new GradientPaint(0, 0, new Color(0x06060F), 0, VelocityRivalsEngine.H, new Color(0x101028)));
        g.fillRect(0, 0, VelocityRivalsEngine.W, VelocityRivalsEngine.H);
        drawStars(g);
        g.setColor(new Color(0x00E5FF));
        g.setFont(fT.deriveFont(52f));
        cs(g, "CHOOSE TRACK", VelocityRivalsEngine.W / 2, 120);
        VelocityRivalsEngine.Theme[] ts = VelocityRivalsEngine.Theme.values();
        Color[] ac = {new Color(0xF5A623), new Color(0xA855F7), new Color(0x00E5FF)};
        String[] ds = {"Sandy desert roads\nHigh Speed · Low Traction", "Neon city at night\nMedium Speed · Wet Roads", "Speedway highway\nMAX Speed · Smooth Tarmac"};
        for (int i = 0; i < ts.length; i++) {
            int x = VelocityRivalsEngine.W / 2 - 430 + i * 305;
            g.setPaint(new GradientPaint(x, VelocityRivalsEngine.H / 2 - 120, ts[i].bgTop, x, VelocityRivalsEngine.H / 2 + 120, ts[i].bgBot));
            g.fillRoundRect(x, VelocityRivalsEngine.H / 2 - 120, 245, 240, 14, 14);
            g.setColor(ac[i]);
            g.setStroke(new BasicStroke(2.5f));
            g.drawRoundRect(x, VelocityRivalsEngine.H / 2 - 120, 245, 240, 14, 14);
            g.setFont(fB.deriveFont(14f));
            g.setColor(ac[i]);
            cs(g, ts[i].label, x + 122, VelocityRivalsEngine.H / 2 + 10);
            g.setFont(fS.deriveFont(11f));
            g.setColor(Color.LIGHT_GRAY);
            String[] ls = ds[i].split("\n");
            for (int j = 0; j < ls.length; j++) cs(g, ls[j], x + 122, VelocityRivalsEngine.H / 2 + 30 + j * 18);
        }
        backBtn(g);
    }

    void drawGame(Graphics2D g) {
        if (screenShake > 0) {
            int shakeX = rng.nextInt(screenShake) - screenShake / 2;
            int shakeY = rng.nextInt(screenShake) - screenShake / 2;
            g.translate(shakeX, shakeY);
        }
        if (!multi) drawHalf(g, 0, VelocityRivalsEngine.W, p1, p1map, false);
        else {
            Shape cl = g.getClip();
            g.setClip(0, 0, VelocityRivalsEngine.W / 2, VelocityRivalsEngine.H);
            drawHalf(g, 0, VelocityRivalsEngine.W / 2, p1, p1map, false);
            g.setClip(VelocityRivalsEngine.W / 2, 0, VelocityRivalsEngine.W / 2, VelocityRivalsEngine.H);
            drawHalf(g, VelocityRivalsEngine.W / 2, VelocityRivalsEngine.W / 2, p2, p2map, true);
            g.setClip(cl);
            g.setColor(Color.BLACK);
            g.fillRect(VelocityRivalsEngine.W / 2 - 2, 0, 4, VelocityRivalsEngine.H);
            g.setColor(new Color(0x00E5FF));
            g.setStroke(new BasicStroke(2));
            g.drawLine(VelocityRivalsEngine.W / 2, 0, VelocityRivalsEngine.W / 2, VelocityRivalsEngine.H);
            g.setStroke(new BasicStroke(1));
        }
        drawSparks(g);
        drawHUD(g);
        drawRaceProgress(g);
        if (!started) drawCountdown(g);
        if (damageFlash > 5 && damageFlash % 3 == 0) {
            g.setColor(new Color(255, 0, 0, 40));
            g.fillRect(0, 0, VelocityRivalsEngine.W, VelocityRivalsEngine.H);
        }
        if (screenShake > 0) g.translate(0, 0);
    }

    class GamePanel extends JPanel {
        GamePanel() {
            setPreferredSize(new Dimension(VelocityRivalsEngine.W, VelocityRivalsEngine.H));
            setBackground(Color.BLACK);
            setFocusable(true);
            addKeyListener(new KeyAdapter() {
                public void keyPressed(KeyEvent e) { int k = e.getKeyCode(); if (k < keys.length) keys[k] = true; onKey(k); }
                public void keyReleased(KeyEvent e) { int k = e.getKeyCode(); if (k < keys.length) keys[k] = false; }
            });
            addMouseListener(new MouseAdapter() { public void mouseClicked(MouseEvent e) { click(e.getX(), e.getY()); } });
            addMouseMotionListener(new MouseMotionAdapter() { public void mouseMoved(MouseEvent e) { hover(e.getX(), e.getY()); } });
        }
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            switch (scr) {
                case MAIN_MENU: drawMenu(g2); break;
                case RACE_MODE_SELECT: drawRaceModeSelect(g2); break;
                case MODE_SELECT: drawMode(g2); break;
                case THEME_SELECT: drawTheme(g2); break;
                case LEADERBOARD: drawBoard(g2); break;
                case GAME_OVER: drawOver(g2); break;
                case GAME_SINGLE: case GAME_MULTI: drawGame(g2); break;
                case PAUSE: drawGame(g2); drawPause(g2); break;
                default: break;
            }
        }
    }

    void onKey(int k) {
        boolean ig = scr == VelocityRivalsEngine.Screen.GAME_SINGLE || scr == VelocityRivalsEngine.Screen.GAME_MULTI;
        if (ig && started) {
            if (k == KeyEvent.VK_SPACE) nitro(p1, p1map);
            if (multi && k == KeyEvent.VK_ENTER && p2 != null) nitro(p2, p2map);
            if (k == KeyEvent.VK_Z) usePU(p1, p1map);
            if (multi && k == KeyEvent.VK_M && p2 != null) usePU(p2, p2map);
        }
        if (k == KeyEvent.VK_ESCAPE) {
            if (scr == VelocityRivalsEngine.Screen.PAUSE) scr = multi ? VelocityRivalsEngine.Screen.GAME_MULTI : VelocityRivalsEngine.Screen.GAME_SINGLE;
            else if (ig) scr = VelocityRivalsEngine.Screen.PAUSE;
        }
        if (scr == VelocityRivalsEngine.Screen.PAUSE && k == KeyEvent.VK_Q) scr = VelocityRivalsEngine.Screen.MAIN_MENU;
    }

    void click(int mx, int my) {
        switch (scr) {
            case MAIN_MENU:
                if (btn(mx, my, VelocityRivalsEngine.W / 2 - 140, VelocityRivalsEngine.H / 2, 280, 52)) scr = VelocityRivalsEngine.Screen.RACE_MODE_SELECT;
                if (btn(mx, my, VelocityRivalsEngine.W / 2 - 140, VelocityRivalsEngine.H / 2 + 70, 280, 52)) scr = VelocityRivalsEngine.Screen.LEADERBOARD;
                if (btn(mx, my, VelocityRivalsEngine.W / 2 - 140, VelocityRivalsEngine.H / 2 + 140, 280, 52)) System.exit(0);
                break;
            case RACE_MODE_SELECT:
                int cardW = 350, cardH = 200, survivalX = VelocityRivalsEngine.W / 2 - cardW - 30, raceX = VelocityRivalsEngine.W / 2 + 30, cardY = VelocityRivalsEngine.H / 2 - cardH / 2;
                if (mx >= survivalX && mx <= survivalX + cardW && my >= cardY && my <= cardY + cardH) {
                    currentGameMode = VelocityRivalsEngine.GameMode.SURVIVAL;
                    scr = VelocityRivalsEngine.Screen.MODE_SELECT;
                } else if (mx >= raceX && mx <= raceX + cardW && my >= cardY && my <= cardY + cardH) {
                    currentGameMode = VelocityRivalsEngine.GameMode.RACE;
                    scr = VelocityRivalsEngine.Screen.MODE_SELECT;
                } else if (btn(mx, my, 28, VelocityRivalsEngine.H - 72, 120, 46)) scr = VelocityRivalsEngine.Screen.MAIN_MENU;
                break;
            case MODE_SELECT:
                int cardW2 = 280, cardH2 = 160, singleX = VelocityRivalsEngine.W / 2 - cardW2 - 20, multiX = VelocityRivalsEngine.W / 2 + 20, cardY2 = VelocityRivalsEngine.H / 2 - cardH2 / 2;
                if (mx >= singleX && mx <= singleX + cardW2 && my >= cardY2 && my <= cardY2 + cardH2) {
                    multi = false;
                    scr = VelocityRivalsEngine.Screen.THEME_SELECT;
                } else if (mx >= multiX && mx <= multiX + cardW2 && my >= cardY2 && my <= cardY2 + cardH2) {
                    multi = true;
                    scr = VelocityRivalsEngine.Screen.THEME_SELECT;
                } else if (btn(mx, my, 28, VelocityRivalsEngine.H - 72, 120, 46)) scr = VelocityRivalsEngine.Screen.RACE_MODE_SELECT;
                break;
            case THEME_SELECT:
                VelocityRivalsEngine.Theme[] ts = VelocityRivalsEngine.Theme.values();
                for (int i = 0; i < ts.length; i++) {
                    int x = VelocityRivalsEngine.W / 2 - 430 + i * 305;
                    if (btn(mx, my, x, VelocityRivalsEngine.H / 2 - 110, 240, 230)) {
                        thm = ts[i];
                        askForPlayerNames();
                        return;
                    }
                }
                if (btn(mx, my, 28, VelocityRivalsEngine.H - 72, 120, 46)) scr = VelocityRivalsEngine.Screen.MODE_SELECT;
                break;
            case PAUSE:
                if (btn(mx, my, VelocityRivalsEngine.W / 2 - 100, VelocityRivalsEngine.H / 2 + 20, 200, 50)) scr = multi ? VelocityRivalsEngine.Screen.GAME_MULTI : VelocityRivalsEngine.Screen.GAME_SINGLE;
                if (btn(mx, my, VelocityRivalsEngine.W / 2 - 100, VelocityRivalsEngine.H / 2 + 90, 200, 50)) scr = VelocityRivalsEngine.Screen.MAIN_MENU;
                break;
            case GAME_OVER:
                if (btn(mx, my, VelocityRivalsEngine.W / 2 - 160, VelocityRivalsEngine.H / 2 + 150, 140, 50)) startGame();
                if (btn(mx, my, VelocityRivalsEngine.W / 2 + 20, VelocityRivalsEngine.H / 2 + 150, 140, 50)) scr = VelocityRivalsEngine.Screen.MAIN_MENU;
                break;
            case LEADERBOARD:
                if (btn(mx, my, VelocityRivalsEngine.W / 2 - 80, VelocityRivalsEngine.H - 80, 160, 50)) scr = VelocityRivalsEngine.Screen.MAIN_MENU;
                break;
            default: break;
        }
    }

    void hover(int mx, int my) {
        hov = -1;
        if (scr == VelocityRivalsEngine.Screen.MAIN_MENU) {
            int[] ys = {VelocityRivalsEngine.H / 2, VelocityRivalsEngine.H / 2 + 70, VelocityRivalsEngine.H / 2 + 140};
            for (int i = 0; i < 3; i++) if (btn(mx, my, VelocityRivalsEngine.W / 2 - 140, ys[i], 280, 52)) { hov = i; break; }
        }
    }

    boolean btn(int mx, int my, int x, int y, int w, int h) { return mx >= x && mx <= x + w && my >= y && my <= y + h; }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(VelocityRivalsGUI::new);
    }
}