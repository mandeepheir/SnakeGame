import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;

/**
 * SNAKE - complete edition (single file, no external libraries)
 *
 * Features: start menu, 3 difficulties, levels with growing obstacles,
 * golden bonus food, sound effects, pause, mute, saved high scores.
 *
 * Compile:  javac SnakeGame.java
 * Run:      java SnakeGame
 */
public class SnakeGame extends JPanel implements ActionListener, KeyListener {

    // ---------- constants ----------
    enum State { MENU, PLAYING, PAUSED, OVER }

    static final int CELL = 25, COLS = 26, ROWS = 22;
    static final int BOARD_W = CELL * COLS, BOARD_H = CELL * ROWS;
    static final int HUD = 44;
    static final int W = BOARD_W, H = BOARD_H + HUD;

    static final String[] DIFF_NAME = {"EASY", "NORMAL", "HARD"};
    static final String[] DIFF_INFO = {
        "Walls wrap around. Slow start.",
        "Walls are deadly. Medium speed.",
        "Walls are deadly. Fast. Obstacles from the start."
    };
    static final int[] BASE_DELAY = {130, 100, 75};
    static final int FOODS_PER_LEVEL = 5;

    static final Color BG1 = new Color(16, 20, 28), BG2 = new Color(24, 30, 42);
    static final Color GRID = new Color(255, 255, 255, 10);

    // ---------- game state ----------
    State state = State.MENU;
    int difficulty = 1;
    final int[] high = new int[3];

    final LinkedList<Point> snake = new LinkedList<>();
    final List<Point> obstacles = new ArrayList<>();
    final ArrayDeque<int[]> dirQueue = new ArrayDeque<>();
    final Random rnd = new Random();
    final javax.swing.Timer timer;

    Point food, bonus;
    int bonusTicks;
    int dx, dy;
    int score, eaten, level;
    boolean newRecord, muted;
    long flashUntil;
    String flashText = "";
    int animTick;

    // ---------- setup ----------
    public SnakeGame() {
        setPreferredSize(new Dimension(W, H));
        setFocusable(true);
        addKeyListener(this);
        loadHighScores();
        timer = new javax.swing.Timer(100, this);
        timer.start();
    }

    void startGame() {
        snake.clear();
        obstacles.clear();
        dirQueue.clear();
        int cx = COLS / 2, cy = ROWS / 2;
        snake.add(new Point(cx, cy));
        snake.add(new Point(cx - 1, cy));
        snake.add(new Point(cx - 2, cy));
        dx = 1;
        dy = 0;
        score = 0;
        eaten = 0;
        level = 1;
        bonus = null;
        newRecord = false;
        if (difficulty == 2) addObstacles(6);
        spawnFood();
        timer.setDelay(currentDelay());
        state = State.PLAYING;
        sound(520, 90);
    }

    int currentDelay() {
        return Math.max(45, BASE_DELAY[difficulty] - (level - 1) * 6);
    }

    boolean occupied(Point p) {
        return snake.contains(p) || obstacles.contains(p)
                || p.equals(food) || p.equals(bonus);
    }

    Point randomFreeCell() {
        Point p;
        do {
            p = new Point(rnd.nextInt(COLS), rnd.nextInt(ROWS));
        } while (occupied(p));
        return p;
    }

    void spawnFood() {
        food = null; // so occupied() does not count the old one
        food = randomFreeCell();
    }

    void addObstacles(int count) {
        Point head = snake.getFirst();
        int added = 0, tries = 0;
        while (added < count && tries++ < 500) {
            Point p = randomFreeCell();
            // keep a safe zone around the head and its path
            if (Math.abs(p.x - head.x) + Math.abs(p.y - head.y) < 6) continue;
            obstacles.add(p);
            added++;
        }
    }

    // ---------- game loop ----------
    @Override
    public void actionPerformed(ActionEvent e) {
        animTick++;
        if (state == State.PLAYING) step();
        repaint();
    }

    void step() {
        if (!dirQueue.isEmpty()) {
            int[] d = dirQueue.poll();
            dx = d[0];
            dy = d[1];
        }

        Point head = snake.getFirst();
        int nx = head.x + dx, ny = head.y + dy;

        if (difficulty == 0) { // easy: wrap around
            nx = (nx + COLS) % COLS;
            ny = (ny + ROWS) % ROWS;
        } else if (nx < 0 || ny < 0 || nx >= COLS || ny >= ROWS) {
            die();
            return;
        }

        Point next = new Point(nx, ny);
        boolean eatFood = next.equals(food);
        boolean eatBonus = next.equals(bonus);
        boolean grow = eatFood || eatBonus;

        // the tail cell is free this tick unless we are growing
        Point tail = snake.getLast();
        boolean hitSelf = snake.contains(next) && (grow || !next.equals(tail));
        if (hitSelf || obstacles.contains(next)) {
            die();
            return;
        }

        snake.addFirst(next);
        if (!grow) snake.removeLast();

        if (eatFood) {
            score += 10;
            eaten++;
            sound(700, 60);
            spawnFood();
            if (bonus == null && rnd.nextInt(4) == 0) {
                bonus = randomFreeCell();
                bonusTicks = 55;
            }
            if (eaten % FOODS_PER_LEVEL == 0) levelUp();
        } else if (eatBonus) {
            score += 50;
            bonus = null;
            flash("+50 BONUS!");
            sound(1000, 70);
            sound(1300, 110);
        }

        if (bonus != null && --bonusTicks <= 0) bonus = null;
        timer.setDelay(currentDelay());
    }

    void levelUp() {
        level++;
        addObstacles(Math.min(3, 25 - obstacles.size()));
        flash("LEVEL " + level);
        sound(880, 120);
    }

    void die() {
        state = State.OVER;
        if (score > high[difficulty]) {
            high[difficulty] = score;
            newRecord = true;
            saveHighScores();
        }
        sound(180, 400);
    }

    void flash(String text) {
        flashText = text;
        flashUntil = System.currentTimeMillis() + 1200;
    }

    // ---------- high scores ----------
    static Path scoreFile() {
        return Paths.get(System.getProperty("user.home"), ".snake_highscores.txt");
    }

    void loadHighScores() {
        try {
            List<String> lines = Files.readAllLines(scoreFile());
            for (int i = 0; i < 3 && i < lines.size(); i++) {
                high[i] = Integer.parseInt(lines.get(i).trim());
            }
        } catch (Exception ignored) { }
    }

    void saveHighScores() {
        try {
            Files.write(scoreFile(), Arrays.asList(
                    String.valueOf(high[0]), String.valueOf(high[1]), String.valueOf(high[2])));
        } catch (Exception ignored) { }
    }

    // ---------- sound ----------
    void sound(int hz, int ms) {
        if (muted) return;
        new Thread(() -> {
            try {
                float rate = 22050f;
                AudioFormat af = new AudioFormat(rate, 8, 1, true, false);
                int n = (int) (rate * ms / 1000);
                byte[] buf = new byte[n];
                for (int i = 0; i < n; i++) {
                    double fade = 1.0 - (double) i / n;
                    buf[i] = (byte) (Math.sin(2 * Math.PI * i * hz / rate) * 50 * fade);
                }
                SourceDataLine line = AudioSystem.getSourceDataLine(af);
                line.open(af);
                line.start();
                line.write(buf, 0, buf.length);
                line.drain();
                line.close();
            } catch (Exception ignored) { }
        }).start();
    }

    // ---------- drawing ----------
    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g.setPaint(new GradientPaint(0, 0, BG1, 0, H, BG2));
        g.fillRect(0, 0, W, H);

        if (state == State.MENU) {
            drawMenu(g);
            return;
        }

        drawBoard(g);
        drawHud(g);

        if (System.currentTimeMillis() < flashUntil) {
            g.setFont(new Font("SansSerif", Font.BOLD, 34));
            g.setColor(new Color(255, 220, 90));
            centered(g, flashText, HUD + BOARD_H / 2 - 60);
        }
        if (state == State.PAUSED) {
            overlay(g, "PAUSED", "Press P to resume   |   ESC for menu");
        } else if (state == State.OVER) {
            String sub = (newRecord ? "NEW HIGH SCORE!  " : "") + "SPACE: play again   |   ESC: menu";
            overlay(g, "GAME OVER", sub);
        }
    }

    void drawBoard(Graphics2D g) {
        g.translate(0, HUD);

        g.setColor(GRID);
        for (int x = 0; x <= COLS; x++) g.drawLine(x * CELL, 0, x * CELL, BOARD_H);
        for (int y = 0; y <= ROWS; y++) g.drawLine(0, y * CELL, BOARD_W, y * CELL);

        // border (solid wall on normal/hard, dashed hint on easy)
        g.setStroke(difficulty == 0
                ? new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{8f, 8f}, 0f)
                : new BasicStroke(3f));
        g.setColor(difficulty == 0 ? new Color(90, 200, 255, 120) : new Color(230, 90, 90, 200));
        g.drawRect(1, 1, BOARD_W - 2, BOARD_H - 2);
        g.setStroke(new BasicStroke(1f));

        // obstacles
        for (Point o : obstacles) {
            g.setColor(new Color(110, 118, 135));
            g.fillRoundRect(o.x * CELL + 1, o.y * CELL + 1, CELL - 2, CELL - 2, 6, 6);
            g.setColor(new Color(150, 158, 175));
            g.drawRoundRect(o.x * CELL + 1, o.y * CELL + 1, CELL - 3, CELL - 3, 6, 6);
        }

        // food (pulsing)
        if (food != null) {
            int pulse = (int) (Math.sin(animTick / 2.0) * 2);
            g.setColor(new Color(240, 80, 80));
            g.fillOval(food.x * CELL + 4 - pulse, food.y * CELL + 4 - pulse,
                    CELL - 8 + pulse * 2, CELL - 8 + pulse * 2);
            g.setColor(new Color(255, 255, 255, 120));
            g.fillOval(food.x * CELL + 8, food.y * CELL + 7, 5, 5);
        }

        // bonus (golden star-ish, blinks when about to vanish)
        if (bonus != null && (bonusTicks > 15 || animTick % 2 == 0)) {
            g.setColor(new Color(255, 205, 40));
            g.fillOval(bonus.x * CELL + 2, bonus.y * CELL + 2, CELL - 4, CELL - 4);
            g.setColor(new Color(255, 245, 170));
            g.fillOval(bonus.x * CELL + 7, bonus.y * CELL + 7, CELL - 14, CELL - 14);
        }

        // snake
        int n = snake.size();
        for (int i = n - 1; i >= 0; i--) {
            Point p = snake.get(i);
            float t = n <= 1 ? 0f : (float) i / (n - 1);
            Color c = i == 0 ? new Color(130, 255, 150)
                    : new Color(60, (int) (200 - 70 * t), (int) (95 - 30 * t));
            g.setColor(c);
            g.fillRoundRect(p.x * CELL + 1, p.y * CELL + 1, CELL - 2, CELL - 2, 9, 9);
        }
        drawEyes(g);

        g.translate(0, -HUD);
    }

    void drawEyes(Graphics2D g) {
        Point h = snake.getFirst();
        int px = h.x * CELL, py = h.y * CELL;
        int e1x, e1y, e2x, e2y;
        if (dx != 0) { // moving horizontally
            int ex = dx > 0 ? px + 15 : px + 6;
            e1x = ex; e1y = py + 6; e2x = ex; e2y = py + 15;
        } else {
            int ey = dy > 0 ? py + 15 : py + 6;
            e1x = px + 6; e1y = ey; e2x = px + 15; e2y = ey;
        }
        g.setColor(Color.WHITE);
        g.fillOval(e1x, e1y, 5, 5);
        g.fillOval(e2x, e2y, 5, 5);
        g.setColor(Color.BLACK);
        g.fillOval(e1x + 1, e1y + 1, 3, 3);
        g.fillOval(e2x + 1, e2y + 1, 3, 3);
    }

    void drawHud(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 90));
        g.fillRect(0, 0, W, HUD);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 16));
        g.drawString("Score: " + score, 14, 28);
        g.drawString("Level: " + level, 150, 28);
        g.drawString("Mode: " + DIFF_NAME[difficulty], 270, 28);
        g.drawString("Best: " + Math.max(high[difficulty], score), 430, 28);
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.setColor(new Color(200, 205, 215));
        String hint = "P pause  M " + (muted ? "unmute" : "mute") + "  ESC menu";
        g.drawString(hint, W - g.getFontMetrics().stringWidth(hint) - 12, 27);
    }

    void drawMenu(Graphics2D g) {
        // decorative snake along the top
        g.setColor(new Color(60, 190, 90));
        for (int i = 0; i < 12; i++) {
            int x = 40 + i * 24;
            int y = 50 + (int) (Math.sin((animTick + i * 2) / 3.0) * 12);
            g.fillOval(x, y, 20, 20);
        }
        g.setColor(new Color(130, 255, 150));
        g.fillOval(40 + 12 * 24, 50 + (int) (Math.sin((animTick + 24) / 3.0) * 12), 24, 24);

        g.setColor(new Color(130, 255, 150));
        g.setFont(new Font("SansSerif", Font.BOLD, 64));
        centered(g, "SNAKE", 170);

        g.setFont(new Font("SansSerif", Font.PLAIN, 16));
        g.setColor(new Color(200, 205, 215));
        centered(g, "Select difficulty (UP / DOWN), then press ENTER", 215);

        for (int i = 0; i < 3; i++) {
            boolean sel = i == difficulty;
            int y = 270 + i * 52;
            if (sel) {
                g.setColor(new Color(60, 190, 90, 60));
                g.fillRoundRect(W / 2 - 200, y - 28, 400, 42, 14, 14);
                g.setColor(new Color(130, 255, 150));
            } else {
                g.setColor(new Color(160, 165, 180));
            }
            g.setFont(new Font("SansSerif", Font.BOLD, 24));
            centered(g, (sel ? "> " : "") + DIFF_NAME[i] + (sel ? " <" : "") + "   (best " + high[i] + ")", y);
        }

        g.setFont(new Font("SansSerif", Font.PLAIN, 15));
        g.setColor(new Color(255, 220, 120));
        centered(g, DIFF_INFO[difficulty], 440);

        g.setColor(new Color(170, 175, 190));
        g.setFont(new Font("SansSerif", Font.PLAIN, 14));
        centered(g, "Move: Arrows / WASD     Red = +10     Gold = +50 (vanishes!)     Grey = obstacle", 485);
        centered(g, "Every " + FOODS_PER_LEVEL + " foods: next level, faster, more obstacles.     M = mute", 508);
    }

    void overlay(Graphics2D g, String title, String sub) {
        g.setColor(new Color(0, 0, 0, 170));
        g.fillRect(0, HUD, W, BOARD_H);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 46));
        centered(g, title, HUD + BOARD_H / 2 - 5);
        g.setFont(new Font("SansSerif", Font.PLAIN, 18));
        centered(g, "Score: " + score + "    Level: " + level, HUD + BOARD_H / 2 + 30);
        g.setColor(newRecord ? new Color(255, 220, 90) : new Color(200, 205, 215));
        centered(g, sub, HUD + BOARD_H / 2 + 62);
    }

    void centered(Graphics2D g, String s, int y) {
        g.drawString(s, (W - g.getFontMetrics().stringWidth(s)) / 2, y);
    }

    // ---------- input ----------
    void queueDir(int ndx, int ndy) {
        int[] last = dirQueue.isEmpty() ? new int[]{dx, dy} : dirQueue.peekLast();
        boolean opposite = last[0] == -ndx && last[1] == -ndy;
        boolean same = last[0] == ndx && last[1] == ndy;
        if (!opposite && !same && dirQueue.size() < 2) dirQueue.add(new int[]{ndx, ndy});
    }

    @Override
    public void keyPressed(KeyEvent e) {
        int k = e.getKeyCode();

        if (k == KeyEvent.VK_M) {
            muted = !muted;
            return;
        }

        switch (state) {
            case MENU:
                if (k == KeyEvent.VK_UP || k == KeyEvent.VK_W) difficulty = (difficulty + 2) % 3;
                else if (k == KeyEvent.VK_DOWN || k == KeyEvent.VK_S) difficulty = (difficulty + 1) % 3;
                else if (k == KeyEvent.VK_ENTER || k == KeyEvent.VK_SPACE) startGame();
                if (k == KeyEvent.VK_UP || k == KeyEvent.VK_DOWN
                        || k == KeyEvent.VK_W || k == KeyEvent.VK_S) sound(400, 40);
                break;

            case PLAYING:
                if (k == KeyEvent.VK_UP || k == KeyEvent.VK_W) queueDir(0, -1);
                else if (k == KeyEvent.VK_DOWN || k == KeyEvent.VK_S) queueDir(0, 1);
                else if (k == KeyEvent.VK_LEFT || k == KeyEvent.VK_A) queueDir(-1, 0);
                else if (k == KeyEvent.VK_RIGHT || k == KeyEvent.VK_D) queueDir(1, 0);
                else if (k == KeyEvent.VK_P) state = State.PAUSED;
                else if (k == KeyEvent.VK_ESCAPE) state = State.MENU;
                break;

            case PAUSED:
                if (k == KeyEvent.VK_P) state = State.PLAYING;
                else if (k == KeyEvent.VK_ESCAPE) state = State.MENU;
                break;

            case OVER:
                if (k == KeyEvent.VK_SPACE || k == KeyEvent.VK_ENTER) startGame();
                else if (k == KeyEvent.VK_ESCAPE) state = State.MENU;
                break;
        }
    }

    @Override public void keyReleased(KeyEvent e) { }
    @Override public void keyTyped(KeyEvent e) { }

    // ---------- main ----------
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Snake");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            SnakeGame game = new SnakeGame();
            f.add(game);
            f.pack();
            f.setResizable(false);
            f.setLocationRelativeTo(null);
            f.setVisible(true);
            game.requestFocusInWindow();
        });
    }
}