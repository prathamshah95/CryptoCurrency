/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cryptocurrency;

import java.net.*;
import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Formatter;
import java.util.Random;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/*import org.json.simple.*;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;*/
public class Transaction {

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

    public static void main(String[] args) throws UnknownHostException, SocketException {
        //  System.out.println(InetAddress.getLocalHost().getAddress()[0]);       

        try {
            /*   System.out.println("Connecting to " + serverName + " on port " + port);
             Socket client = new Socket(serverName, 60500);
             System.out.println("Just connected to " + client.getRemoteSocketAddress());
             OutputStream outToServer = client.getOutputStream();
             DataOutputStream out = new DataOutputStream(outToServer);
             JSONObject obj = new JSONObject();
             JSONArray list = new JSONArray();
             list.add("10.0.0.2");
//             list.add("60101");
//             
//            list.add("60103");
//            list.add("60104");
             
             
             obj.put("type", "POST");
             obj.put("dht", list);
             out.writeUTF("[" + obj.toJSONString() + "]");
System.out.println("hi");
             InputStream inFromServer = client.getInputStream();
             DataInputStream in = new DataInputStream(inFromServer);
             System.out.println(in.readUTF());*/
          // 228695653    497780793   350714631 
             Scanner sc=new Scanner(System.in);
             System.out.println("Enter IP of intermidiate node");
             String IP=sc.nextLine();
             System.out.println("Transfer from ");
             String acc1=sc.nextLine();
             System.out.println("Transfer to ");
             String acc2=sc.nextLine();
             System.out.println("Transfer amount ");
             String amount=sc.nextLine();
            Socket clientt = new Socket(IP, 60502);
            System.out.println("Just connected to " + clientt.getRemoteSocketAddress());
            OutputStream outToServerr = clientt.getOutputStream();
            DataOutputStream outt = new DataOutputStream(outToServerr);
            JSONObject obj = new JSONObject();
            obj.put("inittransaction", "inittransaction");
            obj.put("sender", acc1);//647901142
            obj.put("receiver",acc2);//935765386
            obj.put("bitcoins", amount);
           /*JSONObject transaction = new JSONObject();
            transaction.put("txid", "0");
            JSONArray out = new JSONArray();
            JSONObject out1 = new JSONObject();
            out1.put("bitcoins", "10000");
            out1.put("scriptPubkey", "4ef1ff39b6122e91f3500b9a276befb7f660e82b");
            out.add(out1);
            transaction.put("out", out);
            transaction.put("hash", encryptPassword(transaction.toJSONString()));
            obj.put("first", "[" + transaction.toJSONString() + "]");*/
            outt.writeUTF("[" + obj.toJSONString() + "]");
            InputStream inFromServerr = clientt.getInputStream();
            DataInputStream inn = new DataInputStream(inFromServerr);
            System.out.println(inn.readUTF());
            clientt.close();
            
            /*JSONObject obj=new JSONObject();
                    obj.put("x",null);
                   System.out.println(parseJSON("["+obj.toJSONString()+"]").get("x")==null);*/
            
            
            /* JSONObject obj=new JSONObject();
          JSONObject obj1=new JSONObject();
          obj1.put("hi","hi");
          obj.put("bye", "["+obj1.toJSONString()+"]");
          System.out.println(parseJSON(obj.get("bye").toString()).get("hi").toString());*/
 /* JSONObject obj = new JSONObject();
            obj.put("type", "firsttransaction");

            JSONObject transaction = new JSONObject();
            transaction.put("txid", "0");
            JSONArray out = new JSONArray();
            JSONObject out1 = new JSONObject();
            out1.put("bitcoins", "10000");
            out1.put("scriptPubkey", "");
            out.add(out1);
            transaction.put("out", out);
            transaction.put("hash", encryptPassword(transaction.toJSONString()));
            obj.put("first", "[" + transaction.toJSONString() + "]");*/
 /*  JSONArray a=new JSONArray();
          
          JSONObject d=new JSONObject();
          d.put("hey","by");
          a.add(d);
          obj.put("hi",a);
          JSONObject obj2=new JSONObject();
          obj2.put("obj1","["+obj.toJSONString()+"]");
          JSONObject f=parseJSON(obj2.get("obj1").toString());
          System.out.println(((JSONObject)((JSONArray)f.get("hi")).get(0)).get("hey").toString());*/
 /* Socket clientt = new Socket("10.0.0.4", 60501);
           System.out.println("Just connected to " + clientt.getRemoteSocketAddress());

            OutputStream outToServerr = clientt.getOutputStream();
            DataOutputStream outt = new DataOutputStream(outToServerr);
            JSONObject objj = new JSONObject();
            objj.put("type", "GET");
            objj.put("key", "100000");
           //objj.put("ip", "10.0.0.5");
            //objj.put("publickey", "1dnkdvnkfdnkfdnk");
            outt.writeUTF("[" + objj.toJSONString() + "]");
            InputStream inFromServerr = clientt.getInputStream();
            DataInputStream inn = new DataInputStream(inFromServerr);
            System.out.println(inn.readUTF());
            clientt.close();
             */
 /*Socket socket = new Socket("localhost", 60104);
             DataOutputStream out = createOutputStream(socket);
             JSONObject deleteDHT = new JSONObject();
             deleteDHT.put("type", "DELETE");
             deleteDHT.put("dht", 60103);
             out.writeUTF("[" + deleteDHT.toJSONString() + "]");
             DataInputStream in = createInputStream(socket); 
             System.out.println(in.readUTF());*/

 /*for (int i = 60101; i < 60105; i++) {
                if (i != 60102) {
                    Socket socket = new Socket("localhost", i);
                    DataOutputStream out = createOutputStream(socket);
                    JSONObject deleteDHT = new JSONObject();
                    deleteDHT.put("type", "POST");
                    deleteDHT.put("dht", "60102");
                    out.writeUTF("[" + deleteDHT.toJSONString() + "]");
                    DataInputStream in = createInputStream(socket);
                    System.out.println(in.readUTF());
                }
            }*/
 /*   int i = 0;
            String s = "nknerknlnfkdngfkdngfdkngfdkndgfkndfkngfdkgndfjgnfjfdkndfkbdfkbfd";
            while (i < 100000000) {
                String temp=generateRandomString();                
                System.out.println(encryptPassword(s+temp).substring(0, 3));
                      if(encryptPassword(s+temp).substring(0, 3).equals("000")){
                      System.out.println(temp);
                      System.out.println(i);
                      break;
                      }
                i++;
            }*/
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    static String generateRandomString() {
        String s = "";
        Random r = new Random();

        for (int i = 0; i < 10; i++) {
            s = s + ((char) (32 + r.nextInt(80)));
        }
        return s;
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

