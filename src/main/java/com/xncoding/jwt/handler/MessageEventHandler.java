package com.xncoding.jwt.handler;

import com.alibaba.fastjson.JSON;
import com.corundumstudio.socketio.AckRequest;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.annotation.OnConnect;
import com.corundumstudio.socketio.annotation.OnDisconnect;
import com.corundumstudio.socketio.annotation.OnEvent;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;
import com.xncoding.jwt.model.ChatMessage;
import com.xncoding.jwt.model.LoginRequest;
import com.xncoding.jwt.model.TermSize;
import com.xncoding.jwt.model.WebsocketSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 消息事件处理器
 *
 * @author XiongNeng
 * @version 1.0
 * @since 2018/1/19
 */
@Component
public class MessageEventHandler {

    private static final String USER = "root";
    private static final String PASSWORD = "123456";
    private static final String HOST = "192.168.136.132";
    private static final int DEFAULT_SSH_PORT = 22;

    static Map<String, WebsocketSession> sessions = new ConcurrentHashMap<>(16);

    private final SocketIOServer server;

    private static final Logger logger = LoggerFactory.getLogger(MessageEventHandler.class);

    @Autowired
    public MessageEventHandler(SocketIOServer server) {
        this.server = server;
    }

    /**
     * 添加connect事件，当客户端发起连接时调用
     */
    @OnConnect
    public void onConnect(SocketIOClient client) {
        if (client != null) {
            String username = client.getHandshakeData().getSingleUrlParam("username");
            String password = client.getHandshakeData().getSingleUrlParam("password");
            String sessionId = client.getSessionId().toString();
            logger.info("连接成功, username=" + username + ", password=" + password + ", sessionId=" + sessionId);
            System.out.println("sessionId:" + client.getSessionId().toString());

            if (sessions.get(client.getSessionId().toString()) != null) {
                return;
            }

            // 获取认证权限

            try {
                JSch jsch = new JSch();
                Session session = jsch.getSession(USER, HOST, DEFAULT_SSH_PORT);
                session.setPassword(PASSWORD);

                UserInfo userInfo = new UserInfo() {
                    @Override
                    public String getPassphrase() {
                        System.out.println("getPassphrase");
                        return null;
                    }

                    @Override
                    public String getPassword() {
                        System.out.println("getPassword");
                        return null;
                    }

                    @Override
                    public boolean promptPassword(String s) {
                        System.out.println("promptPassword:" + s);
                        return false;
                    }

                    @Override
                    public boolean promptPassphrase(String s) {
                        System.out.println("promptPassphrase:" + s);
                        return false;
                    }

                    @Override
                    public boolean promptYesNo(String s) {
                        System.out.println("promptYesNo:" + s);
                        //notice here!
                        return true;
                    }

                    @Override
                    public void showMessage(String s) {
                        System.out.println("showMessage:" + s);
                    }
                };

                session.setUserInfo(userInfo);
                session.connect(30000);

                ChannelShell channel = (ChannelShell) session.openChannel("shell");

                PipedOutputStream out = new PipedOutputStream();
                PipedInputStream in = new PipedInputStream(out);
                channel.setInputStream(in);

                PipedInputStream inputStream = new PipedInputStream();
                PipedOutputStream outputStream = new PipedOutputStream(inputStream);
                channel.setOutputStream(outputStream);

                channel.connect(3 * 1000);

                WebsocketSession websocketSession = new WebsocketSession(inputStream, out, channel, session);
                sessions.put(client.getSessionId().toString(), websocketSession);

                new Thread(new OutHandler(client, websocketSession)).start();

                Map<String, Object> setTerminalOpts = new HashMap<>(8);
                setTerminalOpts.put("cursorBlink", true);
                setTerminalOpts.put("scrollback", 10000);
                setTerminalOpts.put("tabStopWidth", 8);
                setTerminalOpts.put("bellStyle", "sound");
                client.sendEvent("setTerminalOpts", setTerminalOpts);

                client.sendEvent("menu", "menu");
                client.sendEvent("status", "SSH CONNECTION ESTABLISHED");
                client.sendEvent("statusBackground", "green");
                client.sendEvent("allowreplay", true);
                client.sendEvent("allowreauth", true);

            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            logger.error("客户端为空");
        }
    }

    /**
     * 添加@OnDisconnect事件，客户端断开连接时调用，刷新客户端信息
     */
    @OnDisconnect
    public void onDisconnect(SocketIOClient client) {
        logger.info("客户端断开连接, sessionId=" + client.getSessionId().toString());
        WebsocketSession session = sessions.get(client.getSessionId().toString());
        if (session != null) {
            session.getChannel().disconnect();
            session.getSession().disconnect();

            sessions.remove(client.getSessionId().toString());
        }
        client.disconnect();
    }

    @OnEvent(value = "data")
    public void onDataEvent(SocketIOClient client, AckRequest ackRequest, String data) {
        WebsocketSession session = sessions.get(client.getSessionId().toString());
        if (session != null) {
            try {
                session.getSocketIn().write(data.getBytes());
                session.getSocketIn().flush();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @OnEvent(value = "resize")
    public void onResize(SocketIOClient client, AckRequest ackRequest, TermSize termSize) {
        WebsocketSession session = sessions.get(client.getSessionId().toString());
        if (session != null) {
            try {
                System.out.println(JSON.toJSON(termSize));
                session.getChannel().setPtySize(termSize.getCols(), termSize.getRows(), 640, 480);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @OnEvent(value = "close")
    public void onClose(SocketIOClient client) {
        WebsocketSession session = sessions.get(client.getSessionId().toString());
        if (session != null) {
            try {
                session.getChannel().disconnect();
                session.getSession().disconnect();

                sessions.remove(client.getSessionId().toString());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 消息接收入口
     */
    @OnEvent(value = "chatevent")
    public void onEvent(SocketIOClient client, AckRequest ackRequest, ChatMessage chat) {
        logger.info("接收到客户端消息");
        if (ackRequest.isAckRequested()) {
            // send ack response with data to client
            ackRequest.sendAckData("服务器回答chatevent, userName=" + chat.getUserName() + ",message=" + chat.getMessage());

            WebsocketSession session = sessions.get(client.getSessionId().toString());
            if (session != null) {
                try {
                    System.out.println("message:");
                    System.out.println(chat.getMessage() + "\n");
                    session.getSocketIn().write((chat.getMessage() + "\n").getBytes());
                    session.getSocketIn().flush();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 登录接口
     */
    @OnEvent(value = "login")
    public void onLogin(SocketIOClient client, AckRequest ackRequest, LoginRequest message) {
        logger.info("接收到客户端登录消息");
        if (ackRequest.isAckRequested()) {
            // send ack response with data to client
            ackRequest.sendAckData("服务器回答login", message.getCode(), message.getBody());
        }
    }
}

class OutHandler implements Runnable {

    private SocketIOClient client;
    private WebsocketSession session;

    public OutHandler(SocketIOClient client, WebsocketSession session) {
        this.client = client;
        this.session = session;
    }

    @Override
    public void run() {
        try {
            byte[] buf = new byte[1024];
            InputStream inputStream = session.getSocketOut();
            while (true) {
                int len = inputStream.read(buf);
                if (len == -1) {
                    client.disconnect();
                    session.getChannel().disconnect();
                    session.getSession().disconnect();

                    MessageEventHandler.sessions.remove(client.getSessionId().toString());

                    break;
                }

                client.sendEvent("data", new String(buf, 0, len, StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
