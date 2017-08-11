/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package greetingserver;

// File Name GreetingClient.java
import java.net.*;
import java.io.*;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.simple.*;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class GreetingClient {

   public static JSONObject parseJSON(String json) {
            JSONObject obj2;
            try {
                JSONParser parser = new JSONParser();
                JSONArray a = (JSONArray) parser.parse(json);
                obj2 = (JSONObject) a.get(0);
            } catch (ParseException ex) {
                obj2 = null;
                
            }
            return obj2;
        }
    public static void main(String[] args) {
        String serverName = "localhost";
        int port = 60000;

        try {
         /*   System.out.println("Connecting to " + serverName + " on port " + port);
            Socket client = new Socket(serverName, 60500);
            System.out.println("Just connected to " + client.getRemoteSocketAddress());
            OutputStream outToServer = client.getOutputStream();
            DataOutputStream out = new DataOutputStream(outToServer);
            JSONObject obj = new JSONObject();
            JSONArray list = new JSONArray();
            list.add("60101");
            list.add("60102");
            list.add("60103");
            list.add("60104");
            obj.put("type", "POST");
            obj.put("dht", list);
            out.writeUTF("[" + obj.toJSONString() + "]");

            InputStream inFromServer = client.getInputStream();
            DataInputStream in = new DataInputStream(inFromServer);
            System.out.println(in.readUTF());
*/
       /*    Socket clientt = new Socket(serverName, 60500);
            System.out.println("Just connected to " + clientt.getRemoteSocketAddress());

            OutputStream outToServerr = clientt.getOutputStream();
            DataOutputStream outt = new DataOutputStream(outToServerr);
            JSONObject objj = new JSONObject();

            objj.put("type", "GET");
            objj.put("dht", "1");
            outt.writeUTF("[" + objj.toJSONString() + "]");

            InputStream inFromServerr = clientt.getInputStream();
            DataInputStream inn = new DataInputStream(inFromServerr);
            System.out.println(inn.readUTF());
            
            clientt.close();*/
            
          /* Socket clientt = new Socket(serverName, 60104);
            System.out.println("Just connected to " + clientt.getRemoteSocketAddress());

           OutputStream outToServerr = clientt.getOutputStream();
            DataOutputStream outt = new DataOutputStream(outToServerr);
            JSONObject objj = new JSONObject();
            objj.put("type", "POST");
            objj.put("key", "PRATHAM");
            objj.put("value", "SHAH");
            outt.writeUTF("[" + objj.toJSONString() + "]");
            InputStream inFromServerr = clientt.getInputStream();
            DataInputStream inn = new DataInputStream(inFromServerr);            
                System.out.println(inn.readUTF());
            clientt.close();*/
            
            
            Socket socket = new Socket("localhost", 60104);
                    DataOutputStream out = createOutputStream(socket);
                    JSONObject deleteDHT = new JSONObject();
                    deleteDHT.put("type", "DELETE");
                    deleteDHT.put("dht", 60103);
                    out.writeUTF("[" + deleteDHT.toJSONString() + "]");
                    DataInputStream in = createInputStream(socket); 
                    System.out.println(in.readUTF());
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
    static DataOutputStream createOutputStream(Socket socket) throws IOException {
        OutputStream outToServerr = socket.getOutputStream();
        DataOutputStream out = new DataOutputStream(outToServerr);
        return out;
    }

    static DataInputStream createInputStream(Socket socket) throws IOException {
        InputStream outToServerr = socket.getInputStream();
        DataInputStream in = new DataInputStream(outToServerr);
        return in;
    }

}
