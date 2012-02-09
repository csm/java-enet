package com.memeo.enet;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import com.memeo.enet.Protocol.Command;

public class Peer extends ListNode<Peer>
{
    public static enum State
    {
        DISCONNECTED,
        CONNECTING,
        ACKNOWLEDGING_CONNECT,
        CONNECTION_PENDING,
        CONNECTION_SUCCEEDED,
        CONNECTED,
        DISCONNECT_LATER,
        DISCONNECTING,
        ACKNOWLEDGING_DISCONNECT,
        ZOMBIE
    }
    
    static class Channel
    {
        
        short outgoingReliableSequenceNumber;
        short outgoingUnreliableSequenceNumber;
        short usedReliableWindows;
        final short[] reliableWindows = new short[RELIABLE_WINDOWS];
        short incomingReliableSequenceNumber;
        short incomingUnreliableSequenceNumber;
        final Queue<IncomingCommand> incomingReliableCommands = new ConcurrentLinkedQueue<IncomingCommand>();
        final Queue<IncomingCommand> incomingUnreliableCommands = new ConcurrentLinkedQueue<IncomingCommand>();
    }
    
    static final int DEFAULT_ROUND_TRIP_TIME      = 500;
    static final int DEFAULT_PACKET_THROTTLE      = 32;
    static final int PACKET_THROTTLE_SCALE        = 32;
    static final int PACKET_THROTTLE_COUNTER      = 7;
    static final int PACKET_THROTTLE_ACCELERATION = 2;
    static final int PACKET_THROTTLE_DECELERATION = 2;
    static final int PACKET_THROTTLE_INTERVAL     = 5000;
    static final int PACKET_LOSS_SCALE            = (1 << 16);
    static final int PACKET_LOSS_INTERVAL         = 10000;
    static final int WINDOW_SIZE_SCALE            = 64 * 1024;
    static final int TIMEOUT_LIMIT                = 32;
    static final int TIMEOUT_MINIMUM              = 5000;
    static final int TIMEOUT_MAXIMUM              = 30000;
    static final int PING_INTERVAL                = 500;
    static final int UNSEQUENCED_WINDOWS          = 64;
    static final int UNSEQUENCED_WINDOW_SIZE      = 1024;
    static final int FREE_UNSEQUENCED_WINDOWS     = 32;
    static final int RELIABLE_WINDOWS             = 16;
    static final int RELIABLE_WINDOW_SIZE         = 0x1000;
    static final int FREE_RELIABLE_WINDOWS        = 8;
    
    short outgoingPeerID;
    short incomingPeerID;
    InetSocketAddress address;
    State state;
    int channelCount;
    List<Channel> channels;
    int connectID;
    int windowSize;
    int incomingBandwidth;
    int outgoingBandwidth;
    int incomingBandwidthThrottleEpoch;
    int outgoingBandwidthThrottleEpoch;
    int incomingDataTotal;
    int outgoingDataTotal;
    int lastSendTime;
    int lastReceiveTime;
    int nextTimeout;
    int   earliestTimeout;
    int   packetLossEpoch;
    int   packetsSent;
    int   packetsLost;
    int   packetLoss;          /**< mean packet loss of reliable packets as a ratio with respect to the constant ENET_PEER_PACKET_LOSS_SCALE */
    int   packetLossVariance;
    int   packetThrottle;
    int   packetThrottleLimit;
    int   packetThrottleCounter;
    int   packetThrottleEpoch;
    int   packetThrottleAcceleration;
    int   packetThrottleDeceleration;
    int   packetThrottleInterval;
    int   lastRoundTripTime;
    int   lowestRoundTripTime;
    int   lastRoundTripTimeVariance;
    int   highestRoundTripTimeVariance;
    int   roundTripTime;            /**< mean round trip time (RTT), in milliseconds, between sending a reliable packet and receiving its acknowledgement */
    int   roundTripTimeVariance;
    int   mtu;
    int   reliableDataInTransit;
    short   outgoingReliableSequenceNumber;    
    short incomingUnsequencedGroup;
    short outgoingUnsequencedGroup;
    int eventData;
    final int[] unsequencedWindow = new int[UNSEQUENCED_WINDOW_SIZE / 32];
    boolean needsDispatch;
    List<Object> dispatchList;
    final Queue<OutgoingCommand> sentReliableCommands = new ConcurrentLinkedQueue<OutgoingCommand>();
    final Queue<OutgoingCommand> sentUnreliableCommands = new ConcurrentLinkedQueue<OutgoingCommand>();
    final Queue<OutgoingCommand> outgoingReliableCommands = new ConcurrentLinkedQueue<OutgoingCommand>();
    final Queue<OutgoingCommand> outgoingUnreliableCommands = new ConcurrentLinkedQueue<OutgoingCommand>();
    final Queue<IncomingCommand> dispatchedCommands = new ConcurrentLinkedQueue<IncomingCommand>();
    // TODO fix type param
    final Queue<Object> acknowledgements = new ConcurrentLinkedQueue<Object>();
    
    final Host host;
    
    Peer(Host host)
    {
        this.host = host;
        reset();
    }
    
    public short getIncomingPeerID()
    {
        return incomingPeerID;
    }
    
    void setIncomingPeerID(short id)
    {
        this.incomingPeerID = id;
    }
    
    public void reset()
    {
        outgoingPeerID = Protocol.MAXIMUM_PEER_ID;
        connectID = 0;
        state = State.DISCONNECTED;
        incomingBandwidth = 0;
        outgoingBandwidth = 0;
        incomingBandwidthThrottleEpoch = 0;
        outgoingBandwidthThrottleEpoch = 0;
        incomingDataTotal = 0;
        outgoingDataTotal = 0;
        lastSendTime = 0;
        lastReceiveTime = 0;
        nextTimeout = 0;
        earliestTimeout = 0;
        packetLossEpoch = 0;
        packetsSent = 0;
        packetsLost = 0;
        packetLoss = 0;
        packetLossVariance = 0;
        packetThrottle = DEFAULT_PACKET_THROTTLE;
        packetThrottleLimit = PACKET_THROTTLE_SCALE;
        packetThrottleCounter = 0;
        packetThrottleEpoch = 0;
        packetThrottleAcceleration = PACKET_THROTTLE_ACCELERATION;
        packetThrottleDeceleration = PACKET_THROTTLE_DECELERATION;
        packetThrottleInterval = PACKET_THROTTLE_INTERVAL;
        lastRoundTripTime = DEFAULT_ROUND_TRIP_TIME;
        lowestRoundTripTime = DEFAULT_ROUND_TRIP_TIME;
        lastRoundTripTimeVariance = 0;
        highestRoundTripTimeVariance = 0;
        roundTripTime = DEFAULT_ROUND_TRIP_TIME;
        roundTripTimeVariance = 0;
        mtu = host.mtu;
        reliableDataInTransit = 0;
        outgoingReliableSequenceNumber = 0;
        windowSize = Protocol.MAXIMUM_WINDOW_SIZE;
        incomingUnsequencedGroup = 0;
        outgoingUnsequencedGroup = 0;
        eventData = 0;
        Arrays.fill(unsequencedWindow, 0);
        resetQueues();
    }
    
    void resetQueues()
    {
        if (needsDispatch)
        {
            this.host.dispatchQueue.remove(this);
            this.needsDispatch = false;
        }
        this.sentReliableCommands.clear();
        this.sentUnreliableCommands.clear();
        this.outgoingReliableCommands.clear();
        this.outgoingUnreliableCommands.clear();
        this.dispatchedCommands.clear();
        this.acknowledgements.clear();
        for (Channel channel : this.channels)
        {
            channel.incomingReliableCommands.clear();
            channel.incomingUnreliableCommands.clear();
        }
    }
    
    @SuppressWarnings("static-access")
    void setupOutgoingCommand(OutgoingCommand command) throws EnetException
    {
        Channel channel = this.channels.get(command.command.channelID());
        this.outgoingDataTotal += command.command.length() + (command.fragmentLength & 0xFFFF);
        if (command.command.channelID() == 0xFF)
        {
            this.outgoingReliableSequenceNumber++;
            command.reliableSequenceNumber = this.outgoingReliableSequenceNumber;
            command.unreliableSequenceNumber = 0;
        }
        else if (command.command.flags().contains(Protocol.CommandFlag.Acknowledge))
        {
            channel.outgoingReliableSequenceNumber++;
            channel.outgoingUnreliableSequenceNumber = 0;
            command.reliableSequenceNumber = channel.outgoingReliableSequenceNumber;
            command.unreliableSequenceNumber = 0;
        }
        else if (command.command.flags().contains(Protocol.CommandFlag.Unsequenced))
        {
            this.outgoingUnsequencedGroup++;
            command.reliableSequenceNumber = 0;
            command.unreliableSequenceNumber = 0;
        }
        else
        {
            if (command.fragmentOffset == 0)
                channel.outgoingUnreliableSequenceNumber++;
            command.reliableSequenceNumber = channel.outgoingReliableSequenceNumber;
            command.unreliableSequenceNumber = channel.outgoingUnreliableSequenceNumber;
        }

        command.sendAttempts = 0;
        command.sentTime = 0;
        command.roundTripTimeout = 0;
        command.roundTripTimeoutLimit = 0;
        command.command.setReliableSequenceNumber(command.reliableSequenceNumber);
        
        switch (command.command.command())
        {
        case SendUnreliable:
            ((Protocol.SendUnreliable) command.command).setUnreliableSequenceNumber(command.unreliableSequenceNumber);
            break;
            
        case SendUnsequenced:
            ((Protocol.SendUnsequenced) command.command).setUnsequencedGroup(this.outgoingUnsequencedGroup);
            break;
        }
        
        if (command.command.flags().contains(Protocol.CommandFlag.Acknowledge))
            this.outgoingReliableCommands.add(command);
        else
            this.outgoingUnreliableCommands.add(command);
    }

    OutgoingCommand enqueueOutgoingCommand(Protocol.CommandHeader command, Packet packet, int offset, short length)
        throws EnetException
    {
        OutgoingCommand outgoingCommand = new OutgoingCommand();
        outgoingCommand.command = command;
        outgoingCommand.fragmentOffset = offset;
        outgoingCommand.fragmentLength = length;
        outgoingCommand.packet = packet;
        setupOutgoingCommand(outgoingCommand);
        return outgoingCommand;
    }
    
    public void disconnectNow(int data)
        throws IOException
    {
        if (this.state == State.DISCONNECTED)
            return;
        
        if (this.state != State.ZOMBIE
            && this.state != State.DISCONNECTING)
        {
            this.resetQueues();
            Protocol.Disconnect disconnect = new Protocol.Disconnect();
            disconnect.setCommand(Protocol.Command.Disconnect);
            disconnect.setFlags(EnumSet.of(Protocol.CommandFlag.Unsequenced));
            disconnect.setData(data);
            this.enqueueOutgoingCommand(disconnect, null, 0, (short) 0);
            this.host.flush();
        }
        
        this.reset();
    }
    
    public void ping()
        throws EnetException
    {
        if (this.state != State.CONNECTED)
            throw new EnetException("peer is not connected");
        
        Protocol.Ping ping = new Protocol.Ping();
        ping.setCommand(Protocol.Command.Ping);
        ping.setChannelID(0xFF);
        enqueueOutgoingCommand(ping, null, 0, (short) 0);
    }
    
    public Packet receive(int[] channelID)
        throws EnetException
    {
        if (this.dispatchedCommands.isEmpty())
            return null;
        
        IncomingCommand command = this.dispatchedCommands.remove();
        if (channelID != null)
            channelID[0] = command.command.channelID();
        Packet packet = command.packet;
        return packet;
    }
    
    public void send(int channelID, Packet packet)
        throws EnetException
    {
        if (this.state != State.CONNECTED)
            throw new EnetException("peer not connected");
        if (channelID < 0 || channelID >= this.channelCount)
            throw new IllegalArgumentException("invalid channel ID");
        Channel channel = channels.get(channelID);
        int fragmentLength = this.mtu - Protocol.Header.length() - Protocol.SendFragment.length();
        if (this.host.checksum != null)
            fragmentLength -= 4;
        if (packet.length() > fragmentLength)
        {
            int fragmentCount = (packet.length() + fragmentLength + 1) / fragmentLength;
            int fragmentNumber;
            int fragmentOffset;
            short startSequenceNumber;
            List<OutgoingCommand> fragments = new ArrayList<OutgoingCommand>(fragmentCount);
            Protocol.Command command = null;
            EnumSet<Protocol.CommandFlag> flags = null;
            if (!packet.flags().contains(Packet.Flag.RELIABLE)
                && packet.flags().contains(Packet.Flag.UNRELIABLE_FRAGMENT)
                && (channel.outgoingUnreliableSequenceNumber & 0xFFFF) < 0xFFFF)
            {
                command = Protocol.Command.SendUnreliable;
                flags = EnumSet.noneOf(Protocol.CommandFlag.class);
                startSequenceNumber = channel.outgoingUnreliableSequenceNumber;
            }
            else
            {
                command = Protocol.Command.SendFragment;
                flags = EnumSet.of(Protocol.CommandFlag.Acknowledge);
                startSequenceNumber = channel.outgoingReliableSequenceNumber;
            }
            
            fragmentOffset = 0;
            for (fragmentNumber = 0; fragmentOffset < packet.length(); fragmentNumber++)
            {
                if (packet.length() - fragmentOffset < fragmentLength)
                    fragmentLength = packet.length() - fragmentOffset;
                OutgoingCommand fragment = new OutgoingCommand();
                fragment.fragmentOffset = fragmentOffset;
                fragment.fragmentLength = (short) fragmentLength;
                fragment.packet = packet;
                Protocol.SendFragment sendFragment = new Protocol.SendFragment();
                sendFragment.setCommand(command);
                sendFragment.setFlags(flags);
                sendFragment.setChannelID(channelID);
                sendFragment.setStartSequenceNumber(startSequenceNumber);
                sendFragment.setDataLength(fragmentLength);
                sendFragment.setFragmentCount(fragmentCount);
                sendFragment.setFragmentNumber(fragmentNumber);
                sendFragment.setTotalLength(packet.length());
                sendFragment.setFragmentOffset(fragmentOffset);
                fragment.command = sendFragment;
                fragments.add(fragment);
                fragmentOffset += fragmentLength;
            }
            
            for (OutgoingCommand cmd : fragments)
            {
                this.setupOutgoingCommand(cmd);
            }
            return;
        }
        Protocol.CommandHeader command = null;
        
        if (packet.flags().contains(Packet.Flag.UNSEQUENCED)
            && !packet.flags().contains(Packet.Flag.RELIABLE))
        {
            Protocol.SendUnsequenced sendUnsequenced = new Protocol.SendUnsequenced();
            sendUnsequenced.setCommand(Protocol.Command.SendUnsequenced);
            sendUnsequenced.setFlags(EnumSet.of(Protocol.CommandFlag.Unsequenced));
            sendUnsequenced.setDataLength(packet.length());
            command = sendUnsequenced;
        }
        else if (packet.flags().contains(Packet.Flag.RELIABLE)
                 && (channel.outgoingUnreliableSequenceNumber & 0xFFFF) < 0xFFFF)
        {
            Protocol.SendReliable sendReliable = new Protocol.SendReliable();
            sendReliable.setCommand(Protocol.Command.SendReliable);
            sendReliable.setFlags(EnumSet.of(Protocol.CommandFlag.Acknowledge));
            sendReliable.setDataLength(packet.length());
            command = sendReliable;
        }
        else
        {
            Protocol.SendUnreliable sendUnreliable = new Protocol.SendUnreliable();
            sendUnreliable.setCommand(Protocol.Command.SendUnreliable);
            sendUnreliable.setFlags(EnumSet.noneOf(Protocol.CommandFlag.class));
            sendUnreliable.setDataLength(packet.length());
            command = sendUnreliable;
        }
        
        command.setChannelID(channelID);
        this.enqueueOutgoingCommand(command, packet, 0, (short) packet.length());
    }
    
    public void throttleConfigure(int interval, int acceleration, int deceleration)
        throws EnetException
    {
        this.packetThrottleInterval = interval;
        this.packetThrottleAcceleration = acceleration;
        this.packetThrottleDeceleration = deceleration;
        
        Protocol.ThrottleConfigure command = new Protocol.ThrottleConfigure();
        command.setCommand(Protocol.Command.ThrottleConfigure);
        command.setChannelID(0xFF);
        command.setPacketThrottleInterval(interval);
        command.setPacketThrottleAcceleration(acceleration);
        command.setPacketThrottleDeceleration(deceleration);
        this.enqueueOutgoingCommand(command, null, 0, (short) 0);
    }
    
    public int throttle(int rtt)
    {
        if (this.lastRoundTripTime <= this.lastRoundTripTimeVariance)
            this.packetThrottle = this.packetThrottleLimit;
        else if (rtt < this.lastRoundTripTime)
        {
            this.packetThrottle += this.packetThrottleAcceleration;
            if (this.packetThrottle > this.packetThrottleLimit)
                this.packetThrottle = this.packetThrottleLimit;
            return 1;
        }
        else if (rtt > this.lastRoundTripTime + 2 * this.lastRoundTripTimeVariance)
        {
            if (this.packetThrottle > this.packetThrottleDeceleration)
                this.packetThrottle -= this.packetThrottleDeceleration;
            else
                this.packetThrottle = 0;
            return -1;
        }
        return 0;
    }
}
