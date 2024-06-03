
import java.io.File;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

public class TamilTextExtractor {

    public static void main(String[] args) {
        // Provide the path to your image or PDF file
        File imageFile = new File("C:/Users/anand/OneDrive/Desktop/docs/page116.jpg");

        // Set the language to Tamil
        Tesseract tesseract = new Tesseract();
        tesseract.setLanguage("tam");

        try {
            // Use Tesseract to do OCR on the image
            String extractedText = tesseract.doOCR(imageFile);

            // Print the extracted text
            System.out.println("Extracted Text:\n" + extractedText);
        } catch (TesseractException e) {
            System.err.println(e.getMessage());
        }
    }
}
