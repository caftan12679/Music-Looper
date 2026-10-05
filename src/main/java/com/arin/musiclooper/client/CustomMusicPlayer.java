package com.arin.musiclooper.client;

import com.arin.musiclooper.MusicLooper;
import com.arin.musiclooper.client.audio.AudioDecoders;
import com.arin.musiclooper.client.audio.OpenAlSink;
import com.arin.musiclooper.client.audio.PcmSource;

import javax.sound.sampled.AudioFormat;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 第三方音乐播放器。
 *
 * <p>分成两半：</p>
 * <ul>
 *     <li><b>解码线程</b>（{@code MusicLooper-Decoder}）把文件解码成 16 位 PCM，
 *         塞进一个有界队列——队列满就自动阻塞，形成天然的反压；</li>
 *     <li><b>渲染线程</b>（{@link #tick()}）把队列里的 PCM 交给 {@link OpenAlSink}，
 *         由游戏的 OpenAL 设备直接输出。</li>
 * </ul>
 *
 * <p>OpenAL 的调用全部留在渲染线程：Minecraft 初始化声音引擎时就是在渲染线程上把
 * 上下文设为 current 的，复用它可以省掉自己搬运上下文（还容易出错）。</p>
 */
public final class CustomMusicPlayer
{
    /** 每块 PCM 的字节数：32 KB ≈ 0.37 秒（44.1 kHz 立体声 16 位） */
    private static final int CHUNK_BYTES = 1 << 15;
    /** 解码线程最多提前缓冲的块数（约 4.4 秒） */
    private static final int QUEUE_CHUNKS = 12;
    private static final long OFFER_TIMEOUT_MS = 50L;

    private final BlockingQueue<short[]> pcmQueue = new ArrayBlockingQueue<>(QUEUE_CHUNKS);
    private final OpenAlSink sink = new OpenAlSink();

    private volatile Thread worker;
    private volatile boolean running = true;
    /** 用户最近一次想播放的文件 */
    private volatile Path requested;
    private volatile Path current;
    private volatile AudioFormat format;
    private volatile boolean endOfStream;
    private volatile String workerError;

    private float gain = 1.0F;
    private boolean trackPrepared;
    private boolean reportedProblem;
    private boolean loggedStart;

    /** 输出后端不可用时的原因（null 表示正常） */
    public String problem()
    {
        return this.sink.problem();
    }

    public boolean isBusy()
    {
        return this.current != null;
    }

    public Path currentTrack()
    {
        return this.current;
    }

    public void setGain(float value)
    {
        this.gain = Math.max(0.0F, Math.min(1.0F, value));
        this.sink.setGain(this.gain);
    }

    /** 切到指定文件；上一首会立刻停下 */
    public void play(Path file)
    {
        stopPlayback();

        this.workerError = null;
        this.endOfStream = false;
        this.format = null;
        this.trackPrepared = false;
        this.reportedProblem = false;
        this.loggedStart = false;
        this.current = file;
        this.requested = file;

        Thread previous = this.worker;
        if (previous != null)
        {
            previous.interrupt();
        }

        Thread thread = new Thread(this::decodeLoop, "MusicLooper-Decoder");
        thread.setDaemon(true);
        this.worker = thread;
        thread.start();
    }

    /** 停止播放并清空队列 */
    public void stop()
    {
        stopPlayback();
        this.current = null;
        this.requested = null;
    }

    /** 游戏退出时调用 */
    public void shutdown()
    {
        this.running = false;
        stop();
        this.sink.destroy();
    }

    private void stopPlayback()
    {
        Thread previous = this.worker;
        this.worker = null;
        if (previous != null)
        {
            previous.interrupt();
        }

        this.pcmQueue.clear();
        this.endOfStream = false;
        this.trackPrepared = false;
        this.sink.stopAndClear();
    }

    // ------------------------------------------------------------------ 渲染线程

    /**
     * 每个 client tick 调一次（渲染线程）。
     * 负责把已经解码好的 PCM 推给 OpenAL，并判断一首歌是不是放完了。
     */
    public void tick()
    {
        if (this.current == null)
        {
            return;
        }

        if (!this.trackPrepared)
        {
            // 等解码线程把文件头读出来，才知道采样格式
            AudioFormat decoded = this.format;
            if (decoded == null)
            {
                return;
            }
            if (!this.sink.begin(decoded))
            {
                reportProblem();
                return;
            }
            this.trackPrepared = true;
        }

        this.sink.setGain(this.gain);

        int free = this.sink.reclaim();
        while (free-- > 0)
        {
            short[] chunk = this.pcmQueue.poll();
            if (chunk == null)
            {
                break;
            }
            this.sink.push(chunk);
        }

        this.sink.ensurePlaying();

        if (!this.loggedStart && !this.sink.isDrained())
        {
            this.loggedStart = true;
            MusicLooper.LOGGER.info("开始播放第三方音乐：{}（{}）",
                    this.current.getFileName(), this.sink.describe());
        }

        String error = this.workerError;
        if (error != null)
        {
            MusicLooper.LOGGER.warn("播放第三方音乐失败：{}（{}）", this.current.getFileName(), error);
            CustomMusicLibrary.markFailed(this.current);
            finish();
            return;
        }

        if (this.endOfStream && this.pcmQueue.isEmpty() && this.sink.isDrained())
        {
            MusicLooper.LOGGER.info("第三方音乐播放结束：{}", this.current.getFileName());
            finish();
        }
    }

    private void reportProblem()
    {
        if (!this.reportedProblem)
        {
            this.reportedProblem = true;
            MusicLooper.LOGGER.warn("第三方音乐无法输出：{}（原版音乐不受影响）", this.sink.problem());
        }
        finish();
    }

    private void finish()
    {
        this.current = null;
        this.requested = null;
        this.format = null;
        this.trackPrepared = false;
        this.endOfStream = false;
        this.workerError = null;
        this.pcmQueue.clear();

        Thread previous = this.worker;
        this.worker = null;
        if (previous != null)
        {
            previous.interrupt();
        }

        this.sink.stopAndClear();
    }

    // ------------------------------------------------------------------ 解码线程

    private void decodeLoop()
    {
        Thread self = Thread.currentThread();
        Path file = this.requested;
        if (file == null)
        {
            return;
        }

        try (PcmSource source = AudioDecoders.open(file))
        {
            this.format = source.format();

            byte[] bytes = new byte[CHUNK_BYTES];
            while (this.running && this.worker == self)
            {
                int read = readFully(source, bytes);
                if (read <= 0)
                {
                    break;
                }

                short[] samples = toShorts(bytes, read);
                if (samples.length == 0)
                {
                    break;
                }

                if (!offer(samples, self))
                {
                    return;
                }
            }
        }
        catch (Throwable throwable)
        {
            if (this.worker == self)
            {
                this.workerError = throwable.toString();
            }
        }
        finally
        {
            if (this.worker == self)
            {
                this.endOfStream = true;
            }
        }
    }

    private boolean offer(short[] samples, Thread self)
    {
        try
        {
            while (this.running && this.worker == self)
            {
                if (this.pcmQueue.offer(samples, OFFER_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                {
                    return true;
                }
            }
        }
        catch (InterruptedException interrupted)
        {
            Thread.currentThread().interrupt();
        }
        return false;
    }

    /** PcmSource 不保证一次读满，这里循环读到填满或文件结束 */
    private static int readFully(PcmSource source, byte[] buffer) throws Exception
    {
        int total = 0;
        while (total < buffer.length)
        {
            int read = source.read(buffer, total, buffer.length - total);
            if (read < 0)
            {
                break;
            }
            if (read == 0)
            {
                break;
            }
            total += read;
        }
        return total;
    }

    /** 16 位小端 PCM 字节 → short；末尾不足一个采样的字节会被丢掉 */
    private static short[] toShorts(byte[] bytes, int length)
    {
        int samples = length / 2;
        short[] result = new short[samples];
        for (int index = 0; index < samples; index++)
        {
            int low = bytes[index * 2] & 0xFF;
            int high = bytes[index * 2 + 1];
            result[index] = (short) ((high << 8) | low);
        }
        return result;
    }
}
