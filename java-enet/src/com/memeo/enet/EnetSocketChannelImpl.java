package com.memeo.enet;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.nio.channels.spi.SelectorProvider;

public class EnetSocketChannelImpl extends AbstractSelectableChannel
{
	private SelectableChannel channel;
	
	protected EnetSocketChannelImpl(SelectorProvider provider)
	{
		super(provider);
	}

	@Override
	protected void implCloseSelectableChannel() throws IOException
	{
		channel.close();
	}

	@Override
	protected void implConfigureBlocking(boolean block) throws IOException
	{
		channel.configureBlocking(block);
	}

	@Override
	public int validOps()
	{
		// TODO Auto-generated method stub
		return 0;
	}
}
