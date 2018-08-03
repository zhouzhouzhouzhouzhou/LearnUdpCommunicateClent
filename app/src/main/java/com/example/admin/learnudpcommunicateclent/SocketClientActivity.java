package com.example.admin.learnudpcommunicateclent;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;


import com.example.admin.udpclient.R;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author zhou.jn
 */
public class SocketClientActivity extends Activity {
    private static int BROADCAST_PORT = 9999;
    private static final String TAG = "SocketConnectActivity";
    private TextView ipInfo;
    private static String IP;
    private boolean isRunning = true;
    private Button btnBack;
    private DatagramSocket receiveSocket = null;
    private DatagramSocket sendSocket = null;
    private DatagramPacket dpReceive = null;
    private SendThread sendThread;
    private String previousContent = new String();
    private Button btnClear;
    private String receiveIp;
    private String sendIp;
    @SuppressLint("HandlerLeak")
    Handler myHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 1:
                    Log.i(TAG, "run handleMessage: ");
                    ipInfo.append(msg.obj.toString() + "\n");
                    /**通过确认该客户端已经接收到信息后，再将自己的ip号码发送出去*/
                    new Thread(){
                        @Override
                        public void run() {
                            try {
                                Thread.sleep(1500);
                                /**开一个将前一次接收的内容置为空的线程，解决发送端两次发送相同的信息，接收端他可能就不会做处理的问题*/
                                previousContent = " ";
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }.start();
                    sendFeedBackToServer(sendThread.mHandler, msg.obj.toString());
                    break;
                case 2:
                    ipInfo.append(msg.obj.toString());
                default:
                    break;
            }
        }
    };


    private void sendFeedBackToServer(Handler mHandler, String ip) {
        Message msg = new Message();
        msg.obj = ip;
        msg.what = 1;
        mHandler.sendMessage(msg);
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        ipInfo = findViewById(R.id.ip_info);
        btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
        btnClear = findViewById(R.id.btnClear);
        btnClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ipInfo.setText("");
            }
        });
        ReceiveThread receiveThread = new ReceiveThread();
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager.isWifiEnabled()) {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            IP = getIpString(wifiInfo.getIpAddress());
            System.out.println("本机IP is :" + IP);
        }
        try {
            receiveSocket = new DatagramSocket(BROADCAST_PORT);
            sendThread = new SendThread();
            sendThread.start();
            receiveThread.start();
        } catch (Exception e1) {
            e1.printStackTrace();
        }
    }

    /**
     * 将获取到的int型ip转成string类型
     */
    private String getIpString(int i) {
        return (i & 0xFF) + "." + ((i >> 8) & 0xFF) + "."
                + ((i >> 16) & 0xFF) + "." + (i >> 24 & 0xFF);
    }

    private class ReceiveThread extends Thread {
        @SuppressLint("LongLogTag")
        @Override
        public void run() {
            while (true) {
                if (isRunning) {
                    String receiveContent = null;
                    byte[] buf = new byte[1024];
                    dpReceive = new DatagramPacket(buf, buf.length);
                    try {
                        receiveSocket.receive(dpReceive);
                        receiveContent = new String(buf, 0, dpReceive.getLength());
                        Log.i(TAG, "run: receive message " + receiveContent);
                        Log.i(TAG, "run: " + dpReceive.getAddress().toString());
                        receiveIp = dpReceive.getAddress().toString().substring(1);
                        if (receiveIp != IP) {
                            sendIp = receiveIp;
                        }
                        Log.i(TAG, "run:1 previousContent " + previousContent + " receiveContent " + receiveContent);
                        if (!previousContent.equals(receiveContent) && !IP.equals(receiveIp)) {
                            Message msg = myHandler.obtainMessage();
                            msg.obj = "服务器：" + sendIp + "发送的内容为：" + receiveContent;
                            msg.what = 1;
                            myHandler.sendMessage(msg);
                        }
                        previousContent = receiveContent;
                        Log.i(TAG, "run:2 previousContent " + previousContent + " receiveContent " + receiveContent);

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private class SendThread extends Thread {
        private Handler mHandler;

        @SuppressLint("HandlerLeak")
        @Override
        public void run() {
            Looper.prepare();
            mHandler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    DatagramPacket dpSend = null;
                    byte[] ip = IP.getBytes();
                    try {
                        if (receiveIp != IP) {
                            InetAddress inetAddress = InetAddress.getByName(sendIp);
                            dpSend = new DatagramPacket(ip, ip.length, inetAddress, BROADCAST_PORT);
                        }
                    } catch (UnknownHostException e) {
                        e.printStackTrace();
                    }
                    try {
                        String sendData = new String(ip, 0, dpSend.getLength());
                        sendSocket = new DatagramSocket();
                        sendSocket.send(dpSend);
                        Log.i(TAG, "run: send message : " + sendData);
                        sendSocket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            };
            Looper.loop();
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        receiveSocket.close();
        isRunning = false;
        System.out.println("UDP Client程序退出,关掉socket,停止广播");
        finish();
    }
}