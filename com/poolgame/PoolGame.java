package com.poolgame;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.util.ArrayList;

public class PoolGame extends JPanel {
  public static final Color BG_COLOR = new Color(24, 24, 25);
  public static final Color shadow = new Color(10, 10, 12, 32);
  public static final Color shine = new Color(255, 255, 255, 48);

  private final Main main;

  private Timer updateTimer;
  protected Timer AITimer;
  private final int updateMS = 20;
  private boolean shouldStartTimer = false;

  protected AI AI;
  protected Connection connection = null;

  private final int screenMargin = 80;

  private Point2D.Double lastMouse;

  enum Gamestate {
    game, cuing, spinMenu
  }

  public boolean isSandbox = false;
  public boolean turn = false;
  public boolean isAIOpponent = false;
  private Boolean turn0IsSolid = null;

  private boolean hasScored = false;

  private Gamestate gamestate = Gamestate.cuing;
  private boolean canMoveCue = true, cueMoving = false;
  private Ball cue;
  private final int cueMult = 10, cueSpinMult = 50;
  private Point2D.Double cuePoint;
  private double stickDX, stickDY, stickDrawback, cueSpinX, cueSpinY;
  private final int maxDrawback = 600;

  private ArrayList<Ball> balls;
  
  private ArrayList<Ball> player1Balls = new ArrayList<Ball>();
  private ArrayList<Ball> player2Balls = new ArrayList<Ball>();

  private final double groundFriction = 0.5;
  private final double bounceFriction = 0.7;
  private final double collisionFriction = 0.98;

  static class Vector {
    public double x, y;

    public Vector(double x, double y) {
      this.x = x; this.y = y;
    }

    public Vector proj(Vector onto) {
      return onto.scale(dot(onto) / (onto.x * onto.x + onto.y * onto.y));
    }

    public double dot(Vector b) {
      return x * b.x + y * b.y;
    }

    public Vector add(Vector b) {
      return new Vector(x + b.x, y + b.y);
    }

    public Vector scale(double scalar) {
      return new Vector(x * scalar, y * scalar);
    }
  }

  class Savestate {
    public Gamestate _gamestate;
    public boolean _canMoveCue;
    public boolean _turn;
    public Boolean _turn0IsSolid;
    public ArrayList<Ball> _balls;

    public Savestate() {
      _gamestate = gamestate; _canMoveCue = canMoveCue;
      _balls = cloneList(balls); _turn = turn;
      _turn0IsSolid = turn0IsSolid;
    }

    public Savestate(Gamestate _gamestate, boolean _canMoveCue, boolean _turn, Boolean _turn0IsSolid, ArrayList<Ball> _balls) {
      this._gamestate = _gamestate; this._canMoveCue = _canMoveCue;
      this._turn = _turn; this._turn0IsSolid = _turn0IsSolid;
      this._balls = _balls;
    }

    public void load() {
      gamestate = _gamestate; canMoveCue = _canMoveCue;
      balls = _balls; turn = _turn;
      turn0IsSolid = _turn0IsSolid;
      cue = _balls.get(0);
      cuePoint = new Point2D.Double(cue.x, cue.y);
    }

    public Savestate invert() {
      _turn = !_turn;
      if (_turn0IsSolid != null) _turn0IsSolid = !_turn0IsSolid;
      return this;
    }

    private ArrayList<Ball> cloneList(ArrayList<Ball> list) {
      ArrayList<Ball> clonedList = new ArrayList<Ball>(list.size());
      for (Ball ball : list) {
        clonedList.add(new Ball(ball));
      }
      return clonedList;
  }
  }

  class Ball {
    public static final int defaultR = 40;
    public int R = defaultR;
    public final double vThreshold = 10;
    private final double spinRange = 1.5, spinMult = 30;

    public final Color color;
    public final boolean isStriped;
    public final int number;
    public double x, y, vx, vy, rotX, rotY, spinX, spinY;
    protected boolean scored;

    public Ball(Color color, boolean isStriped, int number, int initX, int initY) {
      this.color = color; this.isStriped = isStriped; this.number = number;
      x = initX; y = initY; scored = false;
    }

    protected Ball(Color color, boolean isStriped, int number, double x, double y, double vx, double vy, double spinX, double spinY, double rotX, double rotY, boolean scored) {
      this.color = color; this.isStriped = isStriped; this.number = number;
      this.x = x; this.y = y; this.vx = vx; this.vy = vy;
      this.spinX = spinX; this.spinY = spinY; this.rotX = rotX; this.rotY = rotY;
      this.scored = scored;
    }

    protected Ball(Ball b) {
      color = b.color; isStriped = b.isStriped; number = b.number;
      x = b.x; y = b.y; vx = b.vx; vy = b.vy; rotX = b.rotX; rotY = b.rotY;
      spinX = b.spinX; spinY = b.spinY; scored = b.scored;
    }

    public Ball cloneWith(int x, int y) {
      return new Ball(color, isStriped, number, x, y);
    }

    protected Ball setR(int R) {
      this.R = R;
      return this;
    }

    public void fixVelocities() {
      if (Math.abs(vx) < vThreshold) vx = 0;
      if (Math.abs(vy) < vThreshold) vy = 0;
    }

    public Ball update(double dt) {
      if (scored) return null;

      Ball collided = null;

      for (int i = 0; i < Math.max(Math.abs(vx * dt), Math.abs(vy * dt)); i++) {
        if (i < Math.abs(vx * dt)) {
          // move x
          final double dx = clamp(Math.abs(vx * dt) - i) * Math.signum(vx);
          x += dx;
          Ball collide = getBallColliding();
          if (collide != null) {
            x -= dx;
            collideWith(collide);
            collided = collide;
          } else if (isWallColliding()) {
            x -= dx;
            vx *= -bounceFriction;
            vy *= bounceFriction;
            addSpin();
          }
        }
        if (i < Math.abs(vy * dt)) {
          // move y
          final double dy = clamp(Math.abs(vy * dt) - i) * Math.signum(vy);
          y += dy;
          Ball collide = getBallColliding();
          if (collide != null) {
            y -= dy;
            collideWith(collide);
            collided = collide;
          } else if (isWallColliding()) {
            y -= dy;
            vx *= bounceFriction;
            vy *= -bounceFriction;
            addSpin();
          }
        }
      }

      final double vToSpinFactor = 0.0002;
      spinX += vx * vToSpinFactor;
      spinY += vy * vToSpinFactor;

      rotX = overflow(rotX + spinX, -R*spinRange, R*spinRange);
      // final double rotYCap = R*spinRange*Math.cos(this.rotX * (Math.PI/2) / (R * spinRange));
      rotY = overflow(rotY + spinY, -R*spinRange, R*spinRange);

      applyFriction(dt);

      // Check if scored
      if (isBorderColliding()) {
        System.out.println("Scored the " + (number == -1 ? "cue" : number) + "-ball! (" + (isStriped ? "striped" : "solid") + ")");
        scored = true;
      }

      return collided;
    }

    public void addSpin() {
      final double mag = Math.sqrt(vx * vx + vy * vy);
      vx += spinX * spinMult;
      vy += spinY * spinMult;
      double newMag = Math.sqrt(vx * vx + vy * vy);
      if (newMag > mag) {
        vx *= (mag / newMag);
        vy *= (mag / newMag);
      }
      
      spinX *= bounceFriction;
      spinY *= bounceFriction;
    }

    public boolean isBorderColliding() {
      return Math.abs(x) > 600-R|| Math.abs(y) > 1200-R;
    }

    public boolean isWallColliding() {
      return (Math.abs(x) > 600-R-R && Math.abs(y) < 1200-R*2.5 && Math.abs(y) > R) || (Math.abs(y) > 1200-R-R && Math.abs(x) < 600-R*2.5);
    }

    public Ball getBallColliding() {
      for (Ball ball : balls) {
        if (ball == this) continue;
        if (distanceTo(ball) < 2 * R) return ball;
      }
      return null;
    }

    private void collideWith(Ball b) {
      // addSpin();

      final Vector normal = new Vector(b.x - x, b.y - y);
      final Vector tangent = new Vector(normal.y, -normal.x);

      final Vector n = new Vector(vx, vy).proj(normal).scale(collisionFriction);
      final Vector t = new Vector(vx, vy).proj(tangent);
      final Vector bn = new Vector(b.vx, b.vy).proj(normal).scale(collisionFriction);
      final Vector bt = new Vector(b.vx, b.vy).proj(tangent);

      vx = t.x + bn.x;
      vy = t.y + bn.y;

      b.vx = bt.x + n.x;
      b.vy = bt.y + n.y;
    }

    // public Vector getAnchoredVector(Ball b) {
    //   final double angle = Math.atan2(vy, vx);
    //   final double angleTo = Math.atan2(b.y - y, b.x - x);
    //   final double vMag = Point.distance(0, 0, vx, vy);
    //   // TODO: include b's velocity
    //   final double tMag = Point.distance(x, y, b.x, b.y);
    //   final double fMag = Math.cos(angle - angleTo) * Math.pow(vMag, 0.99) / tMag;
    //   return new Vector((b.x - x) * fMag, (b.y - y) * fMag);
    // }

    public double distanceTo(Ball b) {
      return Point.distance(x, y, b.x, b.y);
    }

    private void applyFriction(double dt) {
      vx *= Math.pow(groundFriction, dt);
      vy *= Math.pow(groundFriction, dt);
      spinX *= Math.pow(groundFriction/2, dt);
      spinY *= Math.pow(groundFriction/2, dt);
    }

    public static double clamp(double d) {
      return d > 1 ? 1 : (d < -1 ? -1 : d);
    }

    public static double clamp(double d, double min, double max) {
      return d > max ? max : (d < min ? min : d);
    }

    public static double overflow(double d, double min, double max) {
      final double mod = (d - min) % (max - min);
      if (mod < 0) return max + mod;
      return mod + min;
    }

    public void draw(Graphics2D g2) {
      if (scored) return;

      g2.setColor(shadow);
      g2.fillOval((int)x-R, (int)y-R-1, 2*R+10, 2*R+10);

      g2.setColor(color);
      g2.fillOval((int)x-R, (int)y-R, 2*R, 2*R);

      g2.setClip(new Ellipse2D.Double(x-R, y-R, 2*R, 2*R));

      if (isStriped) {
        g2.setColor(new Color(240, 240, 240));
        g2.fillRect((int)x-R, (int)y-R, 16, R*2);
        g2.fillRect((int)x+R-16, (int)y-R, 16, R*2);
      }

      if (number > 0) {
        g2.setColor(new Color(240, 240, 240));
        g2.fillOval((int)(x+rotX-R/2), (int)(y+rotY-R/2), R, R);
  
        g2.setColor(Color.BLACK);
        g2.setFont(getFont().deriveFont((float)R * 25 / 40));
        final String num = Integer.toString(number);
        final int width = g2.getFontMetrics().stringWidth(num);
        g2.drawString(num, (int)(x+rotX-width/2), (int)(y+rotY+12));
      }

      g2.setColor(number == -1 ? Color.WHITE : shine);
      g2.fillOval((int)x-R, (int)y-R, 20, 20);

      g2.setColor(new Color(255, 255, 255, 48));
      g2.fillOval((int)x-R-1, (int)y-R-1, 2*R-10, 2*R-10);

      g2.setClip(null);
    }
  }

  public PoolGame(Main main, boolean isSandbox, boolean isAIOpponent) {
    this.main = main;
    this.isSandbox = isSandbox;
    this.isAIOpponent = isAIOpponent;

    setBackground(BG_COLOR);
    setPreferredSize(new Dimension(600, 900));
    // game dimentions: 1200x2400

    initGame();
    
    final Mouse mouse = new Mouse();
    addMouseListener(mouse);
    addMouseMotionListener(mouse);

    updateTimer = new Timer(updateMS, new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        update();
      }
    });
    updateTimer.setRepeats(true);
    // updateTimer.start();

    AI = new AI();
    AITimer = new Timer(updateMS, new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        AI.update(updateMS / 1000.0);
      }
    });
    AITimer.setRepeats(true);
  }

  public void startIfNeeded() {
    if (shouldStartTimer) updateTimer.start();
  }

  public void update() {
    // removed scored balls
    for (int i = balls.size() - 1; i > 0; i--) {
      if (balls.get(i).scored) {
        if (turn0IsSolid != null) {
          if (turn0IsSolid == (turn == balls.get(i).isStriped))
            hasScored = true;
        } else {
          turn0IsSolid = turn == balls.get(i).isStriped;
          hasScored = true;
        }
        if (turn0IsSolid == balls.get(i).isStriped) {
          player2Balls.add(balls.get(i).cloneWith(
            760 + (player2Balls.size())/4 * 110,
            1220 - (player2Balls.size()) % 4 * 110
          ).setR(Ball.defaultR * 5 / 4));
        } else {
          player1Balls.add(balls.get(i).cloneWith(
            -760 - (player1Balls.size())/4 * 110,
            1220 - (player1Balls.size()) % 4 * 110
          ).setR(Ball.defaultR * 5 / 4));
        }
        balls.remove(i);
      }
    }

    boolean allStopped = true;
    for (Ball ball : balls) {
      if (ball == cue && cue.scored) continue;
      ball.update(updateMS / 1000.0);
      ball.fixVelocities();
      if (ball.vx != 0 || ball.vy != 0) allStopped = false;
    }

    if (allStopped) {
      // CUE BALL SCRATCHED
      if (cue.scored) {
        cue.x = 0;
        cue.y = 600;
        cue.scored = false;
        cuePoint = new Point2D.Double(0, 600);
        canMoveCue = true;
        gamestate = Gamestate.cuing;
        updateTimer.stop();

        turn = !turn; // next turn
        if (AI != null) AI.stick = null;
        if (turn && !isSandbox && isAIOpponent) AITimer.start();

        hasScored = false;
        repaint();
        return;
      }

      cuePoint = new Point2D.Double(cue.x, cue.y);
      gamestate = Gamestate.cuing;
      updateTimer.stop();

      // game rules
      if (!isSandbox) {
        if (!hasScored) turn = !turn; // next turn
        if (turn && isAIOpponent) AITimer.start();
      }

      hasScored = false;
    }

    repaint();
  }

  private boolean isUserTurn() {
    return isSandbox || !turn || !isAIOpponent;
  }

  class Mouse implements MouseListener, MouseMotionListener {
    public void mouseMoved(MouseEvent e) {
      if (!isUserTurn()) return;
      final Point2D.Double mouse = getMousePoint(e);
      // final int where = whereClicked(mouse);
      if (gamestate == Gamestate.cuing) {
        lastMouse = mouse;
        final double dist = mouse.distance(cue.x, cue.y);
        stickDX = (cue.x - mouse.getX()) / dist;
        stickDY = (cue.y - mouse.getY()) / dist;
      }
      repaint();
    }
    public void mouseDragged(MouseEvent e) {
      if (!isUserTurn()) return;
      final Point2D.Double mouse = getMousePoint(e);
      final int where = whereClicked(mouse, e.getPoint());
      if (gamestate == Gamestate.cuing && where == 0 && !cueMoving) {
        final double dist = lastMouse.distance(mouse);
        stickDrawback = Math.min(dist, maxDrawback);
      } else if (gamestate == Gamestate.spinMenu && where == 1) {
        setCueSpin(mouse);
      } else if (gamestate == Gamestate.cuing && cueMoving) {
        Point2D.Double cueSave = new Point2D.Double(cue.x, cue.y);
        cue.x = mouse.x; cue.y = mouse.y;
        if (cue.getBallColliding() != null || cue.isBorderColliding() || cue.isWallColliding()) {
          cue.x = cueSave.x; cue.y = cueSave.y;
        } else {
          cuePoint = new Point2D.Double(cue.x, cue.y);
        }
      }
      repaint();
    }
    public void mouseClicked(MouseEvent e) {
      if (!isUserTurn()) return;
      final Point2D.Double mouse = getMousePoint(e);
      final int where = whereClicked(mouse, e.getPoint());
      if (gamestate == Gamestate.cuing && where == 1) {
        gamestate = Gamestate.spinMenu;
      }
    }
    public void mousePressed(MouseEvent e) {
      if (!isUserTurn()) return;
      final Point2D.Double mouse = getMousePoint(e);
      final int where = whereClicked(mouse, e.getPoint());
      if (gamestate == Gamestate.spinMenu && where == 1) {
        setCueSpin(mouse);
        repaint();
      } else if (gamestate == Gamestate.cuing && where == 3) {
        cueMoving = true;
      }
    }
    public void mouseReleased(MouseEvent e) {
      final Point2D.Double mouse = getMousePoint(e);
      final int where = whereClicked(mouse, e.getPoint());
      if (where == 4) { // HOME MENU
        shouldStartTimer = updateTimer.isRunning();
        updateTimer.stop();
        main.goToHome();
      }
      if (!isUserTurn()) return;
      lastMouse = mouse;
      if (gamestate == Gamestate.cuing && stickDrawback > 0 && where == 0 && !cueMoving) {
        // player shoots!
        if (connection != null && connection.outputWriter != null) {
          System.out.println("Sending packet to server!!!");
          connection.outputWriter.println(new Connection.Packet(
            new Point2D.Double(stickDX, stickDY),
            new Point2D.Double(cueSpinX, cueSpinY),
            stickDrawback
          ));
        }
        release();
        AI.stick = null;
      } else if (gamestate == Gamestate.cuing & where != 0) {
        stickDrawback = 0;
      } else if (gamestate == Gamestate.spinMenu && where != 1) {
        gamestate = Gamestate.cuing;
      }
      cueMoving = false;
      repaint();
    }
    public void mouseEntered(MouseEvent e) {}
    public void mouseExited(MouseEvent e) {
      if (!isUserTurn()) return;
      stickDrawback = 0;
      cueMoving = false;
      repaint();
    }

    // normal = 0 | cueSpin = 1 | cancel = 2 | cue = 3 | home = 4
    public int whereClicked(Point2D.Double mouse, Point originalMouse) {
      //home
      if (originalMouse.distance(getWidth()-28, 28) <= 28) return 4;
      // cueSpin
      if (mouse.distance(gamestate == Gamestate.spinMenu ? 0 : 840, 0) <= (gamestate == Gamestate.spinMenu ? 200 : 110)) return 1;
      if (mouse.x >= -930 && mouse.x < -770 && mouse.y >= -900 && mouse.y <= -740) return 2;
      if (canMoveCue && mouse.distance(cue.x, cue.y) <= cue.R + 5) return 3;
      return 0;
    }

    private void setCueSpin(Point2D.Double mouse) {
      cueSpinX = mouse.x;
      cueSpinY = mouse.y;
      final double mag = Math.max(Math.sqrt(cueSpinX * cueSpinX + cueSpinY * cueSpinY), 160);
      cueSpinX /= mag;
      cueSpinY /= mag;
    }
  }

  private void release() {
    cue.vx = -stickDX * stickDrawback * cueMult;
    cue.vy = -stickDY * stickDrawback * cueMult;
    final double theta = Math.atan2(cue.vx, -cue.vy);
    cue.spinX = Math.cos(theta) * cueSpinX + Math.sin(theta) * cueSpinY;
    cue.spinY = Math.cos(theta) * cueSpinY + Math.sin(theta) * cueSpinX;
    cue.spinX *= cueSpinMult * ((double)stickDrawback / maxDrawback);
    cue.spinY *= cueSpinMult * ((double)stickDrawback / maxDrawback);

    if (canMoveCue) canMoveCue = false;

    updateTimer.start();

    gamestate = Gamestate.game;
    stickDrawback = 0; cueSpinX = 0; cueSpinY = 0;
  }

  public class Multiplayer extends AI {
    public Multiplayer() {
      setDrawbackSpeed(800);
    }

    public void calculatePoint() {}
  }

  public class AI {
    public Point2D.Double stick = null;
    double targetDrawback = 0, sX = 0, sY = 0;
    Ball eightBall = null;

    private static int drawbackSpeed = 400;
    protected void setDrawbackSpeed(int speed) {
      drawbackSpeed = speed;
    }

    public void setShot(Point2D.Double stick, Point2D.Double spin, double drawback) {
      System.out.println("Setting shot... stick=" + stick + ", d" + drawback);
      this.stick = stick;
      stickDX = stick.x; stickDY = stick.y;
      cueSpinX = spin.x; cueSpinY = spin.y;
      targetDrawback = drawback;
    }

    public void update(double dt) {
      if (stick != null && (stickDX != stick.x || stickDY != stick.y)) {
        stickDX = stick.x;
        stickDY = stick.y;
      }
      if (stick == null) calculatePoint();
      if (stick == null) return;

      gamestate = Gamestate.cuing;

      // drawback
      if (stickDrawback < targetDrawback) {
        stickDrawback += drawbackSpeed * dt;
        if (stickDrawback >= targetDrawback) {
          stickDrawback = targetDrawback;
          release();
          stick = null;
          AITimer.stop();
        }
      }

      repaint();
    }

    public void calculatePoint() {
      if (eightBall != null) {
        Double d = bestShot(eightBall);
        targetDrawback = maxDrawback * (d == null ? 0.75 : d);
        return;
      }
      Ball eight = null; boolean ballsLeft = false;
      for (Ball b : balls) {
        if (b == cue) continue;
        if (b.number == 8) eight = b;
        if (turn0IsSolid != null && b.isStriped != turn0IsSolid) continue;
        if (b.number != 8) ballsLeft = true;
        Double d = bestShot(b);
        if (d != null) {
          targetDrawback = maxDrawback * d;
          return;
        }
      }
      if (!ballsLeft && eight != null) eightBall = eight;
      AAA();
      targetDrawback = maxDrawback * 0.9;
    }

    // TODO: fix
    void AAA() {
      int safe = 0;
      System.out.println("--> Last Resort! Calling AAA()");

      // arbitrary values !!!!
      for (double angle = 0; angle <= 12; angle = angle+0.22) {
        safe++;
        if (checkSafe(safe, 100000)) return;

        stick = new Point2D.Double(Math.sin(angle), Math.cos(angle));
        stickDX = stick.x; stickDY = stick.y;
        // cueSpinY = 1;
        Savestate save = new Savestate(); // save state !!!
        stickDrawback = maxDrawback * 0.9;
        release();
        updateTimer.stop(); // update manually

        // test sticking
        while (true) {
          safe++;
          if (checkSafe(safe, 100000)) return;

          boolean allStopped = true, collided = false;
          for (Ball b : balls) {
            safe++;
            if (checkSafe(safe, 100000)) return;
            if (b == cue) {
              final Ball collide = b.update(0.02);
              if (collide != null && (turn0IsSolid == null || collide.isStriped == turn0IsSolid)) {
                System.out.println("Collided with #" + collide.number + ", " + (collide.isStriped ? "striped" : "solid"));
                collided = true;
              }
            } else b.update(0.02); // constant can be changed
            b.fixVelocities();
            if (b.scored) { b.vx = 0; b.vy = 0; }
            if (!b.scored && (b.vx != 0 || b.vy != 0)) allStopped = false;
          }
          if (allStopped || collided) {
            if (collided) System.out.println("  Found one that collided! (θ = " + angle + ")");
            save.load();
            // if (cue.scored) cueSpinY = -1; //debug
            if (collided) return;
            else break;
          }
        }
      }
    }

    boolean checkSafe(int safe, int limit) {
      if (safe > limit) {
        System.out.println("Loop safe check exceeded " + limit + "!");
        // Thread.dumpStack();
        return true;
      }
      return false;
    }

    Double bestShot(Ball b) {
      for (int y = 1200 - (int)(2.8*b.R); y >= -1200 + 2.8*b.R; y += -1200 + 2.8*b.R) {
        loop: for (int x = -600 + (int)(2.8*b.R); x <= 600 - 2.8*b.R; x += 600 - 2.8*b.R) {
          if (x == 0 && y == 0) continue;

          // calculate shot
          double dx = x - b.x, dy = y - b.y;
          final double mag = Math.sqrt(dx * dx + dy * dy) * 1.05;
          dx /= mag; dy /= mag;

          Point2D.Double shot = new Point2D.Double(b.x - dx * b.R * 2, b.y - dy * b.R * 2);

          // TODO: fix
          // check for clear path
          cuePoint = new Point2D.Double(cue.x, cue.y);
          while (shot.distanceSq(cue.x, cue.y) > b.R*b.R*4 && !cue.isWallColliding()) {
            cue.x += Math.signum(shot.x - cue.x) * b.R;
            cue.y += Math.signum(shot.y - cue.y) * b.R;
            if (cue.getBallColliding() != null) {
              cue.x = cuePoint.x; cue.y = cuePoint.y;
              continue loop;
            }
          }
          cue.x = cuePoint.x; cue.y = cuePoint.y;

          if (cuePoint.distanceSq(b.x, b.y) < cuePoint.distanceSq(shot))
            continue;
          
          shot = new Point2D.Double(cue.x - shot.x, cue.y - shot.y);
          if (tryShot(shot, b, 0.4) != null) return 0.4;
          if (tryShot(shot, b, 0.7) != null) return 0.7;
          if (tryShot(shot, b, 0.2) != null) return 0.2;
        }
      }
      return null;
    }

    Double tryShot(Point2D.Double shot, Ball target, double drawback) {
      stick = shot;
      normalize();
      Savestate save = new Savestate(); // save state !!!
      stickDX = stick.x; stickDY = stick.y;
      stickDrawback = maxDrawback * drawback;
      release();
      updateTimer.stop(); // update manually
      
      // stickDX = stick.x; stickDY = stick.y;
      // repaint();
      // System.out.println("[" + stickDX + ", " + stickDY + "] aim " + target.number);
      // try {
      //   java.util.Scanner scan = new java.util.Scanner(System.in);
      //   scan.nextLine();
      //   scan.close();
      // } catch (Exception e) {

      // }
      
      // if (turn) return true;

      while (true) {
        boolean allStopped = true;
        int score = 0;
        for (Ball b : balls) {
          b.update(0.02); // constant can be changed
          b.fixVelocities();
          if (b.scored) { b.vx = 0; b.vy = 0; }
          if (!b.scored && (b.vx != 0 || b.vy != 0)) allStopped = false;
          
          if (b == cue || !b.scored) continue;
          if (turn0IsSolid != null) {
            if (b == eightBall) score += 1;
            else score += turn0IsSolid == (turn == b.isStriped) ? 1 : -1;
          } else {
            turn0IsSolid = turn == b.isStriped;
            score += 1;
          }
        }
        if (allStopped) {
          if (score != 0) System.out.println("  #" + target.number + " Scored -> " + score + (score > 0 ? "\t\t!!!" : ""));
          save.load();
          if (cue.scored) cueSpinY = -1; //debug
          return score > 0 ? drawback : null;
        }
      }
    }

    void normalize() {
      final double mag = stick.distance(0, 0);
      stick.x /= mag; stick.y /= mag;
    }
  }

  public final Color table = new Color(45, 110, 100);
  public final Color tableDark = new Color(30, 95, 85);
  public final Color tableBorder = new Color(100, 60, 30);

  public void paintComponent(Graphics g) {
    super.paintComponent(g);

    Graphics2D g2 = ((Graphics2D)g.create());
    g2.setRenderingHints(new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON));

    final double scale = getScaleFactor();

    g2.translate(getWidth()/2, getHeight()/2);
    g2.scale(scale, scale);

    // Draw Table
    g2.setColor(tableBorder);
    g2.fillRoundRect(-600 - cue.R*2, -1200 - cue.R*2, 1200 + cue.R*4, 2400 + cue.R*4, 80, 80);
    g2.setColor(tableDark);
    g2.fillRect(-600, -1200, 1200, 2400);
    g2.setColor(table);
    g2.fillRect((int)(-610 + cue.R*1.5), (int)(-1210 + cue.R*1.5), (int)(1220 - cue.R*3), (int)(2420 - cue.R*3));
    
    g2.setColor(table);
    for (int x : new int[] {-600, 600 - cue.R*3}) {
      g2.fillRect(x, 1200 - cue.R*3, cue.R * 3, cue.R * 3);
      g2.fillRect(x, (int)(-cue.R*1.5), cue.R * 3, cue.R * 3);
      g2.fillRect(x, -1200, cue.R * 3, cue.R * 3);
    }

    // Draw Holes
    for (int y : new int[] {1200, 0, -1200}) {
      g2.setColor(Color.BLACK);
      g2.fillOval((int)(-600 - cue.R * 1.5), (int)(y - cue.R * 1.5), cue.R * 3, cue.R * 3);
      g2.fillOval((int)(600 - cue.R * 1.5), (int)(y - cue.R * 1.5), cue.R * 3, cue.R * 3);
    }

    // Draw Balls
    for (Ball ball : balls) {
      ball.draw(g2);
    }

    drawScoredBalls(g2);

    // Draw Cue Stick
    if (stickDX != 0 || stickDY != 0) {
      final int a = (gamestate != Gamestate.game) ? 100 : 0;

      g2.setStroke(new BasicStroke(40, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g2.setColor(shadow);
      g2.drawLine((int)(cuePoint.x + stickDX * (a + stickDrawback)) + 10, (int)(cuePoint.y + stickDY * (a + stickDrawback)) + 20, (int)(cuePoint.x + stickDX * (1200 + stickDrawback)) + 30, (int)(cuePoint.y + stickDY * (1200 + stickDrawback)) + 6);

      g2.setStroke(new BasicStroke(30, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g2.setColor(new Color(220, 130, 100));
      g2.drawLine((int)(cuePoint.x + stickDX * (a + stickDrawback)), (int)(cuePoint.y + stickDY * (a + stickDrawback)), (int)(cuePoint.x + stickDX * (1200 + stickDrawback)), (int)(cuePoint.y + stickDY * (1200 + stickDrawback)));

      g2.setColor(new Color(240, 240, 240));
      g2.drawLine((int)(cuePoint.x + stickDX * (a + stickDrawback)), (int)(cuePoint.y + stickDY * (a + stickDrawback)), (int)(cuePoint.x + stickDX * (a + 40 + stickDrawback)), (int)(cuePoint.y + stickDY * (a + 40 + stickDrawback)));
    }

    drawHints(g2);

    drawGUI(g2);

    // For multiplayer
    if (isAIOpponent && AI != null && AI.getClass() == Multiplayer.class) {
      if (turn && gamestate == Gamestate.cuing && AI.stick == null) {
        g2.setColor(new Color(0, 0, 0, 64));
        g2.setStroke(new BasicStroke(1));
        g2.fillRect(-500, -200, 1000, 400);
        g2.setColor(Color.WHITE);
        g2.setFont(getFont().deriveFont(Font.BOLD, 48));
        g2.drawString("Waiting for opponent...", -240, -12);
      }
    }

    g2.dispose();

    // home button
    g.drawImage(getImage("com/poolgame/images/home.png"), getWidth()-56, 8, this);

    // DEBUG:
    g.setColor(Color.WHITE);
    g.drawString("Gamestate: " + gamestate, 10, 32);
    g.drawString("Turn: " + (turn ? 1 : 0), 10, 44);
    g.drawString("isUserTurn(): " + isUserTurn(), 10, 56);
    g.drawString("You are " + (turn0IsSolid == null ? "any" : (turn0IsSolid ? "solids" : "stripes")), 10, 68);
    g.drawString("Game Timer: " + (updateTimer.isRunning() ? "running" : "idle"), 10, 92);
    g.drawString("AI Timer: " + (AITimer.isRunning() ? "running" : "idle"), 10, 104);
  }

  public void drawHints(Graphics2D g2) {
    if ((gamestate != Gamestate.cuing && gamestate != Gamestate.spinMenu) || (stickDX == 0 && stickDY == 0) || cueMoving) return;

    // Find first collision
    int save = 0;
    while (cue.getBallColliding() == null && !cue.isWallColliding() && !cue.isBorderColliding() && save < 100000) {
      cue.x -= stickDX;
      cue.y -= stickDY;
      save++;
    }

    g2.setStroke(new BasicStroke(8, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    g2.setColor(Color.WHITE);
    g2.drawLine((int)cuePoint.x, (int)cuePoint.y, (int)(cue.x + stickDX * cue.R), (int)(cue.y + stickDY * cue.R));
    final int R = cue.R - 10;
    g2.drawOval((int)cue.x - R, (int)cue.y - R, R * 2, R * 2);

    Ball collide = cue.getBallColliding();
    if (collide != null) {
      g2.drawLine((int)collide.x, (int)collide.y, (int)(collide.x + 2*(collide.x - cue.x)), (int)(collide.y + 2*(collide.y - cue.y)));
    }

    cue.x = cuePoint.x;
    cue.y = cuePoint.y;
  }

  public void drawScoredBalls(Graphics2D g2) {
    for (Ball b : player1Balls) b.draw(g2);
    for (Ball b : player2Balls) b.draw(g2);
  }

  public void drawGUI(Graphics2D g2) {
    // scratch
    if (canMoveCue) {
      g2.setColor(Color.LIGHT_GRAY);
      g2.fillRect((int)cue.x-cue.R-8, (int)cue.y-cue.R-8, 32, 8);
      g2.fillRect((int)cue.x+cue.R-24, (int)cue.y-cue.R-8, 32, 8);
      g2.fillRect((int)cue.x-cue.R-8, (int)cue.y-cue.R, 8, 24);
      g2.fillRect((int)cue.x+cue.R, (int)cue.y-cue.R, 8, 24);
      g2.fillRect((int)cue.x-cue.R-8, (int)cue.y+cue.R+8, 32, 8);
      g2.fillRect((int)cue.x+cue.R-24, (int)cue.y+cue.R+8, 32, 8);
      g2.fillRect((int)cue.x-cue.R-8, (int)cue.y+cue.R-16, 8, 24);
      g2.fillRect((int)cue.x+cue.R, (int)cue.y+cue.R-16, 8, 24);
    }

    // spin
    g2.setColor(Color.WHITE);
    if (gamestate == Gamestate.spinMenu)
      g2.fillOval(-200, -200, 400, 400);
    else
      g2.fillOval(740, -100, 200, 200);

    g2.setColor(Color.RED);
    if (gamestate == Gamestate.spinMenu)
      g2.fillOval(-40 + (int)(cueSpinX * 160), -40 + (int)(cueSpinY * 160), 80, 80);
    else
      g2.fillOval(820 + (int)(cueSpinX * 80), -20 + (int)(cueSpinY * 80), 40, 40);
    
    // cue
    g2.setColor(new Color(100, 100, 200));
    RoundRectangle2D rrect = new RoundRectangle2D.Double(-930, -700, 160, 1400, 100, 100);
    g2.fill(rrect);
    g2.setColor(new Color(100, 100, 100));
    final double pixel = stickDrawback / maxDrawback;
    g2.setClip(rrect);
    g2.fillRect(-930, -700 + (int)(1400 * pixel), 160, (int)(1400 * (1-pixel)));
    g2.setClip(null);

    g2.fillRoundRect(-930, -900, 160, 160, 100, 100); // cancel button

    g2.setStroke(new BasicStroke(5, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    g2.setColor(new Color(0, 0, 0, 32));
    for (int y : new int[] {-500, -300, -100, 100, 300, 500}) {
      g2.drawLine(-900, y, -800, y);
    }

    g2.drawLine(-910, -880, -790, -750);
    g2.drawLine(-910, -750, -790, -880);

    g2.setStroke(new BasicStroke(40, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    g2.setColor(shadow);
    g2.drawLine(-840, 560 + (int)(stickDrawback*2.27), -840, -650 + (int)(stickDrawback*2.27));

    g2.setStroke(new BasicStroke(30, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    g2.setColor(new Color(220, 130, 100));
    g2.drawLine(-850, 550 + (int)(stickDrawback*2.27), -850, -650 + (int)(stickDrawback*2.27));
    g2.setColor(new Color(240, 240, 240));
    g2.drawLine(-850, -650 + (int)(stickDrawback*2.27), -850, -610 + (int)(stickDrawback*2.27));
  }

  private static final Color
    YELLOW = new Color(240, 200, 0),
    GREEN = new Color(0, 120, 0),
    BLUE = new Color(30, 30, 180),
    ORANGE = new Color(250, 100, 0),
    MAROON = new Color(128, 0, 0),
    PURPLE = new Color(100, 0, 160),
    RED = new Color(200, 0, 0);

  public void resetGame() {
    isSandbox = false;
    hasScored = false;
    turn0IsSolid = null;
    player1Balls = new ArrayList<Ball>();
    player2Balls = new ArrayList<Ball>();

    gamestate = Gamestate.cuing;
    AITimer.stop();
    updateTimer.stop();
    canMoveCue = true;
    cueMoving = false;
    cue = null;

    initGame();

    turn = false;
  }

  public void setIsAIOpponent(boolean isAIOpponent) {
    this.isAIOpponent = isAIOpponent;
    if (isAIOpponent && gamestate == Gamestate.cuing && turn) {
      AITimer.start();
    }
  }

  public void initGame() {
    balls = new ArrayList<Ball>(16);
    cue = new Ball(new Color(240, 240, 240), false, -1, 0, 600);
    cuePoint = new Point2D.Double(cue.x, cue.y);
    balls.add(cue);
    
    // Add balls
    final int[] numbers = {1, 9, 14, 2, 8, 6, 10, 7, 15, 13, 3, 11, 4, 12, 5};
    final Color[] colors = {YELLOW, GREEN, YELLOW, GREEN, Color.BLACK, BLUE, ORANGE, MAROON, MAROON, BLUE, ORANGE, PURPLE, PURPLE, RED, RED};
    final boolean[] stripes = {false, true, true, false, false, false, true, true, false, true, false, true, false, true, false};

    int row = 0, prev = 0;
    for (int i = 0; i < numbers.length; i++) {
      if (i-prev > row) {
        row++;
        prev = i;
      }
      final int sx = -row * Ball.defaultR + (i-prev) * Ball.defaultR*2;
      final int sy = (int)(-row * Ball.defaultR*1.8);
      balls.add(
        new Ball(colors[i], stripes[i], numbers[i], sx, sy - 600)
      );
    }
  }

  public double getScaleFactor() {
    final int width = getWidth() - screenMargin;
    final int height = getHeight() - screenMargin;
    return width > (height * 0.75) ? height / 2400.0 : width / 1800.0;
  }

  public Point2D.Double getMousePoint(MouseEvent e) {
    final double scale = getScaleFactor();
    return new Point2D.Double((e.getX() - getWidth()/2) / scale, (e.getY() - getHeight()/2) / scale);
  }

  static public Image getImage(String pathname) {
    try {
      return ImageIO.read(new File(pathname));
    } catch (Exception e1) {
      try {
        return ImageIO.read(Panel.class.getResource(pathname));
      } catch (Exception e2) {
        // e1.printStackTrace();
        // e2.printStackTrace();
      }
    }
    return null;
  }
}
