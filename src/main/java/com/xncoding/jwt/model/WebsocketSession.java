package com.xncoding.jwt.model;

import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.Session;

import java.io.InputStream;
import java.io.OutputStream;

/**
 *
 * @author yuz
 * @date 2019/1/2
 */
public class WebsocketSession {
    private InputStream socketOut;
    private OutputStream socketIn;
    private ChannelShell channel;
    private Session session;

    public WebsocketSession(InputStream socketOut, OutputStream socketIn,
                            ChannelShell channel, Session session) {
        this.socketOut = socketOut;
        this.socketIn = socketIn;
        this.channel = channel;
        this.session = session;
    }

    public InputStream getSocketOut() {
        return socketOut;
    }

    public void setSocketOut(InputStream socketOut) {
        this.socketOut = socketOut;
    }

    public OutputStream getSocketIn() {
        return socketIn;
    }

    public void setSocketIn(OutputStream socketIn) {
        this.socketIn = socketIn;
    }

    public ChannelShell getChannel() {
        return channel;
    }

    public void setChannel(ChannelShell channel) {
        this.channel = channel;
    }

    public Session getSession() {
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
    }
}
