package vajra.net;

import java.net.SocketAddress;

/** The one socket abstraction the session talks through — the whole protocol runs
 *  against this, so the headless tests can substitute a {@link LoopbackTransport} and
 *  walk the handshake with no network at all. Implementations: the in-memory pair for
 *  tests, and {@link UdpTransport} for the real wire. */
public interface Transport {

    /** A packet that arrived, and where it came from. */
    final class Received {
        public final byte[] data;
        public final SocketAddress from;

        public Received(byte[] data, SocketAddress from) {
            this.data = data;
            this.from = from;
        }
    }

    /** Fire a packet. Best effort, like the wire: the protocol's repetitions carry loss. */
    void send(byte[] data, SocketAddress to);

    /** The next packet in line, or null when the buffer is dry. Non-blocking. */
    Received poll();

    /** Stop the transport; sends after this are silently dropped. */
    void close();
}
