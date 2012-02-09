package com.memeo.enet;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.prefs.Preferences;
import java.util.prefs.PreferencesFactory;
import java.util.zip.Checksum;

import com.memeo.enet.Peer.State;

public class Host
{
    static final Preferences enetProperties;
    
    static
    {
        // TODO load properties file.
        enetProperties = Preferences.userNodeForPackage(Host.class);
    }
    
    public static final int DEFAULT_RECEIVE_BUFFER_SIZE = 256 * 1024;
    public static final int DEFAULT_SEND_BUFFER_SIZE = 256 * 1024;
    public static final int DEFAULT_MTU = 1400;
    
    public static final int MINIMUM_CHANNEL_COUNT = 1;
    public static final int MAXIMUM_CHANNEL_COUNT = 255;
    public static final int MAXIMUM_PEER_ID = 0xFFF;
    
    public static final int BANDWIDTH_THROTTLE_INTERVAL = 1000;
    
    private InetSocketAddress address;
    private DatagramChannel channel;
    private Selector selector;
    private ConcurrentMap<Short, Peer> peers;
    private Queue<Protocol.Command> commands;
    private int peerCount;
    
    private int incomingBandwidth;
    private int outgoingBandwidth;
    private int bandwidthThrottleEpoch;
    
    private Peer lastServicedPeer;
    private boolean recalculateBandwidthLimits;
    short mtu;
    private ByteBuffer buffers;
    private int bufferCount;
    private int randomSeed;
    private int channelLimit;
    
    private InetSocketAddress receivedAddress;
    private ByteBuffer receivedBuffer;
    
    private long totalSentData;
    private long totalSentPackets;
    private long totalReceivedData;
    private long totalReceivedPackets;

    private Compressor compressor;
    Checksum checksum;
    Queue<Peer> dispatchQueue;
    private int serviceTime;
    
    public Host(InetSocketAddress address, int peerCount, int channelLimit, int incomingBandwidth, int outgoingBandwidth)
        throws IOException
    {
        channel = DatagramChannel.open();
        channel.configureBlocking(false);
        channel.socket().bind(address);
        channel.socket().setBroadcast(true);
        channel.socket().setReceiveBufferSize(enetProperties.getInt("sockopt.recvbuf", DEFAULT_RECEIVE_BUFFER_SIZE));
        channel.socket().setSendBufferSize(enetProperties.getInt("sockopt.sendbuf", DEFAULT_SEND_BUFFER_SIZE));
        selector = Selector.open();
        channel.register(selector, SelectionKey.OP_READ);
        this.peerCount = Math.max(1, Math.min(peerCount, MAXIMUM_PEER_ID));
        peers = new ConcurrentHashMap<Short, Peer>(this.peerCount);
        commands = new ConcurrentLinkedQueue<Protocol.Command>();
        this.address = (InetSocketAddress) channel.socket().getLocalSocketAddress();
        randomSeed = (int) System.currentTimeMillis();
        randomSeed = (randomSeed << 16) | (randomSeed >> 16);
        this.channelLimit = Math.max(MINIMUM_CHANNEL_COUNT, Math.min(MAXIMUM_CHANNEL_COUNT, channelLimit));
        this.incomingBandwidth = Math.max(0, incomingBandwidth);
        this.outgoingBandwidth = Math.max(0, outgoingBandwidth);
        bandwidthThrottleEpoch = 0;
        this.mtu = (short) enetProperties.getInt("enet.mtu", DEFAULT_MTU);
        receivedAddress = new InetSocketAddress(0);
        totalSentData = 0;
        totalSentPackets = 0;
        totalReceivedData = 0;
        totalReceivedPackets = 0;
        dispatchQueue = new ConcurrentLinkedQueue<Peer>();
    }
    
    public InetSocketAddress address()
    {
        return this.address;
    }
    
    public void bandwidthLimit(int incomingBandwidth, int outgoingBandwidth)
    {
        this.incomingBandwidth = incomingBandwidth;
        this.outgoingBandwidth = outgoingBandwidth;
        this.recalculateBandwidthLimits = true;
    }
    
    void bandwidthThrottle() throws EnetException
    {
        int timeCurrent = Time.get();
        int elapsedTime = timeCurrent - this.bandwidthThrottleEpoch;
        int peersTotal = 0;
        int dataTotal = 0;
        int peersRemaining;
        int bandwidth;
        int throttle = 0;
        int bandwidthLimit = 0;
        boolean needsAdjustment;
        
        if (elapsedTime < BANDWIDTH_THROTTLE_INTERVAL)
            return;
        
        for (Peer peer : peers.values())
        {
            if (peer.state != Peer.State.CONNECTED && peer.state != Peer.State.DISCONNECT_LATER)
                continue;
            
            peersTotal++;
            dataTotal += peer.outgoingDataTotal;
        }
        
        if (peersTotal == 0)
            return;
        
        peersRemaining = peersTotal;
        needsAdjustment = true;
        
        if (this.outgoingBandwidth == 0)
            bandwidth = ~0;
        else
            bandwidth = (this.outgoingBandwidth * elapsedTime) / 1000;
        
        while (peersRemaining > 0 && needsAdjustment)
        {
            needsAdjustment = false;
            
            if (dataTotal < bandwidth)
                throttle = Peer.PACKET_THROTTLE_SCALE;
            else
                throttle = (bandwidth * Peer.PACKET_THROTTLE_SCALE) / dataTotal;
            
            for (Peer peer : this.peers.values())
            {
                int peerBandwidth;
                
                if ((peer.state != Peer.State.CONNECTED && peer.state != Peer.State.DISCONNECT_LATER)
                    || peer.incomingBandwidth == 0
                    || peer.outgoingBandwidthThrottleEpoch == timeCurrent)
                    continue;
                peerBandwidth = (peer.incomingBandwidth * elapsedTime) / 1000;
                if ((throttle * peer.outgoingDataTotal) / Peer.PACKET_THROTTLE_SCALE <= peerBandwidth)
                    continue;
                peer.packetThrottleLimit = (peerBandwidth * Peer.PACKET_THROTTLE_SCALE) / peer.outgoingDataTotal;
                if (peer.packetThrottleLimit == 0)
                    peer.packetThrottleLimit = 1;
                if (peer.packetThrottle > peer.packetThrottleLimit)
                    peer.packetThrottle = peer.packetThrottleLimit;
                peer.outgoingBandwidthThrottleEpoch = timeCurrent;
                
                needsAdjustment = true;
                peersRemaining--;
                bandwidth -= peerBandwidth;
                dataTotal -= peerBandwidth;
            }
        }
        
        if (peersRemaining > 0)
        {
            for (Peer peer : this.peers.values())
            {
                if ((peer.state != Peer.State.CONNECTED && peer.state != Peer.State.DISCONNECT_LATER)
                    || peer.outgoingBandwidthThrottleEpoch == timeCurrent)
                    continue;
                peer.packetThrottleLimit = throttle;
                if (peer.packetThrottle > peer.packetThrottleLimit)
                    peer.packetThrottle = peer.packetThrottleLimit;
            }
        }
        
        if (this.recalculateBandwidthLimits)
        {
            this.recalculateBandwidthLimits = false;
            
            peersRemaining = peersTotal;
            bandwidth = this.incomingBandwidth;
            needsAdjustment = true;
            
            if (bandwidth == 0)
                bandwidthLimit = 0;
            else
            {
                while (peersRemaining > 0 && needsAdjustment)
                {
                    needsAdjustment = false;
                    bandwidthLimit = bandwidth / peersRemaining;
                    
                    for (Peer peer : this.peers.values())
                    {
                        if ((peer.state != Peer.State.CONNECTED && peer.state != Peer.State.DISCONNECT_LATER)
                            || peer.incomingBandwidthThrottleEpoch == timeCurrent)
                            continue;
                        
                        if (peer.outgoingBandwidth > 0
                            && peer.outgoingBandwidth >= bandwidthLimit)
                            continue;
                        
                        peer.incomingBandwidthThrottleEpoch = timeCurrent;
                        needsAdjustment = true;
                        peersRemaining--;
                        bandwidth -= peer.outgoingBandwidth;
                    }
                }
                
                for (Peer peer : this.peers.values())
                {
                    if (peer.state != Peer.State.CONNECTED && peer.state != Peer.State.DISCONNECT_LATER)
                        continue;
                    
                    Protocol.BandwidthLimit command = new Protocol.BandwidthLimit();
                    command.setCommand(Protocol.Command.BandwidthLimit);
                    command.setChannelID(0xFF);
                    command.setOutgoingBandwidth(this.outgoingBandwidth);
                    if (peer.incomingBandwidthThrottleEpoch == timeCurrent)
                        command.setIncomingBandwidth(peer.outgoingBandwidth);
                    else
                        command.setIncomingBandwidth(bandwidthLimit);
                    peer.enqueueOutgoingCommand(command, null, 0, (short) 0);
                }
            }
        }
        
        this.bandwidthThrottleEpoch = timeCurrent;
        for (Peer peer : this.peers.values())
        {
            peer.incomingDataTotal = 0;
            peer.outgoingDataTotal = 0;
        }
    }
    
    public void broadcast(int channelID, Packet packet)
        throws IOException
    {
        for (Peer peer : peers.values())
        {
            if (peer.state != Peer.State.CONNECTED)
                continue;
            peer.send(channelID, packet);
        }
    }
    
    public void channelLimit(int channelLimit)
    {
        this.channelLimit = Math.max(MINIMUM_CHANNEL_COUNT, Math.min(MAXIMUM_CHANNEL_COUNT, channelLimit));
    }
    
    public Peer connect(InetSocketAddress address, int channelCount, int data)
        throws IOException
    {
        channelCount = Math.max(MINIMUM_CHANNEL_COUNT, Math.min(MAXIMUM_CHANNEL_COUNT, channelCount));
        Peer peer = new Peer(this);
        peer.channelCount = channelCount;
        peer.state = State.CONNECTING;
        peer.address = address;
        peer.connectID = ++randomSeed;
        short i;
        do
        {
            if (peers.size() == peerCount)
                throw new EnetException("maximum number of peers connected");
            for (i = 0; peers.containsKey(Short.valueOf(i)); i++);
            peer.incomingPeerID = i;
        } while (peers.putIfAbsent(i, peer) != peer);
        if (this.outgoingBandwidth == 0)
            peer.windowSize = Protocol.MAXIMUM_WINDOW_SIZE;
        else
            peer.windowSize = (this.outgoingBandwidth / Peer.WINDOW_SIZE_SCALE) * Protocol.MAXIMUM_WINDOW_SIZE;
        peer.windowSize = Math.max(Protocol.MINIMUM_WINDOW_SIZE, Math.max(Protocol.MAXIMUM_WINDOW_SIZE, peer.windowSize));
        peer.channels = new ArrayList<Peer.Channel>(peer.channelCount);
        Protocol.Connect connect = new Protocol.Connect();
        connect.setCommand(Protocol.Command.Connect);
        connect.setChannelID(0xFF);
        connect.setOutgoingPeerID(peer.incomingPeerID);
        connect.setOutgoingSessionID(0xFF);
        connect.setIncomingSessionID(0xFF);
        connect.setMtu(this.mtu);
        connect.setWindowSize(peer.windowSize);
        connect.setChannelCount(channelCount);
        connect.setIncomingBandwidth(this.incomingBandwidth);
        connect.setOutgoingBandwidth(this.outgoingBandwidth);
        connect.setPacketThrottleInterval(peer.packetThrottleInterval);
        connect.setPacketThrottleAcceleration(peer.packetThrottleAcceleration);
        connect.setPacketThrottleDeceleration(peer.packetThrottleDeceleration);
        peer.enqueueOutgoingCommand(connect, null, 0, (short) 0);
        return peer;
    }
    
    public void flush() throws IOException
    {
        this.serviceTime = Time.get();
        this.sendOutgoingCommands(null, false);
    }
    
    void sendOutgoingCommands(Event event, boolean checkForTimeouts) throws IOException
    {
        Protocol.Header header = new Protocol.Header();
    }
}
