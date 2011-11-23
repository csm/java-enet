package com.memeo.enet;

enum ProtocolCommand
{
	NONE               (0),
	ACKNOWLEDGE        (1),
	CONNECT            (2),
	VERIFY_CONNECT     (3),
	DISCONNECT         (4),
	PING               (5),
	SEND_RELIABLE      (6),
	SEND_UNRELIABLE    (7),
	SEND_FRAGMENT      (8),
	SEND_UNSEQUENCED   (9),
	BANDWIDTH_LIMIT    (10),
	THROTTLE_CONFIGURE (11),
	SEND_UNRELIABLE_FRAGMENT (12);
	
	final byte value;
	private ProtocolCommand(int val) { this.value = (byte) val; }
	
	static ProtocolCommand forValue(int value) throws EnetException
	{
		switch (value)
		{
		case 0: return NONE;
		case 1: return ACKNOWLEDGE;
		case 2: return CONNECT;
		case 3: return VERIFY_CONNECT;
		case 4: return DISCONNECT;
		case 5: return PING;
		case 6: return SEND_RELIABLE;
		case 7: return SEND_UNRELIABLE;
		case 8: return SEND_FRAGMENT;
		case 9: return SEND_UNSEQUENCED;
		case 10: return BANDWIDTH_LIMIT;
		case 11: return THROTTLE_CONFIGURE;
		case 12: return SEND_UNRELIABLE_FRAGMENT;
		}
		throw new EnetException("no command exists for value: " + value);
	}
}
