package com.arin.musiclooper.client.audio;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;

/**
 * 一个只读的 PCM 数据源：把音乐文件解码成与 {@link #format()} 描述一致的 PCM 字节。
 *
 * <p>约定输出格式为 <b>16 位有符号小端 PCM</b>（单声道或立体声），
 * 这样可以直接喂给 OpenAL 的 {@code AL_FORMAT_MONO16} / {@code AL_FORMAT_STEREO16}。</p>
 */
public interface PcmSource extends AutoCloseable
{
    AudioFormat format();

    /**
     * 读取 PCM 数据到 {@code buffer[offset, offset + length)}。
     *
     * @return 实际读到的字节数；返回 -1 表示文件结束
     */
    int read(byte[] buffer, int offset, int length) throws IOException;

    /** 便捷写法：填满整个 buffer */
    default int read(byte[] buffer) throws IOException
    {
        return read(buffer, 0, buffer.length);
    }

    @Override
    void close() throws IOException;
}
