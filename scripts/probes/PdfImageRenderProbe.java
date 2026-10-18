import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.pdf.PdfRenderer;
import android.graphics.pdf.PdfRendererPreV;
import android.graphics.pdf.RenderParams;
import android.os.ParcelFileDescriptor;
import android.os.ext.SdkExtensions;
import java.io.File;
import java.util.concurrent.FutureTask;
import org.json.JSONArray;
import org.json.JSONObject;

/** Separates native render calls from UI scheduling; it does not measure scrolling latency. */
public final class PdfImageRenderProbe {
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1 || SdkExtensions.getExtensionVersion(31) < 13) {
            throw new IllegalStateException("This probe requires a fixture directory and S extension 13.");
        }
        FutureTask<JSONObject> probe = new FutureTask<>(() -> execute(new File(arguments[0])));
        new Thread(probe, "p2-isolated-image-probe").start();
        System.out.println(probe.get().toString());
    }

    private static JSONObject execute(File root) throws Exception {
        String[] names = {"gray-q85.pdf", "rgb-q85.pdf", "rgb-q50.pdf", "gray-half-q85.pdf"};
        JSONArray runs = new JSONArray();
        for (int run = 0; run < 3; run++) {
            for (int index = 0; index < names.length; index++) {
                String name = names[run % 2 == 0 ? index : names.length - 1 - index];
                for (boolean extension : new boolean[] {false, true}) {
                    runs.put(measure(new File(root, name), extension).put("run", run));
                }
            }
        }
        return new JSONObject()
            .put("kind", "isolated shell render timing; not reader scrolling or on-screen sharpness")
            .put("cacheCondition", "new renderer per sample; OS/decoder process caches not controlled")
            .put("engineOrder", "basic then extension; fixture order reversed on run 1")
            .put("runs", runs);
    }

    private static JSONObject measure(File file, boolean extension) throws Exception {
        ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
        AutoCloseable renderer;
        long begin = System.nanoTime();
        try {
            renderer = extension ? new PdfRendererPreV(descriptor) : new PdfRenderer(descriptor);
        } catch (Exception | Error failure) {
            descriptor.close();
            throw failure;
        }
        JSONObject result = new JSONObject().put("fixture", file.getName())
            .put("engine", extension ? "extension" : "basic").put("openMs", elapsed(begin));
        JSONArray steps = new JSONArray();
        try {
            for (int step = 0; step < 4; step++) {
                long openBegin = System.nanoTime();
                AutoCloseable page = extension
                    ? ((PdfRendererPreV) renderer).openPage(0) : ((PdfRenderer) renderer).openPage(0);
                double openMs = elapsed(openBegin);
                try {
                    int width = extension ? ((PdfRendererPreV.Page) page).getWidth() : ((PdfRenderer.Page) page).getWidth();
                    int height = extension ? ((PdfRendererPreV.Page) page).getHeight() : ((PdfRenderer.Page) page).getHeight();
                    float scale = step == 0 ? 512f / Math.max(width, height) : 4f;
                    int bitmapWidth = step == 0 ? (int) Math.ceil(width * scale) : 512;
                    int bitmapHeight = step == 0 ? (int) Math.ceil(height * scale) : 512;
                    Bitmap bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
                    try {
                        bitmap.eraseColor(Color.WHITE);
                        Matrix transform = new Matrix();
                        transform.setScale(scale, scale);
                        if (step == 2) transform.postTranslate(-512f, 0f);
                        long renderBegin = System.nanoTime();
                        if (extension) {
                            ((PdfRendererPreV.Page) page).render(bitmap,
                                new Rect(0, 0, bitmapWidth, bitmapHeight), transform, new RenderParams.Builder(1).build());
                        } else {
                            ((PdfRenderer.Page) page).render(bitmap,
                                new Rect(0, 0, bitmapWidth, bitmapHeight), transform, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                        }
                        double renderMs = elapsed(renderBegin);
                        int nonWhite = 0;
                        for (int y = 0; y < bitmapHeight; y++) {
                            for (int x = 0; x < bitmapWidth; x++) {
                                if (bitmap.getPixel(x, y) != Color.WHITE) nonWhite++;
                            }
                        }
                        if (nonWhite == 0) throw new AssertionError("Empty synthetic render");
                        steps.put(new JSONObject().put("step", step == 0 ? "preview" : step == 1 ? "firstTile" : step == 2 ? "adjacentTile" : "repeatFirstTile")
                            .put("pageOpenMs", openMs).put("renderMs", renderMs).put("nonWhitePixels", nonWhite)
                            .put("bitmapWidth", bitmapWidth).put("bitmapHeight", bitmapHeight).put("scale", scale));
                    } finally {
                        bitmap.recycle();
                    }
                } finally {
                    page.close();
                }
            }
        } finally {
            renderer.close();
        }
        return result.put("steps", steps);
    }

    private static double elapsed(long begin) {
        return (System.nanoTime() - begin) / 1_000_000.0;
    }
}
