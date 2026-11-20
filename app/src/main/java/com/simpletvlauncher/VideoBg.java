package com.simpletvlauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.view.Surface;
import android.view.TextureView;

import java.io.File;
import java.nio.ByteBuffer;

/**
 * Беззвучное видео на фоне (mp4), обрезка по центру. Зацикливание — без пауз: декодер не пересоздаётся и не сбрасывается,
 * в конце файла экстрактор просто перематывается на начало, а метки времени продолжают расти.
 * При уходе из лаунчера видео ставится на паузу (декодер остаётся), поэтому возврат мгновенный.
 */
public class VideoBg extends TextureView implements TextureView.SurfaceTextureListener {
    private File file;
    private int vw, vh;
    private boolean wantPlay;
    private Worker worker;

    public VideoBg(Context c) {
        super(c);
        setSurfaceTextureListener(this);
        setFocusable(false);
    }

    public void setSource(File f) {
        if (file != null && f != null && file.equals(f) && file.lastModified() == lastMod) return;
        boolean was = worker != null;
        killWorker();
        file = f;
        lastMod = f == null ? 0 : f.lastModified();
        vw = vh = 0;
        if (was && wantPlay) startWorker();
    }

    private long lastMod;

    public void play() {
        wantPlay = true;
        if (worker != null) worker.paused = false;
        else if (isAvailable()) startWorker();
    }

    /** Кадр с идущего видео в уменьшенном размере или null, если видео сейчас не показывается. */
    public Bitmap still(int w, int h) {
        Worker wk = worker;
        if (wk == null || wk.paused || !isAvailable()) return null;
        return getBitmap(w, h);
    }

    /** Пауза без освобождения декодера. */
    public void pause() {
        if (worker != null) worker.paused = true;
    }

    public void stop() {
        wantPlay = false;
        killWorker();
    }

    private void startWorker() {
        killWorker();
        SurfaceTexture st = getSurfaceTexture();
        if (file == null || !file.exists() || st == null) return;
        worker = new Worker(file, new Surface(st));
        worker.start();
    }

    private void killWorker() {
        Worker w = worker;
        worker = null;
        if (w != null) {
            w.running = false;
            w.interrupt();
            try { w.join(500); } catch (InterruptedException ignored) {}
        }
    }

    private void fit() {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0 || vw == 0 || vh == 0) return;
        float va = w / (float) h, ra = vw / (float) vh;
        Matrix m = new Matrix();
        m.setScale(Math.max(1f, ra / va), Math.max(1f, va / ra), w / 2f, h / 2f);
        setTransform(m);
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        fit();
    }

    @Override public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) { if (wantPlay) startWorker(); }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) { fit(); }
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st) { killWorker(); return true; }
    @Override public void onSurfaceTextureUpdated(SurfaceTexture st) {}

    private final class Worker extends Thread {
        volatile boolean running = true, paused;
        private final File f;
        private final Surface surface;

        Worker(File f, Surface s) { super("VideoBg"); this.f = f; this.surface = s; setPriority(Thread.NORM_PRIORITY + 1); }

        @Override public void run() {
            MediaExtractor ex = new MediaExtractor();
            MediaCodec codec = null;
            try {
                ex.setDataSource(f.getAbsolutePath());
                int track = -1;
                MediaFormat fmt = null;
                for (int i = 0; i < ex.getTrackCount(); i++) {
                    MediaFormat tf = ex.getTrackFormat(i);
                    String mime = tf.getString(MediaFormat.KEY_MIME);
                    if (mime != null && mime.startsWith("video/")) { track = i; fmt = tf; break; }
                }
                if (track < 0) return;
                ex.selectTrack(track);
                final int w = fmt.getInteger(MediaFormat.KEY_WIDTH), h = fmt.getInteger(MediaFormat.KEY_HEIGHT);
                int rot = fmt.containsKey("rotation-degrees") ? fmt.getInteger("rotation-degrees") : 0;
                final boolean sw = rot == 90 || rot == 270;
                post(new Runnable() { @Override public void run() { vw = sw ? h : w; vh = sw ? w : h; fit(); } });

                codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME));
                codec.configure(fmt, surface, null, 0);
                codec.start();

                long frameUs = 33333;
                if (fmt.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                    try { int fr = fmt.getInteger(MediaFormat.KEY_FRAME_RATE); if (fr > 0) frameUs = 1000000L / fr; } catch (Throwable ignored) {}
                }
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                long offsetUs = 0, maxPts = 0, baseNs = -1;
                boolean wasPaused = false;

                while (running) {
                    if (paused) {
                        wasPaused = true;
                        try { Thread.sleep(40); } catch (InterruptedException e) { if (!running) break; }
                        continue;
                    }
                    if (wasPaused) { baseNs = -1; wasPaused = false; }

                    // вход: по кругу, без сброса декодера
                    int in = codec.dequeueInputBuffer(0);
                    if (in >= 0) {
                        ByteBuffer buf = codec.getInputBuffer(in);
                        int size = ex.readSampleData(buf, 0);
                        if (size < 0) {
                            offsetUs = maxPts + frameUs;
                            maxPts = 0;
                            ex.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                            size = ex.readSampleData(buf, 0);
                        }
                        if (size >= 0) {
                            long pts = ex.getSampleTime() + offsetUs;
                            codec.queueInputBuffer(in, 0, size, pts, 0);
                            if (pts > maxPts) maxPts = pts;
                            ex.advance();
                        } else {
                            codec.queueInputBuffer(in, 0, 0, 0, 0);
                        }
                    }

                    // выход: показываем кадр строго по времени
                    int out = codec.dequeueOutputBuffer(info, 5000);
                    if (out >= 0) {
                        long now = System.nanoTime();
                        if (baseNs < 0) baseNs = now - info.presentationTimeUs * 1000L;
                        long target = baseNs + info.presentationTimeUs * 1000L;
                        long lateMs = (now - target) / 1000000L;
                        if (lateMs > 120) { baseNs = now - info.presentationTimeUs * 1000L; target = now; }
                        long waitMs = (target - now) / 1000000L;
                        if (waitMs > 1) {
                            try { Thread.sleep(waitMs - 1); } catch (InterruptedException e) { if (!running) { codec.releaseOutputBuffer(out, false); break; } }
                        }
                        codec.releaseOutputBuffer(out, true);
                    }
                }
            } catch (Throwable t) {
                // файл не воспроизводится — остаётся градиент
            } finally {
                try { if (codec != null) { codec.stop(); codec.release(); } } catch (Throwable ignored) {}
                try { ex.release(); } catch (Throwable ignored) {}
                try { surface.release(); } catch (Throwable ignored) {}
            }
        }
    }
}
