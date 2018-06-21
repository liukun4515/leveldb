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
package org.iq80.leveldb.util;

import java.util.Comparator;

/**
 * 基于slice的一个comparator,默认的是使用Slice中的byte的大小比较
 */
public final class SliceComparator
        implements Comparator<Slice>
{
    /** slice的比较器，当需要对slice进行sort的时候需要使用*/
    public static final SliceComparator SLICE_COMPARATOR = new SliceComparator();

    @Override
    public int compare(Slice sliceA, Slice sliceB)
    {
        return sliceA.compareTo(sliceB);
    }
}
