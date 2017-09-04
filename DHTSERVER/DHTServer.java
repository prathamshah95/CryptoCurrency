/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package DHTServers;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 *
 * @author Pratham
 */
public class DHTServer extends Thread {

    int port;
    ServerSocket server;
    String myHash;
    ArrayList<dhtNodeHash> dhtNodes = new ArrayList<>();
    HashMap<String, ipkeystore> publicKeyMap = new HashMap<>();
    String myip;

    class ipkeystore {

        String publicKey;
        String ip;

        ipkeystore(String publicKey, String ip) {
            this.ip = ip;
            this.publicKey = publicKey;
        }
    }

    class dhtNodeHash {

        String nodeId;
        String nodeIdHash;
        int heartbeat;

        dhtNodeHash(String nodeId) throws NoSuchAlgorithmException, UnsupportedEncodingException, UnknownHostException {
            this.nodeId = nodeId;
            nodeIdHash = encryptPassword(nodeId + "");
            heartbeat = 0;
        }
    }

    class sortNodes implements Comparator<dhtNodeHash> {

        public int compare(dhtNodeHash one, dhtNodeHash two) {
            return one.nodeIdHash.compareTo(two.nodeIdHash);
        }
    }

    public static String encryptPassword(String password) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        String sha1 = "";
        MessageDigest crypt = MessageDigest.getInstance("SHA-1");
        crypt.reset();
        crypt.update(password.getBytes("UTF-8"));
        sha1 = byteToHex(crypt.digest());
        return sha1;
    }

    public static String byteToHex(final byte[] hash) {
        Formatter formatter = new Formatter();
        for (byte b : hash) {
            formatter.format("%02x", b);
        }
        String result = formatter.toString();
        formatter.close();
        return result;
    }

    public DHTServer(int port) throws IOException, NoSuchAlgorithmException {
        this.port = port;
        server = new ServerSocket(port, 100);
        Enumeration<NetworkInterface> n = NetworkInterface.getNetworkInterfaces();
        for (; n.hasMoreElements();) {
            NetworkInterface e = n.nextElement();
            //System.out.println("Interface: " + e.getName());
            Enumeration<InetAddress> a = e.getInetAddresses();

            for (; a.hasMoreElements();) {
                InetAddress addr = a.nextElement();
                myip = addr.getHostAddress();
                break;
            }
            break;
        }
        myHash = encryptPassword(myip + "");
        Socket client = new Socket("10.0.0.1", 60500);
        OutputStream outToServer = client.getOutputStream();
        DataOutputStream out = new DataOutputStream(outToServer);
        JSONObject obj = new JSONObject();
        JSONArray list = new JSONArray();
        list.add(myip);
        obj.put("type", "POST");
        obj.put("dht", list);
        out.writeUTF("[" + obj.toJSONString() + "]");
        InputStream inFromServer = client.getInputStream();
        DataInputStream in = new DataInputStream(inFromServer);
    }

    public void run() {
        Thread listen = new listenDHTRequest();
        listen.start();
        Thread dhtList = new fetchDHTServerList();
        dhtList.start();
        Thread heartbeat = new HeartBeat();
        heartbeat.start();
    }

    DataOutputStream createOutputStream(Socket socket) throws IOException {
        OutputStream outToServerr = socket.getOutputStream();
        DataOutputStream out = new DataOutputStream(outToServerr);
        return out;
    }

    DataInputStream createInputStream(Socket socket) throws IOException {
        InputStream outToServerr = socket.getInputStream();
        DataInputStream in = new DataInputStream(outToServerr);
        return in;
    }

    < E> void addPropertyToJSONArray(String property, E value, JSONObject json) {
        json.put(property, value);
    }

    JSONObject parseJSON(String json) {
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

    class sendArrival extends Thread {

        Socket socket;
        String node;

        sendArrival(String node) throws IOException {
            socket = new Socket(node, 60501);
            this.node = node;
        }

        public void run() {
            try {

                if (!(node.equals(myip))) {
                    DataOutputStream out = createOutputStream(socket);
                    JSONObject obj = new JSONObject();
                    obj.put("type", "POST");
                    obj.put("dht", myip);
                    out.writeUTF("[" + obj.toJSONString() + "]");
                    DataInputStream in = createInputStream(socket);
                    in.readUTF();
                }
            } catch (IOException ex) {
                Logger.getLogger(DHTServer.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    void addDHTServers(JSONArray dhtServers) throws NoSuchAlgorithmException, UnsupportedEncodingException, UnknownHostException, IOException {

        synchronized (dhtNodes) {
            int n = dhtServers == null ? 0 : dhtServers.size();
            for (int i = 0; i < n; i++) {
                dhtNodeHash node = new dhtNodeHash(dhtServers.get(i).toString());
                dhtNodes.add(node);
                Thread addMe = new sendArrival(node.nodeId);
                addMe.start();
            }
            Collections.sort(dhtNodes, new sortNodes());
            for (int i = 0; i < n; i++) {
                System.out.println(dhtNodes.get(i).nodeId + " " + dhtNodes.get(i).nodeIdHash + " " + port);
            }
        }
    }

    class fetchDHTServerList extends Thread {

        public void run() {

            try {
                Socket socket = new Socket("10.0.0.1", 60500);
                DataOutputStream write = createOutputStream(socket);
                JSONObject obj = new JSONObject();
                addPropertyToJSONArray("type", "GET", obj);
                addPropertyToJSONArray("dht", "1", obj);
                write.writeUTF("[" + obj.toJSONString() + "]");
                DataInputStream in = createInputStream(socket);
                JSONObject json = parseJSON(in.readUTF());
                JSONArray dhtServers = (JSONArray) json.get("dhtServers");

                try {
                    addDHTServers(dhtServers);
                } catch (NoSuchAlgorithmException ex) {
                    Logger.getLogger(DHTServer.class.getName()).log(Level.SEVERE, null, ex);
                } catch (UnsupportedEncodingException ex) {
                    Logger.getLogger(DHTServer.class.getName()).log(Level.SEVERE, null, ex);
                }
            } catch (IOException ex) {
                Logger.getLogger(DHTServer.class.getName()).log(Level.SEVERE, null, ex);
            }
            Random random = new Random();
            try {
                int x = 60000 + random.nextInt(60000);
                Thread.sleep(x);
            } catch (InterruptedException ex) {
                Logger.getLogger(DHTServer.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    class listenDHTRequest extends Thread {

        public void run() {
            while (true) {
                try {
                    Socket socket = server.accept();
                    Thread serveRequest = new serveRequest(socket);
                    serveRequest.start();
                } catch (Exception ex) {
                    Logger.getLogger(DHTServer.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    class serveRequest extends Thread {

        Socket socket;

        public serveRequest(Socket socket) {
            this.socket = socket;
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

        int getNearestServer(String key) throws NoSuchAlgorithmException, UnsupportedEncodingException {
            int nearest = -1;
            key = encryptPassword(key);
            synchronized (dhtNodes) {
                int n = dhtNodes.size();
                for (int i = 0; i < n; i++) {
                    if (key.compareTo(dhtNodes.get(i).nodeIdHash) < 0) {
                        nearest = i - 1;
                        break;
                    }
                }
                if (nearest == -1) {
                    nearest = n - 1;
                }
            }
            return nearest;
        }

        class redistributeKey extends Thread {

            int index = -1;
            String failed = "";
            int previous = -1;

            redistributeKey(int i, String failedHash, int previous) {
                index = i;
                failed = failedHash;
                this.previous = previous;
            }

            public void run() {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ex) {
                    Logger.getLogger(DHTServer.class.getName()).log(Level.SEVERE, null, ex);
                }
                for (String key : publicKeyMap.keySet()) {
                    try {
                        int serverAddress = getNearestServer(key);
                        JSONObject insert = new JSONObject();
                        insert.put("key", key);
                        insert.put("ip", publicKeyMap.get(key).ip);
                        insert.put("publickey", publicKeyMap.get(key).publicKey);
                        insert.put("redistribute", "1");
                        Thread backup = new insertBackup(insert, dhtNodes.get(serverAddress).nodeId);
                        backup.start();
                    } catch (NoSuchAlgorithmException ex) {
                        Logger.getLogger(DHTServer.class.getName()).log(Level.SEVERE, null, ex);
                    } catch (UnsupportedEncodingException ex) {
                        Logger.getLogger(DHTServer.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
        }

        class addNode extends Thread {

            int index = -1;
            int previous = -1;

            addNode(int index, int previous) {
                this.index = index;
                this.previous = previous;
            }

            public void run() {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ex) {
                    Logger.getLogger(DHTServer.class.getName()).log(Level.SEVERE, null, ex);
                }
                if (previous == 1) {
                    for (String key : publicKeyMap.keySet()) {
                        try {
                            int serverAddress = getNearestServer(key);
                            if (serverAddress == (index + 1) % (dhtNodes.size())) {
                                Socket socket = new Socket(dhtNodes.get(serverAddress).nodeId, port);
                                DataOutputStream out = createOutputStream(socket);
                                JSONObject requestBackup = new JSONObject();
                                requestBackup.put("type", "POST");
                                requestBackup.put("key", key);
                                requestBackup.put("ip", publicKeyMap.get(key).ip);
                                requestBackup.put("publickey", publicKeyMap.get(key).publicKey);
                                out.writeUTF("[" + requestBackup.toJSONString() + "]");
                                DataInputStream in = createInputStream(socket);
                                socket.close();
                            } else if (serverAddress == index) {
                                JSONObject request = new JSONObject();
                                request.put("key", key);
                                request.put("ip", publicKeyMap.get(key).ip);
                                request.put("publickey", publicKeyMap.get(key).publicKey);
                                request.put("type", "POST");
                                Thread insertBackup = new insertBackup(request, dhtNodes.get((index + 1) % (dhtNodes.size())).nodeId);
                                insertBackup.start();
                            } else if (serverAddress == (index + 2) % (dhtNodes.size())) {
                                publicKeyMap.remove(key);
                            }
                        } catch (NoSuchAlgorithmException ex) {
                            Logger.getLogger(DHTServer.class.getName()).log(Level.SEVERE, null, ex);
                        } catch (UnsupportedEncodingException ex) {
                            Logger.getLogger(DHTServer.class.getName()).log(Level.SEVERE, null, ex);
                        } catch (IOException ex) {
                            Logger.getLogger(DHTServer.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                } else if (previous == -1) {
                    for (String key : publicKeyMap.keySet()) {
                        try {
                            int serverAddress = getNearestServer(key);
                            if (dhtNodes.get(serverAddress).nodeId.equals(dhtNodes.get((index + 2) % dhtNodes.size()).nodeId)) {
                                publicKeyMap.remove(key);
                            }
                        } catch (Exception ex) {

                        }
                    }

                } else {
                    for (String key : publicKeyMap.keySet()) {
                        try {
                            int serverAddress = getNearestServer(key);
                            if (serverAddress == index) {
                                JSONObject request = new JSONObject();
                                request.put("key", key);
                                request.put("ip", publicKeyMap.get(key).ip);
                                request.put("publickey", publicKeyMap.get(key).publicKey);
                                request.put("type", "POST");
                                Thread insertBackup = new insertBackup(request, dhtNodes.get(((index - 1) == -1 ? (dhtNodes.size() - 1) : (index - 1))).nodeId);
                                insertBackup.start();
                            } else if (serverAddress == (index - 1 + dhtNodes.size()) % (dhtNodes.size())) {
                                Socket socket = new Socket(dhtNodes.get(serverAddress).nodeId, port);
                                DataOutputStream out = createOutputStream(socket);
                                JSONObject requestBackup = new JSONObject();
                                requestBackup.put("type", "POST");
                                requestBackup.put("key", key);
                                requestBackup.put("ip", publicKeyMap.get(key).ip);
                                requestBackup.put("publickey", publicKeyMap.get(key).publicKey);
                                out.writeUTF("[" + requestBackup.toJSONString() + "]");
                                DataInputStream in = createInputStream(socket);
                                socket.close();
                            } else if (serverAddress == (index - 2 + dhtNodes.size()) % (dhtNodes.size())) {
                                publicKeyMap.remove(key);
                            }
                        } catch (NoSuchAlgorithmException ex) {
                            Logger.getLogger(DHTServer.class.getName()).log(Level.SEVERE, null, ex);
                        } catch (UnsupportedEncodingException ex) {
                            Logger.getLogger(DHTServer.class.getName()).log(Level.SEVERE, null, ex);
                        } catch (IOException ex) {
                            Logger.getLogger(DHTServer.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }

                }
            }

        }

        class insertBackup extends Thread {

            JSONObject request;
            String ip;

            public insertBackup(JSONObject request, String ip) {
                this.request = request;
                this.ip = ip;
            }

            public void run() {
                try {
                    Socket socket = new Socket(ip, port);
                    DataOutputStream out = createOutputStream(socket);
                    JSONObject requestBackup = new JSONObject();
                    requestBackup.put("type", "POST");
                    requestBackup.put("key", request.get("key").toString());
                    requestBackup.put("ip", request.get("ip").toString());
                    requestBackup.put("publickey", request.get("publickey").toString());
                    if (request.get("redistribute") == null) {
                        requestBackup.put("backup", "1");
                    }
                    out.writeUTF("[" + requestBackup.toJSONString() + "]");
                    DataInputStream in = createInputStream(socket);
                    socket.close();
                } catch (IOException ex) {
                    Logger.getLogger(DHTServer.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }

        String createResponse(String request) throws NoSuchAlgorithmException, UnsupportedEncodingException, IOException {
            String response = "";
            JSONObject requestJSON = parseJSON(request);
            JSONObject responseJson = new JSONObject();
            if (requestJSON.get("type").equals("POST") && requestJSON.get("key") != null && requestJSON.get("ip") != null && requestJSON.get("publickey") != null) {
                if (requestJSON.get("backup") == null) {
                    int serverAddress = getNearestServer(requestJSON.get("key").toString());
                    int n = dhtNodes.size();

                    if (dhtNodes.get(serverAddress).nodeId.equals(myip)) {
                        ipkeystore store = new ipkeystore(requestJSON.get("publickey").toString(), requestJSON.get("ip").toString());
                        publicKeyMap.put(requestJSON.get("key").toString(), store);
                        System.out.println(requestJSON.get("key").toString() + " " + myip);
                        Thread backup1 = new insertBackup(requestJSON, dhtNodes.get((serverAddress + 1) % n).nodeId);
                        backup1.start();
                        Thread backup2 = new insertBackup(requestJSON, dhtNodes.get((serverAddress == 0 ? (n - 1) : serverAddress - 1)).nodeId);
                        backup2.start();
                        responseJson.put("success", "1");
                        responseJson.put("inserted", "1");

                    } else {
                        responseJson.put("success", "1");
                        responseJson.put("inserted", "0");
                        responseJson.put("address", dhtNodes.get(serverAddress).nodeId);
                    }
                } else {
                    ipkeystore store = new ipkeystore(requestJSON.get("publickey").toString(), requestJSON.get("ip").toString());
                    publicKeyMap.put(requestJSON.get("key").toString(), store);
                    System.out.println(requestJSON.get("key").toString() + "backup" + myip);
                    responseJson.put("success", "1");
                    responseJson.put("inserted", "1");
                }
                response = "[" + responseJson.toJSONString() + "]";
            } else if (requestJSON.get("type").equals("GET") && requestJSON.get("key") != null) {
                int serverAddress = getNearestServer(requestJSON.get("key").toString());
                if (dhtNodes.get(serverAddress).nodeId.equals(myip)) {
                    responseJson.put("nearest", "1");
                    responseJson.put("ip", publicKeyMap.get(requestJSON.get("key").toString()).ip);
                    responseJson.put("publickey", publicKeyMap.get(requestJSON.get("key").toString()).publicKey);
                } else {
                    JSONArray nearest = new JSONArray();
                    nearest.add(dhtNodes.get(serverAddress).nodeId);
                    nearest.add(dhtNodes.get((serverAddress + dhtNodes.size() + 1) % dhtNodes.size()).nodeId);
                    nearest.add(dhtNodes.get((serverAddress + dhtNodes.size() - 1) % dhtNodes.size()).nodeId);
                    responseJson.put("nearest", "0");
                    responseJson.put("address", nearest);
                }
                responseJson.put("success", "1");
                response = "[" + responseJson.toJSONString() + "]";
            } else if (requestJSON.get("type").equals("heartbeat")) {
                responseJson.put("success", "1");
                response = "[" + responseJson.toJSONString() + "]";
            } else if (requestJSON.get("type").equals("DELETE") && requestJSON.get("dht") != null) {
                synchronized (dhtNodes) {
                    int n = dhtNodes.size();
                    int index = -1;
                    String failedHash = "";
                    for (int i = 0; i < n; i++) {
                        if (dhtNodes.get(i).nodeId.equals(requestJSON.get("dht").toString())) {
                            failedHash = dhtNodes.get(i).nodeIdHash;
                            dhtNodes.remove(i);
                            index = i;
                            break;
                        }
                    }

                    if (index != -1 && (dhtNodes.get(((index - 1) == -1 ? (dhtNodes.size() - 1) : (index - 1)) % (dhtNodes.size())).nodeId.equals(myip + ""))) {
                        Thread redisributeKeyThread = new redistributeKey(index, failedHash, 1);
                        redisributeKeyThread.start();
                    } else if (index != -1 && dhtNodes.get(index % (dhtNodes.size())).nodeId.equals(myip + "")) {
                        Thread redisributeKeyThread = new redistributeKey(index, failedHash, 0);
                        redisributeKeyThread.start();
                    }
                }
                responseJson.put("success", "1");
                response = "[" + responseJson.toJSONString() + "]";
            } else if (requestJSON.get("type").equals("POST") && requestJSON.get("dht") != null) {
                int index = 0;
                String hashOfDHT = encryptPassword(requestJSON.get("dht").toString());
                synchronized (dhtNodes) {
                    int n = dhtNodes.size();
                    index = n;
                    for (int i = 0; i < n; i++) {
                        if (hashOfDHT.compareTo(dhtNodes.get(i).nodeIdHash) < 0) {
                            index = i;
                            break;
                        }
                    }
                    if (index == n) {
                        dhtNodes.add(new dhtNodeHash(requestJSON.get("dht").toString()));
                    } else {
                        dhtNodes.add(index, new dhtNodeHash(requestJSON.get("dht").toString()));
                    }
                    System.out.println(requestJSON.get("dht").toString() + "to" + myip);
                    if ((dhtNodes.get(((index - 1) == -1 ? (dhtNodes.size() - 1) : (index - 1)) % (dhtNodes.size())).nodeId).equals(myip)) {
                        Thread add = new addNode(((index - 1) == -1 ? (dhtNodes.size() - 1) : (index - 1)) % (dhtNodes.size()), 1);
                        add.start();
                    } else if ((dhtNodes.get((index + 1) % (dhtNodes.size())).nodeId).equals(myip)) {
                        Thread add = new addNode((index + 1) % (dhtNodes.size()), 0);
                        add.start();
                    } else if ((dhtNodes.get((index - 2 + dhtNodes.size()) % (dhtNodes.size())).nodeId).equals(myip)) {
                        Thread add = new addNode((index - 2 + dhtNodes.size()) % (dhtNodes.size()), -1);
                        add.start();
                    }
                }
                responseJson.put("success", "1");
                response = "[" + responseJson.toJSONString() + "]";
            } else if (requestJSON.get("type").equals("DELETE") && requestJSON.get("key") != null) {
                publicKeyMap.remove(requestJSON.get("key").toString());
                responseJson.put("success", "1");
                response = "[" + responseJson.toJSONString() + "]";
            }
            return response;
        }

        public void run() {
            try {
                String request = readRequest(socket);
                String response = createResponse(request);
                sendResponse(response, socket);
            } catch (UnsupportedEncodingException ex) {
                Logger.getLogger(DHTServer.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(DHTServer.class.getName()).log(Level.SEVERE, null, ex);
            } catch (NoSuchAlgorithmException ex) {
                Logger.getLogger(DHTServer.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    class nodeFailure extends Thread {

        String nodeId;

        nodeFailure(String nodeId) {
            this.nodeId = nodeId;
        }

        public void run() {
            for (int i = 0; i < dhtNodes.size(); i++) {
                try {
                    Socket socket = new Socket(dhtNodes.get(i).nodeId, port);
                    DataOutputStream out = createOutputStream(socket);
                    JSONObject deleteDHT = new JSONObject();
                    deleteDHT.put("type", "DELETE");
                    deleteDHT.put("dht", nodeId);
                    out.writeUTF("[" + deleteDHT.toJSONString() + "]");
                    DataInputStream in = createInputStream(socket);
                    in.readUTF();
                } catch (IOException ex) {
                    Logger.getLogger(DHTServer.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            try {
                Socket socket = new Socket("10.0.0.1", 60500);
                DataOutputStream out = createOutputStream(socket);
                JSONObject deleteDHT = new JSONObject();
                deleteDHT.put("type", "DELETE");
                deleteDHT.put("dht", nodeId);
                out.writeUTF("[" + deleteDHT.toJSONString() + "]");
                DataInputStream in = createInputStream(socket);
                in.readUTF();
            } catch (IOException ex) {
                Logger.getLogger(DHTServer.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    class HeartBeat extends Thread {

        JSONObject createRequestJSON() {
            JSONObject jsonRequest = new JSONObject();
            jsonRequest.put("type", "heartbeat");
            return jsonRequest;
        }

        public void run() {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ex) {
                Logger.getLogger(DHTServer.class.getName()).log(Level.SEVERE, null, ex);
            }
            while (true) {
                for (int i = 0; i < dhtNodes.size(); i++) {
                    dhtNodeHash node = dhtNodes.get(i);
                    if (!(node.nodeId.equals(myip))) {
                        try {
                            Socket socket = new Socket(node.nodeId, 60501);
                            socket.setSoTimeout(2000);
                            DataOutputStream out = createOutputStream(socket);
                            JSONObject request = createRequestJSON();
                            out.writeUTF("[" + request.toJSONString() + "]");
                            DataInputStream in = createInputStream(socket);
                            JSONObject response = parseJSON(in.readUTF());
                            if (response.get("success").equals("1")) {
                                node.heartbeat = 0;
                                // System.out.println(node.nodeId);
                            }
                        } catch (Exception ex) {
                            node.heartbeat++;
                            if (node.heartbeat > 3) {
                                Thread nodeFailure = new nodeFailure(node.nodeId);
                                nodeFailure.start();
                            }
                            Logger.getLogger(DHTServer.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                }
                Random random = new Random();
                int delay = 10000 + random.nextInt(10000);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ex) {
                    Logger.getLogger(DHTServer.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    public static void main(String args[]) throws IOException, NoSuchAlgorithmException {
        Thread dhtserver = new DHTServer(60501);
        dhtserver.start();
    }
}
