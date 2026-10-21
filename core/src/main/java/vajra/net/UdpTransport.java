package vajra.net;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.util.concurrent.ConcurrentLinkedQueue;

/** The real wire: one UDP socket, a daemon thread draining it into a queue. Sends are
 *  best effort and receives queue for the session's poll — nothing here knows about the
 *  protocol. close() stops the thread by closing the socket, which unblocks the read. */
public final class UdpTransport implements Transport {
    public static final int PORT = 26000;

    private final DatagramSocket socket;
    private final ConcurrentLinkedQueue<Received> in = new ConcurrentLinkedQueue<>();
    private volatile boolean open = true;

    /** Binds port 0 for an ephemeral socket (clients), the fixed port to be a hub. */
    public UdpTransport(int port) throws Exception {
        socket = new DatagramSocket(port);
        Thread reader = new Thread(this::drain, "vajra-net");
        reader.setDaemon(true);
        reader.start();
    }

    private void drain() {
        byte[] buf = new byte[2048]; // the swarm snapshot is the largest plane (~1.4 KB)
        while (open) {
            try {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                socket.receive(p); // blocks; close() unblocks it with a socket-closed error
                byte[] data = new byte[p.getLength()];
                System.arraycopy(p.getData(), p.getOffset(), data, 0, data.length);
                while (in.size() >= 256) in.poll(); // a stalled app must not bank packets forever
                in.add(new Received(data, p.getSocketAddress()));
            } catch (Exception e) {
                if (!open) return; // the close race, not a bad packet
            }
        }
    }

    /** The bound port (0 asks for an ephemeral one and this reports what landed). */
    public int port() {
        return socket.getLocalPort();
    }

    @Override
    public void send(byte[] data, SocketAddress to) {
        if (!open) return;
        try {
            socket.send(new DatagramPacket(data, data.length, to));
        } catch (Exception e) {
            // best effort: the protocol's retries carry a lost packet
        }
    }

    @Override
    public Received poll() {
        return in.poll();
    }

    @Override
    public void close() {
        open = false;
        socket.close();
    }
}
