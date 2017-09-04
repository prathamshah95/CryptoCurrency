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
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Formatter;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
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
    String publickey_hash;
    String dhtIp;
    ArrayList<String> otherNodes = new ArrayList<>();
    int witnessTxId = 0;
    HashMap<Integer, Integer> witnessMap = new HashMap<>();
    blockChain head = null;
    HashMap<String, ArrayList<Integer>> unusedTrransaction = new HashMap<>();
    ArrayList<transaction> txQueue = new ArrayList<>();
    HashMap<String, transaction> mapTrransaction = new HashMap<>();
    HashMap<String, blockChain> blockMap = new HashMap<>();
    HashMap<String, Boolean> txAddedToBlock = new HashMap<>();
    Boolean newBlockAdded = false;
    Boolean knowBlockAdded = false;
    int transactionsPerBlock = 2;

    class blockChain {

        String blockHash;
        blockChain previous = null;
        ArrayList<transaction> tx = new ArrayList<>();
        int height;
        String nounce;

        String toJson() {
            String json = "";
            JSONObject blockJson = new JSONObject();
            blockJson.put("blockhash", blockHash);
            blockJson.put("prevblockhash", (previous != null ? previous.blockHash : null));
            JSONArray transactions = new JSONArray();
            int n = tx.size();
            for (int i = 0; i < n; i++) {
                transactions.add(tx.get(i).transactionJSON);
            }
            blockJson.put("transactions", transactions);
            blockJson.put("nounce", nounce);
            json = blockJson.toJSONString();
            return json;
        }

        blockChain(blockChain previous) {
            this.previous = previous;
            if (previous == null) {
                height = 1;
            } else {
                height = previous.height + 1;
            }
        }

        void addBlockHash() throws NoSuchAlgorithmException, UnsupportedEncodingException {
            int n = tx.size();
            String s = "";
            for (int i = 0; i < n; i++) {
                s += tx.get(i).txHash;
            }
            blockHash = encryptPassword(s);
        }
    }

    boolean verify(String sig, String publicKey, String prevHash, int output_index) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeySpecException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException, UnsupportedEncodingException {
        Cipher c = Cipher.getInstance("RSA");
        c.init(Cipher.DECRYPT_MODE, generatePublicFromString(publicKey));
        String h = new String(c.doFinal(DatatypeConverter.parseBase64Binary(sig)), "UTF-8");
        System.out.println(prevHash);
        transaction tx = mapTrransaction.get(prevHash);
        System.out.println("nullornot" + ((tx == null) ? 0 : 1));
        System.out.println("verify sig decrypt=" + h);
        System.out.println("verify outputpbhash=" + tx.outputs.get(output_index).publickey);
        return h.equals(tx.outputs.get(output_index).publickey);
    }

    class transaction {

        String txHash;
        ArrayList<input> inputs = new ArrayList<>();
        ArrayList<output> outputs = new ArrayList<>();
        boolean valid = false;
        String transactionJSON;
        String txid;

        public transaction(JSONObject transactionObject) throws NoSuchAlgorithmException, UnsupportedEncodingException {
            transactionJSON = transactionObject.toJSONString();
            System.out.println("transactionJSON=" + transactionJSON);
            txHash = transactionObject.get("hash").toString();
            txid = transactionObject.get("txid").toString();
            System.out.println(txid);
            System.out.println("txHash=" + txHash);
            JSONArray input = (JSONArray) ((transactionObject.get("in") == null) ? null : transactionObject.get("in"));
            JSONArray output = (JSONArray) transactionObject.get("out");

            if (input != null) {
                int n = input.size();
                for (int i = 0; i < n; i++) {
                    JSONObject prev_out = (JSONObject) input.get(i);
                    input in = new input(prev_out.get("hash").toString(), Integer.parseInt(prev_out.get("out_index").toString()));
                    in.addSignature(prev_out.get("scriptSig").toString());
                    in.addPublickey(prev_out.get("publickey").toString());
                    inputs.add(in);
                }
            }
            if (output != null) {
                int n = output.size();

                for (int i = 0; i < n; i++) {
                    JSONObject obj = (JSONObject) output.get(i);
                    output out = new output(obj.get("scriptPubkey").toString(), Double.parseDouble(obj.get("bitcoins").toString()), i);
                    System.out.println("scriptpubkey=" + obj.get("scriptPubkey").toString());
                    outputs.add(out);
                }
            }
        }

        public transaction(double bitcoins, String receiverPublickey) throws NoSuchAlgorithmException, UnsupportedEncodingException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, InvalidKeyException {
            blockChain temp = head;
            double need_coins = bitcoins;
            boolean found_needed = false;
            ArrayList<input> potential_input = new ArrayList<>();
            boolean f = true;
            while (temp != null) {
                ArrayList<transaction> tx = temp.tx;
                if (f) {
                    tx = txQueue;
                }
                int n = tx.size();
                for (int i = 0; i < n; i++) {
                    ArrayList<output> out = tx.get(i).outputs;
                    int n_out = out.size();
                    System.out.println("out");
                    for (int j = 0; j < n_out; j++) {
                        if (out.get(j).publickey.equals(publickey_hash)) {
                            System.out.println("before used");
                            boolean used = checkUsed(tx.get(i).txHash, out.get(j).index);
                            System.out.println(used);
                            if (!used) {
                                need_coins -= out.get(j).bitcoins;
                                input in = new input(tx.get(i).txHash, out.get(j).index);
                                inputs.add(in);
                                potential_input.add(in);
                                if (need_coins <= 0) {
                                    found_needed = true;
                                    break;
                                }
                            }
                        }
                        if (found_needed) {
                            break;
                        }
                    }
                    if (found_needed) {
                        break;
                    }
                }
                if (found_needed) {
                    break;
                }
                if (!f) {
                    temp = temp.previous;
                }
                f = false;
            }

            if (found_needed) {
                System.out.println("enoughfund");
                output out1 = new output(receiverPublickey, bitcoins, 0);
                JSONArray create_output = new JSONArray();
                JSONObject create_out1 = new JSONObject();
                create_out1.put("bitcoins", bitcoins + "");
                create_out1.put("scriptPubkey", receiverPublickey);
                create_output.add(create_out1);
                outputs.add(out1);
                if (need_coins < 0) {
                    output out2 = new output(publickey_hash, -need_coins, 1);
                    JSONObject create_out2 = new JSONObject();
                    create_out2.put("bitcoins", out2.bitcoins + "");
                    create_out2.put("scriptPubkey", publickey_hash);
                    create_output.add(create_out2);
                    outputs.add(out2);
                }
                valid = true;
                String scriptSig = createSignature();
                JSONObject createTransactionJson = new JSONObject();
                JSONArray create_input = new JSONArray();
                int n = potential_input.size();
                for (int i = 0; i < n; i++) {
                    JSONObject pre_out = new JSONObject();
                    pre_out.put("hash", potential_input.get(i).prevTxHash);
                    pre_out.put("out_index", potential_input.get(i).output_index);
                    pre_out.put("scriptSig", scriptSig);
                    pre_out.put("publickey", DatatypeConverter.printBase64Binary(public_key.getEncoded()));
                    potential_input.get(i).addPublickey(DatatypeConverter.printBase64Binary(public_key.getEncoded()));
                    create_input.add(pre_out);
                }
                createTransactionJson.put("in", create_input);
                createTransactionJson.put("out", create_output);
                localTxId++;
                txid = acc_number + "" + localTxId;
                createTransactionJson.put("txid", txid);
                //  createTransactionJson.put("acc_number", acc_number+"");
                String hash = encryptPassword(createTransactionJson.toJSONString());
                txHash = hash;
                createTransactionJson.put("hash", hash);
                transactionJSON = createTransactionJson.toJSONString();
            }
        }

        String createSignature() throws NoSuchAlgorithmException, NoSuchPaddingException, UnsupportedEncodingException, IllegalBlockSizeException, BadPaddingException, InvalidKeyException {
            String s = publickey_hash;
            Cipher c = Cipher.getInstance("RSA");
            c.init(Cipher.ENCRYPT_MODE, private_key);
            System.out.println("scriptSig Encrypt=" + DatatypeConverter.printBase64Binary(c.doFinal(s.getBytes("UTF-8"))));
            return DatatypeConverter.printBase64Binary(c.doFinal(s.getBytes("UTF-8")));
        }

        class input {

            String prevTxHash;
            String scriptSig;
            int output_index;
            String publicKey;

            input(String hash, int output_index) {
                prevTxHash = hash;
                this.output_index = output_index;
            }

            void addSignature(String scriptSig) {
                this.scriptSig = scriptSig;
            }

            void addPublickey(String s) {
                publicKey = s;
            }
        }

        class output {

            int index;
            String publickey;
            double bitcoins;

            output(String receiverKey, double bitcoins, int index) {
                this.publickey = receiverKey;
                this.index = index;
                this.bitcoins = bitcoins;
            }
        }
    }

    boolean checkUsed(String hash, int index) {
        boolean used = true;
        System.out.println(unusedTrransaction.get(hash));
        ArrayList<Integer> output = unusedTrransaction.get(hash);
        if (output == null) {
            return used;
        }
        int n = output.size();
        for (int i = 0; i < n; i++) {
            if (output.get(i) == index) {
                used = false;
                break;
            }
        }
        return used;
    }

    CryptoNode() throws SocketException, NoSuchAlgorithmException, InvalidKeySpecException, IOException {
        // head = new blockChain(null);
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
        publickey_hash = encryptPassword(DatatypeConverter.printBase64Binary(public_key.getEncoded()));
        System.out.println("accountnumber" + acc_number);
        System.out.println("ip" + myIp);
        System.out.println("pbhash" + publickey_hash);
        JSONObject request = new JSONObject();
        request.put("type", "POST");
        JSONArray ips = new JSONArray();
        ips.add(myIp);
        request.put("wellknownServers", ips);
        JSONObject response = sendRequest("10.0.0.1", 60500, request);
        if (response.get("success").toString().equals("1")) {
            dhtIp = response.get("dht").toString();
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
        JSONObject request = new JSONObject();
        request.put("type", "GET");
        request.put("dht", "1");
        JSONObject response = sendRequest("10.0.0.1", 60500, request);
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
                JSONObject request = new JSONObject();
                request.put("type", "2Phase1");
                JSONObject response = sendRequest(sendTo, port, request);
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
                JSONObject request = new JSONObject();
                request.put("type", "2Phase2");
                request.put("msg", "[" + msg + "]");
                sendRequest(sendTo, port, request);
            } catch (IOException ex) {
                Logger.getLogger(CryptoNode.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    class broadcast extends Thread {

        String msg;
        String type;

        broadcast(String msg, String type) {
            this.msg = msg;
            this.type = type;
        }

        public void run() {

            Thread allNodes = new getNodes();
            allNodes.run();
            int n = otherNodes.size();
            for (int i = 0; i < n; i++) {
                if (!otherNodes.get(i).equals(myIp)) {
                    Thread broadcastRequest = new broadcastRequest(otherNodes.get(i), msg, type);
                    broadcastRequest.start();
                }
            }
        }
    }

    class broadcastRequest extends Thread {

        String sendTo;
        String transaction;
        String type;

        public broadcastRequest(String sendTo, String msg, String type) {
            this.sendTo = sendTo;
            transaction = msg;
            this.type = type;
        }

        public void run() {
            try {
                JSONObject obj = new JSONObject();
                obj.put("type", type);
                obj.put("transaction", "[" + transaction + "]");
                sendRequest(sendTo, port, obj);
            } catch (IOException ex) {
                Logger.getLogger(CryptoNode.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

    }

    JSONObject sendRequest(String ip, int port, JSONObject request) throws IOException {
        Socket socket = new Socket(ip, port);
        DataOutputStream out = createOutputStream(socket);
        out.writeUTF("[" + request.toJSONString() + "]");
        DataInputStream in = createInputStream(socket);
        JSONObject response = parseJSON(in.readUTF());
        return response;
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

            String createResponse(String request) throws IOException, NoSuchAlgorithmException, UnsupportedEncodingException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, InvalidKeyException, InvalidKeySpecException {
                String response = "";
                JSONObject requestJSON = parseJSON(request);
                JSONObject responseJSON = new JSONObject();
                if (requestJSON.get("inittransaction") != null && requestJSON.get("sender") != null && requestJSON.get("receiver") != null && requestJSON.get("bitcoins") != null) {
                    String sender[] = getIPFromacc(requestJSON.get("sender").toString());
                    String receiver[] = getIPFromacc(requestJSON.get("receiver").toString());
                    System.out.println(sender + "sender");
                    System.out.println(receiver + "receiver");
                    if (!sender[0].equals("") && !receiver[0].equals("")) {
                        requestJSON.put("publickey", receiver[1]);
                        witnessTxId++;
                        Thread askSender = new TwoPhaseCommitPhase1(requestJSON.toJSONString(), sender[0], receiver[0], witnessTxId, 0);
                        askSender.start();
                        Thread askReceiver = new TwoPhaseCommitPhase1(requestJSON.toJSONString(), sender[0], receiver[0], witnessTxId, 1);
                        askReceiver.start();
                        responseJSON.put("initiated", "1");
                    } else {
                        responseJSON.put("initiated", "0");
                    }
                } else if (requestJSON.get("type").toString().equals("2Phase1")) {
                    Random r = new Random();
                    int commit = r.nextInt(2);
                    commit = 1;
                    responseJSON.put("success", "1");
                    responseJSON.put("ack", commit + "");
                    System.out.println(commit);
                } else if (requestJSON.get("type").toString().equals("2Phase2")) {
                    JSONObject json = parseJSON(requestJSON.get("msg").toString());
                    System.out.println(requestJSON.get("msg").toString());
                    if (json.get("sender").toString().equals(acc_number + "")) {
                        transaction tx = new transaction(Double.parseDouble(json.get("bitcoins").toString()), json.get("publickey").toString());
                        System.out.println(requestJSON.get("msg").toString());
                        if (tx.valid) {
                            System.out.println("valid");
                            synchronized (txQueue) {
                                synchronized (unusedTrransaction) {
                                    ArrayList<Integer> al = new ArrayList<>();
                                    ArrayList<transaction.output> temp = tx.outputs;
                                    int n = temp.size();
                                    for (int i = 0; i < n; i++) {
                                        al.add(temp.get(i).index);
                                    }
                                    unusedTrransaction.put(tx.txHash, al);
                                    ArrayList<transaction.input> temp_input = tx.inputs;
                                    n = temp_input.size();
                                    for (int i = 0; i < n; i++) {
                                        ArrayList<Integer> h = unusedTrransaction.get(temp_input.get(i).prevTxHash);
                                        h.remove(new Integer(temp_input.get(i).output_index));
                                        unusedTrransaction.put(temp_input.get(i).prevTxHash, h);
                                    }
                                    mapTrransaction.put(tx.txHash, tx);
                                    System.out.println(tx.txHash);
                                    txQueue.add(tx);
                                    System.out.println("broadcast");
                                    Thread broadcastMsg = new broadcast(tx.transactionJSON, "transaction");
                                    broadcastMsg.start();
                                }
                            }
                        } else {
                            System.out.println("not enough funds");
                        }
                        System.out.println("commit");
                    }
                    responseJSON.put("success", "1");
                } else if (requestJSON.get("type").toString().equals("transaction")) {
                    JSONObject txx = parseJSON(requestJSON.get("transaction").toString());
                    transaction tx = new transaction(txx);
                    boolean verified = verifyTransaction(tx);
                    if (verified) {
                        System.out.println("verified");
                        synchronized (txQueue) {
                            synchronized (unusedTrransaction) {
                                ArrayList<Integer> al = new ArrayList<>();
                                ArrayList<transaction.output> temp = tx.outputs;
                                int n = temp.size();
                                for (int i = 0; i < n; i++) {
                                    al.add(temp.get(i).index);
                                }
                                unusedTrransaction.put(tx.txHash, al);
                                ArrayList<transaction.input> temp_input = tx.inputs;
                                n = temp_input.size();
                                for (int i = 0; i < n; i++) {
                                    ArrayList<Integer> h = unusedTrransaction.get(temp_input.get(i).prevTxHash);
                                    h.remove(new Integer(temp_input.get(i).output_index));
                                    unusedTrransaction.put(temp_input.get(i).prevTxHash, h);
                                }
                                mapTrransaction.put(tx.txHash, tx);
                                txQueue.add(tx);
                            }
                        }
                        responseJSON.put("success", "1");
                    } else {
                        responseJSON.put("success", "0");
                    }
                } else if (requestJSON.get("type").toString().equals("firsttransaction")) {
                    head = new blockChain(null);
                    JSONObject first = parseJSON(requestJSON.get("first").toString());
                    System.out.println(first.toJSONString());
                    transaction tx = new transaction(first);
                    mapTrransaction.put(tx.txHash, tx);
                    ArrayList<Integer> al = new ArrayList<>();
                    ArrayList<transaction.output> temp = tx.outputs;
                    int n = temp.size();
                    for (int i = 0; i < n; i++) {
                        al.add(temp.get(i).index);
                    }
                    unusedTrransaction.put(tx.txHash, al);
                    head.tx.add(tx);
                    head.addBlockHash();
                    txAddedToBlock.put(tx.txHash, true);
                    blockMap.put(head.blockHash, head);
                    System.out.println("after added:-" + head.tx.get(0).txid);
                    System.out.println("after added:-" + head.tx.get(0).outputs.get(0).bitcoins);
                } else if (requestJSON.get("type").toString().equals("block")) {
                    newBlockAdded = true;
                    knowBlockAdded = true;
                    System.out.println("got a block ip" + myIp);
                    JSONObject newBlockJSON = parseJSON(requestJSON.get("transaction").toString());
                    blockChain parent = null;
                    if (newBlockJSON.get("prevblockhash") != null) {
                        parent = blockMap.get(newBlockJSON.get("prevblockhash").toString());
                    }
                    if (parent != null || head == null) {
                        System.out.println("found parent");
                        blockChain newBlock = new blockChain(parent);
                        JSONArray transactions = (JSONArray) newBlockJSON.get("transactions");
                        int n = transactions.size();
                        boolean h = true;
                        for (int i = 0; i < n; i++) {
                            JSONObject obj = parseJSON("[" + transactions.get(i).toString() + "]");
                            transaction t = mapTrransaction.get(obj.get("hash").toString());
                            if (t != null) {
                                System.out.println("transaction present");
                                txQueue.remove(mapTrransaction.get(obj.get("hash").toString()));
                                newBlock.tx.add(t);
                            } else {
                                transaction tt = new transaction(obj);
                                if (verifyTransaction(tt) || head == null) {
                                    newBlock.tx.add(tt);
                                    synchronized (txQueue) {
                                        synchronized (unusedTrransaction) {
                                            ArrayList<Integer> al = new ArrayList<>();
                                            ArrayList<transaction.output> temp = tt.outputs;
                                            int s = temp.size();
                                            for (int j = 0; j < s; j++) {
                                                al.add(temp.get(j).index);
                                            }
                                            unusedTrransaction.put(tt.txHash, al);
                                            if (!tt.txid.equals("0")) {
                                                ArrayList<transaction.input> temp_input = tt.inputs;
                                                s = temp_input.size();
                                                for (int j = 0; j < s; j++) {
                                                    ArrayList<Integer> k = unusedTrransaction.get(temp_input.get(j).prevTxHash);
                                                    k.remove(new Integer(temp_input.get(j).output_index));
                                                    unusedTrransaction.put(temp_input.get(j).prevTxHash, k);
                                                }
                                            }
                                            mapTrransaction.put(tt.txHash, tt);
                                            txAddedToBlock.put(tt.txHash, true);
                                        }
                                    }
                                } else {
                                    System.out.println("block rejected");
                                    //   deleteConflictQueue(tt);                                    
                                    i--;
                                    h = false;
                                }
                            }
                        }
                        if (h) {
                            newBlock.addBlockHash();
                            if (newBlockJSON.get("nounce") != null) {
                                newBlock.nounce = newBlockJSON.get("nounce").toString();
                            }
                            if (newBlock.blockHash != null) {
                                blockMap.put(newBlock.blockHash, newBlock);
                            }
                            System.out.println("block Added ip" + myIp);
                            newBlockAdded = false;
                            if (newBlock.blockHash != null) {
                                String longestChainLastBlockHash = null;
                                int max_height = 0;
                                for (String key : blockMap.keySet()) {
                                    blockChain block = blockMap.get(key);
                                    if (block.height > max_height) {
                                        max_height = block.height;
                                        longestChainLastBlockHash = block.blockHash;
                                    }
                                }
                                head = blockMap.get(longestChainLastBlockHash);
                            }
                            responseJSON.put("success", "1");
                            System.out.println("bloc added ip=" + myIp + " hash= " + newBlock.blockHash);
                            System.out.println("bloc head ip=" + myIp + " hash= " + head.blockHash);
                        } else {
                            System.out.println("rejected");
                        }
                    } else {
                        System.out.println("parent not found ip=" + myIp);
                        responseJSON.put("success", "0");
                    }
                } else if (requestJSON.get("type").toString().equals("getblocks")) {
                    Thread sendBlock = new sendBlock(requestJSON.get("ip").toString());
                    sendBlock.start();
                } else if (requestJSON.get("type").toString().equals("printtxhash")) {
                    if (head != null) {
                        blockChain temp = head;
                        while (temp != null) {
                            ArrayList<transaction> tx = temp.tx;
                            int n = tx.size();
                            for (int i = 0; i < n; i++) {
                                System.out.println(tx.get(i).txHash);
                            }
                            temp = temp.previous;
                        }
                    }
                    JSONObject obj = new JSONObject();
                    obj.put("type", "printtxhash");
                    if (requestJSON.get("ip") != null && requestJSON.get("ip").toString().equals(myIp)) {
                        Thread broadcastMsg = new broadcast(obj.toJSONString(), "printtxhash");
                        broadcastMsg.start();
                    }
                    responseJSON.put("success", "1");
                }
                response = "[" + responseJSON.toJSONString() + "]";
                return response;
            }

            void deleteConflictQueue(transaction tt) {
                ArrayList<transaction.input> direct = tt.inputs;
                int nn = direct.size();
                int n = txQueue.size();
                int c = 0;
                ArrayList<transaction> f = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    transaction q = txQueue.get(i);
                    ArrayList<transaction.input> ii = q.inputs;
                    int isize = ii.size();
                    for (int j = 0; j < isize; j++) {
                        transaction.input l = ii.get(i);
                        for (int k = 0; k < nn; k++) {
                            if (direct.get(k).prevTxHash.equals(l.prevTxHash) && direct.get(k).output_index == l.output_index) {

                            }
                        }

                    }

                }

            }

            boolean verifyTransaction(transaction t) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, InvalidKeySpecException, IllegalBlockSizeException, BadPaddingException, UnsupportedEncodingException {

                ArrayList<transaction.input> in = t.inputs;
                int n = in.size();
                boolean x = true;
                for (int i = 0; i < n; i++) {
                    System.out.println("prevHash=" + in.get(i).prevTxHash);
                    System.out.println(checkUsed(in.get(i).prevTxHash, in.get(i).output_index));
                    System.out.println((verify(in.get(i).scriptSig, in.get(i).publicKey, in.get(i).prevTxHash, in.get(i).output_index)));
                    if (checkUsed(in.get(i).prevTxHash, in.get(i).output_index) || !(verify(in.get(i).scriptSig, in.get(i).publicKey, in.get(i).prevTxHash, in.get(i).output_index))) {
                        x = false;
                        break;
                    }
                }
                return x;
            }

            String[] getIPFromacc(String acc) throws IOException {
                System.out.println(dhtIp);
                String[] s = new String[2];
                s[0] = "";
                try {
                    JSONObject obj = sendDHTRequest(acc);
                    System.out.println(obj.get("nearest").toString());
                    if (obj.get("nearest").toString().equals("0")) {
                        System.out.println("hi");
                        dhtIp = ((JSONArray) obj.get("address")).get(0).toString();
                        System.out.println(dhtIp);
                        JSONObject o = sendDHTRequest(acc);
                        System.out.println(o.get("ip").toString());
                        System.out.println(o.get("publickey").toString());
                        s[0] = o.get("ip").toString();
                        s[1] = o.get("publickey").toString();
                        return s;
                    } else if (obj.get("nearest").toString().equals("1")) {
                        s[0] = obj.get("ip").toString();
                        s[1] = obj.get("publickey").toString();
                        return s;
                    }
                } catch (IOException ex) {
                    changeDHT();
                }
                return s;
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
                } catch (NoSuchAlgorithmException ex) {
                    Logger.getLogger(CryptoNode.class.getName()).log(Level.SEVERE, null, ex);
                } catch (NoSuchPaddingException ex) {
                    Logger.getLogger(CryptoNode.class.getName()).log(Level.SEVERE, null, ex);
                } catch (IllegalBlockSizeException ex) {
                    Logger.getLogger(CryptoNode.class.getName()).log(Level.SEVERE, null, ex);
                } catch (BadPaddingException ex) {
                    Logger.getLogger(CryptoNode.class.getName()).log(Level.SEVERE, null, ex);
                } catch (InvalidKeyException ex) {
                    Logger.getLogger(CryptoNode.class.getName()).log(Level.SEVERE, null, ex);
                } catch (InvalidKeySpecException ex) {
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
            otherNodes.run();
            Thread getBlocks = new getBlock();
            getBlocks.run();
            Thread blockSolver = new solveBlock();
            blockSolver.start();
        } catch (IOException ex) {
            Logger.getLogger(CryptoNode.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    class sendBlock extends Thread {

        String sendTo;

        sendBlock(String sendTo) {
            this.sendTo = sendTo;
        }

        public void run() {
            Stack<blockChain> stk = new Stack();
            blockChain temp = head;
            while (temp != null) {
                stk.push(temp);
                temp = temp.previous;
            }
            while (!stk.isEmpty()) {
                try {
                    JSONObject obj = new JSONObject();
                    obj.put("type", "block");
                    obj.put("transaction", "[" + stk.pop().toJson() + "]");
                    System.out.print("block");
                    sendRequest(sendTo, port, obj);
                } catch (IOException ex) {
                    Logger.getLogger(CryptoNode.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    class getBlock extends Thread {

        public void run() {
            Thread otherNodess = new getNodes();
            otherNodess.run();
            int n = otherNodes.size();
            if (n != 0) {
                try {

                    Random r = new Random();
                    String getFromIp = "";
                    do {
                        getFromIp = otherNodes.get(r.nextInt(n));
                    } while (getFromIp.equals(myIp));
                    JSONObject jsonRequest = new JSONObject();
                    jsonRequest.put("type", "getblocks");
                    jsonRequest.put("ip", myIp);
                    sendRequest(getFromIp, port, jsonRequest);
                } catch (IOException ex) {
                    Logger.getLogger(CryptoNode.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    class solveBlock extends Thread {

        String generateRandomString() {
            String s = "";
            Random r = new Random();
            for (int i = 0; i < 10; i++) {
                s = s + ((char) (32 + r.nextInt(80)));
            }
            return s;
        }

        public void run() {
            int i = 0;
            Random r = new Random();
            while (true) {
                try {
                    if (txQueue.size() >= transactionsPerBlock && !newBlockAdded) {
                        try {
                            String blockHash = "";
                            for (int j = 0; j < transactionsPerBlock; j++) {
                                blockHash += encryptPassword(txQueue.get(j).txHash);
                            }
                            boolean nounceFound = false;
                            String nounce = "";
                            while (true) {
                                try {
                                    String temp = generateRandomString();
                                    //   System.out.println(encryptPassword(blockHash + temp).substring(0, 3));
                                    if (encryptPassword(blockHash + temp).substring(0, 3).equals("000")) {
                                        nounceFound = true;
                                        nounce = temp;
                                        break;
                                    }
                                    i++;
                                    if (knowBlockAdded) {
                                        knowBlockAdded = false;
                                        break;
                                    }
                                    if (i > 500) {
                                        Thread.sleep(20000 + r.nextInt(10000));
                                        i = 0;
                                    }
                                } catch (NoSuchAlgorithmException ex) {
                                    Logger.getLogger(CryptoNode.class.getName()).log(Level.SEVERE, null, ex);
                                } catch (UnsupportedEncodingException ex) {
                                    Logger.getLogger(CryptoNode.class.getName()).log(Level.SEVERE, null, ex);
                                } catch (InterruptedException ex) {
                                    Logger.getLogger(CryptoNode.class.getName()).log(Level.SEVERE, null, ex);
                                }
                            }

                            if (nounceFound) {
                                System.out.println("found nounce ip" + myIp);
                                String longestChainLastBlockHash = "";
                                int max_height = 0;
                                for (String key : blockMap.keySet()) {
                                    blockChain block = blockMap.get(key);
                                    if (block.height > max_height) {
                                        max_height = block.height;
                                        longestChainLastBlockHash = block.blockHash;
                                    }
                                }
                                blockChain b = blockMap.get(longestChainLastBlockHash);
                                blockChain newBlock = new blockChain(b);
                                for (int j = 0; j < transactionsPerBlock; j++) {
                                    newBlock.tx.add(txQueue.get(0));
                                    txQueue.remove(0);
                                }
                                newBlock.nounce = nounce;
                                newBlock.addBlockHash();
                                blockMap.put(newBlock.blockHash, newBlock);
                                head = newBlock;
                                Thread broadcastMsg = new broadcast(newBlock.toJson(), "block");
                                broadcastMsg.start();
                            }
                        } catch (NoSuchAlgorithmException ex) {
                            Logger.getLogger(CryptoNode.class.getName()).log(Level.SEVERE, null, ex);
                        } catch (UnsupportedEncodingException ex) {
                            Logger.getLogger(CryptoNode.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                    Thread.sleep(10000);
                } catch (InterruptedException ex) {
                    Logger.getLogger(CryptoNode.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    class getNodes extends Thread {

        public void run() {
            try {
                JSONObject request = new JSONObject();
                request.put("type", "GET");
                request.put("wellknownServers", "1");
                JSONObject response = sendRequest("10.0.0.1", 60500, request);
                JSONArray nodes = (JSONArray) response.get("wellknownServers");
                synchronized (otherNodes) {
                    otherNodes.clear();
                    int n = nodes.size();
                    for (int i = 0; i < n; i++) {
                        otherNodes.add(nodes.get(i).toString());
                        System.out.println(nodes.get(i).toString());
                    }
                }
            } catch (IOException ex) {
                Logger.getLogger(CryptoNode.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    class addToDHT extends Thread {

        public void run() {
            try {
                JSONObject request = new JSONObject();
                request.put("type", "POST");
                request.put("key", acc_number);
                request.put("ip", myIp);
                request.put("publickey", encryptPassword(DatatypeConverter.printBase64Binary(public_key.getEncoded())));
                JSONObject response = sendRequest(dhtIp, 60501, request);
                if (response.get("success").toString().equals("1")) {
                    System.out.println("in success");
                    if (response.get("inserted").toString().equals("1")) {

                    } else {
                        dhtIp = response.get("address").toString();
                        Thread addNearest = new addToDHT();
                        addNearest.start();
                    }
                }
            } catch (Exception e) {

            }
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

    public static void main(String args[]) throws SocketException, NoSuchAlgorithmException, InvalidKeySpecException, IOException, NoSuchProviderException {
        Thread x = new CryptoNode();
        x.start();
        /*KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
        keyGen.initialize(256);
        KeyPair keypair = keyGen.genKeyPair();
        PrivateKey privateKey = keypair.getPrivate();
        PublicKey publicKey = keypair.getPublic();*/
        //System.out.println("07a59b238f1d48de11944f60d4d38525720d6a10");
        //System.out.println(encryptPassword("MEECAQAwEwYHKoZIzj0CAQYIKoZIzj0DAQcEJzAlAgEBBCD9RUt9wVZW+h9/LEvZ2WaPu2J8X/VQB91kS83bgijNaw=="));
    }
}
