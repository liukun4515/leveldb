/*
 * Copyright (C) 2011 the original author or authors.
 * See the notice.md file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.iq80.leveldb.impl;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import org.iq80.leveldb.WriteBatch;
import org.iq80.leveldb.util.Slice;
import org.iq80.leveldb.util.Slices;

import java.util.List;
import java.util.Map.Entry;

import static com.google.common.collect.Lists.newArrayList;

/**
 * write操作都通过这个接口来操作
 */
public class WriteBatchImpl
        implements WriteBatch
{
    /**
     * batch表示一系列操作
     * 操作的key和value的都是slice
     */
    private final List<Entry<Slice, Slice>> batch = newArrayList();
    /**
     * 统计内存的bytes大小
     */
    private int approximateSize;

    public int getApproximateSize()
    {
        return approximateSize;
    }

    public int size()
    {
        // point操作的大小
        return batch.size();
    }


    @Override
    public WriteBatchImpl put(byte[] key, byte[] value)
    {
        Preconditions.checkNotNull(key, "key is null");
        Preconditions.checkNotNull(value, "value is null");
        batch.add(Maps.immutableEntry(Slices.wrappedBuffer(key), Slices.wrappedBuffer(value)));
        approximateSize += 12 + key.length + value.length;
        return this;
    }

    public WriteBatchImpl put(Slice key, Slice value)
    {
        Preconditions.checkNotNull(key, "key is null");
        Preconditions.checkNotNull(value, "value is null");
        batch.add(Maps.immutableEntry(key, value));
        approximateSize += 12 + key.length() + value.length();
        return this;
    }

    @Override
    public WriteBatchImpl delete(byte[] key)
    {
        Preconditions.checkNotNull(key, "key is null");
        // delete 操作value是null
        batch.add(Maps.immutableEntry(Slices.wrappedBuffer(key), (Slice) null));
        approximateSize += 6 + key.length;
        return this;
    }

    public WriteBatchImpl delete(Slice key)
    {
        Preconditions.checkNotNull(key, "key is null");
        batch.add(Maps.immutableEntry(key, (Slice) null));
        approximateSize += 6 + key.length();
        return this;
    }

    @Override
    public void close()
    {
    }

    /**
     * 把所有的操作都写入到handler对象中
     * handler对象的写入操作都是一样的
     * @param handler
     */
    public void forEach(Handler handler)
    {
        for (Entry<Slice, Slice> entry : batch) {
            Slice key = entry.getKey();
            Slice value = entry.getValue();
            if (value != null) {
                handler.put(key, value);
            }
            else {
                handler.delete(key);
            }
        }
    }

    public interface Handler
    {
        void put(Slice key, Slice value);

        void delete(Slice key);
    }
}
