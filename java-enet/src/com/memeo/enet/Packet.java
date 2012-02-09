package com.memeo.enet;

import java.nio.ByteBuffer;
import java.util.EnumSet;

public class Packet
{
    public static enum Flag
    {
        RELIABLE (1 << 0),
        UNSEQUENCED (1 << 1),
        UNRELIABLE_FRAGMENT (1 << 3);
        
        public final int flagValue;
 
        private Flag(int flagValue)
        {
            this.flagValue = flagValue;
        }
    }
    
    final ByteBuffer buffer;
    EnumSet<Flag> flags;
    
    public Packet(byte[] data)
    {
        this(data, 0, data.length);
    }
    
    public Packet(byte[] data, EnumSet<Flag> flags)
    {
        this(data, 0, data.length, flags);
    }
    
    public Packet(byte[] data, int offset, int length)
    {
        this(data, offset, length, EnumSet.noneOf(Flag.class));
    }
    
    public Packet(byte[] data, int offset, int length, EnumSet<Flag> flags)
    {
        this.flags = flags;
        buffer = ByteBuffer.wrap(data, offset, length);
    }
    
    public Packet(ByteBuffer buffer)
    {
        this(buffer, EnumSet.noneOf(Flag.class));
    }
    
    public Packet(ByteBuffer buffer, EnumSet<Flag> flags)
    {
        this.flags = flags;
        this.buffer = buffer.slice();
    }
    
    public ByteBuffer buffer()
    {
        return buffer.slice();
    }
    
    public EnumSet<Flag> flags()
    {
        return flags.clone();
    }
    
    public boolean setFlag(Flag flag)
    {
        return flags.add(flag);
    }
    
    public boolean unsetFlag(Flag flag)
    {
        return flags.remove(flag);
    }
    
    public int length()
    {
        return buffer.remaining();
    }
    
    @Override
    public String toString()
    {
        return String.format("Packet { %d bytes, flags: %s }", buffer.remaining(), flags);
    }
}
