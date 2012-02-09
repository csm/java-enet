package com.memeo.enet;

import java.nio.ByteBuffer;

public class IncomingCommand extends ListNode<IncomingCommand>
{
    short reliableSequenceNumber;
    short unreliableSequenceNumber;
    Protocol.CommandHeader command;
    int fragmentCount;
    int fragmentsRemaining;
    ByteBuffer fragments;
    Packet packet;
}
