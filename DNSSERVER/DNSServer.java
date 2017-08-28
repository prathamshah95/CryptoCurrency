package DNSSERVER;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.simple.*;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class DNSServer extends Thread {

    int port = 60500;
    ServerSocket server;
    ArrayList<String> dhtServers = new ArrayList<>();
    ArrayList<String> cryptServers = new ArrayList<>();

    DNSServer() throws IOException {
        ServerSocket CreateServer = new ServerSocket(port, 100);
        server = CreateServer;
        System.out.println("hi");
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

        boolean addServers(JSONObject requestJSON) {
            boolean added = true;
            try {
                boolean isDHT = !(requestJSON.get("dht") == null);
                JSONArray dhtJOSNArray = (JSONArray) (isDHT ? requestJSON.get("dht") : requestJSON.get("wellknownServers"));
                Iterator<String> iterator = dhtJOSNArray.iterator();
                while (iterator.hasNext()) {
                    ArrayList<String> temp = (isDHT ? dhtServers : cryptServers);
                    synchronized (temp) {
                        String s = iterator.next();
                        temp.remove(s);
                        temp.add(s);
                    }
                }
            } catch (Exception e) {
                added = false;
            }
            return added;
        }

        String createResponse(String request) {
            String response = "";
            JSONObject requestJSON = parseJSON(request);
            JSONObject obj = new JSONObject();
            if (((String) requestJSON.get("type")).equals("POST") && requestJSON.get("dht") != null) {
                boolean dhtAdded = addServers(requestJSON);
                int n = dhtServers.size();
                for (int i = 0; i < n; i++) {
                    System.out.println(dhtServers.get(i));
                }
                if (dhtAdded) {
                    obj.put("success", "1");
                } else {
                    obj.put("success", "0");
                }
            } else if (((String) requestJSON.get("type")).equals("GET") && requestJSON.get("dht") != null) {
                int n = dhtServers.size();
                if (n == 0) {
                    obj.put("success", "0");
                } else {
                    JSONArray dhtServersRegistered = new JSONArray();
                    for (int i = 0; i < n; i++) {
                        dhtServersRegistered.add(dhtServers.get(i).toString());
                    }
                    obj.put("success", "1");
                    obj.put("dhtServers", dhtServersRegistered);
                }
            } else if (((String) requestJSON.get("type")).equals("POST") && requestJSON.get("wellknownServers") != null) {
                boolean dhtAdded = addServers(requestJSON);
                System.out.println(dhtAdded);
                if (dhtAdded) {
                    int n = dhtServers.size();
                    System.out.println(n);
                    if (n != 0) {
                        Random r = new Random();
                        obj.put("success", "1");
                        obj.put("dht", dhtServers.get(r.nextInt(n)).toString());
                    }
                    // obj.put("success", "0");
                } else {
                    obj.put("success", "0");
                }

            } else if (((String) requestJSON.get("type")).equals("GET") && requestJSON.get("wellknownServers") != null) {
                int n = cryptServers.size();
                JSONArray cryptRegisteredServers = new JSONArray();
                if (n == 0) {
                    obj.put("success", "0");
                } else {
                    for (int i = 0; i < n; i++) {
                        cryptRegisteredServers.add(cryptServers.get(i).toString());
                    }
                    obj.put("success", "1");
                    obj.put("wellknownServers", cryptRegisteredServers);
                }
            } else if ((requestJSON.get("type").toString()).equals("DELETE") && requestJSON.get("dht") != null) {
                synchronized (dhtServers) {
                    dhtServers.remove(requestJSON.get("dht").toString());
                }
                obj.put("success", "1");
            }
            response = "[" + obj.toJSONString() + "]";
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
