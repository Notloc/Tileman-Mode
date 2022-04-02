package com.tileman.multiplayer;

import com.tileman.Util;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

public class TilemanClient extends Thread {

    @Getter @Setter
    private String hostname;
    private int portNumber = 7777;

    public TilemanClient(String hostname, int portNumber) {
        this.hostname = hostname;
        this.portNumber = portNumber;
    }

    @Override
    public void run() {
        System.out.println("Launching MP client");
        if (Util.isEmpty(hostname)) {
            return;
        }

        try(Socket socket = new Socket(hostname, portNumber)) {
            ObjectOutputStream outStream = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream inStream = new ObjectInputStream(socket.getInputStream());

            outStream.writeObject(new TilemanPacket("Sup?"));
            TilemanPacket response = (TilemanPacket)inStream.readObject();

            print("Received [" + response.message + "]");

        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private static void print(String string) {
        System.out.println("Client: " + string);
    }
}
