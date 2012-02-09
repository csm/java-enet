package com.memeo.enet;

public class Event
{
    public static enum Type
    {
        NONE,
        CONNECT,
        DISCONNECT,
        RECEIVE
    }
    
    Type type;
    Peer peer;
    int channelID;
    int data;
    Packet packet;
    
    public Type getType()
    {
        return type;
    }
    public void setType(Type type)
    {
        this.type = type;
    }
    public Peer getPeer()
    {
        return peer;
    }
    public void setPeer(Peer peer)
    {
        this.peer = peer;
    }
    public int getChannelID()
    {
        return channelID;
    }
    public void setChannelID(int channelID)
    {
        this.channelID = channelID;
    }
    public int getData()
    {
        return data;
    }
    public void setData(int data)
    {
        this.data = data;
    }
    public Packet getPacket()
    {
        return packet;
    }
    public void setPacket(Packet packet)
    {
        this.packet = packet;
    }
}
