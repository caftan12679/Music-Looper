package com.arin.musiclooper.client.audio;

import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALCCapabilities;

import javax.sound.sampled.AudioFormat;
import java.util.Locale;

/**
 * 把 PCM 数据流式推给 Minecraft 已经在用的 OpenAL 设备。
 *
 * <p><b>为什么不用 javax.sound.sampled？</b>
 * 手机启动器用的 JRE 里没有 {@code libjsound.so}，JavaSound 只有“读文件”能力，
 * <b>没有任何音频输出设备</b>，{@code AudioSystem.getSourceDataLine(...)} 必然失败。
 * 而游戏自己的声音走的是 OpenAL（日志里的 “OpenAL initialized on device OpenSL”），
 * 所以这里复用同一个 OpenAL 上下文。</p>
 *
 * <p><b>线程要求：</b>所有方法都必须在<b>渲染线程</b>上调用。Minecraft 在初始化声音引擎时
 * （{@code SoundEngine.reload() -> Library.init()}）就是在渲染线程上
 * {@code alcMakeContextCurrent} 并建立 AL 能力的，所以渲染线程天然可用；
 * 换到别的线程还得自己搬运上下文，没必要。</p>
 */
public final class OpenAlSink
{
    /** 同时挂在音源上的缓冲数量 */
    private static final int BUFFER_COUNT = 4;

    private final int[] buffers = new int[BUFFER_COUNT];

    private long context;
    private int source;
    private int alFormat = -1;
    private int frequency;
    /** 已入队、还没被播完的缓冲数量 */
    private int queued;
    /** 下一个要写入的缓冲下标（队列是先进先出，所以回收顺序和写入顺序一致） */
    private int writeIndex;
    private float gain = 1.0F;
    private String problem;

    /** 不可用时的一句话原因，用于日志 */
    public String problem()
    {
        return this.problem;
    }

    /**
     * 每个 tick 调一次：确认 OpenAL 上下文和音源都还可用。
     * 资源重载或音频设备切换后上下文会变，这时需要重建音源。
     */
    public boolean prepare()
    {
        if (this.problem != null)
        {
            return false;
        }

        try
        {
            establishCapabilities();

            long current = ALC10.alcGetCurrentContext();
            if (current == 0L)
            {
                fail("当前线程没有 OpenAL 上下文（声音引擎可能还没启动）");
                return false;
            }

            if (current != this.context)
            {
                destroy();
                this.context = current;
            }

            if (this.source == 0)
            {
                return createSource();
            }
            return true;
        }
        catch (Throwable throwable)
        {
            fail(throwable.toString());
            return false;
        }
    }

    /**
     * 本线程如果没有 AL 能力，就自己补一份。
     * （正常情况 Minecraft 已经建好了，这里只是兜底。）
     */
    private static void establishCapabilities()
    {
        if (AL.getCapabilities() != null)
        {
            return;
        }

        long current = ALC10.alcGetCurrentContext();
        if (current == 0L)
        {
            return;
        }

        ALCCapabilities alcCapabilities = ALC.getCapabilities();
        if (alcCapabilities == null)
        {
            long device = ALC10.alcGetContextsDevice(current);
            if (device == 0L)
            {
                return;
            }
            alcCapabilities = ALC.createCapabilities(device);
        }

        AL.createCapabilities(alcCapabilities);
    }

    private boolean createSource()
    {
        int handle = AL10.alGenSources();
        int error = AL10.alGetError();
        if (handle == 0 || error != AL10.AL_NO_ERROR)
        {
            fail("创建 OpenAL 音源失败（错误码 " + error + "）");
            return false;
        }

        // 相对音源 + 位置原点 + 无距离衰减 = 纯 2D 背景音乐
        AL10.alSourcei(handle, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
        AL10.alSource3f(handle, AL10.AL_POSITION, 0.0F, 0.0F, 0.0F);
        AL10.alSourcef(handle, AL10.AL_ROLLOFF_FACTOR, 0.0F);
        AL10.alSourcef(handle, AL10.AL_PITCH, 1.0F);
        AL10.alSourcef(handle, AL10.AL_GAIN, this.gain);

        for (int index = 0; index < BUFFER_COUNT; index++)
        {
            this.buffers[index] = AL10.alGenBuffers();
        }

        this.source = handle;
        this.queued = 0;
        this.writeIndex = 0;
        return true;
    }

    /** 开始一首新曲目：清掉上一条的残留，并锁定采样格式 */
    public boolean begin(AudioFormat format)
    {
        if (!prepare())
        {
            return false;
        }

        int mapped = toAlFormat(format);
        if (mapped == 0)
        {
            fail("不支持的音频格式：" + format);
            return false;
        }

        stopAndClear();
        this.alFormat = mapped;
        this.frequency = Math.round(format.getSampleRate());
        return true;
    }

    private static int toAlFormat(AudioFormat format)
    {
        if (!AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding())
                || format.getSampleSizeInBits() != 16
                || format.isBigEndian())
        {
            return 0;
        }

        return switch (format.getChannels())
        {
            case 1 -> AL10.AL_FORMAT_MONO16;
            case 2 -> AL10.AL_FORMAT_STEREO16;
            default -> 0;
        };
    }

    /** 回收已经播完的缓冲，返回现在还能塞几个 */
    public int reclaim()
    {
        if (this.source == 0)
        {
            return 0;
        }

        int processed = AL10.alGetSourcei(this.source, AL10.AL_BUFFERS_PROCESSED);
        for (int index = 0; index < processed; index++)
        {
            AL10.alSourceUnqueueBuffers(this.source);
        }
        this.queued = Math.max(0, this.queued - processed);
        return BUFFER_COUNT - this.queued;
    }

    /** 填充一个空闲缓冲并入队 */
    public void push(short[] samples)
    {
        if (this.source == 0 || samples.length == 0 || this.queued >= BUFFER_COUNT)
        {
            return;
        }

        int buffer = this.buffers[this.writeIndex];
        AL10.alBufferData(buffer, this.alFormat, samples, this.frequency);
        AL10.alSourceQueueBuffers(this.source, buffer);

        this.writeIndex = (this.writeIndex + 1) % BUFFER_COUNT;
        this.queued++;
    }

    /** 有数据但没在播（比如刚刚重新填满）就让它跑起来 */
    public void ensurePlaying()
    {
        if (this.source == 0 || this.queued == 0)
        {
            return;
        }

        if (AL10.alGetSourcei(this.source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING)
        {
            AL10.alSourcePlay(this.source);
        }
    }

    /** 缓冲区里已经没有数据、音源也停了 */
    public boolean isDrained()
    {
        if (this.source == 0)
        {
            return true;
        }
        return this.queued == 0
                && AL10.alGetSourcei(this.source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING;
    }

    public void setGain(float value)
    {
        this.gain = value;
        if (this.source != 0)
        {
            AL10.alSourcef(this.source, AL10.AL_GAIN, value);
        }
    }

    /** 停止并丢掉队列里剩下的所有缓冲 */
    public void stopAndClear()
    {
        if (this.source == 0)
        {
            return;
        }

        AL10.alSourceStop(this.source);
        int pending = AL10.alGetSourcei(this.source, AL10.AL_BUFFERS_QUEUED);
        for (int index = 0; index < pending; index++)
        {
            AL10.alSourceUnqueueBuffers(this.source);
        }

        this.queued = 0;
        this.writeIndex = 0;
    }

    /** 当前实际音量（用于日志） */
    public String describe()
    {
        if (this.source == 0)
        {
            return "未创建";
        }
        return String.format(Locale.ROOT, "alFormat=%d, %d Hz, 已排队 %d 个缓冲, 增益 %.2f",
                this.alFormat, this.frequency, this.queued, this.gain);
    }

    private void fail(String reason)
    {
        this.problem = reason;
        destroy();
    }

    /** 释放 OpenAL 资源（上下文改变或模组卸载时） */
    public void destroy()
    {
        if (this.source != 0)
        {
            try
            {
                AL10.alSourceStop(this.source);
                AL10.alDeleteSources(this.source);
            }
            catch (Throwable ignored)
            {
                // 上下文可能已经没了，忽略
            }
            this.source = 0;
        }

        for (int index = 0; index < BUFFER_COUNT; index++)
        {
            if (this.buffers[index] != 0)
            {
                try
                {
                    AL10.alDeleteBuffers(this.buffers[index]);
                }
                catch (Throwable ignored)
                {
                    // 同上
                }
                this.buffers[index] = 0;
            }
        }

        this.queued = 0;
        this.writeIndex = 0;
        this.context = 0L;
    }
}
