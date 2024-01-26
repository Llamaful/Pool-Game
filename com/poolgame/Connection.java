package com.poolgame;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Scanner;

public class Connection {
  private static final int PORT = 4004;

  private final PoolGame game;
  protected PrintWriter outputWriter;
  
  public Connection(PoolGame game) {
    this.game = game;
  }
  
  /*
   * Used to send messages to sockets
   */
  public static class Packet {
    public static enum Type {
      MESSAGE, SHOT, GAME, JOIN, QUIT, ERROR;
    }
    Type type;
    String message;
    Point2D.Double stick, spin;
    double drawback;
    PoolGame.Savestate state;
    public Packet(String message) {
      type = Type.MESSAGE;
      this.message = message;
      stick = null; spin = null; drawback = 0; state = null;
    }
    public Packet(Point2D.Double stick, Point2D.Double spin, double drawback) {
      type = Type.SHOT;
      message = null; state = null;
      this.stick = stick; this.spin = spin; this.drawback = drawback;
    }
    public Packet(PoolGame.Savestate state) {
      type = Type.GAME;
      this.state = state;
      message = null; stick = null; spin = null; drawback = 0;
    }
    public String toString() {
      if (type == Type.SHOT) return type.ordinal()+";"+stick.x+";"+stick.y+";"+spin.x+";"+spin.y+";"+drawback;
      if (type == Type.GAME) return type.ordinal()+";"+stateToString();
      return type.ordinal()+";"+message;
    }
    public String stateToString() {
      if (state == null) return null;
      StringBuilder sb = new StringBuilder();
      sb.append(state._gamestate.ordinal() + ";");
      sb.append(state._canMoveCue + ";");
      sb.append(state._turn + ";");
      sb.append(state._turn0IsSolid + ";");
      for (PoolGame.Ball b : state._balls) {
        sb.append(b.color.getRGB()+","+b.isStriped+","+b.number+","+b.x+","+b.y+","+b.vx+","+b.vy+","+b.spinX+","+b.spinY+","+b.rotX+","+b.rotY+","+b.scored+"|");
      }
      return sb.toString();
    }
  }

  Packet parsePacket(String line) {
    String[] args = line.split(";");
    if (args[0].equals("0")) return new Packet(line.substring(2));
    if (args[0].equals("2")) return new Packet(game.new Savestate(
      PoolGame.Gamestate.values()[Integer.parseInt(args[1])],
      Boolean.parseBoolean(args[2]),
      Boolean.parseBoolean(args[3]),
      args[4].equals("null") ? null : Boolean.parseBoolean(args[4]),
      parseBalls(args[5])
    ).invert());
    return new Packet(
      new Point2D.Double(Double.parseDouble(args[1]), Double.parseDouble(args[2])),
      new Point2D.Double(Double.parseDouble(args[3]), Double.parseDouble(args[4])),
      Double.parseDouble(args[5])
    );
  }

  ArrayList<PoolGame.Ball> parseBalls(String line) {
    String[] args = line.split("\\|");
    ArrayList<PoolGame.Ball> balls = new ArrayList<PoolGame.Ball>();
    for (String s : args) {
      String[] bArgs = s.split(",");
      balls.add(game.new Ball(
        new Color(Integer.parseInt(bArgs[0])),
        Boolean.parseBoolean(bArgs[1]),
        Integer.parseInt(bArgs[2]),
        Double.parseDouble(bArgs[3]),
        Double.parseDouble(bArgs[4]),
        Double.parseDouble(bArgs[5]),
        Double.parseDouble(bArgs[6]),
        Double.parseDouble(bArgs[7]),
        Double.parseDouble(bArgs[8]),
        Double.parseDouble(bArgs[9]),
        Double.parseDouble(bArgs[10]),
        Boolean.parseBoolean(bArgs[11])
      ));
    }
    return balls;
  }

  public void createNewServer() {
    try (ServerSocket serverSocket = new ServerSocket(PORT, 100, InetAddress.getByName("64.251.53.41"))) {
      System.out.println("Server is running on " + serverSocket.toString() + ":" + serverSocket.getLocalPort());
      Socket connectionSocket = serverSocket.accept();

      // Create InputStream to read from the client
      InputStream inputToServer = connectionSocket.getInputStream();
      Scanner inputScanner = new Scanner(inputToServer, "UTF-8");

      // Create OutputStream to send messages to client
      OutputStream outputFromServer = connectionSocket.getOutputStream();
      outputWriter = new PrintWriter(new OutputStreamWriter(outputFromServer, "UTF-8"), true);

      outputWriter.println(new Packet("Connection found!"));

      // Set game opponent to multiplayer
      game.AI = game.new Multiplayer();

      // Send game information
      outputWriter.println(new Packet(game.new Savestate()));

      while (inputScanner.hasNextLine()) {
        String line = inputScanner.nextLine();
        Packet packet = parsePacket(line);
        System.out.println("> Recieved packet: " + packet);
        if (packet.type == Packet.Type.SHOT) {
          game.AI.setShot(packet.stick, packet.spin, packet.drawback);
        }
      }

      inputScanner.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void joinServer(String server) {
    try (Socket serverSocket = new Socket(InetAddress.getByName(server), PORT)) {
      System.out.println("Connected to " + server + ":" + PORT);

      // Set game opponent to multiplayer
      game.AI = game.new Multiplayer();

      // Create InputStream to read from the client
      InputStream inputToServer = serverSocket.getInputStream();
      Scanner inputScanner = new Scanner(inputToServer, "UTF-8");

      // Create OutputStream to send messages to client
      OutputStream outputFromServer = serverSocket.getOutputStream();
      outputWriter = new PrintWriter(new OutputStreamWriter(outputFromServer, "UTF-8"), true);
  
      while (inputScanner.hasNextLine()) {
      String line = inputScanner.nextLine();
        Packet packet = parsePacket(line);
        System.out.println("> Recieved packet: " + packet);
        if (packet.type == Packet.Type.SHOT) {
          game.AI.setShot(packet.stick, packet.spin, packet.drawback);
        } else if (packet.type == Packet.Type.GAME) {
          packet.state.load();
          if (game.turn) game.AITimer.start();
          game.repaint();
        }
      }

      inputScanner.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
