/**
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
package org.iq80.leveldb.table;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import org.jboss.netty.buffer.ChannelBuffer;

import java.util.Arrays;
import java.util.Map.Entry;

import static com.google.common.base.Charsets.UTF_8;

/**
 * Binary Structure
 * <table summary="record format">
 * <tbody>
 * <thead>
 * <tr>
 * <th>name</th>
 * <th>offset</th>
 * <th>length</th>
 * <th>description</th>
 * </tr>
 * </thead>
 * <p/>
 * <tr>
 * <td>shared key length</td>
 * <td>0</td>
 * <td>vary</td>
 * <td>variable length encoded int: size of shared key prefix with the key from the previous entry</td>
 * </tr>
 * <tr>
 * <td>non-shared key length</td>
 * <td>vary</td>
 * <td>vary</td>
 * <td>variable length encoded int: size of non-shared key suffix in this entry</td>
 * </tr>
 * <tr>
 * <td>value length</td>
 * <td>vary</td>
 * <td>vary</td>
 * <td>variable length encoded int: size of value in this entry</td>
 * </tr>
 * <tr>
 * <td>non-shared key</td>
 * <td>vary</td>
 * <td>non-shared key length</td>
 * <td>non-shared key data</td>
 * </tr>
 * <tr>
 * <td>value</td>
 * <td>vary</td>
 * <td>value length</td>
 * <td>value data</td>
 * </tr>
 * </tbody>
 * </table>
 */
public class BlockEntry implements Entry<byte[], ChannelBuffer>
{
    public static final Function<BlockEntry, byte[]> GET_KEY = new Function<BlockEntry, byte[]>()
    {
        @Override
        public byte[] apply(BlockEntry blockEntry)
        {
            return blockEntry.getKey();
        }
    };

    private final byte[] key;
    private final ChannelBuffer value;

    public BlockEntry(byte[] key, ChannelBuffer value)
    {
        Preconditions.checkNotNull(key, "key is null");
        Preconditions.checkNotNull(value, "value is null");
        this.key = key;
        this.value = value;
    }

    public byte[] getKey()
    {
        return key;
    }

    public ChannelBuffer getValue()
    {
        return value.duplicate();
    }


    /**
     * @throws UnsupportedOperationException always
     */
    @Override
    public final ChannelBuffer setValue(ChannelBuffer value)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        BlockEntry entry = (BlockEntry) o;

        if (!Arrays.equals(key, entry.key)) {
            return false;
        }
        if (!value.equals(entry.value)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = Arrays.hashCode(key);
        result = 31 * result + value.hashCode();
        return result;
    }

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("BlockEntry");
        sb.append("{key=").append(new String(key, UTF_8));      // todo don't print the real value
        sb.append(", value=").append(value.toString(UTF_8));
        sb.append('}');
        return sb.toString();
    }
}