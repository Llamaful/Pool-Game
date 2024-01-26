package com.poolgame;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

public class Main {
  public static void main(String[] args) {
    new Main(args).play();
  }

  DisplayFrame frame;
  PoolGame game;
  Menu menu;

  public Main(String[] args) {
    try {
      for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
          /*
          if ("Nimbus".equals(info.getName())) {
              javax.swing.UIManager.setLookAndFeel(info.getClassName());
              break;
          }
          */
          if ("Mac OS X".equals(info.getName())) {
              javax.swing.UIManager.setLookAndFeel(info.getClassName());
              break;
          }
      }
  } catch (ClassNotFoundException ex) {
      java.util.logging.Logger.getLogger(Main.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
  } catch (InstantiationException ex) {
      java.util.logging.Logger.getLogger(Main.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
  } catch (IllegalAccessException ex) {
      java.util.logging.Logger.getLogger(Main.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
  } catch (javax.swing.UnsupportedLookAndFeelException ex) {
      java.util.logging.Logger.getLogger(Main.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
  }

    game = args.length == 0 ? new PoolGame(this, false, true) : new PoolGame(this, Boolean.parseBoolean(args[0]), Boolean.parseBoolean(args[1]));
    frame = new DisplayFrame();
    menu = new Menu(this);
  }

  public void play() {
    frame.remove(menu);
    game.startIfNeeded();
    frame.add(game);
    frame.setVisible(true);
    frame.repaint();
  }

  public void goToHome() {
    frame.remove(game);
    frame.add(menu);
    frame.setVisible(true);
    frame.repaint();
  }

  class DisplayFrame extends JFrame {
    public static final Color BG_COLOR = new Color(24, 24, 25);
  
    public DisplayFrame() {
      setSize(600, 900);
      setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
      getContentPane().setBackground(BG_COLOR);

      System.setProperty("apple.laf.useScreenMenuBar", "true");
      System.setProperty("apple.awt.application.name", "8-Ball");

      getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
      getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);

      setLayout(new BorderLayout());

      addMenuBar();
    }

    private void addMenuBar() {
      JMenuBar menu = new JMenuBar();

      JMenu game = new JMenu("Game");

      JMenuItem newGame = menuItem("New Game", KeyEvent.VK_N, new AbstractAction() {
        public void actionPerformed(ActionEvent e) {
          Main.this.game.resetGame();
          Main.this.game.repaint();
        }
      });
      game.add(newGame);

      game.addSeparator();

      JCheckBoxMenuItem sandbox = new JCheckBoxMenuItem();
      sandbox.setSelected(Main.this.game.isSandbox);
      sandbox.setAction(new AbstractAction() {
        public void actionPerformed(ActionEvent e) {
          Main.this.game.isSandbox = sandbox.isSelected();
        }
      });
      sandbox.setText("Sandbox Mode");
      game.add(sandbox);

      JCheckBoxMenuItem useAI = new JCheckBoxMenuItem();
      useAI.setSelected(Main.this.game.isAIOpponent);
      useAI.setAction(new AbstractAction() {
        public void actionPerformed(ActionEvent e) {
          Main.this.game.setIsAIOpponent(useAI.isSelected());
        }
      });
      useAI.setText("Play Against AI Opponent");
      game.add(useAI);

      menu.add(game);

      JMenu window = new JMenu("Window");
      JCheckBoxMenuItem floatOnTop = new JCheckBoxMenuItem();
      floatOnTop.setAction(new AbstractAction() {
        public void actionPerformed(ActionEvent e) {
          DisplayFrame.this.setAlwaysOnTop(floatOnTop.isSelected());
        }
      });
      floatOnTop.setText("Float on Top");
      window.add(floatOnTop);
      menu.add(window);

      setJMenuBar(menu);
    }

    @SuppressWarnings("unchecked")
    private <T extends JMenuItem> T menuItem(String text, int key, Action action) {
      T menuItem = (T)(new JMenuItem(text));
      if (key >= 0) action.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(key, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
      menuItem.setAction(action);
      menuItem.setText(text);
      if (key >= 0) menuItem.setMnemonic(key);
      return menuItem;
    }
  }
}
