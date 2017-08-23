/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cryptocurrency;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import javax.xml.bind.DatatypeConverter;

/**
 *
 * @author mininet
 */
public class CryptoNode extends Thread {

    String myNodeId;
    String myIp;
    int localTxId = 0;
    String acc_number;
    int port = 60502;
    PrivateKey private_key;
    PublicKey public_key;
    String dhtIp;
    ArrayList<String> otherNodes = new ArrayList<>();
    int witnessTxId = 0;
    HashMap<Integer, Integer> witnessMap = new HashMap<>();

    CryptoNode() throws SocketException, NoSuchAlgorithmException, InvalidKeySpecException, IOException {
        Enumeration<NetworkInterface> n = NetworkInterface.getNetworkInterfaces();
        for (; n.hasMoreElements();) {
            NetworkInterface e = n.nextElement();
            Enumeration<InetAddress> a = e.getInetAddresses();
            for (; a.hasMoreElements();) {
                InetAddress addr = a.nextElement();
                NetworkInterface network = NetworkInterface.getByInetAddress(addr);
                byte[] mac = network.getHardwareAddress();
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < mac.length; i++) {
                    sb.append(String.format("%02X%s", mac[i], (i < mac.length - 1) ? "-" : ""));
                }
                myNodeId = sb.toString().replace("-", "");
                myIp = addr.getHostAddress();
                break;
            }
            break;
        }
        Random rnd = new Random();
        Integer i = new Integer(100000000 + rnd.nextInt(900000000));
        acc_number = i.toString();
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(512);
        KeyPair keypair = keyGen.genKeyPair();
        PrivateKey privateKey = keypair.getPrivate();
        PublicKey publicKey = keypair.getPublic();
        private_key = privateKey;
        public_key = publicKey;
        System.out.println(acc_number);
        System.out.println(myIp);
        Socket socket = new Socket("10.0.0.1", 60500);
        DataOutputStream out = createOutputStream(socket);
        JSONObject request = new JSONObject();
        request.put("type", "POST");
        JSONArray ips=new JSONArray();
        ips.add(myIp);
        request.put("wellknownServers", ips);
        out.writeUTF("[" + request.toJSONString() + "]");
        DataInputStream in = createInputStream(socket);
        String t=in.readUTF();
        System.out.println(t);
        JSONObject json = parseJSON(t);
        if (json.get("success").toString().equals("1")) {
            dhtIp = json.get("dht").toString();
        }
        System.out.println(dhtIp);
    }

    PublicKey generatePublicFromString(String s) throws NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] x = DatatypeConverter.parseBase64Binary(s);
        X509EncodedKeySpec X509publicKey = new X509EncodedKeySpec(x);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        PublicKey keyNew = kf.generatePublic(X509publicKey);
        return keyNew;
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

    void changeDHT() throws IOException {
        Socket socket = new Socket("10.0.0.1", 60500);
        DataOutputStream out = createOutputStream(socket);
        JSONObject request = new JSONObject();
        request.put("type", "GET");
        request.put("dht", "1");
        out.writeUTF("[" + request.toJSONString() + "]");
        DataInputStream in = createInputStream(socket);
        JSONObject response = parseJSON(in.readUTF());
        JSONArray dhtNodes = (JSONArray) response.get("dht");
        Random r = new Random();
        int n = dhtNodes.size();
        String temp = new String(dhtIp);
        do {
            dhtIp = dhtNodes.get(r.nextInt(n)).toString();
        } while (!dhtIp.equals(temp));

    }

    class TwoPhaseCommitPhase1 extends Thread {

        String msg;
        String sendTo;
        int id;
        String sender;
        String receiver;

        TwoPhaseCommitPhase1(String msg, String sender, String receiver, int witnessId, int SorR) {
            this.msg = msg;
            this.sendTo = (SorR == 0) ? sender : receiver;
            id = witnessId;
            this.sender = sender;
            this.receiver = receiver;
        }

        public void run() {
            try {
                Socket socket = new Socket(sendTo, port);
                DataOutputStream out = createOutputStream(socket);
                JSONObject request = new JSONObject();
                request.put("type", "2Phase1");
                out.writeUTF("[" + request.toJSONString() + "]");
                DataInputStream in = createInputStream(socket);
                JSONObject response = parseJSON(in.readUTF());
                if (response.get("ack").toString().equals("1")) {
                    synchronized (witnessMap) {
                        Integer i = witnessMap.get(id);
                        if (i == null) {
                            witnessMap.put(id, 1);
                        } else {
                            Thread senderCommit = new TwoPhaseCommitPhase2(msg, sender);
                            senderCommit.start();
                            Thread receiverCommit = new TwoPhaseCommitPhase2(msg, receiver);
                            receiverCommit.start();
                        }
                    }
                }
            } catch (IOException ex) {
                Logger.getLogger(CryptoNode.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    class TwoPhaseCommitPhase2 extends Thread {

        String sendTo;
        String msg;

        TwoPhaseCommitPhase2(String msg, String sendTo) {
            this.msg = msg;
            this.sendTo = sendTo;
        }

        public void run() {
            try {
                Socket socket = new Socket(sendTo, port);
                DataOutputStream out = createOutputStream(socket);
                JSONObject request = new JSONObject();
                request.put("type", "2Phase2");
                request.put("msg", msg);
                out.writeUTF("[" + request.toJSONString() + "]");
                DataInputStream in = createInputStream(socket);
                in.readUTF();
            } catch (IOException ex) {
                Logger.getLogger(CryptoNode.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    class listen extends Thread {

        ServerSocket server;

        listen() throws IOException {
            server = new ServerSocket(port, 100);
        }

        class serveRequest extends Thread {

            Socket socket;

            serveRequest(Socket socket) {
                this.socket = socket;
            }

            String readRequest(Socket server) throws IOException {
                String request = "";
                InputStream inFromServer = server.getInputStream();
                DataInputStream in = new DataInputStream(inFromServer);
                request = in.readUTF();
                return request;
            }

            String createResponse(String request) throws IOException {
                String response = "";
                JSONObject requestJSON = parseJSON(request);
                JSONObject responseJSON = new JSONObject();
                if (requestJSON.get("inittransaction") != null && requestJSON.get("sender") != null && requestJSON.get("receiver") != null && requestJSON.get("bitcoins") != null) {
                    String sender = getIPFromacc(requestJSON.get("sender").toString());
                    String receiver = getIPFromacc(requestJSON.get("receiver").toString());
                    System.out.println(sender + "sender");
                    System.out.println(receiver + "receiver");
                    if (!sender.equals("") && !receiver.equals("")) {
                        witnessTxId++;
                        Thread askSender = new TwoPhaseCommitPhase1(request, sender, receiver, witnessTxId, 0);
                        askSender.start();
                        Thread askReceiver = new TwoPhaseCommitPhase1(request, sender, receiver, witnessTxId, 1);
                        askReceiver.start();
                        responseJSON.put("initiated", "1");
                    } else {
                        responseJSON.put("initiated", "0");
                    }
                } else if (requestJSON.get("type").equals("2Phase1")) {
                    Random r = new Random();
                    int commit = r.nextInt(2);
                    responseJSON.put("success", "1");
                    responseJSON.put("ack", commit + "");
                    System.out.println(commit);
                } else if (requestJSON.get("type").equals("2Phase2")) {
                    //create a thread to broadcast transaction.
                    System.out.println("commit");
                    responseJSON.put("success", "1");
                }
                response = "[" + responseJSON.toJSONString() + "]";
                return response;
            }

            String getIPFromacc(String acc) throws IOException {
                System.out.println(dhtIp);
                String ip = "";
                try {
                    JSONObject obj = sendDHTRequest(acc);
                    System.out.println(obj.get("nearest").toString());
                    if (obj.get("nearest").toString().equals("0")) {
                        dhtIp = ((JSONArray) obj.get("address")).get(0).toString();                        
                        return sendDHTRequest(acc).get("ip").toString();
                    } else if (obj.get("nearest").toString().equals("1")) {
                        return obj.get("ip").toString();
                    }
                } catch (IOException ex) {
                    changeDHT();
                }
                return ip;
            }

            JSONObject sendDHTRequest(String acc) throws IOException {

                Socket socket = new Socket(dhtIp, 60501);
                DataOutputStream out = createOutputStream(socket);
                JSONObject request = new JSONObject();
                request.put("type", "GET");
                request.put("key", acc);
                out.writeUTF("[" + request.toJSONString() + "]");
                DataInputStream in = createInputStream(socket);
                JSONObject response = parseJSON(in.readUTF());
                return response;

            }

            void sendResponse(String response, Socket socket) throws IOException {
                OutputStream outToServer = socket.getOutputStream();
                DataOutputStream out = new DataOutputStream(outToServer);
                out.writeUTF(response);
                socket.close();
            }

            public void run() {
                try {
                    String request = readRequest(socket);
                    String response = createResponse(request);
                    sendResponse(response, socket);
                } catch (IOException ex) {
                    Logger.getLogger(CryptoNode.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }

        public void run() {
            while (true) {
                try {
                    Socket socket = server.accept();
                    Thread serveRequest = new serveRequest(socket);
                    serveRequest.start();
                } catch (Exception ex) {

                }
            }
        }
    }

    public void run() {
        try {
            Thread listen = new listen();
            listen.start();
            Thread add = new addToDHT();
            add.start();
            Thread otherNodes = new getNodes();
            otherNodes.start();
        } catch (IOException ex) {
            Logger.getLogger(CryptoNode.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    class getNodes extends Thread {

        public void run() {
            try {
                Socket socket = new Socket("10.0.0.1", 60500);
                DataOutputStream out = createOutputStream(socket);
                JSONObject request = new JSONObject();
                request.put("type", "GET");
                request.put("wellknownServers", "1");
                out.writeUTF("[" + request.toJSONString() + "]");
                DataInputStream in = createInputStream(socket);
                
                JSONObject response = parseJSON(in.readUTF());
                
                JSONArray nodes = (JSONArray) response.get("wellknownServers");
                int n = nodes.size();
                for (int i = 0; i < n; i++) {
                    otherNodes.add(nodes.get(i).toString());
                }
            } catch (IOException ex) {
                Logger.getLogger(CryptoNode.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    class addToDHT extends Thread {

        public void run() {
            try {
                Socket socket = new Socket(dhtIp, 60501);
                DataOutputStream out = createOutputStream(socket);
                JSONObject request = new JSONObject();
                request.put("type", "POST");
                request.put("key", acc_number);
                request.put("ip", myIp);
                request.put("publickey", DatatypeConverter.printBase64Binary(public_key.getEncoded()));
                out.writeUTF("[" + request.toJSONString() + "]");
                DataInputStream in = createInputStream(socket);
                JSONObject response = parseJSON(in.readUTF());
                if (response.get("success").toString().equals("1")) {
                    if (response.get("inserted").toString().equals("1")) {

                    } else {
                        dhtIp = ((JSONArray) response.get("address")).get(0).toString();
                        Thread addNearest = new addToDHT();
                        addNearest.start();
                    }
                }
            } catch (Exception e) {

            }
        }
    }

    public static void main(String args[]) throws SocketException, NoSuchAlgorithmException, InvalidKeySpecException, IOException {
        Thread x = new CryptoNode();
        x.start();
    }
}
