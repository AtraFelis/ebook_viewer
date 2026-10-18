import dev.example.ebookviewer.DebugFrameProbeKt;
import java.util.ArrayList;
import java.util.List;
import kotlin.Unit;

/** PC-only checks against the compiled instrumentation helper; no Android device or PDF needed. */
public final class ProbeCompletionCheck {
    public static void main(String[] args) {
        List<String> events = new ArrayList<>();
        DebugFrameProbeKt.finishProbe(null,
                () -> { events.add("restore"); return Unit.INSTANCE; },
                () -> { events.add("report"); return Unit.INSTANCE; });
        check(events.equals(List.of("restore", "report")), "Successful completion order");

        events.clear();
        AssertionError restore = new AssertionError("synthetic restore failure");
        try {
            DebugFrameProbeKt.finishProbe(null,
                    () -> { throw restore; },
                    () -> { events.add("report"); return Unit.INSTANCE; });
            throw new AssertionError("Restore failure was hidden");
        } catch (AssertionError error) {
            check(error == restore, "Restore failure identity");
            check(events.equals(List.of("report")), "Report survives restore failure");
        }

        events.clear();
        AssertionError primary = new AssertionError("synthetic measurement failure");
        IllegalStateException write = new IllegalStateException("synthetic report write failure");
        DebugFrameProbeKt.finishProbe(primary,
                () -> { throw restore; },
                () -> { throw write; },
                () -> { events.add("send"); return Unit.INSTANCE; });
        check(primary.getSuppressed().length == 2, "Both secondary failures retained");
        check(primary.getSuppressed()[0] == restore, "Restore attached to original failure");
        check(primary.getSuppressed()[1] == write, "Write attached to original failure");
        check(events.equals(List.of("send")), "Status survives report write failure");

        events.clear();
        try {
            DebugFrameProbeKt.finishProbe(null,
                    () -> { throw write; },
                    () -> { events.add("send"); return Unit.INSTANCE; });
            throw new AssertionError("Report failure was hidden");
        } catch (IllegalStateException error) {
            check(error == write, "Report failure identity");
            check(events.equals(List.of("send")), "Status attempted before report failure escapes");
        }
        System.out.println("Probe completion checks: 4 passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
