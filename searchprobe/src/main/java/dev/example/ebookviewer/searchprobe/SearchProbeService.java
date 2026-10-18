package dev.example.ebookviewer.searchprobe;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.graphics.pdf.PdfRenderer;
import android.graphics.pdf.PdfRendererPreV;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.ext.SdkExtensions;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Diagnostic-only isolated UID. Sampling is an abort policy, not a kernel memory ceiling. */
@SuppressLint("NewApi") // API 30/S extension 13 is checked before entering PreV code.
@SuppressWarnings("deprecation") // Bundle's typed Parcelable overload requires API 33.
public final class SearchProbeService extends Service {
    static final long MAX_INPUT_BYTES = 8L * 1024 * 1024;
    private static final long MIN_AVAILABLE_KB = 2L * 1024 * 1024;
    private static final long MAX_RSS_KB = 160L * 1024;
    private static final long MAX_RSS_GROWTH_KB = 32L * 1024;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean accepted = new AtomicBoolean();
    private final AtomicBoolean finished = new AtomicBoolean();
    private Messenger client;
    private volatile long deadline;
    private volatile long baselineRss;
    private volatile long growthLimit = MAX_RSS_GROWTH_KB;
    private volatile long observedPeakRss;
    private volatile long observedMinAvailable = Long.MAX_VALUE;
    private volatile byte[] deliberateAllocation;
    private final Messenger server = new Messenger(new Handler(Looper.getMainLooper(), message -> {
        if (message.what == 2) {
            terminate("CANCELLED");
        } else if (message.what == 1 && accepted.compareAndSet(false, true)) {
            client = message.replyTo;
            Bundle request = message.getData();
            new Thread(() -> execute(request), "synthetic-pdf-worker").start();
        }
        return true;
    }));

    @Override
    public IBinder onBind(Intent intent) {
        return server.getBinder();
    }

    private void send(String event, Bundle data) {
        data.putString("event", event);
        Message reply = Message.obtain(null, 1);
        reply.setData(data);
        try {
            client.send(reply);
        } catch (RemoteException error) {
            Process.killProcess(Process.myPid());
        }
    }

    private void terminate(String reason) {
        if (!finished.compareAndSet(false, true)) return;
        Bundle result = new Bundle();
        result.putString("outcome", reason);
        result.putLong("baselineRssKb", baselineRss);
        result.putLong("peakObservedRssKb", observedPeakRss);
        result.putLong("minObservedAvailableKb", observedMinAvailable);
        // Neither the main looper nor a blocked PDF worker may delay process teardown.
        // Arm this before IPC: even terminal delivery is only best effort.
        Thread terminator = new Thread(() -> {
            SystemClock.sleep(30);
            Process.killProcess(Process.myPid());
        }, "synthetic-pdf-terminator");
        terminator.setDaemon(true);
        terminator.start();
        send("terminal", result);
    }

    private void watchdog() {
        Thread guard = new Thread(() -> {
            while (!finished.get()) {
                try {
                    if (SystemClock.elapsedRealtime() >= deadline) {
                        terminate("TIME_BUDGET");
                    } else {
                        long rss = ProbeMetrics.rssKb();
                        observedPeakRss = Math.max(observedPeakRss, rss);
                        long available = ProbeMetrics.availableKb();
                        observedMinAvailable = Math.min(observedMinAvailable, available);
                        if (rss > MAX_RSS_KB || rss - baselineRss > growthLimit) {
                            terminate("RESIDENT_BUDGET");
                        } else if (available < MIN_AVAILABLE_KB) {
                            terminate("SYSTEM_MEMORY");
                        }
                    }
                } catch (IOException error) {
                    terminate("METRICS_UNAVAILABLE");
                }
                SystemClock.sleep(50);
            }
        }, "synthetic-pdf-budget");
        guard.setDaemon(true);
        guard.start();
    }

    private void execute(Bundle request) {
        ParcelFileDescriptor source = request.getParcelable("fd");
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            if (!Process.isIsolated() || Process.myUid() == request.getInt("parentUid")) {
                terminate("NOT_ISOLATED");
                return;
            }
            Bundle started = ProbeMetrics.snapshot();
            started.putBoolean("isolated", true);
            started.putBoolean("differentUid", true);
            baselineRss = started.getLong("rssKb");
            observedPeakRss = baselineRss;
            observedMinAvailable = started.getLong("availableKb");
            send("started", started);
            if (started.getLong("availableKb") < MIN_AVAILABLE_KB || baselineRss > MAX_RSS_KB) {
                terminate("SYSTEM_MEMORY");
                return;
            }
            String operation = request.getString("operation", "ping");
            growthLimit = operation.equals("allocate") ? 1024 : MAX_RSS_GROWTH_KB;
            deadline = SystemClock.elapsedRealtime() + (operation.equals("stall") ? 150 : 15_000);
            watchdog();
            if (finished.get()) return;
            if (operation.equals("die")) {
                Process.killProcess(Process.myPid());
            } else if (operation.equals("stall-main")) {
                main.post(() -> {
                    // Block only this synthetic child's looper, never the reader or system UI.
                    send("main-blocked", new Bundle());
                    deadline = SystemClock.elapsedRealtime() + 150;
                    SystemClock.sleep(10_000);
                });
                SystemClock.sleep(10_000);
            } else if (operation.equals("abandon")) {
                // No cancel command/self-termination: the parent must release the last binding.
                send("worker-blocked", new Bundle());
                SystemClock.sleep(10_000);
            } else if (operation.equals("stall") || operation.equals("cancel")) {
                SystemClock.sleep(10_000);
            } else if (operation.equals("allocate")) {
                // A bounded 4 MiB test; never provoke actual system OOM to test the watchdog.
                deliberateAllocation = new byte[4 * 1024 * 1024];
                for (int i = 0; i < deliberateAllocation.length; i += 4096) {
                    deliberateAllocation[i] = 1;
                }
                SystemClock.sleep(10_000);
            } else if (operation.equals("search")) {
                if (source == null || source.getStatSize() <= 0
                        || source.getStatSize() > MAX_INPUT_BYTES) {
                    terminate("INPUT_LIMIT");
                } else if (SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) < 13) {
                    terminate("UNSUPPORTED");
                } else {
                    search(source, request);
                }
            } else if (operation.equals("ping")) {
                terminate("OK");
            } else {
                terminate("BAD_REQUEST");
            }
        } catch (Exception | LinkageError error) {
            // No exception message/stack: it could include document paths or framework text.
            terminate("ERROR");
        } finally {
            if (source != null) {
                try {
                    source.close();
                } catch (IOException error) {
                    terminate("FD_CLOSE_ERROR");
                }
            }
        }
    }

    private void search(ParcelFileDescriptor source, Bundle request) throws IOException {
        String mode = request.getString("mode", "single-keep");
        if (!mode.matches("(single|dual)-(keep|reopen)")) {
            terminate("BAD_REQUEST");
            return;
        }
        int expectedPages = request.getInt("pages");
        if (expectedPages != 100 && expectedPages != 430 && expectedPages != 1000) {
            terminate("BAD_REQUEST");
            return;
        }
        PdfRenderer basic = null;
        PdfRendererPreV text = null;
        try {
            if (mode.startsWith("dual")) basic = openBasic(source);
            text = openText(source);
            if (text.getPageCount() != expectedPages) {
                terminate("PAGE_COUNT_MISMATCH");
                return;
            }
            long matches = 0;
            long start = SystemClock.elapsedRealtime();
            for (int index = 0; index < expectedPages && !finished.get(); index++) {
                if (index > 0 && index % 16 == 0 && mode.endsWith("reopen")) {
                    text.close();
                    text = openText(source);
                }
                deadline = Math.min(start + 15_000, SystemClock.elapsedRealtime() + 2_000);
                long pageStart = SystemClock.elapsedRealtime();
                try (PdfRendererPreV.Page page = text.openPage(index)) {
                    // Only this page's list is transiently retained. No extracted text leaves IPC.
                    matches += page.searchText("ne").size();
                }
                if (index % 16 == 0 || index == expectedPages - 1) {
                    Bundle sample = ProbeMetrics.snapshot();
                    sample.putInt("pages", index + 1);
                    sample.putLong("matches", matches);
                    sample.putLong("elapsedMs", SystemClock.elapsedRealtime() - start);
                    sample.putLong("pageMs", SystemClock.elapsedRealtime() - pageStart);
                    send("sample", sample);
                }
            }
            if (!finished.get()) {
                Bundle end = ProbeMetrics.snapshot();
                end.putInt("pages", expectedPages);
                end.putLong("matches", matches);
                end.putLong("elapsedMs", SystemClock.elapsedRealtime() - start);
                send("sample", end);
            }
        } finally {
            if (text != null) text.close();
            if (basic != null) basic.close();
        }
        terminate("OK");
    }

    private static PdfRenderer openBasic(ParcelFileDescriptor source) throws IOException {
        ParcelFileDescriptor copy = ParcelFileDescriptor.dup(source.getFileDescriptor());
        try {
            return new PdfRenderer(copy);
        } catch (Exception | Error failure) {
            copy.close();
            throw failure;
        }
    }

    private static PdfRendererPreV openText(ParcelFileDescriptor source) throws IOException {
        ParcelFileDescriptor copy = ParcelFileDescriptor.dup(source.getFileDescriptor());
        try {
            return new PdfRendererPreV(copy);
        } catch (Exception | Error failure) {
            copy.close();
            throw failure;
        }
    }

    @Override
    public void onDestroy() {
        // A native call might still be blocked: kernel process teardown reclaims its descriptors.
        Process.killProcess(Process.myPid());
        super.onDestroy();
    }
}
