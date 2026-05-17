import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.Random;

public class VelocityRivals extends JFrame {
    static int W = 1200, H = 700;
    static final int ROAD_W = 400;
    static int ROAD_X;
    static final int ROAD_TOP = (int)(H * 0.4);
    static int[] LANE_X;

    // ==================== CUSTOM DATA STRUCTURES ====================
    @SuppressWarnings("unchecked")
    static class CustomQueue<T> {
        Object[] buf; int head, tail, size, cap;
        CustomQueue(int c) { cap = c; buf = new Object[c]; head = tail = size = 0; }
        void enqueue(T t) {
            if (size == cap) {
                Object[] nb = new Object[cap * 2];
                for (int i = 0; i < size; i++) nb[i] = buf[(head + i) % cap];
                buf = nb; head = 0; tail = size; cap *= 2;
            }
            buf[tail] = t; tail = (tail + 1) % cap; size++;
        }
        T dequeue() { if (size == 0) return null; T t = (T) buf[head]; buf[head] = null; head = (head + 1) % cap; size--; return t; }
        boolean isEmpty() { return size == 0; }
    }

    @SuppressWarnings("unchecked")
    static class CustomStack<T> {
        Object[] data; int top;
        CustomStack(int c) { data = new Object[c]; top = -1; }
        void push(T t) { if (top + 1 == data.length) { Object[] nd = new Object[data.length * 2]; System.arraycopy(data, 0, nd, 0, data.length); data = nd; } data[++top] = t; }
        T pop() { return top < 0 ? null : (T) data[top--]; }
        T get(int i) { return top - i < 0 ? null : (T) data[top - i]; }
        boolean isEmpty() { return top < 0; }
        int size() { return top + 1; }
        void clear() { data = new Object[16]; top = -1; }
    }

    @SuppressWarnings("unchecked")
    static class CustomHashMap<K, V> {
        static class Entry<K, V> { K k; V v; Entry<K, V> next; Entry(K k, V v) { this.k = k; this.v = v; } }
        Entry<K, V>[] buckets; int size;
        CustomHashMap() { buckets = new Entry[16]; size = 0; }
        int hash(K k) { return Math.abs(k.hashCode() % buckets.length); }
        void put(K k, V v) {
            int i = hash(k);
            for (Entry<K, V> e = buckets[i]; e != null; e = e.next) if (k.equals(e.k)) { e.v = v; return; }
            Entry<K, V> e = new Entry<>(k, v); e.next = buckets[i]; buckets[i] = e; size++;
        }
        V get(K k) { for (Entry<K, V> e = buckets[hash(k)]; e != null; e = e.next) if (k.equals(e.k)) return e.v; return null; }
        boolean containsKey(K k) { return get(k) != null; }
        void remove(K k) {
            int i = hash(k); Entry<K, V> e = buckets[i], p = null;
            while (e != null) { if (k.equals(e.k)) { if (p == null) buckets[i] = e.next; else p.next = e.next; size--; return; } p = e; e = e.next; }
        }
        void clear() { buckets = new Entry[16]; size = 0; }
        void tickAndPrune() {
            for (int i = 0; i < buckets.length; i++) {
                Entry<K, V> e = buckets[i], p = null;
                while (e != null) {
                    if (e.v instanceof Integer) {
                        int v = (Integer) e.v - 1;
                        if (v <= 0) { if (p == null) buckets[i] = e.next; else p.next = e.next; size--; e = (p == null) ? buckets[i] : e.next; }
                        else { e.v = (V) (Integer) v; p = e; e = e.next; }
                    } else { p = e; e = e.next; }
                }
            }
        }
    }

    static class CustomPriorityQueue<T> {
        Object[] heap; int size; java.util.Comparator<T> cmp;
        CustomPriorityQueue(int cap, java.util.Comparator<T> cmp) { heap = new Object[cap + 1]; this.cmp = cmp; size = 0; }
        void offer(T t) { if (size + 1 == heap.length) { Object[] n = new Object[heap.length * 2]; System.arraycopy(heap, 0, n, 0, heap.length); heap = n; } heap[++size] = t; swim(size); }
        T poll() { if (size == 0) return null; T r = (T) heap[1]; heap[1] = heap[size]; heap[size--] = null; sink(1); return r; }
        void swim(int k) { while (k > 1 && cmp.compare((T) heap[k / 2], (T) heap[k]) < 0) { Object t = heap[k]; heap[k] = heap[k / 2]; heap[k / 2] = t; k /= 2; } }
        void sink(int k) { while (2 * k <= size) { int j = 2 * k; if (j < size && cmp.compare((T) heap[j], (T) heap[j + 1]) < 0) j++; if (cmp.compare((T) heap[k], (T) heap[j]) >= 0) break; Object t = heap[k]; heap[k] = heap[j]; heap[j] = t; k = j; } }
        int size() { return size; }
        @SuppressWarnings("unchecked")
        T[] toSortedArray() {
            Object[] cp = new Object[size];
            System.arraycopy(heap, 1, cp, 0, size);
            java.util.Arrays.sort(cp, (a, b) -> cmp.compare((T) a, (T) b));
            return (T[]) cp;
        }
    }

    // ==================== ENUMS ====================
    enum Screen { MAIN_MENU, RACE_MODE_SELECT, MODE_SELECT, THEME_SELECT, GAME_SINGLE, GAME_MULTI, PAUSE, LEADERBOARD, GAME_OVER }
    enum GameMode { SURVIVAL, RACE }
    enum Theme {
        DESERT("DESERT HEAT", new Color(0xC8986A), new Color(0xE5C87A), new Color(0xB8651A), new Color(0xD4782A), 1.10f, 0.80f),
        NIGHT("NIGHT CITY", new Color(0x303048), new Color(0x484870), new Color(0x080814), new Color(0x141428), 1.00f, 0.92f),
        HIGHWAY("NEON HIGHWAY", new Color(0x3C3C3C), new Color(0x606060), new Color(0x0C0C1E), new Color(0x181830), 1.25f, 1.00f);
        final String label; final Color road, lane, bgTop, bgBot; final float spd, trc;
        Theme(String l, Color r, Color ln, Color bt, Color bb, float sp, float tr) { label = l; road = r; lane = ln; bgTop = bt; bgBot = bb; spd = sp; trc = tr; }
    }
    enum PU { BOOST, SHIELD, MISSILE, OIL, SWAP }

    // ==================== ENTITIES ====================
    static class ScoreEntry { String name; int score; String mode; ScoreEntry(String n, int s, String m) { name = n; score = s; mode = m; } }
    static class Car {
        double x, y, vx, vy;
        int score = 0, nitro = 100, health = 100, boostT = 0, shieldT = 0, oilT = 0, invincibleT = 0, coins = 0, dmgFlash = 0;
        boolean alive = true, boosted = false, shielded = false;
        Color body, accent; boolean isP1;
        double distance = 0; int lastCheckpoint = 0;
        Car(double x, double y, Color b, Color a, boolean p1) { this.x = x; this.y = y; body = b; accent = a; isP1 = p1; }
        Rectangle2D.Double box() { return new Rectangle2D.Double(x - 14, y - 26, 28, 52); }
    }
    static class AI { double x, y, spd; int lane, tgtLane, cd; Color col; boolean alive = true, agg; AI(double x, double y, double s, int l, Color c, boolean a) { this.x = x; this.y = y; spd = s; lane = l; tgtLane = l; col = c; agg = a; } Rectangle2D.Double box() { return new Rectangle2D.Double(x - 13, y - 24, 26, 48); } }
    static class Pickup { double x, y; PU type; boolean done = false; int tick = 0; Pickup(double x, double y, PU t) { this.x = x; this.y = y; type = t; } Rectangle2D.Double box() { return new Rectangle2D.Double(x - 13, y - 13, 26, 26); } }
    static class Coin { double x, y; boolean done = false; int tick = 0; Coin(double x, double y) { this.x = x; this.y = y; } Rectangle2D.Double box() { return new Rectangle2D.Double(x - 9, y - 9, 18, 18); } }
    static class Crate { double x, y; boolean reinforced; boolean done = false; int tick = 0; Crate(double x, double y, boolean r) { this.x = x; this.y = y; reinforced = r; } Rectangle2D.Double box() { return new Rectangle2D.Double(x - 16, y - 16, 32, 32); } }
    static class Spark { double x, y, vx, vy; int life, maxL; Color col; float sz; Spark(double x, double y, double vx, double vy, Color c, int l, float s) { this.x = x; this.y = y; this.vx = vx; this.vy = vy; col = c; life = l; maxL = l; sz = s; } }
    static class OilPatch { double x, y; int life = 360; boolean used = false; OilPatch(double x, double y) { this.x = x; this.y = y; } Rectangle2D.Double box() { return new Rectangle2D.Double(x - 22, y - 10, 44, 20); } }
    static class MissileProjectile { double x, y, vx, vy; Car owner; int life = 60; MissileProjectile(double x, double y, double vx, double vy, Car owner) { this.x = x; this.y = y; this.vx = vx; this.vy = vy; this.owner = owner; } Rectangle2D.Double box() { return new Rectangle2D.Double(x - 6, y - 6, 12, 12); } }

    // ==================== DSA INSTANCES ====================
    final ArrayList<AI> cars = new ArrayList<>();
    final ArrayList<Pickup> pickups = new ArrayList<>();
    final ArrayList<Coin> coins = new ArrayList<>();
    final ArrayList<Spark> sparks = new ArrayList<>();
    final ArrayList<OilPatch> oils = new ArrayList<>();
    final ArrayList<Crate> crates = new ArrayList<>();
    final ArrayList<MissileProjectile> missiles = new ArrayList<>();
    final CustomQueue<AI> spawnQ = new CustomQueue<>(32);
    final CustomStack<String> evtStk = new CustomStack<>(20);
    final CustomHashMap<PU, Integer> p1map = new CustomHashMap<>();
    final CustomHashMap<PU, Integer> p2map = new CustomHashMap<>();
    final CustomPriorityQueue<ScoreEntry> board = new CustomPriorityQueue<>(32, (a, b) -> b.score - a.score);

    // ==================== STATE ====================
    Screen scr = Screen.MAIN_MENU;
    Theme thm = Theme.HIGHWAY;
    boolean multi = false;
    GameMode currentGameMode = GameMode.SURVIVAL;
    Car p1, p2;
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

    public VelocityRivals() {
        super("Velocity Rivals — Racing Championship");
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        W = Math.min(1200, screenSize.width - 50);
        H = Math.min(700, screenSize.height - 80);
        ROAD_X = W / 2 - ROAD_W / 2;
        LANE_X = new int[]{ROAD_X + ROAD_W / 6, ROAD_X + ROAD_W / 2, ROAD_X + 5 * ROAD_W / 6};
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
        p1map.clear(); p2map.clear(); evtStk.clear(); while (!spawnQ.isEmpty()) spawnQ.dequeue();
        roadOff = 0; frame = 0; diff = 1; secs = 0; secTick = 0; spawnCD = 60; pickCD = 140; coinCD = 80; crateCD = 90; p2CD = 0;
        started = false; cdNum = 3; cdTick = 0; raceFinished = false; raceWinner = ""; winnerName = ""; damageFlash = 0; screenShake = 0;
        collisionsEnabled = false;
        collisionDelay = 60;

        if (!multi) p1 = new Car(LANE_X[1], H - 160, new Color(0x00E5FF), new Color(0x0055CC), true);
        else { p1 = new Car(W / 4, H - 160, new Color(0x00E5FF), new Color(0x0055CC), true); p2 = new Car(3 * W / 4, H - 160, new Color(0xFF4757), new Color(0xCC2200), false); }

        if (currentGameMode == GameMode.RACE) { if (p1 != null) { p1.distance = 0; p1.lastCheckpoint = 0; } if (p2 != null) { p2.distance = 0; p2.lastCheckpoint = 0; } }

        for (int i = 0; i < 6; i++) scheduleAI(false);
        if (multi) for (int i = 0; i < 6; i++) scheduleAI(true);

        scr = multi ? Screen.GAME_MULTI : Screen.GAME_SINGLE;
        evt((currentGameMode == GameMode.RACE ? "🏁 RACE MODE - " : "SURVIVAL MODE - ") + thm.label);
        evt("⚠️ ANY COLLISION = INSTANT GAME OVER ⚠️");
        if (currentGameMode == GameMode.RACE) evt("🔄 SWAP BOOSTER: Swap positions with opponent! 🔄");
    }

    void scheduleAI(boolean p2side) {
        int lane = rng.nextInt(3);
        double spd = 1.4 + rng.nextDouble() * 2.0 * (0.5 + diff * 0.25);
        Color[] cols = {new Color(0xFF6B6B), new Color(0xF7DC6F), new Color(0x82E0AA), new Color(0xF0B27A), new Color(0xAED6F1), new Color(0xD7BDE2)};
        Color col = cols[rng.nextInt(cols.length)];
        boolean agg = rng.nextFloat() < 0.12f * diff;
        double yOffset = -80 - (rng.nextInt(100) * (p2side ? 1 : -1));
        spawnQ.enqueue(new AI(getLX(lane, p2side), yOffset, spd, lane, col, agg));
    }

    double getLX(int lane, boolean p2side) {
        if (!multi) return LANE_X[lane];
        int laneWidth = ROAD_W / 3;
        int startX = p2side ? (W / 2 + (W / 2 - ROAD_W) / 2) : ((W / 2 - ROAD_W) / 2);
        return startX + laneWidth / 2 + lane * laneWidth;
    }

    int getRoadLeft(boolean p2side) {
        if (!multi) return ROAD_X;
        return p2side ? (W / 2 + (W / 2 - ROAD_W) / 2) : ((W / 2 - ROAD_W) / 2);
    }

    int getRoadRight(boolean p2side) { return getRoadLeft(p2side) + ROAD_W; }

    double[] playerBounds(Car p) {
        boolean p2s = (p2 != null && !p.isP1);
        int rl = getRoadLeft(p2s) + 18, rr = getRoadRight(p2s) - 18;
        return new double[]{rl, rr, 20, H - 60};
    }

    void evt(String s) { evtStk.push(s); }

    // SWAP BOOSTER FUNCTION - Swaps positions between players in race mode
    void swapPositions() {
        if (currentGameMode != GameMode.RACE || p1 == null || p2 == null) return;
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
            sparks.add(new Spark(p1.x, p1.y, Math.cos(a) * s, Math.sin(a) * s, new Color(0x00E5FF), 30, 5f));
            sparks.add(new Spark(p2.x, p2.y, Math.cos(a + Math.PI) * s, Math.sin(a + Math.PI) * s, new Color(0xFF4757), 30, 5f));
        }

        screenShake = 20;
        evt("🔄 SWAP BOOSTER! " + player1Name + " ↔ " + player2Name + " swapped positions! 🔄");
    }

    void checkAllCollisions() {
        if (!collisionsEnabled) return;
        if (!started) return;

        if (p1 != null && p1.alive) {
            for (AI ai : cars) {
                if (ai.alive && p1.box().intersects(ai.box())) {
                    if (ai.y > 50 && ai.y < H - 50) {
                        gameOverDueToCollision(player1Name.isEmpty() ? "P1" : player1Name, "an AI car");
                        return;
                    }
                }
            }
        }

        if (multi && p2 != null && p2.alive) {
            for (AI ai : cars) {
                if (ai.alive && p2.box().intersects(ai.box())) {
                    if (ai.y > 50 && ai.y < H - 50) {
                        gameOverDueToCollision(player2Name.isEmpty() ? "P2" : player2Name, "an AI car");
                        return;
                    }
                }
            }
        }

        if (multi && p1 != null && p2 != null && p1.alive && p2.alive && p1.box().intersects(p2.box())) {
            if (p1.y > 50 && p1.y < H - 50 && p2.y > 50 && p2.y < H - 50) {
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
            for (Crate c : crates) {
                if (!c.done && p1.box().intersects(c.box()) && c.y > 50) {
                    int dmg = currentGameMode == GameMode.RACE ? 20 : 30;
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
            for (Crate c : crates) {
                if (!c.done && p2.box().intersects(c.box()) && c.y > 50) {
                    int dmg = currentGameMode == GameMode.RACE ? 20 : 30;
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
        if (scr != Screen.GAME_SINGLE && scr != Screen.GAME_MULTI) return;
        evt("💥💥💥 GAME OVER! " + who + " collided with " + with + "! 💥💥💥");
        if (p1 != null && p1.alive) explode(p1.x, p1.y);
        if (p2 != null && p2.alive) explode(p2.x, p2.y);
        for (AI ai : cars) if (ai.alive && ai.y > 0 && ai.y < H) explode(ai.x, ai.y);
        screenShake = 30;
        damageFlash = 30;
        if (p1 != null) p1.alive = false;
        if (p2 != null) p2.alive = false;
        for (AI ai : cars) ai.alive = false;
        endGame();
    }

    // FIX: Implement missing endGame() method
    void endGame() {
        if (scr == Screen.GAME_OVER) return;
        if (p1 != null && !(currentGameMode == GameMode.RACE && !multi && !raceFinished)) {
            board.offer(new ScoreEntry(player1Name.isEmpty() ? "RACER" : player1Name, p1.score, currentGameMode.name()));
        }
        if (p2 != null && multi) {
            board.offer(new ScoreEntry(player2Name.isEmpty() ? "RACER2" : player2Name, p2.score, currentGameMode.name()));
        }
        scr = Screen.GAME_OVER;
    }

    void update() {
        frame++;
        boolean inGame = scr == Screen.GAME_SINGLE || scr == Screen.GAME_MULTI;
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

        // TIME'S UP HANDLING with winner determination
        if (currentGameMode == GameMode.RACE && !raceFinished && started && secs >= raceTimeLimit) {
            raceFinished = true;
            evt("⏰ TIME'S UP!");
            if (multi && p1 != null && p2 != null) {
                if (p1.distance > p2.distance) raceWinner = player1Name;
                else if (p2.distance > p1.distance) raceWinner = player2Name;
                else raceWinner = "TIE";
            } else if (p1 != null) {
                // Single player race – no winner if time runs out
                raceWinner = "";
            }
            endGame();
            return;
        }

        double scroll = 5.0 * thm.spd + diff * 0.25;
        if (p1 != null && p1.boosted) scroll *= 1.55;

        if (currentGameMode == GameMode.RACE && p1 != null && p1.alive) {
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

        if (currentGameMode == GameMode.RACE && p2 != null && p2.alive && multi) {
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
            Integer boost = p1map.get(PU.BOOST);
            Integer shield = p1map.get(PU.SHIELD);
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
            Integer boost = p2map.get(PU.BOOST);
            Integer shield = p2map.get(PU.SHIELD);
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
            if (m.life <= 0 || m.y < -100 || m.y > H + 100) return true;
            Car target = (m.owner == p1 && p2 != null) ? p2 : (m.owner == p2 && p1 != null) ? p1 : null;
            if (target != null && target.alive && m.box().intersects(target.box()) && collisionsEnabled) {
                if (target.shielded) { evt("🛡️ SHIELD BLOCKED MISSILE!"); return true; }
                gameOverDueToCollision(target.isP1 ? player1Name : player2Name, "a MISSILE"); return true;
            }
            for (AI a : cars) {
                if (a.alive && m.box().intersects(a.box()) && collisionsEnabled) {
                    a.alive = false; explode(a.x, a.y); m.owner.score += 150; evt("🎯 MISSILE DESTROYED AI! +150"); return true;
                }
            }
            return false;
        });

        oils.removeIf(o -> { o.life--; return o.life <= 0; });
        crates.removeIf(c -> c.done);
        sparks.removeIf(s -> s.life <= 0 || s.y > H + 200);
        for (Spark s : sparks) { s.x += s.vx; s.y += s.vy; s.vy += 0.10; s.life--; }

        if (p1 != null && p1.alive) p1.score += diff;
        if (p2 != null && p2.alive) p2.score += diff;

        checkAllCollisions();
    }

    void input(Car p, boolean isP2) {
        if (p == null || !p.alive || !started) return;
        double ac = 3.2 * thm.trc, mx = 8.0;
        if (currentGameMode == GameMode.RACE) {
            if (!multi) { if (keys[KeyEvent.VK_LEFT]) p.vx = Math.max(p.vx - ac, -mx); if (keys[KeyEvent.VK_RIGHT]) p.vx = Math.min(p.vx + ac, mx); p.vy *= 0.9; }
            else if (!isP2) { if (keys[KeyEvent.VK_A]) p.vx = Math.max(p.vx - ac, -mx); if (keys[KeyEvent.VK_D]) p.vx = Math.min(p.vx + ac, mx); p.vy *= 0.9; }
            else { if (keys[KeyEvent.VK_J]) p.vx = Math.max(p.vx - ac, -mx); if (keys[KeyEvent.VK_L]) p.vx = Math.min(p.vx + ac, mx); p.vy *= 0.9; }
        } else {
            if (!multi) { if (keys[KeyEvent.VK_LEFT]) p.vx = Math.max(p.vx - ac, -mx); if (keys[KeyEvent.VK_RIGHT]) p.vx = Math.min(p.vx + ac, mx); if (keys[KeyEvent.VK_UP]) p.vy = Math.max(p.vy - ac / 1.4, -mx); if (keys[KeyEvent.VK_DOWN]) p.vy = Math.min(p.vy + ac / 1.4, mx); }
            else if (!isP2) { if (keys[KeyEvent.VK_A]) p.vx = Math.max(p.vx - ac, -mx); if (keys[KeyEvent.VK_D]) p.vx = Math.min(p.vx + ac, mx); if (keys[KeyEvent.VK_W]) p.vy = Math.max(p.vy - ac / 1.4, -mx); if (keys[KeyEvent.VK_S]) p.vy = Math.min(p.vy + ac / 1.4, mx); }
            else { if (keys[KeyEvent.VK_J]) p.vx = Math.max(p.vx - ac, -mx); if (keys[KeyEvent.VK_L]) p.vx = Math.min(p.vx + ac, mx); if (keys[KeyEvent.VK_I]) p.vy = Math.max(p.vy - ac / 1.4, -mx); if (keys[KeyEvent.VK_K]) p.vy = Math.min(p.vy + ac / 1.4, mx); }
        }
    }

    void physics(Car p) { if (p == null || !p.alive) return; double trc = thm.trc; if (p.oilT > 0) { trc *= 0.22; p.oilT--; } p.vx *= 0.82 * trc; p.vy *= 0.86; p.x += p.vx; p.y += p.vy; double[] b = playerBounds(p); if (p.x < b[0]) { p.x = b[0]; p.vx = 0; } if (p.x > b[1]) { p.x = b[1]; p.vx = 0; } if (p.y < b[2]) { p.y = b[2]; p.vy = 7; } if (p.y > b[3]) { p.y = b[3]; p.vy = 0; } }

    void updateAI(double scroll) {
        cars.removeIf(ai -> {
            if (!ai.alive) return true;
            ai.y += scroll - ai.spd * 0.65;
            if (ai.y > H + 100 || ai.y < -200) return true;
            for (AI other : cars) {
                if (other != ai && other.alive && Math.abs(ai.y - other.y) < 60 && Math.abs(ai.x - other.x) < 40) {
                    if (ai.x < other.x) ai.x -= 2;
                    else ai.x += 2;
                }
            }
            if (--ai.cd <= 0 && ai.agg) {
                int best = ai.lane;
                double bestSc = Double.NEGATIVE_INFINITY;
                for (int l = 0; l < 3; l++) {
                    boolean p2s = (p2 != null && ai.x > W / 2);
                    double lx = getLX(l, p2s);
                    double d1 = (p1 != null && p1.alive) ? Math.abs(p1.x - lx) : 9999;
                    double d2 = (p2 != null && p2.alive) ? Math.abs(p2.x - lx) : 9999;
                    double sc = ai.agg ? -(Math.min(d1, d2)) : Math.min(d1, d2);
                    for (AI o : cars) if (o != ai && o.lane == l && Math.abs(o.y - ai.y) < 85) sc -= 200;
                    if (sc > bestSc) { bestSc = sc; best = l; }
                }
                ai.tgtLane = best;
                ai.cd = 55 + rng.nextInt(55);
            }
            boolean p2s = (p2 != null && ai.x > W / 2);
            double tx = getLX(ai.tgtLane, p2s);
            ai.x += (tx - ai.x) * 0.09;
            ai.lane = ai.tgtLane;
            if (frame % 4 == 0) exhaust(ai.x, ai.y - 24, ai.col);
            return false;
        });
    }

    void vsPick(Car p, CustomHashMap<PU, Integer> m) {
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

    void vsCoin(Car p) {
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

    void vsOil(Car p) {
        if (p == null || !p.alive) return;
        for (OilPatch o : oils)
            if (!o.used && p.box().intersects(o.box())) {
                p.oilT = 130;
                o.used = true;
                evt("🛢️ OIL SLICK! SLOWING DOWN!");
            }
    }

    void applyPU(Car p, CustomHashMap<PU, Integer> m, PU t) {
        switch (t) {
            case BOOST:
                m.put(PU.BOOST, 250);
                p.boosted = true;
                p.nitro = 100;
                p.score += 50;
                evt("🚀 SPEED BOOST! NITRO FULL! 🚀");
                break;
            case SHIELD:
                m.put(PU.SHIELD, 400);
                p.shielded = true;
                p.score += 50;
                evt("🛡️ SHIELD ACTIVE! (400 frames) 🛡️");
                break;
            case MISSILE:
                m.put(PU.MISSILE, 1);
                p.score += 50;
                evt("💣 MISSILE READY! Press Z/M to fire! 💣");
                break;
            case OIL:
                m.put(PU.OIL, 1);
                p.score += 50;
                evt("🛢️ OIL SLICK READY! Press Z/M to deploy! 🛢️");
                break;
            case SWAP:
                if (currentGameMode == GameMode.RACE && multi) {
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

    void usePU(Car p, CustomHashMap<PU, Integer> m) {
        if (p == null || !p.alive) return;
        if (m.containsKey(PU.MISSILE)) {
            m.remove(PU.MISSILE);
            Car target = null;
            if (multi) target = (p.isP1 ? p2 : p1);
            else {
                double bestDist = Double.MAX_VALUE;
                for (AI a : cars) if (a.alive) {
                    double d = Math.hypot(a.x - p.x, a.y - p.y);
                    if (d < bestDist) { bestDist = d; target = null; }
                }
            }
            if (target != null && target.alive) {
                double dx = target.x - p.x, dy = target.y - p.y;
                double len = Math.hypot(dx, dy);
                if (len > 0) missiles.add(new MissileProjectile(p.x, p.y - 20, dx / len * 8, dy / len * 8, p));
            }
            else missiles.add(new MissileProjectile(p.x, p.y - 20, 0, -8, p));
            evt("🚀 MISSILE FIRED! 🚀");
            return;
        }
        if (m.containsKey(PU.OIL)) {
            m.remove(PU.OIL);
            oils.add(new OilPatch(p.x, p.y + 45));
            ring(p.x, p.y + 45, new Color(0x334455));
            evt("🛢️ OIL DEPLOYED! 🛢️");
        }
    }

    void nitro(Car p, CustomHashMap<PU, Integer> m) {
        if (p == null || !p.alive || p.nitro < 20) return;
        p.nitro -= 20;
        p.boosted = true;
        p.boostT = 100;
        m.put(PU.BOOST, 100);
        evt(p.isP1 ? "P1 NITRO! 🔥" : "P2 NITRO! 🔥");
        screenShake = 8;
    }

    void spawnPickup() {
        PU[] ts = {PU.BOOST, PU.SHIELD, PU.MISSILE, PU.OIL, PU.SWAP};
        PU t = ts[rng.nextInt(ts.length)];
        pickups.add(new Pickup(getLX(rng.nextInt(3), false), ROAD_TOP + 20, t));
        if (multi) pickups.add(new Pickup(getLX(rng.nextInt(3), true), ROAD_TOP + 20, ts[rng.nextInt(ts.length)]));
    }

    void spawnCoin() {
        coins.add(new Coin(getLX(rng.nextInt(3), false), ROAD_TOP + 20));
        if (multi) coins.add(new Coin(getLX(rng.nextInt(3), true), ROAD_TOP + 20));
    }

    void spawnCrate() {
        boolean reinforced = rng.nextBoolean();
        crates.add(new Crate(getLX(rng.nextInt(3), false), ROAD_TOP + 20, reinforced));
        if (multi) crates.add(new Crate(getLX(rng.nextInt(3), true), ROAD_TOP + 20, reinforced));
    }

    void exhaust(double x, double y, Color c) {
        if (frame % 2 != 0) return;
        sparks.add(new Spark(x, y, (rng.nextDouble() - .5) * 1.2, rng.nextDouble() * 1.2 + 0.3, c.darker().darker(), 10, 3f));
    }

    void smoke(double x, double y) {
        for (int i = 0; i < 8; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            double s = 1 + rng.nextDouble() * 2.5;
            sparks.add(new Spark(x, y, Math.cos(a) * s, Math.sin(a) * s, new Color(100, 100, 100), 20, 4f));
        }
    }

    void explode(double x, double y) {
        Color[] cs = {Color.ORANGE, Color.RED, Color.YELLOW, Color.WHITE};
        for (int i = 0; i < 35; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            double s = 1.5 + rng.nextDouble() * 6.5;
            sparks.add(new Spark(x, y, Math.cos(a) * s, Math.sin(a) * s, cs[rng.nextInt(cs.length)], 40, 7f));
        }
    }

    void ring(double x, double y, Color c) {
        for (int i = 0; i < 12; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            double s = 1 + rng.nextDouble() * 3;
            sparks.add(new Spark(x, y, Math.cos(a) * s, Math.sin(a) * s, c, 24, 3f));
        }
    }

    // DRAWING METHODS
    void drawStars(Graphics2D g) { rng.setSeed(77); for (int i = 0; i < 90; i++) { int sx = rng.nextInt(W), sy = rng.nextInt(H); float fl = (float) (0.35 + 0.65 * Math.sin(frame * 0.04 + i * 1.3)); float alpha = Math.min(0.7f, Math.max(0.15f, 0.15f + 0.55f * fl)); g.setColor(new Color(1f, 1f, 1f, alpha)); g.fillOval(sx, sy, rng.nextInt(2) + 1, rng.nextInt(2) + 1); } rng.setSeed(System.nanoTime()); }
    void drawGrid(Graphics2D g) { g.setColor(new Color(255, 255, 255, 7)); int gs = 60, off = (int) (frame * 0.35) % gs; for (int x = 0; x <= W; x += gs) g.drawLine(x, 0, x, H); for (int y = -gs; y <= H; y += gs) g.drawLine(0, y + off, W, y + off); }
    void drawBtn(Graphics2D g, int cx, int y, int w, int h, String txt, Color col, boolean hov) { int x = cx - w / 2; if (hov) { g.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), 45)); g.fillRoundRect(x - 4, y - 4, w + 8, h + 8, 12, 12); } g.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), 30)); g.fillRoundRect(x, y, w, h, 8, 8); g.setColor(hov ? col : col.darker()); g.setStroke(new BasicStroke(hov ? 2.5f : 1.5f)); g.drawRoundRect(x, y, w, h, 8, 8); g.setStroke(new BasicStroke(1)); g.setFont(fB.deriveFont(18f)); g.setColor(hov ? Color.WHITE : col); cs(g, txt, cx, y + h / 2 + 7); }
    void backBtn(Graphics2D g) { g.setColor(new Color(255, 255, 255, 25)); g.fillRoundRect(25, H - 74, 122, 47, 8, 8); g.setColor(new Color(0x00E5FF)); g.setStroke(new BasicStroke(1.5f)); g.drawRoundRect(25, H - 74, 122, 47, 8, 8); g.setStroke(new BasicStroke(1)); g.setFont(fB.deriveFont(15f)); g.setColor(Color.WHITE); cs(g, "← BACK", 86, H - 74 + 29); }
    void cs(Graphics2D g, String s, int cx, int cy) { FontMetrics fm = g.getFontMetrics(); g.drawString(s, cx - fm.stringWidth(s) / 2, cy); }
    String fmt(int s) { return String.format("%02d:%02d", s / 60, s % 60); }
    Color puCol(PU t) { return switch (t) { case BOOST -> new Color(0xFF8C00); case SHIELD -> new Color(0x00BFFF); case MISSILE -> new Color(0xFF2222); case OIL -> new Color(0x445566); case SWAP -> new Color(0x9B59B6); }; }
    String puLbl(PU t) { return switch (t) { case BOOST -> "NOS"; case SHIELD -> "SHD"; case MISSILE -> "MSL"; case OIL -> "OIL"; case SWAP -> "SWP"; }; }

    void drawDesertSides(Graphics2D g, int ox, int vw, int rl, int rr) { g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); g.setColor(new Color(0xF5DEB3)); g.fillRect(ox, H - 180, rl - ox, 180); g.fillRect(rr, H - 180, ox + vw - rr, 180); g.setColor(new Color(0xE8C99A)); int[] hill1x = {ox - 20, ox + 40, ox + 100, ox + 160, Math.min(rl + 10, ox + vw - 10)}; int[] hill1y = {H - 100, H - 220, H - 250, H - 200, H - 100}; if (rl - ox > 30) g.fillPolygon(hill1x, hill1y, 5); g.setColor(new Color(0xD4A373)); int[] hill2x = {ox + 30, ox + 90, ox + 150, Math.max(rl - 20, ox + 10)}; int[] hill2y = {H - 90, H - 200, H - 220, H - 100}; if (rl - ox > 30) g.fillPolygon(hill2x, hill2y, 4); g.setColor(new Color(0xE8C99A)); int[] hill1rx = {rr - 10, Math.max(ox + vw - 150, rr + 10), Math.max(ox + vw - 90, rr + 10), Math.max(ox + vw - 40, rr + 10), ox + vw + 20}; int[] hill1ry = {H - 100, H - 210, H - 240, H - 200, H - 100}; if (ox + vw - rr > 30) g.fillPolygon(hill1rx, hill1ry, 5); g.setColor(new Color(0xFFD700)); g.fillOval(ox + vw - 70, 25, 45, 45); g.setColor(new Color(0xFFF0A0)); g.fillOval(ox + vw - 65, 30, 35, 35); }
    void drawNightSides(Graphics2D g, int ox, int vw, int rl, int rr) { g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); g.setColor(new Color(0x1A1025)); g.fillRect(ox, H - 180, rl - ox, 180); g.fillRect(rr, H - 180, ox + vw - rr, 180); g.setColor(new Color(0xFFF5C0)); g.fillOval(ox + vw - 90, 25, 55, 55); g.setColor(thm.bgTop); g.fillOval(ox + vw - 82, 20, 50, 50); for (int i = 0; i < 60; i++) { int sx = ox + 10 + (i * 37) % Math.max(1, vw - 70); int sy = 30 + (i * 23) % 140; float twinkle = (float) (0.4 + 0.6 * Math.sin(frame * 0.05 + i)); float alpha = Math.min(0.8f, Math.max(0.2f, twinkle)); int size = 2 + (i % 3); g.setColor(new Color(1f, 1f, 0.9f, alpha)); g.fillOval(sx, sy, size, size); if (i % 7 == 0) { g.setColor(new Color(1f, 1f, 0.7f, alpha * 0.5f)); g.fillOval(sx - 1, sy - 1, size + 2, size + 2); } } }
    void drawHighwaySides(Graphics2D g, int ox, int vw, int rl, int rr) { g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); g.setColor(new Color(0xE8E8F0)); g.fillRect(ox, H - 180, rl - ox, 180); g.fillRect(rr, H - 180, ox + vw - rr, 180); Color[] pastels = {new Color(0xFFB3BA), new Color(0xFFDFBA), new Color(0xBAFFC9), new Color(0xBAE1FF)}; for (int i = 0; i < 4; i++) { int bx = ox + 15 + i * 55; if (bx + 25 < rl) { int height = 100 + (i * 15) % 80; g.setColor(pastels[i % pastels.length]); g.fillRect(bx, H - 80 - height, 22, height); g.setColor(new Color(255, 255, 255, 180)); for (int wy = H - 70 - height; wy < H - 90; wy += 15) { g.fillRect(bx + 4, wy, 5, 8); g.fillRect(bx + 13, wy, 5, 8); } g.setColor(new Color(255, 200, 100)); g.fillRect(bx + 8, H - 88 - height, 6, 10); } } for (int i = 0; i < 4; i++) { int bx = rr + 20 + i * 60; if (bx + 25 < ox + vw) { int height = 110 + (i * 12) % 70; g.setColor(pastels[(i + 2) % pastels.length]); g.fillRect(bx, H - 80 - height, 22, height); g.setColor(new Color(255, 255, 255, 180)); for (int wy = H - 70 - height; wy < H - 90; wy += 15) { g.fillRect(bx + 4, wy, 5, 8); g.fillRect(bx + 13, wy, 5, 8); } g.setColor(new Color(255, 200, 100)); g.fillRect(bx + 8, H - 88 - height, 6, 10); } } g.setColor(new Color(0xFFE066)); g.fillOval(ox + vw - 80, 20, 55, 55); g.setColor(new Color(0xFFF0A0)); g.fillOval(ox + vw - 75, 25, 45, 45); }

    void drawHalf(Graphics2D g, int ox, int vw, Car p, CustomHashMap<PU, Integer> m, boolean isP2) {
        int rl = getRoadLeft(isP2), rr = rl + ROAD_W;
        rl = Math.max(ox, Math.min(rl, ox + vw - ROAD_W));
        rr = rl + ROAD_W;
        g.setPaint(new GradientPaint(ox, 0, thm.bgTop, ox, H, thm.bgBot));
        g.fillRect(ox, 0, vw, H);
        switch (thm) {
            case DESERT -> drawDesertSides(g, ox, vw, rl, rr);
            case NIGHT -> drawNightSides(g, ox, vw, rl, rr);
            case HIGHWAY -> drawHighwaySides(g, ox, vw, rl, rr);
        }
        g.setColor(thm.road);
        g.fillRect(rl, 0, ROAD_W, H);
        for (int y = 0; y < H; y += 40) {
            int mod = ((y + (int) roadOff) % 80 + 80) % 80;
            g.setColor(mod < 40 ? new Color(0xDD3333) : Color.WHITE);
            g.fillRoundRect(rl, y, 10, 35, 5, 5);
            g.fillRoundRect(rr - 10, y, 10, 35, 5, 5);
        }
        g.setColor(thm.lane);
        float[] dash = {28f, 20f};
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_MITER, 10, dash, (float) roadOff));
        for (int l = 1; l < 3; l++) {
            int lx = rl + l * (ROAD_W / 3);
            g.drawLine(lx, 0, lx, H);
        }
        g.setStroke(new BasicStroke(1));
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(3));
        g.drawLine(rl + 12, 0, rl + 12, H);
        g.drawLine(rr - 12, 0, rr - 12, H);
        g.setStroke(new BasicStroke(1));

        for (OilPatch o : oils) { if (!multi || (isP2 ? (o.x >= W / 2) : (o.x < W / 2))) { float a = (float) o.life / 360f; g.setColor(new Color(0.1f, 0.1f, 0.2f, 0.75f * a)); g.fillOval((int) o.x - 24, (int) o.y - 12, 48, 24); } }
        for (MissileProjectile msl : missiles) { if ((!multi || (isP2 ? (msl.x >= W / 2) : (msl.x < W / 2))) && msl.owner == p) { g.setColor(new Color(0xFF4444)); g.fillOval((int) msl.x - 6, (int) msl.y - 6, 12, 12); g.setColor(Color.WHITE); g.fillOval((int) msl.x - 3, (int) msl.y - 3, 6, 6); } }
        for (Crate c : crates) { boolean mine = !multi || (isP2 ? (c.x >= W / 2 - 5) : (c.x < W / 2 + 5)); if (mine && !c.done) { c.tick++; c.y += 5 * thm.spd; if (c.y > H + 40) c.done = true; else { int cx = (int) c.x, cy = (int) c.y; Color crateColor = c.reinforced ? new Color(0x8B4513) : new Color(0xB8860B); g.setColor(crateColor); g.fillRect(cx - 16, cy - 16, 32, 32); g.setColor(new Color(0x654321)); g.setStroke(new BasicStroke(2)); g.drawLine(cx - 16, cy, cx + 16, cy); g.drawLine(cx, cy - 16, cx, cy + 16); g.setStroke(new BasicStroke(1)); if (c.reinforced) { g.setColor(new Color(0xFFD700)); g.drawRect(cx - 14, cy - 14, 28, 28); } } } }
        for (Coin c : coins) { boolean mine = !multi || (isP2 ? (c.x >= W / 2 - 5) : (c.x < W / 2 + 5)); if (mine && !c.done) { c.tick++; c.y += 5 * thm.spd; if (c.y > H + 40) c.done = true; else { float pulse = 0.8f + 0.2f * (float) Math.sin(c.tick * 0.15); int cx = (int) c.x, cy = (int) c.y; g.setColor(new Color(0xFFD700)); g.fillOval(cx - 9, cy - 9, 18, 18); g.setColor(new Color(0xFFF0C0)); g.fillOval(cx - 6, cy - 7, 7, 7); g.setFont(fS.deriveFont(9f)); g.setColor(new Color(0x6B4C10)); cs(g, "$", cx, cy + 4); } } }
        for (Pickup pk : pickups) { boolean mine = !multi || (isP2 ? (pk.x >= W / 2 - 5) : (pk.x < W / 2 + 5)); if (mine && !pk.done) { pk.tick++; pk.y += 5.2 * thm.spd; if (pk.y > H + 40) pk.done = true; else { int px = (int) pk.x, py = (int) pk.y; Color col = puCol(pk.type); g.setColor(col); g.fillRoundRect(px - 14, py - 14, 28, 28, 7, 7); g.setColor(Color.WHITE); g.setStroke(new BasicStroke(2)); g.drawRoundRect(px - 14, py - 14, 28, 28, 7, 7); g.setStroke(new BasicStroke(1)); g.setFont(fS.deriveFont(10f)); g.setColor(Color.WHITE); cs(g, puLbl(pk.type), px, py + 4); } } }
        for (AI ai : cars) { if (!ai.alive) continue; boolean mine = !multi || (isP2 ? (ai.x >= W / 2 - 10) : (ai.x < W / 2 + 10)); if (mine) { int cx = (int) ai.x, cy = (int) ai.y; g.setColor(new Color(0, 0, 0, 45)); g.fillOval(cx - 15, cy + 22, 30, 11); g.setColor(ai.col); g.fillRoundRect(cx - 15, cy - 30, 30, 62, 9, 9); g.setColor(ai.col.darker()); g.fillRect(cx - 15, cy - 12, 30, 5); g.setColor(Color.BLACK); g.fillOval(cx - 12, cy + 25, 7, 5); g.fillOval(cx + 5, cy + 25, 7, 5); } }
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
                g.setColor(new Color(0, 0, 0, 65)); g.fillOval(cx - 19, cy + 24, 38, 13);
                g.setColor(p.body); g.fillRoundRect(cx - 15, cy - 30, 30, 62, 9, 9);
                g.setColor(p.accent); g.fillRect(cx - 15, cy - 12, 30, 5);
                g.setColor(p.body.brighter()); g.fillRoundRect(cx - 11, cy - 30, 22, 18, 7, 7);
                g.setColor(new Color(160, 215, 255, 172)); g.fillRoundRect(cx - 9, cy - 26, 18, 12, 4, 4);
                g.setColor(p.boosted ? new Color(0xFFFF00) : new Color(0xFFFFCC)); g.fillOval(cx - 13, cy - 35, 8, 6); g.fillOval(cx + 5, cy - 35, 8, 6);
                g.setColor(Color.BLACK); g.fillOval(cx - 12, cy + 25, 7, 5); g.fillOval(cx + 5, cy + 25, 7, 5);
            } else {
                g.setColor(new Color(0, 0, 0, 40)); g.fillOval(cx - 19, cy + 24, 38, 13);
                g.setColor(new Color(p.body.getRed(), p.body.getGreen(), p.body.getBlue(), 100)); g.fillRoundRect(cx - 15, cy - 30, 30, 62, 9, 9);
                g.setColor(new Color(p.accent.getRed(), p.accent.getGreen(), p.accent.getBlue(), 100)); g.fillRect(cx - 15, cy - 12, 30, 5);
            }
            int bw = 40, bh = 6; g.setColor(new Color(0, 0, 0, 150)); g.fillRect(cx - bw / 2, cy - 38, bw, bh);
            Color healthColor = p.health > 60 ? new Color(0x00FF00) : (p.health > 30 ? new Color(0xFFA500) : new Color(0xFF0000));
            g.setColor(healthColor); g.fillRect(cx - bw / 2, cy - 38, (int) (bw * p.health / 100.0), bh);
            g.setColor(Color.WHITE); g.drawRect(cx - bw / 2, cy - 38, bw, bh);
            int nbw = 36, nbh = 5; g.setColor(new Color(0, 0, 0, 100)); g.fillRect(cx - nbw / 2, cy - 52, nbw, nbh);
            g.setColor(p.isP1 ? new Color(0x00E5FF) : new Color(0xFF4757)); g.fillRect(cx - nbw / 2, cy - 52, (int) (nbw * p.nitro / 100.0), nbh);
            g.setColor(Color.WHITE); g.drawRect(cx - nbw / 2, cy - 52, nbw, nbh);
            if (p.dmgFlash > 0) { g.setColor(new Color(255, 0, 0, 100)); g.fillRoundRect(cx - 15, cy - 30, 30, 62, 9, 9); }
        }
    }

    void drawSparks(Graphics2D g) { for (Spark s : sparks) { float a = Math.min(1f, (float) s.life / s.maxL); g.setColor(new Color(s.col.getRed(), s.col.getGreen(), s.col.getBlue(), (int) (255 * a))); int sz = Math.max(1, (int) (s.sz * a)); g.fillOval((int) s.x - sz / 2, (int) s.y - sz / 2, sz, sz); } }
    void drawCountdown(Graphics2D g) { g.setColor(new Color(0, 0, 0, 200)); g.fillRect(0, 0, W, H); String txt = cdNum > 0 ? String.valueOf(cdNum) : "GO!"; Color col = cdNum > 0 ? new Color(0xFF4757) : new Color(0x00E676); for (int gw = 12; gw >= 0; gw--) { g.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), (int) (7 * gw))); g.setFont(fT.deriveFont(112 + gw * 3)); FontMetrics fm = g.getFontMetrics(); g.drawString(txt, (W - fm.stringWidth(txt)) / 2 - gw, H / 2 + 55 + gw); } g.setFont(fT.deriveFont(112f)); g.setColor(col); cs(g, txt, W / 2, H / 2 + 55); g.setFont(fB.deriveFont(19f)); g.setColor(Color.LIGHT_GRAY); cs(g, "GET READY!", W / 2, H / 2 - 55); }

    void drawHUD(Graphics2D g) {
        if (!multi && p1 != null) {
            int px = 10, py = 20;
            g.setColor(new Color(0, 0, 0, 180)); g.fillRoundRect(px, py, 210, 150, 12, 12);
            g.setColor(new Color(0x00E5FF)); g.setStroke(new BasicStroke(1.5f)); g.drawRoundRect(px, py, 210, 150, 12, 12);
            g.setFont(fS.deriveFont(Font.BOLD, 13f)); g.setColor(new Color(0x00E5FF)); g.drawString("🏎️ " + (player1Name.isEmpty() ? "RACER" : player1Name), px + 10, py + 20);
            g.setFont(fH.deriveFont(20f)); g.setColor(Color.WHITE); g.drawString(String.format("%,d", p1.score), px + 10, py + 48);
            g.setFont(fS.deriveFont(11f)); g.setColor(new Color(255, 215, 0)); g.drawString("🪙 " + p1.coins, px + 10, py + 70);
            g.setColor(new Color(255, 215, 0)); g.drawString("⚡ LV." + diff, px + 10, py + 88);
            g.setColor(Color.WHITE); g.drawString("💨", px + 10, py + 108);
            g.setColor(new Color(0, 0, 0, 100)); g.fillRoundRect(px + 28, py + 100, 80, 8, 4, 4);
            g.setColor(new Color(255, 140, 0)); g.fillRoundRect(px + 28, py + 100, (int) (80 * p1.nitro / 100.0), 8, 4, 4);
            g.setColor(Color.WHITE); g.drawString("❤️", px + 10, py + 130);
            g.setColor(new Color(0, 0, 0, 100)); g.fillRoundRect(px + 28, py + 122, 80, 8, 4, 4);
            Color hpColor = p1.health > 60 ? new Color(0x00FF00) : (p1.health > 30 ? new Color(0xFFA500) : new Color(0xFF0000));
            g.setColor(hpColor); g.fillRoundRect(px + 28, py + 122, (int) (80 * p1.health / 100.0), 8, 4, 4);
        } else if (multi && p1 != null && p2 != null) {
            int px1 = 10, py = 20;
            g.setColor(new Color(0, 0, 0, 180)); g.fillRoundRect(px1, py, 210, 150, 12, 12);
            g.setColor(new Color(0x00E5FF)); g.drawRoundRect(px1, py, 210, 150, 12, 12);
            g.setFont(fS.deriveFont(Font.BOLD, 13f)); g.setColor(new Color(0x00E5FF)); g.drawString("🏎️ " + (player1Name.isEmpty() ? "RACER1" : player1Name), px1 + 10, py + 20);
            g.setFont(fH.deriveFont(20f)); g.setColor(Color.WHITE); g.drawString(String.format("%,d", p1.score), px1 + 10, py + 48);
            g.setFont(fS.deriveFont(11f)); g.setColor(new Color(255, 215, 0)); g.drawString("🪙 " + p1.coins, px1 + 10, py + 70);
            g.setColor(Color.WHITE); g.drawString("💨", px1 + 10, py + 100);
            g.setColor(new Color(0, 0, 0, 100)); g.fillRoundRect(px1 + 28, py + 92, 80, 8, 4, 4);
            g.setColor(new Color(255, 140, 0)); g.fillRoundRect(px1 + 28, py + 92, (int) (80 * p1.nitro / 100.0), 8, 4, 4);
            g.setColor(Color.WHITE); g.drawString("❤️", px1 + 10, py + 122);
            g.setColor(new Color(0, 0, 0, 100)); g.fillRoundRect(px1 + 28, py + 114, 80, 8, 4, 4);
            Color hpColor1 = p1.health > 60 ? new Color(0x00FF00) : (p1.health > 30 ? new Color(0xFFA500) : new Color(0xFF0000));
            g.setColor(hpColor1); g.fillRoundRect(px1 + 28, py + 114, (int) (80 * p1.health / 100.0), 8, 4, 4);
            int px2 = W - 210;
            g.setColor(new Color(0, 0, 0, 180)); g.fillRoundRect(px2, py, 210, 150, 12, 12);
            g.setColor(new Color(0xFF4757)); g.drawRoundRect(px2, py, 210, 150, 12, 12);
            g.setFont(fS.deriveFont(Font.BOLD, 13f)); g.setColor(new Color(0xFF4757)); g.drawString("🏎️ " + (player2Name.isEmpty() ? "RACER2" : player2Name), px2 + 10, py + 20);
            g.setFont(fH.deriveFont(20f)); g.setColor(Color.WHITE); g.drawString(String.format("%,d", p2.score), px2 + 10, py + 48);
            g.setFont(fS.deriveFont(11f)); g.setColor(new Color(255, 215, 0)); g.drawString("🪙 " + p2.coins, px2 + 10, py + 70);
            g.setColor(Color.WHITE); g.drawString("💨", px2 + 10, py + 100);
            g.setColor(new Color(0, 0, 0, 100)); g.fillRoundRect(px2 + 28, py + 92, 80, 8, 4, 4);
            g.setColor(new Color(255, 140, 0)); g.fillRoundRect(px2 + 28, py + 92, (int) (80 * p2.nitro / 100.0), 8, 4, 4);
            g.setColor(Color.WHITE); g.drawString("❤️", px2 + 10, py + 122);
            g.setColor(new Color(0, 0, 0, 100)); g.fillRoundRect(px2 + 28, py + 114, 80, 8, 4, 4);
            Color hpColor2 = p2.health > 60 ? new Color(0x00FF00) : (p2.health > 30 ? new Color(0xFFA500) : new Color(0xFF0000));
            g.setColor(hpColor2); g.fillRoundRect(px2 + 28, py + 114, (int) (80 * p2.health / 100.0), 8, 4, 4);
        }
        if (currentGameMode != GameMode.RACE) {
            g.setColor(new Color(0, 0, 0, 150)); g.fillRoundRect(W / 2 - 150, 8, 300, 28, 10, 10);
            g.setColor(new Color(0x00E5FF)); g.drawRoundRect(W / 2 - 150, 8, 300, 28, 10, 10);
            g.setFont(fS.deriveFont(12f));
            String themeIcon = thm == Theme.DESERT ? "🏜️" : (thm == Theme.NIGHT ? "🌙" : "🛣️");
            g.setColor(new Color(0x00E5FF)); g.drawString(themeIcon + " " + thm.label, W / 2 - 140, 27);
            g.setColor(Color.WHITE); g.drawString("⏱️ " + fmt(secs), W / 2 - 40, 27);
            g.setColor(new Color(0xFFD700)); g.drawString("⚡" + diff, W / 2 + 80, 27);
        }
        int ex = 15, ey = H - 95;
        g.setColor(new Color(0, 0, 0, 140)); g.fillRoundRect(ex, ey, 210, 85, 8, 8);
        g.setColor(new Color(0x00E5FF)); g.drawRoundRect(ex, ey, 210, 85, 8, 8);
        g.setFont(fS.deriveFont(9f)); g.setColor(new Color(0x00E5FF)); g.drawString("EVENTS", ex + 8, ey + 14);
        for (int i = 0; i < Math.min(5, evtStk.size()); i++) {
            String event = evtStk.get(i);
            if (event != null) {
                if (event.length() > 28) event = event.substring(0, 25) + "...";
                g.drawString("• " + event, ex + 8, ey + 30 + i * 14);
            }
        }
    }

    void drawRaceProgress(Graphics2D g) {
        if (currentGameMode != GameMode.RACE) return;
        int barWidth = W - 100, barX = 50, barY = 55, barH = 20;
        g.setColor(new Color(0, 0, 0, 180)); g.fillRoundRect(barX - 2, barY - 2, barWidth + 4, barH + 4, 10, 10);
        if (!multi && p1 != null) {
            double progress = Math.min(1.0, p1.distance / RACE_DISTANCE);
            g.setColor(new Color(0x00E5FF)); g.fillRoundRect(barX, barY, (int) (barWidth * progress), barH, 8, 8);
            g.setColor(Color.WHITE); g.drawRoundRect(barX, barY, barWidth, barH, 8, 8);
            g.setFont(fB.deriveFont(14f)); cs(g, String.format("DISTANCE: %.0fm / %.0fm", p1.distance, RACE_DISTANCE), W / 2, barY - 8);
            int timeLeft = (int) Math.max(0, raceTimeLimit - secs);
            Color timerColor = timeLeft < 30 ? Color.RED : (timeLeft < 60 ? Color.YELLOW : Color.WHITE);
            g.setColor(timerColor); cs(g, String.format("⏰ TIME: %02d:%02d", timeLeft / 60, timeLeft % 60), W / 2, barY + barH + 18);
            for (int i = 1; i < 5; i++) { int checkpointX = barX + (int) (barWidth * (i / 5.0)); g.setColor(new Color(0xFFD700)); g.fillRect(checkpointX - 2, barY - 5, 4, barH + 10); }
        } else if (multi && p1 != null && p2 != null) {
            double p1Progress = Math.min(1.0, p1.distance / RACE_DISTANCE); int p1BarWidth = (int) ((W / 2 - 40) * p1Progress);
            g.setColor(new Color(0x00E5FF)); g.fillRoundRect(20, barY, p1BarWidth, barH, 8, 8);
            g.setColor(Color.WHITE); g.drawRoundRect(20, barY, W / 2 - 40, barH, 8, 8);
            g.setFont(fS.deriveFont(10f)); g.setColor(new Color(0x00E5FF)); cs(g, (player1Name.isEmpty() ? "P1" : player1Name) + ": " + (int) p1.distance + "m", W / 4, barY - 5);
            double p2Progress = Math.min(1.0, p2.distance / RACE_DISTANCE); int p2BarWidth = (int) ((W / 2 - 40) * p2Progress);
            g.setColor(new Color(0xFF4757)); g.fillRoundRect(W / 2 + 20, barY, p2BarWidth, barH, 8, 8);
            g.setColor(Color.WHITE); g.drawRoundRect(W / 2 + 20, barY, W / 2 - 40, barH, 8, 8);
            g.setColor(new Color(0xFF4757)); cs(g, (player2Name.isEmpty() ? "P2" : player2Name) + ": " + (int) p2.distance + "m", 3 * W / 4, barY - 5);
            if (p1.distance > p2.distance) { g.setColor(new Color(0x00E5FF)); cs(g, "🏆 LEADING", W / 4, barY + barH + 15); }
            else if (p2.distance > p1.distance) { g.setColor(new Color(0xFF4757)); cs(g, "🏆 LEADING", 3 * W / 4, barY + barH + 15); }
        }
    }

    void drawPause(Graphics2D g) { g.setColor(new Color(0, 0, 0, 200)); g.fillRect(0, 0, W, H); g.setColor(new Color(0x00E5FF)); g.setFont(fT.deriveFont(54f)); cs(g, "PAUSED", W / 2, H / 2 - 70); drawBtn(g, W / 2, H / 2 + 20, 200, 50, "RESUME", new Color(0x00E5FF), false); drawBtn(g, W / 2, H / 2 + 90, 200, 50, "MAIN MENU", new Color(0xFF8C00), false); g.setFont(fS.deriveFont(12f)); g.setColor(Color.GRAY); cs(g, "ESC=Resume  Q=Quit", W / 2, H / 2 + 165); }
    void drawBoard(Graphics2D g) { g.setPaint(new GradientPaint(0, 0, new Color(0x06060F), 0, H, new Color(0x101028))); g.fillRect(0, 0, W, H); drawStars(g); g.setColor(new Color(0xFFD700)); g.setFont(fT.deriveFont(52f)); cs(g, "🏆 LEADERBOARD 🏆", W / 2, 80); Object[] sortedObj = board.toSortedArray(); ScoreEntry[] sorted = new ScoreEntry[sortedObj.length]; for (int i = 0; i < sortedObj.length; i++) sorted[i] = (ScoreEntry) sortedObj[i]; Color[] rankCols = {new Color(0xFFD700), new Color(0xC0C0C0), new Color(0xCD7F32)}; String[] medals = {"🥇", "🥈", "🥉"}; if (sorted.length == 0) { g.setFont(fH.deriveFont(20f)); g.setColor(Color.GRAY); cs(g, "No scores yet! Play a game!", W / 2, H / 2); } else { for (int i = 0; i < Math.min(sorted.length, 10); i++) { ScoreEntry e = sorted[i]; int ry = 140 + i * 52; g.setColor(new Color(255, 255, 255, i % 2 == 0 ? 12 : 6)); g.fillRoundRect(W / 2 - 360, ry, 720, 48, 10, 10); g.setFont(fH.deriveFont(24f)); if (i < 3) { g.setColor(rankCols[i]); g.drawString(medals[i], W / 2 - 330, ry + 33); } else { g.setColor(Color.GRAY); g.drawString("#" + (i + 1), W / 2 - 330, ry + 33); } g.setFont(fB.deriveFont(18f)); g.setColor(Color.WHITE); String name = e.name.length() > 12 ? e.name.substring(0, 10) + ".." : e.name; g.drawString(name, W / 2 - 260, ry + 33); g.setFont(fH.deriveFont(23f)); g.setColor(new Color(0x00E5FF)); g.drawString(String.format("%,d", e.score), W / 2 + 80, ry + 33); g.setFont(fS.deriveFont(12f)); g.setColor(Color.GRAY); g.drawString(e.mode, W / 2 + 240, ry + 33); } } drawBtn(g, W / 2, H - 80, 160, 50, "BACK", new Color(0x00E5FF), false); }
    void drawOver(Graphics2D g) { g.setPaint(new GradientPaint(0, 0, new Color(0x150000), 0, H, new Color(0x250808))); g.fillRect(0, 0, W, H); for (int gw = 8; gw >= 0; gw--) { g.setColor(new Color(1f, 0.18f, 0.18f, 0.04f * gw)); g.setFont(fT.deriveFont(58 + gw * 2)); FontMetrics fm = g.getFontMetrics(); g.drawString("GAME OVER", (W - fm.stringWidth("GAME OVER")) / 2 - gw, H / 2 - 110 + gw); } g.setColor(new Color(0xFF4757)); g.setFont(fT.deriveFont(58f)); cs(g, "GAME OVER", W / 2, H / 2 - 110); if (!raceWinner.isEmpty()) { g.setFont(fB.deriveFont(36f)); g.setColor(new Color(0xFFD700)); cs(g, "🏆 WINNER: " + raceWinner + " 🏆", W / 2, H / 2 - 80); } else if (multi && !winnerName.isEmpty()) { g.setFont(fB.deriveFont(36f)); g.setColor(new Color(0xFFD700)); cs(g, "🏆 WINNER: " + winnerName + " 🏆", W / 2, H / 2 - 80); } if (p1 != null && !(currentGameMode == GameMode.RACE && !multi && !raceFinished)) { g.setFont(fH.deriveFont(22f)); g.setColor(new Color(0x00E5FF)); cs(g, (player1Name.isEmpty() ? "P1" : player1Name) + " → " + String.format("%,d", p1.score) + " pts (" + p1.coins + " coins)", W / 2, H / 2 - 25); } if (p2 != null && multi) { g.setFont(fH.deriveFont(22f)); g.setColor(new Color(0xFF4757)); cs(g, (player2Name.isEmpty() ? "P2" : player2Name) + " → " + String.format("%,d", p2.score) + " pts (" + p2.coins + " coins)", W / 2, H / 2 + 15); } g.setFont(fS.deriveFont(13f)); g.setColor(Color.GRAY); cs(g, "Time: " + fmt(secs) + "  ·  Difficulty LV" + diff + "  ·  " + thm.label, W / 2, H / 2 + 100); drawBtn(g, W / 2 - 100, H / 2 + 150, 140, 50, "RETRY", new Color(0x00E5FF), false); drawBtn(g, W / 2 + 60, H / 2 + 150, 140, 50, "MENU", new Color(0xFF8C00), false); }
    void drawMenu(Graphics2D g) { g.setPaint(new GradientPaint(0, 0, new Color(0x06060F), 0, H, new Color(0x101028))); g.fillRect(0, 0, W, H); drawStars(g); drawGrid(g); for (int gw = 9; gw >= 0; gw--) { g.setColor(new Color(0f, 0.9f, 1f, 0.033f * gw)); g.setFont(fT.deriveFont(72 + gw * 2)); FontMetrics fm = g.getFontMetrics(); g.drawString("VELOCITY RIVALS", (W - fm.stringWidth("VELOCITY RIVALS")) / 2 - gw, H / 2 - 190 + gw); } g.setColor(new Color(0x00E5FF)); g.setFont(fT); cs(g, "VELOCITY RIVALS", W / 2, H / 2 - 190); g.setColor(new Color(0xFF8C00)); g.setFont(fB.deriveFont(15f)); cs(g, "RACING CHAMPIONSHIP", W / 2, H / 2 - 130); String[] bt = {"▶ START RACE", "★ LEADERBOARD", "✕ EXIT"}; Color[] bc = {new Color(0x00E5FF), new Color(0xFFD700), new Color(0xFF4757)}; int[] by = {H / 2, H / 2 + 70, H / 2 + 140}; for (int i = 0; i < 3; i++) drawBtn(g, W / 2, by[i], 280, 52, bt[i], bc[i], hov == i); g.setFont(fS.deriveFont(11f)); g.setColor(new Color(255, 255, 255, 60)); cs(g, "⚠️ ANY COLLISION = INSTANT GAME OVER ⚠️", W / 2, H - 20); cs(g, "Single: ARROWS+SPACE+Z  Multi: P1=WASD+SPACE+Z  P2=IJKL+ENTER+M", W / 2, H - 40); cs(g, "🔄 SWAP BOOSTER: Purple pickup - Swap positions in Race Mode! 🔄", W / 2, H - 60); }
    void drawRaceModeSelect(Graphics2D g) { g.setPaint(new GradientPaint(0, 0, new Color(0x06060F), 0, H, new Color(0x101028))); g.fillRect(0, 0, W, H); drawStars(g); g.setColor(new Color(0x00E5FF)); g.setFont(fT.deriveFont(52f)); cs(g, "SELECT GAME MODE", W / 2, 120); int cardW = 350, cardH = 200; int survivalX = W / 2 - cardW - 30, raceX = W / 2 + 30, cardY = H / 2 - cardH / 2; g.setColor(new Color(0x00E5FF, true)); g.fillRoundRect(survivalX, cardY, cardW, cardH, 12, 12); g.setColor(new Color(0x00E5FF)); g.setStroke(new BasicStroke(2)); g.drawRoundRect(survivalX, cardY, cardW, cardH, 12, 12); g.setFont(fB.deriveFont(24f)); cs(g, "🏆 SURVIVAL", survivalX + cardW / 2, cardY + 50); g.setFont(fS.deriveFont(12f)); g.setColor(Color.LIGHT_GRAY); String[] survivalDesc = {"Classic arcade mode", "Auto-scrolling road", "Collect coins & power-ups", "ANY collision = GAME OVER", "Survive as long as possible"}; for (int i = 0; i < survivalDesc.length; i++) cs(g, survivalDesc[i], survivalX + cardW / 2, cardY + 85 + i * 18); g.setColor(new Color(0xFF8C00, true)); g.fillRoundRect(raceX, cardY, cardW, cardH, 12, 12); g.setColor(new Color(0xFF8C00)); g.drawRoundRect(raceX, cardY, cardW, cardH, 12, 12); g.setFont(fB.deriveFont(24f)); cs(g, "🏁 RACE MODE", raceX + cardW / 2, cardY + 50); g.setFont(fS.deriveFont(12f)); g.setColor(Color.LIGHT_GRAY); String[] raceDesc = {"Proper racing experience", "Control your speed!", "UP/W = Accelerate", "DOWN/S = Brake/Reverse", "NEW: SWAP BOOSTER Power-up!"}; for (int i = 0; i < raceDesc.length; i++) cs(g, raceDesc[i], raceX + cardW / 2, cardY + 85 + i * 18); backBtn(g); }
    void drawMode(Graphics2D g) { g.setPaint(new GradientPaint(0, 0, new Color(0x06060F), 0, H, new Color(0x101028))); g.fillRect(0, 0, W, H); drawStars(g); g.setColor(new Color(0x00E5FF)); g.setFont(fT.deriveFont(52f)); cs(g, "SELECT PLAYERS", W / 2, 120); int cardW = 280, cardH = 160, singleX = W / 2 - cardW - 20, multiX = W / 2 + 20, cardY = H / 2 - cardH / 2; String modeName = (currentGameMode == GameMode.RACE) ? "RACE MODE" : "SURVIVAL MODE"; g.setFont(fS.deriveFont(14f)); g.setColor(new Color(0xFFD700)); cs(g, "⚡ " + modeName + " ⚡", W / 2, 200); g.setColor(new Color(0x00E5FF, true)); g.fillRoundRect(singleX, cardY, cardW, cardH, 12, 12); g.setColor(new Color(0x00E5FF)); g.drawRoundRect(singleX, cardY, cardW, cardH, 12, 12); g.setFont(fB.deriveFont(18f)); g.setColor(new Color(0x00E5FF)); cs(g, "SINGLE PLAYER", singleX + cardW / 2, cardY + 40); g.setFont(fS.deriveFont(11f)); g.setColor(Color.LIGHT_GRAY); String[] singleDesc = (currentGameMode == GameMode.RACE) ? new String[]{"Race to the finish!", "UP/DOWN = Speed Control", "Reach 5000m in 90 seconds", "ANY collision = GAME OVER"} : new String[]{"Arrow Keys to Drive", "SPACE = Nitro", "Z = Power-up", "ANY collision = GAME OVER"}; for (int i = 0; i < singleDesc.length; i++) cs(g, singleDesc[i], singleX + cardW / 2, cardY + 70 + i * 22); g.setColor(new Color(0xFF4757, true)); g.fillRoundRect(multiX, cardY, cardW, cardH, 12, 12); g.setColor(new Color(0xFF4757)); g.drawRoundRect(multiX, cardY, cardW, cardH, 12, 12); g.setFont(fB.deriveFont(18f)); g.setColor(new Color(0xFF4757)); cs(g, "MULTIPLAYER", multiX + cardW / 2, cardY + 40); g.setFont(fS.deriveFont(11f)); g.setColor(Color.LIGHT_GRAY); String[] multiDesc = (currentGameMode == GameMode.RACE) ? new String[]{"P1: WASD + SPACE + Z", "P2: IJKL + ENTER + M", "First to 5000m WINS!", "NEW: SWAP BOOSTER!", "ANY collision = GAME OVER"} : new String[]{"P1: WASD + SPACE + Z", "P2: IJKL + ENTER + M", "Last one standing wins!", "ANY collision = GAME OVER"}; for (int i = 0; i < multiDesc.length; i++) cs(g, multiDesc[i], multiX + cardW / 2, cardY + 70 + i * 22); backBtn(g); }
    void drawTheme(Graphics2D g) { g.setPaint(new GradientPaint(0, 0, new Color(0x06060F), 0, H, new Color(0x101028))); g.fillRect(0, 0, W, H); drawStars(g); g.setColor(new Color(0x00E5FF)); g.setFont(fT.deriveFont(52f)); cs(g, "CHOOSE TRACK", W / 2, 120); Theme[] ts = Theme.values(); Color[] ac = {new Color(0xF5A623), new Color(0xA855F7), new Color(0x00E5FF)}; String[] ds = {"Sandy desert roads\nHigh Speed · Low Traction", "Neon city at night\nMedium Speed · Wet Roads", "Speedway highway\nMAX Speed · Smooth Tarmac"}; for (int i = 0; i < ts.length; i++) { int x = W / 2 - 430 + i * 305; g.setPaint(new GradientPaint(x, H / 2 - 120, ts[i].bgTop, x, H / 2 + 120, ts[i].bgBot)); g.fillRoundRect(x, H / 2 - 120, 245, 240, 14, 14); g.setColor(ac[i]); g.setStroke(new BasicStroke(2.5f)); g.drawRoundRect(x, H / 2 - 120, 245, 240, 14, 14); g.setFont(fB.deriveFont(14f)); g.setColor(ac[i]); cs(g, ts[i].label, x + 122, H / 2 + 10); g.setFont(fS.deriveFont(11f)); g.setColor(Color.LIGHT_GRAY); String[] ls = ds[i].split("\n"); for (int j = 0; j < ls.length; j++) cs(g, ls[j], x + 122, H / 2 + 30 + j * 18); } backBtn(g); }
    void drawGame(Graphics2D g) { if (screenShake > 0) { int shakeX = rng.nextInt(screenShake) - screenShake / 2; int shakeY = rng.nextInt(screenShake) - screenShake / 2; g.translate(shakeX, shakeY); } if (!multi) drawHalf(g, 0, W, p1, p1map, false); else { Shape cl = g.getClip(); g.setClip(0, 0, W / 2, H); drawHalf(g, 0, W / 2, p1, p1map, false); g.setClip(W / 2, 0, W / 2, H); drawHalf(g, W / 2, W / 2, p2, p2map, true); g.setClip(cl); g.setColor(Color.BLACK); g.fillRect(W / 2 - 2, 0, 4, H); g.setColor(new Color(0x00E5FF)); g.setStroke(new BasicStroke(2)); g.drawLine(W / 2, 0, W / 2, H); g.setStroke(new BasicStroke(1)); } drawSparks(g); drawHUD(g); drawRaceProgress(g); if (!started) drawCountdown(g); if (damageFlash > 5 && damageFlash % 3 == 0) { g.setColor(new Color(255, 0, 0, 40)); g.fillRect(0, 0, W, H); } if (screenShake > 0) g.translate(0, 0); }

    class GamePanel extends JPanel {
        GamePanel() {
            setPreferredSize(new Dimension(W, H)); setBackground(Color.BLACK); setFocusable(true);
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
                case MAIN_MENU -> drawMenu(g2);
                case RACE_MODE_SELECT -> drawRaceModeSelect(g2);
                case MODE_SELECT -> drawMode(g2);
                case THEME_SELECT -> drawTheme(g2);
                case LEADERBOARD -> drawBoard(g2);
                case GAME_OVER -> drawOver(g2);
                case GAME_SINGLE, GAME_MULTI -> drawGame(g2);
                case PAUSE -> { drawGame(g2); drawPause(g2); }
            }
        }
    }

    void onKey(int k) { boolean ig = scr == Screen.GAME_SINGLE || scr == Screen.GAME_MULTI; if (ig && started) { if (k == KeyEvent.VK_SPACE) nitro(p1, p1map); if (multi && k == KeyEvent.VK_ENTER && p2 != null) nitro(p2, p2map); if (k == KeyEvent.VK_Z) usePU(p1, p1map); if (multi && k == KeyEvent.VK_M && p2 != null) usePU(p2, p2map); } if (k == KeyEvent.VK_ESCAPE) { if (scr == Screen.PAUSE) scr = multi ? Screen.GAME_MULTI : Screen.GAME_SINGLE; else if (ig) scr = Screen.PAUSE; } if (scr == Screen.PAUSE && k == KeyEvent.VK_Q) scr = Screen.MAIN_MENU; }
    void click(int mx, int my) { switch (scr) { case MAIN_MENU -> { if (btn(mx, my, W / 2 - 140, H / 2, 280, 52)) scr = Screen.RACE_MODE_SELECT; if (btn(mx, my, W / 2 - 140, H / 2 + 70, 280, 52)) scr = Screen.LEADERBOARD; if (btn(mx, my, W / 2 - 140, H / 2 + 140, 280, 52)) System.exit(0); } case RACE_MODE_SELECT -> { int cardW = 350, cardH = 200, survivalX = W / 2 - cardW - 30, raceX = W / 2 + 30, cardY = H / 2 - cardH / 2; if (mx >= survivalX && mx <= survivalX + cardW && my >= cardY && my <= cardY + cardH) { currentGameMode = GameMode.SURVIVAL; scr = Screen.MODE_SELECT; } else if (mx >= raceX && mx <= raceX + cardW && my >= cardY && my <= cardY + cardH) { currentGameMode = GameMode.RACE; scr = Screen.MODE_SELECT; } else if (btn(mx, my, 28, H - 72, 120, 46)) scr = Screen.MAIN_MENU; } case MODE_SELECT -> { int cardW = 280, cardH = 160, singleX = W / 2 - cardW - 20, multiX = W / 2 + 20, cardY = H / 2 - cardH / 2; if (mx >= singleX && mx <= singleX + cardW && my >= cardY && my <= cardY + cardH) { multi = false; scr = Screen.THEME_SELECT; } else if (mx >= multiX && mx <= multiX + cardW && my >= cardY && my <= cardY + cardH) { multi = true; scr = Screen.THEME_SELECT; } else if (btn(mx, my, 28, H - 72, 120, 46)) scr = Screen.RACE_MODE_SELECT; } case THEME_SELECT -> { Theme[] ts = Theme.values(); for (int i = 0; i < ts.length; i++) { int x = W / 2 - 430 + i * 305; if (btn(mx, my, x, H / 2 - 110, 240, 230)) { thm = ts[i]; askForPlayerNames(); return; } } if (btn(mx, my, 28, H - 72, 120, 46)) scr = Screen.MODE_SELECT; } case PAUSE -> { if (btn(mx, my, W / 2 - 100, H / 2 + 20, 200, 50)) scr = multi ? Screen.GAME_MULTI : Screen.GAME_SINGLE; if (btn(mx, my, W / 2 - 100, H / 2 + 90, 200, 50)) scr = Screen.MAIN_MENU; } case GAME_OVER -> { if (btn(mx, my, W / 2 - 160, H / 2 + 150, 140, 50)) startGame(); if (btn(mx, my, W / 2 + 20, H / 2 + 150, 140, 50)) scr = Screen.MAIN_MENU; } case LEADERBOARD -> { if (btn(mx, my, W / 2 - 80, H - 80, 160, 50)) scr = Screen.MAIN_MENU; } } }
    void hover(int mx, int my) { hov = -1; if (scr == Screen.MAIN_MENU) { int[] ys = {H / 2, H / 2 + 70, H / 2 + 140}; for (int i = 0; i < 3; i++) if (btn(mx, my, W / 2 - 140, ys[i], 280, 52)) { hov = i; break; } } }
    boolean btn(int mx, int my, int x, int y, int w, int h) { return mx >= x && mx <= x + w && my >= y && my <= y + h; }

    public static void main(String[] args) { SwingUtilities.invokeLater(VelocityRivals::new); }
}