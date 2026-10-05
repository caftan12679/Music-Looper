package com.arin.musiclooper.client.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.IOException;
import java.nio.file.Path;

/**
 * 用 Java 自带（或内置解码器提供）的 JavaSound 读取 wav / aiff / au / mp3 等格式，
 * 并统一转换成 16 位小端 PCM，方便直接写进音频设备。
 */
public class JavaSoundPcmSource implements PcmSource
{
    private final AudioInputStream stream;
    private final AudioFormat format;

    public JavaSoundPcmSource(Path file) throws Exception
    {
        AudioInputStream decoded = AudioSystem.getAudioInputStream(file.toFile());
        AudioFormat sourceFormat = decoded.getFormat();

        boolean alreadyPcm16 = AudioFormat.Encoding.PCM_SIGNED.equals(sourceFormat.getEncoding())
                && sourceFormat.getSampleSizeInBits() == 16
                && !sourceFormat.isBigEndian();

        if (!alreadyPcm16)
        {
            AudioFormat target = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    sourceFormat.getSampleRate(),
                    16,
                    sourceFormat.getChannels(),
                    sourceFormat.getChannels() * 2,
                    sourceFormat.getSampleRate(),
                    false);

            if (AudioSystem.isConversionSupported(target, sourceFormat))
            {
                decoded = AudioSystem.getAudioInputStream(target, decoded);
            }
        }

        this.stream = decoded;
        this.format = decoded.getFormat();
    }

    @Override
    public AudioFormat format()
    {
        return this.format;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException
    {
        return this.stream.read(buffer, offset, length);
    }

    @Override
    public void close() throws IOException
    {
        this.stream.close();
    }
}
