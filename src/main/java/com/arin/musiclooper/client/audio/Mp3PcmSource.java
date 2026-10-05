package com.arin.musiclooper.client.audio;

import javazoom.spi.mpeg.sampled.convert.MpegFormatConversionProvider;
import javazoom.spi.mpeg.sampled.file.MpegAudioFileReader;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import java.io.IOException;
import java.nio.file.Path;

/**
 * .mp3 解码器。
 *
 * <p><b>为什么不用 {@code AudioSystem}？</b>
 * JavaSound 的解码器是通过 {@code META-INF/services} + <i>线程上下文类加载器</i> 注册的。
 * 在 Forge 里，模组 jar 不一定出现在那个类加载器上（实测在渲染线程上就看不到），
 * 结果就是所有 mp3 都会被判成 {@code UnsupportedAudioFileException: File of unsupported format}。
 * 直接实例化 mp3spi 的解码器（它们就在模组 jar 里）可以完全绕开这张注册表。</p>
 */
public final class Mp3PcmSource implements PcmSource
{
    private final AudioInputStream stream;
    private final AudioFormat format;

    public Mp3PcmSource(Path file) throws Exception
    {
        AudioInputStream decoded = new MpegAudioFileReader().getAudioInputStream(file.toFile());
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

            decoded = new MpegFormatConversionProvider().getAudioInputStream(target, decoded);
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
