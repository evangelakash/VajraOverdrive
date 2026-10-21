package vajra.net;

import java.net.SocketAddress;
import java.util.ArrayDeque;

/** Two transports wired mouth-to-tail in memory: whatever one sends lands in the other's
 *  poll() queue. The headless session tests run a host and a client through a pair of
 *  these — the protocol runs for real, the wire is an ArrayDeque. */
public final class LoopbackTransport implements Transport {
    private final ArrayDeque<Received> in = new ArrayDeque<>();
    private LoopbackTransport peer;

    public static void pair(LoopbackTransport a, LoopbackTransport b) {
        a.peer = b;
        b.peer = a;
    }

    @Override
    public void send(byte[] data, SocketAddress to) {
        if (peer != null) peer.in.add(new Received(data, to)); // the "address" is whatever the caller claimed
    }

    @Override
    public Received poll() {
        return in.pollFirst();
    }

    @Override
    public void close() {
        peer = null;
    }
}
