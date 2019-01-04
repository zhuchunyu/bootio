package com.xncoding.jwt.socket.client;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelShell;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.config.hosts.HostConfigEntryResolver;
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.common.util.io.NoCloseInputStream;
import org.apache.sshd.common.util.io.NoCloseOutputStream;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;

public class SSHDTest {
    private static final int port = 22;

    public static void main(String[] args) throws InterruptedException, IOException {
        SshClient client = SshClient.setUpDefaultClient();
        client.setServerKeyVerifier(AcceptAllServerKeyVerifier.INSTANCE);
        client.setHostConfigEntryResolver(HostConfigEntryResolver.EMPTY);
        client.setKeyPairProvider(KeyPairProvider.EMPTY_KEYPAIR_PROVIDER);
        client.start();

        ClientSession session = client.connect("root",
                new InetSocketAddress("192.168.136.132", port))
                .verify(7L, TimeUnit.SECONDS).getSession();
        session.addPasswordIdentity("123456");
        session.auth().verify(7L, TimeUnit.SECONDS);

        ChannelShell channel =session.createShellChannel();
        channel.setOut(new NoCloseOutputStream(System.out));
        channel.setErr(new NoCloseOutputStream(System.err));
        channel.setIn(new NoCloseInputStream(System.in));
        channel.open();

        channel.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), TimeUnit.SECONDS.toMillis(20L));
        channel.close(false);
        session.close(false);
        client.stop();
    }
}
