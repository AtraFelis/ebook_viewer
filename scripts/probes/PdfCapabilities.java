import android.graphics.pdf.PdfRenderer;
import android.os.Build;
import android.os.ext.SdkExtensions;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import org.json.JSONArray;
import org.json.JSONObject;

/** Public platform API inventory only; no document, app data, or device identifier is read. */
public final class PdfCapabilities {
    public static void main(String[] arguments) throws Exception {
        JSONObject report = new JSONObject();
        report.put("kind", "public SDK inventory; not a PDF feature execution test");
        report.put("api", Build.VERSION.SDK_INT);
        if (Build.VERSION.SDK_INT >= 30) {
            report.put("sExtension", SdkExtensions.getExtensionVersion(31));
            if (Build.VERSION.SDK_INT >= 33) {
                report.put("tExtension", SdkExtensions.getExtensionVersion(33));
            }
        }
        JSONArray classes = new JSONArray();
        classes.put(describe(PdfRenderer.class.getName()));
        classes.put(describe(PdfRenderer.Page.class.getName()));
        classes.put(describe("android.graphics.pdf.PdfRendererPreV"));
        classes.put(describe("android.graphics.pdf.PdfRendererPreV$Page"));
        classes.put(describe("android.graphics.pdf.LoadParams$Builder"));
        report.put("classes", classes);
        System.out.println(report.toString());
    }

    private static JSONObject describe(String name) throws Exception {
        JSONObject result = new JSONObject().put("class", name);
        try {
            Class<?> type = Class.forName(name);
            result.put("present", true);
            Method[] methods = type.getMethods();
            Arrays.sort(methods, Comparator.comparing(Method::toGenericString));
            JSONArray signatures = new JSONArray();
            for (Method method : methods) {
                if (method.getDeclaringClass().getName().startsWith("android.graphics.pdf.")) {
                    signatures.put(method.toGenericString());
                }
            }
            result.put("publicMethods", signatures);
            result.put("publicConstructors", Arrays.toString(type.getConstructors()));
        } catch (ClassNotFoundException absent) {
            result.put("present", false);
        }
        return result;
    }
}
