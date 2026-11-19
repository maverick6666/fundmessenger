import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.cos.*;
import java.io.File;

public class PdfFontCheck {
    public static void main(String[] args) throws Exception {
        String path = "uploads/bfae1e25-20b0-4a83-aae3-19719bdaf68c.pdf";
        try (PDDocument doc = Loader.loadPDF(new File(path))) {
            for (int p = 0; p < Math.min(doc.getNumberOfPages(), 3); p++) {
                PDPage page = doc.getPage(p);
                var resources = page.getResources();
                if (resources == null) continue;
                System.out.println("=== Page " + p + " ===");
                for (var fontName : resources.getFontNames()) {
                    PDFont font = resources.getFont(fontName);
                    System.out.println("Font: " + fontName + " -> " + font.getName() + " [" + font.getClass().getSimpleName() + "]");
                    
                    var dict = font.getCOSObject();
                    var encodingObj = dict.getDictionaryObject(COSName.ENCODING);
                    System.out.println("  Encoding raw: " + encodingObj);
                    if (encodingObj instanceof COSName) {
                        System.out.println("  Encoding name: " + ((COSName)encodingObj).getName());
                    } else if (encodingObj instanceof COSDictionary) {
                        COSDictionary encDict = (COSDictionary) encodingObj;
                        System.out.println("  Encoding dict BaseEncoding: " + encDict.getDictionaryObject(COSName.BASE_ENCODING));
                        System.out.println("  Encoding dict keys: " + encDict.keySet());
                    }
                    
                    if (font instanceof PDType0Font) {
                        PDType0Font t0 = (PDType0Font) font;
                        System.out.println("  Type0 descendant: " + t0.getDescendantFont().getClass().getSimpleName());
                        System.out.println("  CIDSystemInfo: " + t0.getDescendantFont().getCIDSystemInfo());
                    }
                }
            }
        }
    }
}
