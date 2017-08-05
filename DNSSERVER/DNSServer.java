/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package DNSSERVER;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.simple.*;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 *
 * @author Pratham
 */
public class DNSServer extends Thread {

    int port;
    ServerSocket server;
    ArrayList<Integer> dhtServers = new ArrayList<>();

    DNSServer() throws IOException {
        this.port = port;
        ServerSocket CreateServer = new ServerSocket(60500, 100);
        server = CreateServer;
    }

    public void run() {
        Thread listen = new Listen(server);
        listen.start();
    }

    class Listen extends Thread {

        ServerSocket server;

        Listen(ServerSocket server) {
            this.server = server;
        }
        
        public void run() {
            while (true) {
                try {
                    System.out.println("hi");
                    Socket serverSocket = server.accept();
                    Thread processRequest = new communicate(serverSocket);
                    processRequest.start();
                } catch (IOException ex) {
                    Logger.getLogger(Listen.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }

    }

    class communicate extends Thread {

        private Socket socket;

        communicate(Socket socket) {
            this.socket = socket;
        }

        boolean addDHTServers(JSONObject requestJSON) {
            boolean added = true;
            try {
                JSONArray dhtJOSNArray = (JSONArray) requestJSON.get("dht");
                Iterator<String> iterator = dhtJOSNArray.iterator();
                while (iterator.hasNext()) {
                    synchronized (dhtServers) {
                        dhtServers.add(Integer.parseInt((iterator.next())));
                    }
                }
            } catch (Exception e) {
                added = false;
            }
            System.out.println(Arrays.toString(dhtServers.toArray()));
            return added;
        }

        String createResponse(String request) {
            String response = "";
            JSONObject requestJSON = parseJSON(request);
            if (((String) requestJSON.get("type")).equals("POST") && requestJSON.get("dht") != null) {
                boolean dhtAdded = addDHTServers(requestJSON);
                JSONObject obj = new JSONObject();
                if (dhtAdded) {
                    obj.put("success", "1");
                } else {
                    obj.put("success", "0");
                }
                response = "[" + obj.toJSONString() + "]";
            }else if(((String) requestJSON.get("type")).equals("GET")&&requestJSON.get("dht") != null){
                 int n=dhtServers.size();
                 JSONArray dhtServersAddresses = new JSONArray();
                 for(int i=0;i<n;i++){
                   dhtServersAddresses.add(dhtServers.get(i)+"");
                 }
                 JSONObject obj = new JSONObject();
                 obj.put("success","1");
                 obj.put("dhtServers",dhtServersAddresses);
                 response = "[" + obj.toJSONString() + "]";                 
            }
            return response;
        }

        String readRequest(Socket server) throws IOException {
            String request = "";
            InputStream inFromServer = server.getInputStream();
            DataInputStream in = new DataInputStream(inFromServer);
            request = in.readUTF();
            return request;
        }

        void sendResponse(String response, Socket socket) throws IOException {
            OutputStream outToServer = socket.getOutputStream();
            DataOutputStream out = new DataOutputStream(outToServer);
            out.writeUTF(response);
            socket.close();
        }

        JSONObject parseJSON(String json) {
            JSONObject obj2;
            try {
                JSONParser parser = new JSONParser();
                JSONArray a = (JSONArray) parser.parse(json);
                obj2 = (JSONObject) a.get(0);
            } catch (ParseException ex) {
                obj2 = null;
                Logger.getLogger(DNSServer.class.getName()).log(Level.SEVERE, null, ex);
            }
            return obj2;
        }

        public void run() {
            try {
                String request = readRequest(socket);
                System.out.println(request);
                String response = createResponse(request);
                sendResponse(response, socket);
            } catch (IOException ex) {
                Logger.getLogger(DNSServer.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public static void main(String args[]) throws ParseException, IOException {
        Thread DNSServer = new DNSServer();
        DNSServer.start();

    }
}
