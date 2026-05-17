import java.awt.Color;
import java.awt.geom.Rectangle2D;
import java.util.Random;

public class VelocityRivalsEngine {
    public static int W = 1200;
    public static int H = 700;
    public static final int ROAD_W = 400;
    public static int ROAD_X;
    public static final int ROAD_TOP;
    public static int[] LANE_X;

    static {
        ROAD_TOP = (int)(H * 0.4);
    }

    // ==================== CUSTOM DATA STRUCTURES ====================
    @SuppressWarnings("unchecked")
    public static class CustomQueue<T> {
        Object[] buf; int head, tail, size, cap;
        public CustomQueue(int c) { cap = c; buf = new Object[c]; head = tail = size = 0; }
        public void enqueue(T t) {
            if (size == cap) {
                Object[] nb = new Object[cap * 2];
                for (int i = 0; i < size; i++) nb[i] = buf[(head + i) % cap];
                buf = nb; head = 0; tail = size; cap *= 2;
            }
            buf[tail] = t; tail = (tail + 1) % cap; size++;
        }
        public T dequeue() { if (size == 0) return null; T t = (T) buf[head]; buf[head] = null; head = (head + 1) % cap; size--; return t; }
        public boolean isEmpty() { return size == 0; }
    }

    @SuppressWarnings("unchecked")
    public static class CustomStack<T> {
        Object[] data; int top;
        public CustomStack(int c) { data = new Object[c]; top = -1; }
        public void push(T t) { if (top + 1 == data.length) { Object[] nd = new Object[data.length * 2]; System.arraycopy(data, 0, nd, 0, data.length); data = nd; } data[++top] = t; }
        public T pop() { return top < 0 ? null : (T) data[top--]; }
        public T get(int i) { return top - i < 0 ? null : (T) data[top - i]; }
        public boolean isEmpty() { return top < 0; }
        public int size() { return top + 1; }
        public void clear() { data = new Object[16]; top = -1; }
    }

    @SuppressWarnings("unchecked")
    public static class CustomHashMap<K, V> {
        static class Entry<K, V> { K k; V v; Entry<K, V> next; Entry(K k, V v) { this.k = k; this.v = v; } }
        Entry<K, V>[] buckets; int size;
        public CustomHashMap() { buckets = new Entry[16]; size = 0; }
        int hash(K k) { return Math.abs(k.hashCode() % buckets.length); }
        public void put(K k, V v) {
            int i = hash(k);
            for (Entry<K, V> e = buckets[i]; e != null; e = e.next) if (k.equals(e.k)) { e.v = v; return; }
            Entry<K, V> e = new Entry<>(k, v); e.next = buckets[i]; buckets[i] = e; size++;
        }
        public V get(K k) { for (Entry<K, V> e = buckets[hash(k)]; e != null; e = e.next) if (k.equals(e.k)) return e.v; return null; }
        public boolean containsKey(K k) { return get(k) != null; }
        public void remove(K k) {
            int i = hash(k); Entry<K, V> e = buckets[i], p = null;
            while (e != null) { if (k.equals(e.k)) { if (p == null) buckets[i] = e.next; else p.next = e.next; size--; return; } p = e; e = e.next; }
        }
        public void clear() { buckets = new Entry[16]; size = 0; }
        public void tickAndPrune() {
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

    public static class CustomPriorityQueue<T> {
        Object[] heap; int size; java.util.Comparator<T> cmp;
        public CustomPriorityQueue(int cap, java.util.Comparator<T> cmp) { heap = new Object[cap + 1]; this.cmp = cmp; size = 0; }
        public void offer(T t) { if (size + 1 == heap.length) { Object[] n = new Object[heap.length * 2]; System.arraycopy(heap, 0, n, 0, heap.length); heap = n; } heap[++size] = t; swim(size); }
        public T poll() { if (size == 0) return null; T r = (T) heap[1]; heap[1] = heap[size]; heap[size--] = null; sink(1); return r; }
        void swim(int k) { while (k > 1 && cmp.compare((T) heap[k / 2], (T) heap[k]) < 0) { Object t = heap[k]; heap[k] = heap[k / 2]; heap[k / 2] = t; k /= 2; } }
        void sink(int k) { while (2 * k <= size) { int j = 2 * k; if (j < size && cmp.compare((T) heap[j], (T) heap[j + 1]) < 0) j++; if (cmp.compare((T) heap[k], (T) heap[j]) >= 0) break; Object t = heap[k]; heap[k] = heap[j]; heap[j] = t; k = j; } }
        public int size() { return size; }
        @SuppressWarnings("unchecked")
        public T[] toSortedArray() {
            Object[] cp = new Object[size];
            System.arraycopy(heap, 1, cp, 0, size);
            java.util.Arrays.sort(cp, (a, b) -> cmp.compare((T) a, (T) b));
            return (T[]) cp;
        }
    }

    // ==================== ENUMS ====================
    public enum Screen { MAIN_MENU, RACE_MODE_SELECT, MODE_SELECT, THEME_SELECT, GAME_SINGLE, GAME_MULTI, PAUSE, LEADERBOARD, GAME_OVER }
    public enum GameMode { SURVIVAL, RACE }
    public enum Theme {
        DESERT("DESERT HEAT", new Color(0xC8986A), new Color(0xE5C87A), new Color(0xB8651A), new Color(0xD4782A), 1.10f, 0.80f),
        NIGHT("NIGHT CITY", new Color(0x303048), new Color(0x484870), new Color(0x080814), new Color(0x141428), 1.00f, 0.92f),
        HIGHWAY("NEON HIGHWAY", new Color(0x3C3C3C), new Color(0x606060), new Color(0x0C0C1E), new Color(0x181830), 1.25f, 1.00f);
        public final String label;
        public final Color road, lane, bgTop, bgBot;
        public final float spd, trc;
        Theme(String l, Color r, Color ln, Color bt, Color bb, float sp, float tr) {
            label = l; road = r; lane = ln; bgTop = bt; bgBot = bb; spd = sp; trc = tr;
        }
    }
    public enum PU { BOOST, SHIELD, MISSILE, OIL, SWAP }

    // ==================== ENTITIES ====================
    public static class ScoreEntry {
        public String name;
        public int score;
        public String mode;
        public ScoreEntry(String n, int s, String m) { name = n; score = s; mode = m; }
    }

    public static class Car {
        public double x, y, vx, vy;
        public int score = 0, nitro = 100, health = 100, boostT = 0, shieldT = 0, oilT = 0, invincibleT = 0, coins = 0, dmgFlash = 0;
        public boolean alive = true, boosted = false, shielded = false;
        public Color body, accent;
        public boolean isP1;
        public double distance = 0;
        public int lastCheckpoint = 0;
        public Car(double x, double y, Color b, Color a, boolean p1) {
            this.x = x; this.y = y; body = b; accent = a; isP1 = p1;
        }
        public Rectangle2D.Double box() { return new Rectangle2D.Double(x - 14, y - 26, 28, 52); }
    }

    public static class AI {
        public double x, y, spd;
        public int lane, tgtLane, cd;
        public Color col;
        public boolean alive = true, agg;
        public AI(double x, double y, double s, int l, Color c, boolean a) {
            this.x = x; this.y = y; spd = s; lane = l; tgtLane = l; col = c; agg = a;
        }
        public Rectangle2D.Double box() { return new Rectangle2D.Double(x - 13, y - 24, 26, 48); }
    }

    public static class Pickup {
        public double x, y;
        public PU type;
        public boolean done = false;
        public int tick = 0;
        public Pickup(double x, double y, PU t) { this.x = x; this.y = y; type = t; }
        public Rectangle2D.Double box() { return new Rectangle2D.Double(x - 13, y - 13, 26, 26); }
    }

    public static class Coin {
        public double x, y;
        public boolean done = false;
        public int tick = 0;
        public Coin(double x, double y) { this.x = x; this.y = y; }
        public Rectangle2D.Double box() { return new Rectangle2D.Double(x - 9, y - 9, 18, 18); }
    }

    public static class Crate {
        public double x, y;
        public boolean reinforced;
        public boolean done = false;
        public int tick = 0;
        public Crate(double x, double y, boolean r) { this.x = x; this.y = y; reinforced = r; }
        public Rectangle2D.Double box() { return new Rectangle2D.Double(x - 16, y - 16, 32, 32); }
    }

    public static class Spark {
        public double x, y, vx, vy;
        public int life, maxL;
        public Color col;
        public float sz;
        public Spark(double x, double y, double vx, double vy, Color c, int l, float s) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy; col = c; life = l; maxL = l; sz = s;
        }
    }

    public static class OilPatch {
        public double x, y;
        public int life = 360;
        public boolean used = false;
        public OilPatch(double x, double y) { this.x = x; this.y = y; }
        public Rectangle2D.Double box() { return new Rectangle2D.Double(x - 22, y - 10, 44, 20); }
    }

    public static class MissileProjectile {
        public double x, y, vx, vy;
        public Car owner;
        public int life = 60;
        public MissileProjectile(double x, double y, double vx, double vy, Car owner) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy; this.owner = owner;
        }
        public Rectangle2D.Double box() { return new Rectangle2D.Double(x - 6, y - 6, 12, 12); }
    }
}