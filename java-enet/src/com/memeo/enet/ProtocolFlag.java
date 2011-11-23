package com.memeo.enet;

import java.util.EnumSet;

public enum ProtocolFlag
{
	ACKNOWLEDGE  (1 << 7),
	UNSEQUENCED  (1 << 6),
	COMPRESSED  (1 << 14),
	SENT_TIME   (1 << 15),
	SESSION_MASK     (3 << 12);

	final int value;
	private ProtocolFlag(int value) { this.value = value; }
	
	static int valueOf(EnumSet<ProtocolFlag> set)
	{
		int value = 0;
		for (ProtocolFlag f : set)
			value |= f.value;
		return value;
	}
	
	static EnumSet<ProtocolFlag> forValue(int value)
	{
		EnumSet<ProtocolFlag> ret = EnumSet.noneOf(ProtocolFlag.class);
		if ((value & ACKNOWLEDGE.value) != 0)
			ret.add(ACKNOWLEDGE);
		if ((value & UNSEQUENCED.value) != 0)
			ret.add(UNSEQUENCED);
		if ((value & COMPRESSED.value) != 0)
			ret.add(COMPRESSED);
		if ((value & SENT_TIME.value) != 0)
			ret.add(SENT_TIME);
		if ((value & SESSION_MASK.value) != 0)
			ret.add(SESSION_MASK);
		return ret;
	}
}
