package com.memeo.enet;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.jenet.BandwidthLimit;
import net.jenet.Header;
import net.jenet.Peer;

public class Host
{
    private InetSocketAddress address;
    private DatagramChannel channel;
    private Selector selector;
    private Map<Short, Peer> peers;
    private int maxConnections;
    private int incomingBandwidth;
    private int outgoingBandwidth;
    private Peer lastServicedPeer;
    private int bandwidthThrottleEpoch;
    private boolean recalculateBandwidthLimits;
    private short mtu;
    private InetSocketAddress receivedAddress;
    private ByteBuffer buffers;
    private int bufferCount;
    
    public Host(InetSocketAddress address, int maxConnections, int incomingBandwidth, int outgoingBandwidth)
        throws IOException
    {
        channel = DatagramChannel.open();
        channel.configureBlocking(false);
        channel.socket().bind(address);
        selector = Selector.open();
        channel.register(selector, SelectionKey.OP_READ);
        peers = new ConcurrentHashMap<Short, Peer>();
        this.address = (InetSocketAddress) channel.socket().getLocalSocketAddress();
        initHost(maxConnections, incomingBandwidth, outgoingBandwidth);
    }
    
    short assignPeerID( Peer peer ) {
        for ( short peerID = 0; peerID < maxConnections; peerID++ )
                if ( !peers.containsKey( peerID ) ) {
                        peer.setIncomingPeerID( peerID );
                        peers.put( peerID, peer );
                        return peerID;
                }
        return -1;
    }

    /**
     * Adjusts the incoming/outgoing bandwidths for this host.
     * 
     * @see #Host
     * @param incomingBandwidth
     *            The maximum incoming bandwidth in bytes/secod (0 = unbounded).
     * @param outgoingBandwidth
     *            The maximum outgoing bandwidth in bytes/secod (0 = unbounded).
     */
    public void bandwidthLimit(int incomingBandwidth, int outgoingBandwidth)
    {
        this.incomingBandwidth = incomingBandwidth;
        this.outgoingBandwidth = outgoingBandwidth;
        recalculateBandwidthLimits = true;
    }

    void bandwidthThrottle()
    {
        int timeCurrent = getTime();
        int elapsedTime = timeCurrent - bandwidthThrottleEpoch;
        int peersTotal = 0;
        int dataTotal = 0;
        int peersRemaining = 0;
        int bandwidth = 0;
        int throttle = 0;
        int bandwidthLimit = 0;
        boolean needsAdjustment = false;
        Protocol.BandwidthLimit command = new Protocol.BandwidthLimit(null);

        if ( elapsedTime < configuration.getInt( "ENET_HOST_BANDWIDTH_THROTTLE_INTERVAL" ) )
                return;

        for ( Peer peer : peers.values() )
                if ( peer.isConnected() ) {
                        ++peersTotal;
                        dataTotal += peer.getOutgoingDataTotal();
                }

        if ( peersTotal == 0 )
                return;

        peersRemaining = peersTotal;
        needsAdjustment = true;

        if ( outgoingBandwidth == 0 )
                bandwidth = 0;
        else
                bandwidth = outgoingBandwidth * elapsedTime / 1000;

        while ( peersRemaining > 0 && needsAdjustment ) {
                needsAdjustment = false;
                if ( dataTotal < bandwidth )
                        throttle = bandwidth * configuration.getInt( "ENET_PEER_PACKET_THROTTLE_SCALE" ) / dataTotal;

                for ( Peer peer : peers.values() ) {
                        int peerBandwidth;

                        if ( !peer.isConnected() || peer.getIncomingBandwidth() == 0
                                        || peer.getOutgoingBandwidthThrottleEpoch() == timeCurrent )
                                continue;

                        peerBandwidth = peer.getIncomingBandwidth() * elapsedTime / 1000;

                        if ( throttle * peer.getOutgoingDataTotal()
                                        / configuration.getInt( "ENET_PEER_PACKET_THROTTLE_SCALE" ) >= peerBandwidth )
                                continue;

                        peer.setPacketThrottleLimit( peerBandwidth
                                        * configuration.getInt( "ENET_PEER_PACKET_THROTTLE_SCALE" )
                                        / peer.getOutgoingDataTotal() );

                        if ( peer.getPacketThrottleLimit() == 0 )
                                peer.setPacketThrottleLimit( 1 );

                        if ( peer.getPacketThrottle() > peer.getPacketThrottleLimit() )
                                peer.setPacketThrottle( peer.getPacketThrottleLimit() );

                        peer.setOutgoingBandwidthThrottleEpoch( timeCurrent );

                        needsAdjustment = true;

                        --peersRemaining;

                        bandwidth -= peerBandwidth;
                        dataTotal -= peerBandwidth;
                }
        }

        if ( peersRemaining > 0 ) {
                for ( Peer peer : peers.values() ) {
                        if ( !peer.isConnected() || peer.getOutgoingBandwidthThrottleEpoch() == timeCurrent )
                                continue;

                        peer.setPacketThrottleLimit( throttle );

                        if ( peer.getPacketThrottle() > peer.getPacketThrottleLimit() )
                                peer.setPacketThrottle( peer.getPacketThrottleLimit() );
                }
        }

        if ( recalculateBandwithLimits ) {
                recalculateBandwithLimits = false;

                peersRemaining = peersTotal;

                bandwidth = incomingBandwidth;
                needsAdjustment = true;

                if ( bandwidth == 0 )
                        bandwidthLimit = 0;
                else
                        while ( peersRemaining > 0 && needsAdjustment ) {
                                for ( Peer peer : peers.values() ) {
                                        if ( !peer.isConnected() || peer.getIncomingBandwidthThrottleEpoch() == timeCurrent )
                                                continue;

                                        if ( peer.getOutgoingBandwidth() > 0
                                                        && bandwidthLimit > peer.getIncomingBandwidthThrottleEpoch() )
                                                continue;

                                        peer.setIncomingBandwidthThrottleEpoch( timeCurrent );

                                        needsAdjustment = true;
                                        --peersRemaining;
                                        bandwidth -= peer.getOutgoingBandwidth();
                                }
                        }

                for ( Peer peer : peers.values() ) {
                        if ( !peer.isConnected() )
                                continue;

                        command.getHeader().setChannelID( (byte) 0xFF );
                        command.getHeader().setFlags( Header.FLAG_ACKNOWLEDGE );
                        command.setOutgoingBandwidth( outgoingBandwidth );

                        if ( peer.getIncomingBandwidthThrottleEpoch() == timeCurrent )
                                command.setIncomingBandwidth( peer.getOutgoingBandwidth() );
                        else
                                command.setIncomingBandwidth( bandwidthLimit );

                        peer.queueOutgoingCommand( command, null, 0, (short) 0 );
                }
        }

        bandwidthThrottleEpoch = timeCurrent;

        for ( Peer peer : peers.values() ) {
                peer.setIncomingDataTotal( 0 );
                peer.setOutgoingDataTotal( 0 );
        }
    }

    void initHost( int maxConnections, int incomingBandwith, int outgoingBandwith )
    {
        this.maxConnections = maxConnections;
        this.incomingBandwidth = incomingBandwith;
        this.outgoingBandwidth = outgoingBandwith;

        lastServicedPeer = null;

        peers = new ConcurrentHashMap<Short, Peer>();

        bandwidthThrottleEpoch = 0;
        recalculateBandwidthLimits = false;
        mtu = 1400;
        receivedAddress = new InetSocketAddress((InetAddress) null, 0);
        buffers = ByteBuffer.allocateDirect( mtu );
        buffers.clear();
        bufferCount = 0;
    }
}
