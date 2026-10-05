package com.arin.musiclooper.client.audio;

import com.mojang.blaze3d.audio.OggAudioStream;

import javax.sound.sampled.AudioFormat;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 用 Minecraft 自带的 Vorbis 解码器（OggAudioStream）解码 .ogg 文件。
 */
public class OggPcmSource implements PcmSource
{
    private final OggAudioStream stream;
    private final InputStream source;
    private final AudioFormat format;
    private boolean endOfFile;

    public OggPcmSource(Path file) throws IOException
    {
        InputStream raw = Files.newInputStream(file);
        this.source = raw;
        BufferedInputStream buffered = new BufferedInputStream(raw, 1 << 16);
        this.stream = new OggAudioStream(buffered);
        this.format = this.stream.getFormat();
    }

    @Override
    public AudioFormat format()
    {
        return this.format;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException
    {
        if (this.endOfFile)
        {
            return -1;
        }

        int total = 0;
        int emptyReads = 0;

        while (total < length)
        {
            ByteBuffer chunk;
            try
            {
                chunk = this.stream.read(length - total);
            }
            catch (Exception exception)
            {
                this.endOfFile = true;
                if (total > 0)
                {
                    return total;
                }
                throw exception instanceof IOException io ? io : new IOException(exception);
            }

            if (chunk == null)
            {
                this.endOfFile = true;
                break;
            }

            int available = chunk.remaining();
            if (available <= 0)
            {
                // 解码器偶尔会返回空缓冲，防止死循环
                if (++emptyReads > 128)
                {
                    break;
                }
                continue;
            }

            emptyReads = 0;
            chunk.get(buffer, offset + total, available);
            total += available;
        }

        return total == 0 ? -1 : total;
    }

    @Override
    public void close() throws IOException
    {
        try
        {
            this.stream.close();
        }
        catch (Exception exception)
        {
            // 忽略解码器关闭时的异常
        }
        this.source.close();
    }
}
