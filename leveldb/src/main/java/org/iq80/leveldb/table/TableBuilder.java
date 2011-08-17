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

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import org.iq80.leveldb.util.PureJavaCrc32C;
import org.jboss.netty.buffer.ChannelBuffer;
import org.iq80.leveldb.util.Buffers;
import org.xerial.snappy.Snappy;

import java.io.IOException;
import java.nio.channels.FileChannel;

public class TableBuilder
{
    /**
     * TABLE_MAGIC_NUMBER was picked by running
     * echo http://code.google.com/p/leveldb/ | sha1sum
     * and taking the leading 64 bits.
     */
    public static final long TABLE_MAGIC_NUMBER = 0xdb4775248b80fb57L;

    private final int blockRestartInterval;
    private final int blockSize;
    private final CompressionType compressionType;

    private final FileChannel fileChannel;
    private final BlockBuilder dataBlockBuilder;
    private final BlockBuilder indexBlockBuilder;
    private byte[] lastKey;
    private final UserComparator userComparator;

    private long entryCount;

    // Either Finish() or Abandon() has been called.
    private boolean closed;

    // We do not emit the index entry for a block until we have seen the
    // first key for the next data block.  This allows us to use shorter
    // keys in the index block.  For example, consider a block boundary
    // between the keys "the quick brown fox" and "the who".  We can use
    // "the r" as the key for the index block entry since it is >= all
    // entries in the first block and < all entries in subsequent
    // blocks.
    private boolean pendingIndexEntry;
    private BlockHandle pendingHandle;  // Handle to add to index block

    private final ChannelBuffer compressedOutput;

    private long position;

    public TableBuilder(Options options, FileChannel fileChannel, UserComparator userComparator)
    {
        Preconditions.checkNotNull(options, "options is null");
        Preconditions.checkNotNull(fileChannel, "fileChannel is null");
        try {
            Preconditions.checkState(position == fileChannel.position(), "Expected position %s to equal fileChannel.position %s", position, fileChannel.position());
        }
        catch (IOException e) {
            throw Throwables.propagate(e);
        }

        this.fileChannel = fileChannel;
        this.userComparator = userComparator;

        blockRestartInterval = options.getBlockRestartInterval();
        blockSize = options.getBlockSize();
        compressionType = options.getCompressionType();

        dataBlockBuilder = new BlockBuilder(Buffers.dynamicBuffer(), blockRestartInterval, userComparator);
        indexBlockBuilder = new BlockBuilder(Buffers.dynamicBuffer(), 1, userComparator);

        lastKey = new byte[0];
        compressedOutput = Buffers.dynamicBuffer(128);

    }

    public long getEntryCount()
    {
        return entryCount;
    }

    public long getFileSize()
            throws IOException
    {
        return position + dataBlockBuilder.currentSizeEstimate();
    }

    public void add(BlockEntry blockEntry)
            throws IOException
    {
        Preconditions.checkNotNull(blockEntry, "blockEntry is null");
        add(blockEntry.getKey(), blockEntry.getValue());
    }

    public void add(byte[] key, ChannelBuffer value)
            throws IOException
    {
        Preconditions.checkNotNull(key, "key is null");
        Preconditions.checkNotNull(value, "value is null");

        Preconditions.checkState(!closed, "table is finished");

        if (entryCount > 0) {
            assert (userComparator.compare(key, lastKey) > 0) : "key must be greater than last key";
        }

        // If we just wrote a block, we can now add the handle to index block
        if (pendingIndexEntry) {
            Preconditions.checkState(dataBlockBuilder.isEmpty(), "Internal error: Table has a pending index entry but data block builder is empty");

            byte[] shortestSeparator = userComparator.findShortestSeparator(lastKey, key);

            ChannelBuffer handleEncoding = Buffers.dynamicBuffer();
            BlockHandle.writeBlockHandle(pendingHandle, handleEncoding);
            indexBlockBuilder.add(shortestSeparator, handleEncoding);
            pendingIndexEntry = false;
        }

        lastKey = key; // todo copy?
        entryCount++;
        dataBlockBuilder.add(key, value);

        int estimatedBlockSize = dataBlockBuilder.currentSizeEstimate();
        if (estimatedBlockSize >= blockSize) {
            flush();
        }
    }

    private void flush()
            throws IOException
    {
        Preconditions.checkState(!closed, "table is finished");
        if (dataBlockBuilder.isEmpty()) {
            return;
        }

        Preconditions.checkState(!pendingIndexEntry, "Internal error: Table already has a pending index entry to flush");

        pendingHandle = writeBlock(dataBlockBuilder);
        pendingIndexEntry = true;
    }

    private BlockHandle writeBlock(BlockBuilder blockBuilder)
            throws IOException
    {
        // close the block
        ChannelBuffer raw = blockBuilder.finish();

        // attempt to compress the block
        ChannelBuffer blockContents = raw;
        CompressionType blockCompressionType = CompressionType.NONE;
        if (compressionType == CompressionType.SNAPPY) {
            compressedOutput.ensureWritableBytes(Snappy.maxCompressedLength(raw.readableBytes()));
            compressedOutput.clear();

            try {
                int compressedSize = Snappy.compress(raw.array(), raw.arrayOffset() + raw.readerIndex(), raw.readableBytes(), compressedOutput.array(), 0);
                compressedOutput.writerIndex(compressedSize);

                // Don't use the compressed data if compressed less than 12.5%,
                if (compressedSize < raw.readableBytes() - (raw.readableBytes() / 8)) {
                    blockContents = compressedOutput;
                    blockCompressionType = CompressionType.SNAPPY;
                }
            }
            catch (IOException ignored) {
                // compression failed, so just store uncompressed form
            }
        }

        // create block trailer
        BlockTrailer blockTrailer = new BlockTrailer(blockCompressionType, crc32c(blockContents, blockCompressionType));
        ChannelBuffer trailer = BlockTrailer.writeBlockTrailer(blockTrailer);

        // create a handle to this block
        BlockHandle blockHandle = new BlockHandle(position, blockContents.readableBytes());

        // write data and trailer
        position += fileChannel.write(Buffers.wrappedBuffer(blockContents, trailer).toByteBuffers());

        // clean up state
        compressedOutput.clear();
        blockBuilder.reset();

        return blockHandle;
    }

    public void finish()
            throws IOException
    {
        Preconditions.checkState(!closed, "table is finished");

        // flush current data block
        flush();

        // mark table as closed
        closed = true;

        // write (empty) meta index block
        BlockBuilder metaIndexBlockBuilder = new BlockBuilder(Buffers.dynamicBuffer(), blockRestartInterval, new BasicUserComparator());
        // TODO(postrelease): Add stats and other meta blocks
        BlockHandle metaindexBlockHandle = writeBlock(metaIndexBlockBuilder);

        // add last handle to index block
        if (pendingIndexEntry) {
            byte[] shortSuccessor = userComparator.findShortSuccessor(lastKey);

            ChannelBuffer handleEncoding = Buffers.dynamicBuffer();
            BlockHandle.writeBlockHandle(pendingHandle, handleEncoding);
            indexBlockBuilder.add(shortSuccessor, handleEncoding);
            pendingIndexEntry = false;
        }

        // write index block
        BlockHandle indexBlockHandle = writeBlock(indexBlockBuilder);

        // write footer
        Footer footer = new Footer(metaindexBlockHandle, indexBlockHandle);
        ChannelBuffer footerEncoding = Footer.writeFooter(footer);
        position += fileChannel.write(footerEncoding.toByteBuffers());
    }

    public void abandon()
    {
        Preconditions.checkState(!closed, "table is finished");
        closed = true;
    }

    public static int crc32c(ChannelBuffer data, CompressionType type)
    {
        PureJavaCrc32C crc32c = new PureJavaCrc32C();
        crc32c.update(data.array(), data.arrayOffset() + data.readerIndex(), data.readableBytes());
        crc32c.update(type.getPersistentId() & 0xFF);
        return crc32c.getMaskedValue();
    }



}