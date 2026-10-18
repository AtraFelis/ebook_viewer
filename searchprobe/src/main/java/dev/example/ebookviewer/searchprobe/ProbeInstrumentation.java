package dev.example.ebookviewer.searchprobe;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;

/** Self-targeted probe APK. It has no reader dependency, SAF picker or network permission. */
public final class ProbeInstrumentation extends Instrumentation {
    private String suite;
    private String fixture;
    private String mode;
    private String activeCase = "initialization";

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        suite = arguments == null ? "guards" : arguments.getString("suite", "guards");
        fixture = arguments == null ? "text-1000.pdf"
                : arguments.getString("fixture", "text-1000.pdf");
        mode = arguments == null ? "single-keep"
                : arguments.getString("mode", "single-keep");
        start();
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        JSONObject report = new JSONObject();
        try {
            require(suite.equals("guards") || suite.equals("search"), "Unknown suite");
            int before = ProbeMetrics.descriptors();
            JSONArray cases = new JSONArray();
            if (suite.equals("guards")) {
                for (String operation : new String[] {"ping", "stall", "cancel", "allocate", "die"}) {
                    String expected = operation.equals("stall") ? "TIME_BUDGET"
                            : operation.equals("cancel") ? "CANCELLED"
                            : operation.equals("allocate") ? "RESIDENT_BUDGET"
                            : operation.equals("die") ? "PROCESS_DIED" : "OK";
                    cases.put(runCase(operation, null, 0, expected));
                }
                File oversized = new File(getTargetContext().getCacheDir(), "oversized-synthetic.pdf");
                try {
                    try (RandomAccessFile output = new RandomAccessFile(oversized, "rw")) {
                        output.setLength(SearchProbeService.MAX_INPUT_BYTES + 1);
                    }
                    cases.put(runCase("search", oversized, 1000, "INPUT_LIMIT"));
                } finally {
                    require(oversized.delete(), "Synthetic size guard file not removed");
                }
                cases.put(runCase("ping", null, 0, "OK"));
            } else {
                require(fixture.matches("text-1000.pdf|images-(430|1000).pdf|dense-100.pdf"),
                        "Only bounded built-in synthetic fixtures are permitted");
                require(mode.matches("(single|dual)-(keep|reopen)"), "Unknown comparison mode");
                JSONObject metadata = metadata(fixture);
                File file = copyFixture(fixture, metadata.getString("sha256"));
                try {
                    JSONObject search = runCase("search", file, metadata.getInt("pages"), null);
                    JSONArray samples = search.getJSONArray("samples");
                    if (search.getString("outcome").equals("OK")) {
                        require(samples.length() > 0, "No search measurements");
                        JSONObject last = samples.getJSONObject(samples.length() - 1);
                        require(last.getInt("pages") == metadata.getInt("pages"), "Incomplete pages");
                        require(last.getLong("matches") == metadata.getLong("matches"),
                                "Unexpected synthetic search count");
                    }
                    cases.put(search);
                    report.put("fixture", fixture).put("mode", mode);
                    report.put("fixtureSha256", metadata.getString("sha256"));
                } finally {
                    require(file.delete(), "Synthetic fixture not removed");
                }
            }
            int after = ProbeMetrics.descriptors();
            require(after <= before + 4, "Parent descriptor count grew across isolated runs");
            report.put("kind", "bounded synthetic probe; not reader acceptance");
            report.put("suite", suite).put("cases", cases);
            report.put("parentFdBefore", before).put("parentFdAfter", after);
            result.putBoolean("p2ProbePassed", true);
        } catch (Exception | AssertionError error) {
            try {
                report.put("failedCase", activeCase);
                report.put("failureClass", error.getClass().getSimpleName());
                if (error instanceof AssertionError) report.put("assertion", error.getMessage());
            } catch (org.json.JSONException ignored) {
                result.putBoolean("reportEncodingFailed", true);
            }
            result.putBoolean("p2ProbePassed", false);
        }
        result.putString("p2ProbeReport", report.toString());
        finish(Activity.RESULT_OK, result);
    }

    private JSONObject runCase(String operation, File file, int pages, String expected) throws Exception {
        activeCase = operation;
        Context context = getTargetContext();
        CountDownLatch connected = new CountDownLatch(1);
        CountDownLatch dead = new CountDownLatch(1);
        AtomicReference<Messenger> remote = new AtomicReference<>();
        AtomicReference<Exception> connectionFailure = new AtomicReference<>();
        AtomicBoolean receiving = new AtomicBoolean(true);
        ArrayBlockingQueue<Bundle> events = new ArrayBlockingQueue<>(128);
        Messenger receiver = new Messenger(new Handler(Looper.getMainLooper(), message -> {
            if (receiving.get()) {
                Bundle event = new Bundle(message.getData());
                if (!events.offer(event)) {
                    events.poll();
                    events.offer(event);
                }
            }
            return true;
        }));
        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder binder) {
                try {
                    binder.linkToDeath(dead::countDown, 0);
                    remote.set(new Messenger(binder));
                } catch (RemoteException error) {
                    connectionFailure.set(error);
                }
                connected.countDown();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                dead.countDown();
            }

            @Override
            public void onNullBinding(ComponentName name) {
                connected.countDown();
            }
        };
        AtomicBoolean bound = new AtomicBoolean();
        runOnMainSync(() -> bound.set(context.bindService(
                new Intent(context, SearchProbeService.class), connection,
                Context.BIND_AUTO_CREATE | Context.BIND_WAIVE_PRIORITY | Context.BIND_NOT_FOREGROUND)));
        require(bound.get(), "Isolated service did not bind");
        long start = SystemClock.elapsedRealtime();
        try {
            require(connected.await(5, TimeUnit.SECONDS), "Isolated service connection timed out");
            if (connectionFailure.get() != null) throw connectionFailure.get();
            require(remote.get() != null, "Isolated service returned no binder");
            Message request = Message.obtain(null, 1);
            Bundle data = new Bundle();
            data.putString("operation", operation);
            data.putString("mode", mode);
            data.putInt("pages", pages);
            data.putInt("parentUid", Process.myUid());
            request.replyTo = receiver;
            try (ParcelFileDescriptor descriptor = file == null ? null
                    : ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)) {
                if (descriptor != null) data.putParcelable("fd", descriptor);
                request.setData(data);
                remote.get().send(request);
            }
            JSONObject result = new JSONObject().put("operation", operation);
            JSONArray samples = new JSONArray();
            String outcome = null;
            boolean isolated = false;
            while (SystemClock.elapsedRealtime() - start < 20_000) {
                Bundle event = events.poll(100, TimeUnit.MILLISECONDS);
                if (event != null) {
                    String type = event.getString("event", "");
                    if (type.equals("started")) {
                        isolated = event.getBoolean("isolated") && event.getBoolean("differentUid");
                        result.put("start", json(event));
                        if (operation.equals("cancel")) remote.get().send(Message.obtain(null, 2));
                    } else if (type.equals("sample")) {
                        samples.put(json(event));
                    } else if (type.equals("terminal")) {
                        outcome = event.getString("outcome");
                        result.put("terminal", json(event));
                    }
                }
                if (dead.getCount() == 0 && events.isEmpty()) {
                    // A death callback can race a final Messenger delivery on the main looper.
                    waitForIdleSync();
                    if (events.isEmpty()) break;
                }
            }
            require(dead.await(2, TimeUnit.SECONDS), "Child process survived the request deadline");
            require(isolated, "Child did not confirm a separate isolated UID");
            if (outcome == null) outcome = "PROCESS_DIED";
            result.put("outcome", outcome).put("samples", samples);
            result.put("elapsedMs", SystemClock.elapsedRealtime() - start);
            result.put("childTerminated", true).put("isolatedUid", true);
            if (expected != null) require(expected.equals(outcome), "Unexpected guard outcome: " + outcome);
            return result;
        } finally {
            receiving.set(false);
            runOnMainSync(() -> context.unbindService(connection));
        }
    }

    private JSONObject metadata(String name) throws Exception {
        byte[] bytes;
        try (InputStream input = getTargetContext().getAssets().open("manifest.json")) {
            bytes = readSmall(input, 32 * 1024);
        }
        JSONArray files = new JSONObject(new String(bytes, StandardCharsets.UTF_8)).getJSONArray("files");
        for (int index = 0; index < files.length(); index++) {
            if (files.getJSONObject(index).getString("file").equals(name)) return files.getJSONObject(index);
        }
        throw new AssertionError("Synthetic fixture missing from manifest");
    }

    private File copyFixture(String name, String expectedHash) throws Exception {
        File file = new File(getTargetContext().getCacheDir(), "current-synthetic.pdf");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = getTargetContext().getAssets().open(name);
                FileOutputStream output = new FileOutputStream(file)) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                require(total <= SearchProbeService.MAX_INPUT_BYTES, "Synthetic input exceeded limit");
                digest.update(buffer, 0, count);
                output.write(buffer, 0, count);
            }
        }
        StringBuilder hash = new StringBuilder();
        for (byte value : digest.digest()) hash.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        require(hash.toString().equals(expectedHash), "Synthetic fixture hash mismatch");
        return file;
    }

    private static byte[] readSmall(InputStream input, int limit) throws Exception {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) != -1) {
            require(output.size() + count <= limit, "Manifest exceeded limit");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static JSONObject json(Bundle bundle) throws Exception {
        JSONObject result = new JSONObject();
        for (String key : bundle.keySet()) result.put(key, bundle.get(key));
        return result;
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
