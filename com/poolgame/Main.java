package com.poolgame;
import javax.swing.JFrame;
import java.awt.BorderLayout;
import java.awt.Color;

public class Main {
  public static void main(String[] args) {
    DisplayFrame d = new DisplayFrame();
    PoolGame p = args.length == 0 ? new PoolGame(false, false) : new PoolGame(Boolean.parseBoolean(args[0]), Boolean.parseBoolean(args[1]));
    d.add(p);
    d.setVisible(true);
  }

  static class DisplayFrame extends JFrame {
    public static final Color BG_COLOR = Color.decode("0x181819");
  
    public DisplayFrame() {
      setSize(600, 900);
      setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
      getContentPane().setBackground(BG_COLOR);
      getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
      getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
      // setResizable(false);
      // setAlwaysOnTop(true);
    
      setLayout(new BorderLayout());
    }
  }
}
