package com.test.screenimage.net;

import android.util.Log;

import com.test.screenimage.MyApplication;
import com.test.screenimage.entity.ReceiveData;
import com.test.screenimage.entity.ReceiveHeader;
import com.test.screenimage.stream.sender.tcp.EncodeV1;
import com.test.screenimage.utils.AnalyticDataUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;

/**
 * Created by wt
 * Date on  2018/7/4
 *
 * @Desc
 */

public class RequestTcp extends Thread {
    private int mainCmd;
    private int subCmd;
    private byte[] bytes;
    private String ip;
    private int port;
    private String sendBody;
    private Socket mSocket;
    private AnalyticDataUtils mAnalyticDataUtils;
    private OnTcpSendMessageListner mListener;
    private int connectSoTime;
    private OutputStream outputStream;
    private InputStream inputStream;

    public RequestTcp(String ip, int port, int mainCmd, int subCmd, String sendBody, byte[] bytes,
                      int connectSoTime, OnTcpSendMessageListner listener) {
        this.ip = ip;
        this.port = port;
        this.mainCmd = mainCmd;
        this.subCmd = subCmd;
        this.sendBody = sendBody;
        this.mListener = listener;
        this.bytes = bytes;
        this.connectSoTime = connectSoTime;
    }

    @Override
    public void run() {
        try {
            initialSendMessage(mainCmd, subCmd, sendBody, bytes, connectSoTime);
        } catch (final Exception e) {
            Log.e("wtt", e.toString());
            if (MyApplication.mHandler != null) {
                MyApplication.mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mListener != null) {
                            mListener.error(e);
                        }
                    }
                });
            }
        }
    }

    /**
     * 发送消息
     *
     * @param mainCmd       主命令
     * @param subCmd        子命令
     * @param sendBody      发送消息
     * @param connectSoTime 超时时间
     * @throws Exception throws Exception
     */
    private void initialSendMessage(int mainCmd, int subCmd, String sendBody,
                                    byte[] bytes, int connectSoTime) throws Exception {
        if (mAnalyticDataUtils == null) {
            mAnalyticDataUtils = new AnalyticDataUtils();
        }
        mSocket = new Socket();
        mSocket.setReuseAddress(true);
        SocketAddress socketAddress = new InetSocketAddress(ip, port);
        mSocket.connect(socketAddress, connectSoTime);
        mSocket.setSoTimeout(60000);    //此方法意为tcp连接成功后is.read阻塞多长时间
        outputStream = mSocket.getOutputStream();
        EncodeV1 encodeV1 = new EncodeV1(mainCmd, subCmd, sendBody, bytes);
        outputStream.write(encodeV1.buildSendContent());
        outputStream.flush();
        inputStream = mSocket.getInputStream();
        byte[] tempBytes = mAnalyticDataUtils.readByte(inputStream, 18);
        ReceiveHeader receiveHeader = mAnalyticDataUtils.analysisHeader(tempBytes);
        ReceiveData receiveData = mAnalyticDataUtils.synchAnalyticData(inputStream, receiveHeader);
        if (mListener != null) {
            MyApplication.mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mListener.success(receiveData.getHeader().getMainCmd(), receiveData.getHeader().getSubCmd(),
                            receiveData.getSendBody(), receiveData.getBuff());
                }
            });
        }
        mSocket.close();


    }

    // TODO: 2018/7/25 关闭
    public void shutdown() {
        try {
            if (outputStream != null) outputStream.close();
            if (inputStream != null) inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        clearSocket();
        this.interrupt();
    }

    private void clearSocket() {
        if (mSocket != null && (!mSocket.isClosed())) {
            try {
                mSocket.close();
                mSocket = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
