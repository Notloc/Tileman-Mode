package com.tileman.multiplayer;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.NotImplementedException;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

public class TilemanServer extends Thread {

    private int portNumber;
    private ServerSocket serverSocket;

    public TilemanServer(int portNumber) {
        this.portNumber = portNumber;
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(portNumber);
            handleNewConnections(serverSocket);
            if (!serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            serverSocket = null;
        }
    }

    public void shutdown() {
        try {
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleNewConnections(ServerSocket serverSocket) {
        boolean running = true;
        while (running) {
            try {
                Socket connection = serverSocket.accept();
                startConnectionThread(connection);
            } catch (SocketException e) {
              running = false;
            } catch (IOException e) {}
        }
    }

    private void startConnectionThread(Socket connection) {
        Runnable connectionThread = () -> handleConnection(connection);
        Thread thread = new Thread(connectionThread);
        thread.start();
    }

    private void handleConnection(Socket connection) {
        try {
            ObjectInputStream inputStream = new ObjectInputStream(connection.getInputStream());
            ObjectOutputStream outputStream = new ObjectOutputStream(connection.getOutputStream());

            TilemanPacket packet = (TilemanPacket)inputStream.readObject();
            print("Received [" + packet.message + "]");

            outputStream.writeObject(new TilemanPacket("Nm, you?"));

            connection.close();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private static void print(String string) {
        System.out.println("Server: " + string);
    }
}
