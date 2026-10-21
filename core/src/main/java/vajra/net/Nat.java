package vajra.net;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * THE DOOR OPENER — internet play with no port forwarding, no setup file and no second
 * program. Three standard mechanisms, tried in the order that works on the most routers:
 *
 * <ol>
 *   <li><b>UPnP</b> (SSDP discovery + SOAP AddPortMapping): the router is told to forward
 *       this machine's UDP port to it, and asked for its public address. The most widely
 *       supported, and the one that makes the invite a plain "public address:port".</li>
 *   <li><b>NAT-PMP</b> (RFC 6886): the same thing over a two-byte UDP request to the
 *       gateway, for routers that answer it and refuse UPnP.</li>
 *   <li><b>STUN</b> (RFC 5389): no mapping, just the address the internet sees this
 *       machine at — enough for a hole-punching peer behind a cone NAT.</li>
 * </ol>
 *
 * <p>Every step is bounded by a timeout and every failure is a line of text, never an
 * exception at the player. Nothing here is required for LAN play: the LAN answer is the
 * machine's own address and needs no door at all.
 */
public final class Nat {

    /** How long any single step may take. The whole opener is bounded by a few seconds. */
    private static final int TIMEOUT_MS = 1500;
    /** How long the SSDP hunt listens for a gateway's answer. */
    private static final int SSDP_MS = 2500;

    /** The public STUN servers used only to learn the address the internet sees. */
    private static final String[] STUN_SERVERS = {
            "stun.l.google.com:19302", "stun1.l.google.com:19302" };

    private Nat() {}

    /** What the opener found. */
    public static final class Result {
        /** The address the internet sees this machine at, or "" when it could not be found. */
        public final String address;
        /** The port the internet sees. 0 when there is no mapping. */
        public final int port;
        /** True when the router was told to forward the port to this machine. */
        public final boolean mapped;
        /** How it was done, for the UI: "UPnP", "NAT-PMP", "STUN" or "". */
        public final String method;
        /** One line a player can read. */
        public final String note;

        Result(String address, int port, boolean mapped, String method, String note) {
            this.address = address;
            this.port = port;
            this.mapped = mapped;
            this.method = method;
            this.note = note;
        }

        /** Is there an address the internet can knock on? */
        public boolean reachable() {
            return !address.isEmpty() && port > 0;
        }
    }

    /**
     * Open the door: map the local UDP port on the router and learn the public address.
     * Never throws — a machine that cannot be reached from the internet still gets a result
     * that says so, and the lobby falls back to the relay.
     */
    public static Result open(int localPort) {
        Result upnp = upnp(localPort);
        if (upnp.mapped && !upnp.address.isEmpty()) return upnp;
        Result pmp = natPmp(localPort);
        if (pmp.mapped && !pmp.address.isEmpty()) return pmp;
        if (!upnp.address.isEmpty()) return upnp; // an address without a mapping still helps
        if (!pmp.address.isEmpty()) return pmp;
        Result stun = stun(localPort);
        if (!stun.address.isEmpty()) return stun;
        return new Result("", 0, false, "", "NO PUBLIC ADDRESS — A RELAY IS NEEDED");
    }

    // ---- UPnP ---------------------------------------------------------------------------------

    /** The SOAP service that owns the WAN port. */
    static final class Gateway {
        String host;
        int port = 80;
        String control = "/";
        String service = "urn:schemas-upnp-org:service:WANIPConnection:1";
    }

    /** UPnP: hunt for the gateway, map the port, ask for the public address. */
    private static Result upnp(int localPort) {
        Gateway gw = discoverGateway();
        if (gw == null) return new Result("", 0, false, "", "NO UPnP GATEWAY ANSWERED");
        boolean ok = false;
        String note = "UPnP GATEWAY FOUND, NO PORT MAPPING";
        try {
            String local = localAddress();
            String body = soap(gw, "AddPortMapping",
                    "<NewRemoteHost></NewRemoteHost>"
                  + "<NewExternalPort>" + localPort + "</NewExternalPort>"
                  + "<NewProtocol>UDP</NewProtocol>"
                  + "<NewInternalPort>" + localPort + "</NewInternalPort>"
                  + "<NewInternalClient>" + local + "</NewInternalClient>"
                  + "<NewEnabled>1</NewEnabled>"
                  + "<NewPortMappingDescription>Vajra Overdrive</NewPortMappingDescription>"
                  + "<NewLeaseDuration>0</NewLeaseDuration>");
            ok = body != null && !body.contains("AddPortMappingUnavailable")
                    && !body.contains("NewExternalPort");
        } catch (Exception e) {
            ok = false;
        }
        String ip = "";
        try {
            String body = soap(gw, "GetExternalIPAddress", "");
            if (body != null) {
                int a = body.indexOf("<NewExternalIPAddress>");
                int b = body.indexOf("</NewExternalIPAddress>");
                if (a >= 0 && b > a) ip = body.substring(a + 21, b).trim();
            }
        } catch (Exception ignored) {
            // the address is a bonus: the mapping is what matters
        }
        if (ok) note = "UPnP PORT OPENED ON THE ROUTER";
        else if (!ip.isEmpty()) note = "UPnP ADDRESS FOUND, PORT NOT OPENED";
        return new Result(ip, ok ? localPort : 0, ok, "UPnP", note);
    }

    /** The SSDP hunt: shout for a gateway, collect the answers, read the one that serves WAN. */
    private static Gateway discoverGateway() {
        List<String> locations = ssdp();
        for (String loc : locations) {
            Gateway gw = fromLocation(loc);
            if (gw == null) continue;
            try {
                String xml = httpGet(gw.host, gw.port, locationPath(loc));
                if (xml == null) continue;
                String ctl = controlUrl(xml);
                if (ctl != null) {
                    gw.control = ctl;
                    if (xml.contains("WANPPPConnection")) gw.service = "urn:schemas-upnp-org:service:WANPPPConnection:1";
                    return gw;
                }
            } catch (Exception ignored) {
                // this gateway did not answer; try the next one
            }
        }
        return null;
    }

    /** The M-SEARCH shout and the answers it collects. */
    static List<String> ssdp() {
        List<String> out = new ArrayList<>();
        DatagramSocket sock = null;
        try {
            sock = new DatagramSocket();
            sock.setSoTimeout(300);
            sock.setBroadcast(true);
            byte[] req = ("M-SEARCH * HTTP/1.1\r\n"
                    + "HOST: 239.255.255.250:1900\r\n"
                    + "MAN: \"ssdp:discover\"\r\n"
                    + "MX: 2\r\n"
                    + "ST: urn:schemas-upnp-org:device:InternetGatewayDevice:1\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII);
            List<InetAddress> targets = new ArrayList<>();
            try {
                targets.add(InetAddress.getByName("239.255.255.250"));
            } catch (Exception ignored) {
                // no multicast on this stack
            }
            targets.addAll(LanBeacon.lanAddresses()); // some gateways answer the segment shout
            targets.add(LanBeacon.limitedBroadcast());
            for (InetAddress t : targets) {
                try {
                    sock.send(new DatagramPacket(req, req.length, new InetSocketAddress(t, 1900)));
                } catch (Exception ignored) {
                    // a target that refuses the shout is not fatal
                }
            }
            long until = System.currentTimeMillis() + SSDP_MS;
            byte[] buf = new byte[1024];
            while (System.currentTimeMillis() < until) {
                try {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    sock.receive(p);
                    String body = new String(p.getData(), 0, p.getLength(), StandardCharsets.US_ASCII);
                    for (String line : body.split("\r\n")) {
                        if (line.toLowerCase(java.util.Locale.ROOT).startsWith("location:")) {
                            String loc = line.substring(9).trim();
                            if (!out.contains(loc)) out.add(loc);
                        }
                    }
                } catch (SocketTimeoutException quiet) {
                    // keep listening until the window closes
                }
            }
        } catch (Exception ignored) {
            // no socket: no UPnP
        } finally {
            if (sock != null) sock.close();
        }
        return out;
    }

    /** The host, port and path of a "http://ip:port/path" location. */
    static Gateway fromLocation(String loc) {
        Gateway gw = new Gateway();
        try {
            java.net.URI u = java.net.URI.create(loc);
            gw.host = u.getHost();
            gw.port = u.getPort() > 0 ? u.getPort() : 80;
        } catch (Exception e) {
            return null;
        }
        return gw.host == null ? null : gw;
    }

    private static String locationPath(String loc) {
        try {
            String path = java.net.URI.create(loc).getPath();
            return path == null || path.isEmpty() ? "/" : path;
        } catch (Exception e) {
            return "/";
        }
    }

    /** The control URL of the WAN service in a device description. */
    static String controlUrl(String xml) {
        // the WAN service's block: <serviceType>urn:...:WANIPConnection:1</serviceType>
        // followed by its <controlURL>
        int at = xml.indexOf("WANIPConnection");
        if (at < 0) at = xml.indexOf("WANPPPConnection");
        if (at < 0) return null;
        int end = xml.indexOf("</service>", at);
        String block = end > at ? xml.substring(at, end) : xml.substring(at);
        int c = block.indexOf("<controlURL>");
        if (c < 0) return null;
        int d = block.indexOf("</controlURL>", c);
        if (d < 0) return null;
        String url = block.substring(c + 12, d).trim();
        return url.isEmpty() ? "/" : url;
    }

    /** One SOAP call against the gateway. The body comes back as text, or null. */
    private static String soap(Gateway gw, String action, String args) throws Exception {
        String body = "<?xml version=\"1.0\"?>"
                + "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\">"
                + "<s:Body><u:" + action + " xmlns:u=\"" + gw.service + "\">"
                + args + "</u:" + action + "></s:Body></s:Envelope>";
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        String head = "POST " + gw.control + " HTTP/1.1\r\n"
                + "Host: " + gw.host + ":" + gw.port + "\r\n"
                + "SOAPAction: \"" + gw.service + "#" + action + "\"\r\n"
                + "Content-Type: text/xml; charset=\"utf-8\"\r\n"
                + "Content-Length: " + payload.length + "\r\n"
                + "Connection: close\r\n\r\n";
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress(gw.host, gw.port), TIMEOUT_MS);
            s.setSoTimeout(TIMEOUT_MS);
            OutputStream out = s.getOutputStream();
            out.write(head.getBytes(StandardCharsets.US_ASCII));
            out.write(payload);
            out.flush();
            return readAll(s.getInputStream());
        } finally {
            try {
                s.close();
            } catch (Exception ignored) {
                // the socket is going away anyway
            }
        }
    }

    /** A plain HTTP GET. */
    private static String httpGet(String host, int port, String path) throws Exception {
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
            s.setSoTimeout(TIMEOUT_MS);
            OutputStream out = s.getOutputStream();
            out.write(("GET " + path + " HTTP/1.1\r\nHost: " + host + "\r\n"
                    + "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            return readAll(s.getInputStream());
        } finally {
            try {
                s.close();
            } catch (Exception ignored) {
                // the socket is going away anyway
            }
        }
    }

    private static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int n;
        while ((n = in.read(chunk)) > 0) buf.write(chunk, 0, n);
        return new String(buf.toByteArray(), StandardCharsets.UTF_8);
    }

    // ---- NAT-PMP ------------------------------------------------------------------------------

    /** NAT-PMP: the gateway's own two-byte language. The reply carries the public address
     *  and, for a mapping, the port the internet sees. */
    static Result natPmp(int localPort) {
        DatagramSocket sock = null;
        try {
            sock = new DatagramSocket();
            sock.setSoTimeout(TIMEOUT_MS);
            sock.setBroadcast(true);
            // 0 = the external address; 2 = map a UDP port
            byte[] addrReq = new byte[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
            byte[] mapReq = new byte[] { 0, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
            mapReq[2] = (byte) (localPort >> 8);
            mapReq[3] = (byte) localPort;   // the internal port
            mapReq[4] = (byte) (localPort >> 8);
            mapReq[5] = (byte) localPort;   // the suggested external port
            mapReq[6] = (byte) (0 >> 8);
            mapReq[7] = (byte) 0;          // a zero lease asks for a permanent mapping

            for (InetAddress target : gateways()) {
                try {
                    sock.send(new DatagramPacket(addrReq, addrReq.length,
                            new InetSocketAddress(target, 5351)));
                    byte[] reply = receive(sock, 32);
                    if (reply != null && reply.length >= 12 && (reply[0] & 0xFF) == 128) {
                        String ip = ipOf(reply, 8);
                        if (!ip.isEmpty()) {
                            sock.send(new DatagramPacket(mapReq, mapReq.length,
                                    new InetSocketAddress(target, 5351)));
                            byte[] mapped = receive(sock, 32);
                            if (mapped != null && mapped.length >= 16
                                    && (mapped[0] & 0xFF) == 130) {
                                int external = (mapped[8] & 0xFF) << 8 | (mapped[9] & 0xFF);
                                return new Result(ip, external, true, "NAT-PMP",
                                        "NAT-PMP PORT OPENED ON THE ROUTER");
                            }
                            return new Result(ip, 0, false, "NAT-PMP",
                                    "NAT-PMP ADDRESS FOUND, PORT NOT OPENED");
                        }
                    }
                } catch (Exception ignored) {
                    // this gateway did not answer; try the next
                }
            }
        } catch (Exception ignored) {
            // no socket: no NAT-PMP
        } finally {
            if (sock != null) sock.close();
        }
        return new Result("", 0, false, "", "NO NAT-PMP GATEWAY ANSWERED");
    }

    private static byte[] receive(DatagramSocket s, int max) throws Exception {
        byte[] buf = new byte[max];
        DatagramPacket p = new DatagramPacket(buf, buf.length);
        s.receive(p);
        byte[] out = new byte[p.getLength()];
        System.arraycopy(p.getData(), 0, out, 0, out.length);
        return out;
    }

    private static String ipOf(byte[] d, int at) {
        if (d.length < at + 4) return "";
        return (d[at] & 0xFF) + "." + (d[at + 1] & 0xFF) + "." + (d[at + 2] & 0xFF) + "."
                + (d[at + 3] & 0xFF);
    }

    /** The gateways on this machine's links (the NAT-PMP port, 5351, lives on them). */
    static List<InetAddress> gateways() {
        List<InetAddress> out = new ArrayList<>();
        try {
            for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nif.isUp() || nif.isLoopback()) continue;
                for (InetAddress a : Collections.list(nif.getInetAddresses())) {
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) {
                        out.add(a); // answered by the gateway on this link
                    }
                }
            }
        } catch (Exception ignored) {
            // no interfaces: NAT-PMP is out
        }
        return out;
    }

    // ---- STUN ---------------------------------------------------------------------------------

    /** STUN: ask the internet what address it sees. RFC 5389 binding request. */
    static Result stun(int localPort) {
        final int magic = 0x2112A442;
        Random rng = new Random();
        byte[] req = new byte[20];
        req[0] = 0x00;
        req[1] = 0x01; // a binding request
        req[2] = 0;
        req[3] = 0; // no attributes
        req[4] = (byte) (magic >> 24);
        req[5] = (byte) (magic >> 16);
        req[6] = (byte) (magic >> 8);
        req[7] = (byte) magic;
        for (int i = 8; i < 20; i++) req[i] = (byte) rng.nextInt(256);

        for (String server : STUN_SERVERS) {
            DatagramSocket sock = null;
            try {
                sock = new DatagramSocket();
                sock.setSoTimeout(TIMEOUT_MS);
                int c = server.lastIndexOf(':');
                InetSocketAddress addr = new InetSocketAddress(server.substring(0, c),
                        Integer.parseInt(server.substring(c + 1)));
                sock.send(new DatagramPacket(req, req.length, addr));
                byte[] buf = new byte[512];
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                sock.receive(p);
                byte[] d = p.getData();
                int len = p.getLength();
                if (len >= 20 && (d[0] & 0xFF) == 0x01 && (d[1] & 0xFF) == 0x01) {
                    String[] a = stunAddress(d, len, magic);
                    if (a != null) return new Result(a[0], Integer.parseInt(a[1]), false, "STUN",
                            "PUBLIC ADDRESS FOUND — NO PORT MAPPING");
                }
            } catch (Exception ignored) {
                // this server did not answer; try the next
            } finally {
                if (sock != null) sock.close();
            }
        }
        return new Result("", 0, false, "", "NO STUN SERVER ANSWERED");
    }

    /** The address a STUN binding response carries: {ip, port}. Null when it carries none. */
    static String[] stunAddress(byte[] d, int len, int magic) {
        int o = 20;
        while (o + 4 <= len) {
            int type = (d[o] & 0xFF) << 8 | (d[o + 1] & 0xFF);
            int alen = (d[o + 2] & 0xFF) << 8 | (d[o + 3] & 0xFF);
            int v = o + 4;
            if (v + alen > len) return null;
            if (type == 0x0020 || type == 0x0001) { // XOR-MAPPED-ADDRESS, MAPPED-ADDRESS
                if (alen < 8) return null;
                int port = (d[v + 2] & 0xFF) << 8 | (d[v + 3] & 0xFF);
                int ip = (d[v + 4] & 0xFF) << 24 | (d[v + 5] & 0xFF) << 16
                        | (d[v + 6] & 0xFF) << 8 | (d[v + 7] & 0xFF);
                if (type == 0x0020) { // the XOR flavour hides the address behind the cookie
                    port ^= magic >> 16;
                    ip ^= magic;
                }
                return new String[] {
                        ((ip >> 24) & 0xFF) + "." + ((ip >> 16) & 0xFF) + "."
                                + ((ip >> 8) & 0xFF) + "." + (ip & 0xFF),
                        Integer.toString(port) };
            }
            o = v + alen + ((4 - (alen & 3)) & 3); // attributes are padded to four bytes
        }
        return null;
    }

    // ---- the machine's own addresses -------------------------------------------------------------

    /** This machine's first real IPv4 — the address a router would forward to. */
    public static String localAddress() {
        try {
            for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nif.isUp() || nif.isLoopback()) continue;
                for (InetAddress a : Collections.list(nif.getInetAddresses()))
                    if (a instanceof Inet4Address && !a.isLoopbackAddress())
                        return a.getHostAddress();
            }
        } catch (Exception ignored) {
            // no interfaces to read
        }
        return "127.0.0.1";
    }
}
