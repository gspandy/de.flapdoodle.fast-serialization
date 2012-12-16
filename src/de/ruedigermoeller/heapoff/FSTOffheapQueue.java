package de.ruedigermoeller.heapoff;

/*
 * Copyright (c) 2012, Ruediger Moeller. All rights reserved.
 * <p/>
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * <p/>
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 * <p/>
 * Date: 16.12.12
 * Time: 01:44
 * To change this template use File | Settings | File Templates.
 */

import de.ruedigermoeller.serialization.FSTConfiguration;
import de.ruedigermoeller.serialization.FSTObjectInput;
import de.ruedigermoeller.serialization.FSTObjectOutput;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Semaphore;

/**
 * a queue based on off heap memory. The advantage is, that objects are serialized at the time you add them to the queue.
 * This has several advantages (Client/Server related)
 *  - the size of the queue is defined in memory size, not number of objects (no risk to get OOM)
 *  - queues do not suck CPU indirectly by being subject to garbage collection
 *  - if a message is added to multiple queues, you actually serialize this message once, then copy the resulting bytes
 *  - its easier to control network message size, as you already know the size of a serialized object
 *  - easy recovery in case you use memory mapped files
 *
 *  Once a message is sent to a client, a bunch of bytes is taken from the queue.
 *
 */
public class FSTOffheapQueue  {

    private static final int HEADER_SIZE = 4;
    ByteBuffer buffer;
    FSTConfiguration conf = FSTConfiguration.createDefaultConfiguration();

    int headPosition = 0;
    int tailPosition = 0;
    int currentQeueEnd = 0;

    Object rwLock = "QueueRW";
    Object writeLock = "QueueWriteLock";
    Object readLock = "QueueReadLock";
    Semaphore added = new Semaphore(0);
    Semaphore taken = new Semaphore(0);

    FSTObjectOutput out;
    FSTObjectInput in;
    byte[] tmpWriteBuf;
    byte[] tmpReadBuf;

    public static class ByteBufferResult {
        public int off;
        public int len;
        public ByteBuffer buffer;
        public byte[] b;
    }

    public FSTOffheapQueue(int sizeMB) throws IOException {
        this( ByteBuffer.allocateDirect(sizeMB*1000*1000));
    }

    public FSTOffheapQueue(ByteBuffer buffer) throws IOException {
        this.buffer = buffer;
        currentQeueEnd = buffer.limit();
        out = new FSTObjectOutput(conf);
        in  = new FSTObjectInput(conf);
    }

    byte[] getTmpWriteBuf(int siz) {
        if ( tmpWriteBuf == null || tmpWriteBuf.length < siz ) {
            tmpWriteBuf = new byte[siz];
        }
        return tmpWriteBuf;
    }

    byte[] getTmpReadBuf(int siz) {
        if ( tmpReadBuf == null || tmpReadBuf.length < siz ) {
            tmpReadBuf = new byte[siz];
        }
        return tmpReadBuf;
    }

    public boolean add(Object o) throws IOException {
        synchronized ( writeLock ) {
            out.resetForReUse(null);
            out.writeObject(o);
            boolean full = false;
            int siz = 0;
            synchronized (rwLock) {
                siz = out.getWritten();
                int pos = 0;
                if ( siz+tailPosition+HEADER_SIZE >= buffer.limit() ) {
                    currentQeueEnd = tailPosition;
                    tailPosition = 0;
                }
                full = added.availablePermits() > 0;
                if ( full ) {
                    if ( tailPosition < headPosition ) {
                        full = tailPosition+siz >= headPosition;
                    } else if ( tailPosition > headPosition ){
                        full = false;
                    } else {
                        full = true;
                    }
                }
            }
            if ( full ) {
                taken.drainPermits();
                try {
                    taken.acquire();
                } catch (InterruptedException e) {
                    return false;
                }
            }
            synchronized (rwLock) {
                buffer.putInt(tailPosition, siz);
                buffer.position(tailPosition+4);
                buffer.put(out.getBuffer(), 0, siz );
                tailPosition+=siz+HEADER_SIZE;
            }
            added.release();
            return true;
        }
    }

    ByteBufferResult tmpRes = new ByteBufferResult();

    public Object takeObject() throws ClassNotFoundException, InstantiationException, IllegalAccessException, IOException{
        synchronized (readLock) {
            takeBytes(tmpRes);
            in.resetForReuseUseArray(tmpRes.b,0,tmpRes.b.length);
            return in.readObject();
        }
    }

    public boolean takeBytes(ByteBufferResult res) throws ClassNotFoundException, InstantiationException, IllegalAccessException, IOException{
        synchronized (readLock) {
            try {
                added.acquire();
            } catch (InterruptedException e) {
                return false;
            }
            synchronized (rwLock) {
                if (headPosition == currentQeueEnd ) {
                    headPosition = 0;
                }
                res.len = buffer.getInt(headPosition);
                buffer.position(headPosition+HEADER_SIZE);
                byte b[] = new byte[res.len];
                buffer.get(b);
                res.buffer = ByteBuffer.wrap(b);
                res.off = 0;
                res.b = b;
                headPosition += res.len+HEADER_SIZE;
            }
            taken.release();
        }
        return true;
    }

}