package com.poolgame;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class Menu extends JPanel {
  public static final Color BG_COLOR = new Color(24, 24, 25);

  Main main;

  JLabel title;

  JLabel createServerLabel;
  JButton createServerButton;

  JLabel joinServerLabel;
  JTextField joinServerTextField;
  JButton joinServerButton;

  JLabel playLabel;
  JButton play;
  
  public Menu(Main main) {
    this.main = main;
    setBackground(BG_COLOR);
    setPreferredSize(new Dimension(600, 900));

    Box box = new Box(BoxLayout.Y_AXIS);
    add(box);

    title = new JLabel("MENU");
    title.setForeground(Color.WHITE);
    title.setFont(getFont().deriveFont(Font.BOLD, 48));
    box.add(title);

    createServerLabel = new JLabel("Create New Server");
    createServerLabel.setForeground(Color.WHITE);
    box.add(createServerLabel);

    createServerButton = new JButton("Create");
    createServerButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        main.game.connection = new Connection(main.game);
        new Thread(new Runnable() {
          public void run() {
            main.game.connection.createNewServer();
          }
        }).start();
        main.play();
      }
    });
    box.add(createServerButton);

    joinServerLabel = new JLabel("Join Server: (type IP address below)");
    joinServerLabel.setForeground(Color.WHITE);
    box.add(joinServerLabel);

    joinServerTextField = new JTextField(12);
    box.add(joinServerTextField);

    joinServerButton = new JButton("Join");
    joinServerButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        main.game.connection = new Connection(main.game);
        new Thread(new Runnable() {
          public void run() {
            main.game.connection.joinServer(joinServerTextField.getText());
          }
        }).start();;
        main.play();
      }
    });
    box.add(joinServerButton);

    playLabel = new JLabel("Continue Singleplayer Game");
    playLabel.setForeground(Color.WHITE);
    box.add(playLabel);

    play = new JButton("Play Singleplayer");
    play.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        main.play();
      }
    });
    box.add(play);
  }

  public void paintComponent(Graphics g) {
    super.paintComponent(g);
  }
}
