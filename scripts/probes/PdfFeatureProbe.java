import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.pdf.LoadParams;
import android.graphics.pdf.PdfRendererPreV;
import android.graphics.pdf.RenderParams;
import android.graphics.pdf.content.PdfPageTextContent;
import android.graphics.pdf.models.selection.PageSelection;
import android.graphics.pdf.models.selection.SelectionBoundary;
import android.os.ParcelFileDescriptor;
import android.os.ext.SdkExtensions;
import java.io.File;
import java.util.concurrent.FutureTask;
import org.json.JSONObject;

/** Synthetic platform feature execution, isolated from the installed reader and its records. */
public final class PdfFeatureProbe {
    private static final String FIXTURE_PASSWORD = "p2-public-synthetic-fixture";

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1 || SdkExtensions.getExtensionVersion(31) < 13) {
            throw new IllegalStateException("This probe requires a fixture directory and S extension 13.");
        }
        FutureTask<JSONObject> checks = new FutureTask<>(() -> execute(new File(arguments[0])));
        Thread worker = new Thread(checks, "p2-platform-feature-probe");
        worker.start();
        System.out.println(checks.get().toString());
    }

    private static JSONObject execute(File root) throws Exception {
        JSONObject report = new JSONObject().put("kind", "synthetic shell feature probe; not app acceptance");
        try (PdfRendererPreV renderer = open(new File(root, "text-outline.pdf"), null)) {
            require(renderer.getPageCount() == 1, "Unexpected fixture page count");
            try (PdfRendererPreV.Page page = renderer.openPage(0)) {
                StringBuilder text = new StringBuilder();
                for (PdfPageTextContent part : page.getTextContents()) text.append(part.getText());
                require(text.toString().contains("needle"), "Text extraction failed");
                require(page.searchText("needle").size() == 2, "Search count failed");
                require(page.searchText("not-in-synthetic-page").isEmpty(), "Empty search failed");
                PageSelection selection = page.selectContent(new SelectionBoundary(0), new SelectionBoundary(6));
                require(selection != null, "Text selection failed");
                StringBuilder selected = new StringBuilder();
                for (PdfPageTextContent part : selection.getSelectedTextContents()) selected.append(part.getText());
                require(selected.toString().contains("needle"), "Selection content failed");
                render(page);
            }
        }
        report.put("textExtraction", true).put("searchMatches", 2).put("emptySearch", true);
        report.put("selection", true).put("render", true);
        for (String name : new String[] {"aes128.pdf", "aes256.pdf"}) {
            File file = new File(root, name);
            requireRejected(file, null);
            requireRejected(file, "intentionally-wrong-synthetic-password");
            try (PdfRendererPreV renderer = open(file, FIXTURE_PASSWORD)) {
                require(renderer.getPageCount() == 1, "Encrypted fixture page count failed");
                try (PdfRendererPreV.Page page = renderer.openPage(0)) {
                    require(page.searchText("needle").size() == 2, "Encrypted search failed");
                    render(page);
                }
            }
            report.put(name.equals("aes128.pdf") ? "aes128" : "aes256", "missing/wrong rejected; correct render/search passed");
        }
        return report.put("passed", true);
    }

    private static PdfRendererPreV open(File file, String password) throws Exception {
        ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
        try {
            if (password == null) return new PdfRendererPreV(descriptor);
            return new PdfRendererPreV(descriptor, new LoadParams.Builder().setPassword(password).build());
        } catch (Exception | Error failure) {
            descriptor.close();
            throw failure;
        }
    }

    private static void requireRejected(File file, String password) throws Exception {
        try (PdfRendererPreV ignored = open(file, password)) {
            throw new AssertionError("Missing/wrong password was accepted");
        } catch (SecurityException expected) {
            // This is the documented rejection; other failure types must fail the probe.
        }
    }

    private static void render(PdfRendererPreV.Page page) {
        Bitmap bitmap = Bitmap.createBitmap(256, 384, Bitmap.Config.ARGB_8888);
        try {
            bitmap.eraseColor(Color.WHITE);
            Matrix transform = new Matrix();
            transform.setScale(256f / page.getWidth(), 384f / page.getHeight());
            page.render(bitmap, new Rect(0, 0, 256, 384), transform, new RenderParams.Builder(1).build());
            boolean nonWhite = false;
            for (int y = 0; y < bitmap.getHeight() && !nonWhite; y++) {
                for (int x = 0; x < bitmap.getWidth(); x++) {
                    if (bitmap.getPixel(x, y) != Color.WHITE) {
                        nonWhite = true;
                        break;
                    }
                }
            }
            require(nonWhite, "Render produced an empty synthetic page");
        } finally {
            bitmap.recycle();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
