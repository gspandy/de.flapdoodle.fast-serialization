/*
 * Copyright (c) 2012, Ruediger Moeller. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 *
 */
package de.ruedigermoeller.serialization;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Created with IntelliJ IDEA.
 * User: Möller
 * Date: 03.11.12
 * Time: 13:11
 * To change this template use File | Settings | File Templates.
 */
public class FSTClazzInfoRegistry {

    HashMap mInfos = new HashMap(97);
    FSTSerializerRegistry serializerRegistry = new FSTSerializerRegistry();
    boolean ignoreAnnotations = false;
    final AtomicBoolean rwLock = new AtomicBoolean(false);
    private boolean structMode = false;

    public FSTClazzInfoRegistry() {
    }

    public FSTClazzInfo getCLInfo(Class c) {
        while(!rwLock.compareAndSet(false,true));
        FSTClazzInfo res = (FSTClazzInfo) mInfos.get(c);
        if ( res == null ) {
            if ( c == null ) {
                rwLock.set(false);
                throw new NullPointerException("Class is null");
            }
            res = new FSTClazzInfo(c, this, ignoreAnnotations);
            mInfos.put( c, res );
        }
        rwLock.set(false);
        return res;
    }

    public final boolean isIgnoreAnnotations() {
        return ignoreAnnotations;
    }

    public void setIgnoreAnnotations(boolean ignoreAnnotations) {
        this.ignoreAnnotations = ignoreAnnotations;
    }


    public void setSerializerRegistryDelegate(FSTSerializerRegistryDelegate delegate) {
        serializerRegistry.setDelegate(delegate);
    }

    public FSTSerializerRegistryDelegate getSerializerRegistryDelegate() {
        return serializerRegistry.getDelegate();
    }

    public void setStructMode(boolean structMode) {
        this.structMode = structMode;
    }

    public boolean isStructMode() {
        return structMode;
    }
}
