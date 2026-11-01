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

    /** 用户设定的目标增益（音量滑块 × 倍率） */
    private float gain = 1.0F;
    /** 当前真正应用给 OpenAL 的增益，淡入淡出期间会与 gain 不同 */
    private float appliedGain = 1.0F;
    /** 每 tick 的增益变化量；0 表示不淡变 */
    private float fadeStep = 0.0F;
    /** 淡出结束后要执行的动作（切换曲目等） */
    private Runnable pendingAction;
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
        // 淡出过程中不要打断；其余情况（比如拖动音量滑块）立即生效
        if (this.pendingAction == null)
        {
            this.appliedGain = this.gain;
            this.sink.setGain(this.appliedGain);
        }
    }

    /**
     * 先淡出当前曲目，再执行 {@code action}。
     *
     * <p>用在“常规音乐 ↔ 战斗音乐”这种需要换曲的场合：直接 stop 再 play 会听到
     * 明显的断点，淡出之后再切就自然得多。</p>
     *
     * <p>当前没有在放东西时不再等待，直接执行 {@code action}。</p>
     */
    public void fadeOutThen(Runnable action)
    {
        if (!isBusy())
        {
            action.run();
            return;
        }

        int ms = com.arin.musiclooper.Config.fadeOutMs;
        if (ms <= 0)
        {
            // 没开淡出：立刻切
            this.appliedGain = 0.0F;
            this.sink.setGain(0.0F);
            action.run();
            return;
        }

        this.pendingAction = action;
        this.fadeStep = fadeStepFor(ms);
    }

    /** 是否正在淡入或淡出 */
    public boolean isFading()
    {
        return this.pendingAction != null
                || Math.abs(this.appliedGain - this.gain) > 0.001F;
    }

    /** 把毫秒换算成每 tick 的增益步长（20 tick = 1 秒） */
    private static float fadeStepFor(int ms)
    {
        if (ms <= 0)
        {
            return 0.0F;
        }
        int ticks = Math.max(1, ms / 50);
        return 1.0F / ticks;
    }

    /** 每 tick 推进一次淡入淡出；到终点时执行挂起的动作 */
    private void updateFade()
    {
        float target = this.pendingAction != null ? 0.0F : this.gain;

        if (Math.abs(this.appliedGain - target) <= 0.001F)
        {
            if (this.appliedGain != target)
            {
                this.appliedGain = target;
                this.sink.setGain(this.appliedGain);
            }
            if (this.pendingAction != null)
            {
                Runnable action = this.pendingAction;
                this.pendingAction = null;
                this.fadeStep = 0.0F;
                action.run();
            }
            return;
        }

        float step = this.fadeStep > 0.0F ? this.fadeStep : 0.05F;
        if (target > this.appliedGain)
        {
            this.appliedGain = Math.min(target, this.appliedGain + step);
        }
        else
        {
            this.appliedGain = Math.max(target, this.appliedGain - step);
        }
        this.sink.setGain(this.appliedGain);
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

        // 新曲目从 0 淡入
        int fadeIn = com.arin.musiclooper.Config.fadeInMs;
        this.pendingAction = null;
        this.fadeStep = fadeStepFor(fadeIn);
        this.appliedGain = fadeIn > 0 ? 0.0F : this.gain;
        this.sink.setGain(this.appliedGain);

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
        this.pendingAction = null;
        this.fadeStep = 0.0F;
        this.appliedGain = 0.0F;
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

        // 推进淡入淡出；淡出终点可能要执行切换动作，放在推送音频之前
        updateFade();

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
