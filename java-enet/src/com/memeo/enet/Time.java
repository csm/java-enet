package com.memeo.enet;

import java.util.concurrent.atomic.AtomicInteger;

public class Time
{
    private static AtomicInteger timeBase = new AtomicInteger(0);
    
    static int get()
    {
        return (int) (System.currentTimeMillis() - timeBase.get());
    }
    
    static void set(int newTimeBase)
    {
        timeBase.set((int) (System.currentTimeMillis() - newTimeBase));
    }
}
