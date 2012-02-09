package com.memeo.enet;

public class OutgoingCommand extends ListNode<OutgoingCommand>
{
    short reliableSequenceNumber;
    short unreliableSequenceNumber;
    int sentTime;
    int roundTripTimeout;
    int roundTripTimeoutLimit;
    int fragmentOffset;
    short fragmentLength;
    short sendAttempts;
    Protocol.CommandHeader command;
    Packet packet;
}
