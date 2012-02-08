package com.memeo.enet;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * enet protocol handling.
 * 
 * @author csm
 */
final class Protocol
{
	static enum Constants
	{
		MinimumMTU              (576),
		MaximumMTU             (4096),
		MaximumPacketCommands    (32),
		MinimumWindowSize      (4096),
		MaximumWindowSize     (32768),
		MinimumChannelCount       (1),
		MaximumChannelCount     (255),
		MaximumPeerID         (0xFFF);
		
		final int value;
		Constants(int value)
		{
			this.value = value;
		}
	}
	
	static enum Command
	{
		None               (0),
		Acknowledge        (1),
		Connect            (2),
		VerifyConnect      (3),
		Disconnect         (4),
		Ping               (5),
		SendReliable       (6),
		SendUnreliable     (7),
		SendFragment       (8),
		SendUnsequenced    (9),
		BandwidthLimit     (10),
		ThrottleConfigure  (11),
		SendUnreliableFragment (12);
		
		final byte value;
		private Command(int val) { this.value = (byte) val; }
		
		static Command forValue(int value) throws EnetException
		{
			switch (value)
			{
			case 0: return None;
			case 1: return Acknowledge;
			case 2: return Connect;
			case 3: return VerifyConnect;
			case 4: return Disconnect;
			case 5: return Ping;
			case 6: return SendReliable;
			case 7: return SendUnreliable;
			case 8: return SendFragment;
			case 9: return SendUnsequenced;
			case 10: return BandwidthLimit;
			case 11: return ThrottleConfigure;
			case 12: return SendUnreliableFragment;
			}
			throw new EnetException("no command exists for value: " + value);
		}
	}
	
	static class Header
	{
		private final ByteBuffer buffer;
		
		Header(ByteBuffer buffer)
		{
			this.buffer = buffer.order(ByteOrder.BIG_ENDIAN);
		}
		
		static int length()
		{
			return 4;
		}
		
		int peerID()
		{
			return buffer.getShort(0) & 0xFFFF;
		}
		
		int sentTime()
		{
			return buffer.getShort(2) & 0xFFFF;
		}
		
		void setPeerID(int peerID)
		{
			buffer.putShort(0, (short) peerID);
		}
		
		void setSentTime(int sentTime)
		{
			buffer.putShort(2, (short) sentTime);
		}
	}
	
	static class CommandHeader
	{
		private final ByteBuffer buffer;
		
		public CommandHeader(ByteBuffer buffer)
		{
			this.buffer = buffer.order(ByteOrder.BIG_ENDIAN);
		}
		
		static int length()
		{
			return 4;
		}
		
		Command command() throws EnetException
		{
			return Command.forValue(buffer.get(0) & 0xFF);
		}
		
		int channelID()
		{
			return buffer.get(1) & 0xFF;
		}
		
		int reliableSequenceNumber()
		{
			return buffer.getShort(2) & 0xFFFF;
		}
		
		void setCommand(Command command)
		{
			buffer.put(0, command.value);
		}
		
		void setChannelID(int channelID)
		{
			buffer.put(1, (byte) channelID);
		}
		
		void setReliableSequenceNumber(int reliableSequenceNumber)
		{
			buffer.putShort(2, (short) reliableSequenceNumber);
		}
	}
	
	static class Acknowledge extends CommandHeader
	{
		private final ByteBuffer buffer;
		
		public Acknowledge(ByteBuffer buffer)
		{
			super(buffer);
			this.buffer = ((ByteBuffer) buffer.order(ByteOrder.BIG_ENDIAN).position(CommandHeader.length())).slice();
		}
		
		static int length()
		{
		    return CommandHeader.length() + 4;
		}

		int receivedReliableSequenceNumber()
		{
			return buffer.getShort(0) & 0xFFFF;
		}
		
		int receivedSentTime()
		{
			return buffer.getShort(2) & 0xFFFF;
		}
		
		void setReceivedReliableSequenceNumber(int receivedReliableSequenceNumber)
		{
			buffer.putShort(0, (short) receivedReliableSequenceNumber);
		}
		
		void setReceivedSentTime(int receivedSentTime)
		{
			buffer.putShort(2, (short) receivedSentTime);
		}
	}
	
	static class Connect extends VerifyConnect
	{
		private final ByteBuffer buffer;
		
		Connect(ByteBuffer buffer)
		{
			super(buffer);
			this.buffer = ((ByteBuffer) buffer.order(ByteOrder.BIG_ENDIAN).position(VerifyConnect.length())).slice();
		}
		
		static int length()
		{
			return VerifyConnect.length() + 4;
		}
		
		int data()
		{
			return buffer.getInt(0);
		}
		
		void setData(int data)
		{
			buffer.putInt(0, data);
		}
	}
	
	static class VerifyConnect extends CommandHeader
	{
		private final ByteBuffer buffer;
		
		VerifyConnect(ByteBuffer buffer)
		{
			super(buffer);
			this.buffer = ((ByteBuffer) buffer.order(ByteOrder.BIG_ENDIAN).position(CommandHeader.length())).slice();
		}

		static int length()
		{
			return CommandHeader.length() + 40;
		}
		
		int outgoingPeerID()
		{
			return buffer.getShort(0) & 0xFFFF;
		}
		
		void setOutgoingPeerID(int outgoingPeerID)
		{
			buffer.putShort(0, (short) outgoingPeerID);
		}
		
		int incomingSessionID()
		{
			return buffer.get(2) & 0xFF;
		}
		
		void setIncomingSessionID(int incomingSessionID)
		{
			buffer.put(2, (byte) incomingSessionID);
		}
		
		int outgoingSessionID()
		{
			return buffer.get(3) & 0xFF;
		}
		
		void setOutgoingSessionID(int outgoingSessionID)
		{
			buffer.put(3, (byte) outgoingSessionID);
		}
		
		int mtu()
		{
			return buffer.getInt(4);
		}

		void setMtu(int mtu)
		{
			buffer.putInt(4, mtu);
		}
		
		int windowSize()
		{
			return buffer.getInt(8);
		}
		
		void setWindowSize(int windowSize)
		{
			buffer.putInt(8, windowSize);
		}
		
		int channelCount()
		{
			return buffer.getInt(12);
		}
		
		void setChannelCount(int channelCount)
		{
			buffer.putInt(12, channelCount);
		}
		
		int incomingBandwidth()
		{
			return buffer.getInt(16);
		}
		
		void setIncomingBandwidth(int incomingBandwidth)
		{
			buffer.putInt(16, incomingBandwidth);
		}
		
		int outgoingBandwidth()
		{
			return buffer.getInt(20);
		}
		
		void setOutgoingBandwidth(int outgoingBandwidth)
		{
			buffer.putInt(20, outgoingBandwidth);
		}
		
		int packetThrottleInterval()
		{
			return buffer.getInt(24);
		}
		
		void setPacketThrottleInterval(int packetThrottleInterval)
		{
			buffer.putInt(24, packetThrottleInterval);
		}
		
		int packetThrottleAcceleration()
		{
			return buffer.getInt(28);
		}
		
		void setPacketThrottleAcceleration(int packetThrottleAcceleration)
		{
			buffer.putInt(28, packetThrottleAcceleration);
		}
		
		int packetThrottleDeceleration()
		{
			return buffer.getInt(32);
		}
		
		void setPacketThrottleDeceleration(int packetThrottleDeceleration)
		{
			buffer.putInt(32, packetThrottleDeceleration);
		}
		
		int connectID()
		{
			return buffer.getInt(36);
		}
		
		void setConnectID(int connectID)
		{
			buffer.putInt(36, connectID);
		}
	}
	
	static class BandwidthLimit extends CommandHeader
	{
		private final ByteBuffer buffer;
		
		BandwidthLimit(ByteBuffer buffer)
		{
			super(buffer);
			this.buffer = ((ByteBuffer) buffer.order(ByteOrder.BIG_ENDIAN).position(CommandHeader.length())).slice();
		}
		
		static int length()
		{
			return CommandHeader.length() + 8;
		}

		int incomingBandwidth()
		{
			return buffer.getInt(0);
		}
		
		void setIncomingBandwidth(int incomingBandwidth)
		{
			buffer.putInt(0, incomingBandwidth);
		}
		
		int outgoingBandwidth()
		{
			return buffer.getInt(4);
		}
		
		void setOutgoingBandwidth(int outgoingBandwidth)
		{
			buffer.putInt(4, outgoingBandwidth);
		}
	}
	
	static class ThrottleConfigure extends CommandHeader
	{
		private final ByteBuffer buffer;
		
		ThrottleConfigure(ByteBuffer buffer)
		{
			super(buffer);
			this.buffer = ((ByteBuffer) buffer.order(ByteOrder.BIG_ENDIAN).position(CommandHeader.length())).slice();
		}

		static int length()
		{
			return CommandHeader.length() + 12;
		}
		
		int packetThrottleInterval()
		{
			return buffer.getInt(0);
		}
		
		void setPacketThrottleInterval(int packetThrottleInterval)
		{
			buffer.putInt(0, packetThrottleInterval);
		}
		
		int packetThrottleAcceleration()
		{
			return buffer.getInt(4);
		}
		
		void setPacketThrottleAcceleration(int packetThrottleAcceleration)
		{
			buffer.putInt(4, packetThrottleAcceleration);
		}
		
		int packetThrottleDeceleration()
		{
			return buffer.getInt(8);
		}
		
		void setPacketThrottleDeceleration(int packetThrottleDeceleration)
		{
			buffer.putInt(8, packetThrottleDeceleration);
		}
	}
	
	static class Disconnect extends CommandHeader
	{
		private final ByteBuffer buffer;
		
		Disconnect(ByteBuffer buffer)
		{
			super(buffer);
			this.buffer = ((ByteBuffer) buffer.order(ByteOrder.BIG_ENDIAN).position(CommandHeader.length())).slice();
		}

		static int length()
		{
			return CommandHeader.length() + 4;
		}
		
		int data()
		{
			return buffer.getInt(0);
		}
		
		void setData(int data)
		{
			buffer.putInt(0, data);
		}
	}
	
	static class Ping extends CommandHeader
	{
		Ping(ByteBuffer buffer)
		{
			super(buffer);
		}
	}
	
	static class SendReliable extends CommandHeader
	{
		private final ByteBuffer buffer;
		
		SendReliable(ByteBuffer buffer)
		{
			super(buffer);
			this.buffer = ((ByteBuffer) buffer.order(ByteOrder.BIG_ENDIAN).position(CommandHeader.length())).slice();
		}
		
		static int length()
		{
			return CommandHeader.length() + 2;
		}

		int dataLength()
		{
			return buffer.getShort(0) & 0xFFFF;
		}
		
		void setDataLength(int dataLength)
		{
			buffer.putShort(0, (short) dataLength);
		}
	}
	
	static class SendUnreliable extends CommandHeader
	{
		private final ByteBuffer buffer;
		
		SendUnreliable(ByteBuffer buffer)
		{
			super(buffer);
			this.buffer = ((ByteBuffer) buffer.order(ByteOrder.BIG_ENDIAN).position(CommandHeader.length())).slice();
		}

		static int length()
		{
			return CommandHeader.length() + 4;
		}
		
		int unreliableSequenceNumber()
		{
			return buffer.getShort(0) & 0xFFFF;
		}
		
		void setUnreliableSequenceNumber(int unreliableSequenceNumber)
		{
			buffer.putShort((short) unreliableSequenceNumber);
		}
		
		int dataLength()
		{
			return buffer.getShort(2) & 0xFFFF;
		}
		
		void setDataLength(int dataLength)
		{
			buffer.putShort((short) dataLength);
		}
	}
	
	static class SendUnsequenced extends CommandHeader
	{
		private final ByteBuffer buffer;
		
		SendUnsequenced(ByteBuffer buffer)
		{
			super(buffer);
			this.buffer = ((ByteBuffer) buffer.order(ByteOrder.BIG_ENDIAN).position(CommandHeader.length())).slice();
		}
		
		static int length()
		{
			return CommandHeader.length() + 4;
		}

		int unsequencedGroup()
		{
			return buffer.getShort(0) & 0xFFFF;
		}
		
		void setUnsequencedGroup(int unsequencedGroup)
		{
			buffer.putShort((short) unsequencedGroup);
		}
		
		int dataLength()
		{
			return buffer.getShort(2) & 0xFFFF;
		}
		
		void setDataLength(int dataLength)
		{
			buffer.putShort((short) dataLength);
		}
	}
	
	static class SendFragment extends CommandHeader
	{
		private final ByteBuffer buffer;
		
		SendFragment(ByteBuffer buffer)
		{
			super(buffer);
			this.buffer = ((ByteBuffer) buffer.order(ByteOrder.BIG_ENDIAN).position(CommandHeader.length())).slice();
		}
		
		static int length()
		{
		    return CommandHeader.length() + 20;
		}

		int startSequenceNumber()
		{
			return buffer.getShort(0) & 0xFFFF;
		}
		
		void setStartSequenceNumber(int startSequenceNumber)
		{
			buffer.putShort(0, (short) startSequenceNumber);
		}
		
		int dataLength()
		{
			return buffer.getShort(2) & 0xFFFF;
		}
		
		void setDataLength(int dataLength)
		{
			buffer.putShort(2, (short) dataLength);
		}
		
		int fragmentCount()
		{
			return buffer.getInt(4);
		}
		
		void setFragmentCount(int fragmentCount)
		{
			buffer.putInt(4, fragmentCount);
		}
		
		int fragmentNumber()
		{
			return buffer.getInt(8);
		}
		
		void setFragmentNumber(int fragmentNumber)
		{
			buffer.putInt(8, fragmentNumber);
		}
		
		int totalLength()
		{
			return buffer.getInt(12);
		}
		
		void setTotalLength(int totalLength)
		{
			buffer.putInt(12, totalLength);
		}
		
		int fragmentOffset()
		{
			return buffer.getInt(16);
		}
		
		void setFragmentOffset(int fragmentOffset)
		{
			buffer.putInt(16, fragmentOffset);
		}
	}
}
