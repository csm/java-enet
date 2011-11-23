package com.memeo.enet;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ProtocolHeader
{
	private ByteBuffer buffer;
	
	public ProtocolHeader(ByteBuffer buffer)
	{
		this.buffer = buffer.duplicate().order(ByteOrder.BIG_ENDIAN);
	}
	
	public int length()
	{
		return 4;
	}
	
	public int getPeerID()
	{
		return buffer.getShort(0) & 0xFFFF;
	}
	
	public int getSentTime()
	{
		return buffer.getShort(2) & 0xFFFF;
	}
	
	public void setPeerID(int peerID)
	{
		buffer.putShort(0, (short) peerID);
	}
	
	public void setSentTime(int sentTime)
	{
		buffer.putShort(2, (short) sentTime);
	}
}
