package com.xncoding.jwt.socket.client;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelExec;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.session.ClientSession;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;

/**
 * Created by jerry on 2019/1/4.
 */
public class SSHDemo {
    public static void main(String[] args) throws IOException {
        SshClient client = SshClient.setUpDefaultClient();
        client.start();

        try (ClientSession session = client.connect("root", "192.168.136.132", 22)
                .verify(7L, TimeUnit.SECONDS)
                .getSession()) {
            System.out.println("##########################");
            System.out.println(session);

            session.addPasswordIdentity("123456");
            session.auth().verify(7L, TimeUnit.SECONDS);

            ChannelExec ls = session.createExecChannel("ls");
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            ls.setOut(byteArrayOutputStream);
            ls.open();

            ls.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), TimeUnit.SECONDS.toMillis(15L));

            System.out.println("byteArrayOutputStream.toString():");
            System.out.println(byteArrayOutputStream.toString());

            ls.close();

        } catch (Exception e) {

        }
    }
}
