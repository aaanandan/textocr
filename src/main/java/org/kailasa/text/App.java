
import java.awt.BorderLayout;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import org.apache.commons.io.FileUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import com.cybozu.labs.langdetect.Detector;
import com.cybozu.labs.langdetect.DetectorFactory;
import com.cybozu.labs.langdetect.LangDetectException;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

//TODO: tamil chars are not displaying propery.
public class App {

    private static JProgressBar progressBar;
    private static JTextArea progressTextArea;
    private static JTextArea extractedTextArea;
    private static JButton selectFolderButton;
    private static JButton cancelButton;
    private static JButton restartButton;
    private static JRadioButton englishRadioButton;
    private static JRadioButton tamilRadioButton;
    private static volatile boolean isCancelled = false;
    private static String selectedLanguage = "eng";

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("File Conversion Progress");
            frame.setSize(800, 600);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            JPanel panel = new JPanel();
            panel.setLayout(new BorderLayout());

            selectFolderButton = new JButton("Select Folder");
            selectFolderButton.addActionListener(e -> selectFolder());

            cancelButton = new JButton("Cancel");
            cancelButton.setEnabled(false);
            cancelButton.addActionListener(e -> isCancelled = true);

            restartButton = new JButton("Restart");
            restartButton.setEnabled(false);
            restartButton.addActionListener(e -> restartApplication());

            englishRadioButton = new JRadioButton("English");
            tamilRadioButton = new JRadioButton("Tamil");

            englishRadioButton.setSelected(true);

            ButtonGroup languageGroup = new ButtonGroup();
            languageGroup.add(englishRadioButton);
            languageGroup.add(tamilRadioButton);

            JPanel buttonPanel = new JPanel();
            buttonPanel.add(selectFolderButton);
            buttonPanel.add(cancelButton);
            buttonPanel.add(restartButton);
            buttonPanel.add(englishRadioButton);
            buttonPanel.add(tamilRadioButton);

            panel.add(buttonPanel, BorderLayout.NORTH);

            progressTextArea = new JTextArea();
            progressTextArea.setEditable(false);
            JScrollPane progressScrollPane = new JScrollPane(progressTextArea);
            panel.add(progressScrollPane, BorderLayout.WEST);

            extractedTextArea = new JTextArea();
            extractedTextArea.setEditable(false);
            JScrollPane extractedScrollPane = new JScrollPane(extractedTextArea);
            panel.add(extractedScrollPane, BorderLayout.EAST);

            progressBar = new JProgressBar(0, 100);
            progressBar.setValue(0);
            progressBar.setStringPainted(true);
            panel.add(progressBar, BorderLayout.SOUTH);

            frame.add(panel);
            frame.setVisible(true);

            // Initialize language detection
            initializeLanguageDetection();
        });
    }

    private static void initializeLanguageDetection() {
        try {
            // Load language profiles for detection
            DetectorFactory.loadProfile("path/to/profiles");
        } catch (LangDetectException e) {
            e.printStackTrace();
        }
    }

    private static void selectFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int option = chooser.showOpenDialog(null);

        if (option == JFileChooser.APPROVE_OPTION) {
            File selectedFolder = chooser.getSelectedFile();
            new Thread(() -> startProcessing(selectedFolder)).start();
        }
    }

    private static void startProcessing(File selectedFolder) {
        selectFolderButton.setEnabled(false);
        cancelButton.setEnabled(true);
        isCancelled = false;

        // Load properties
        Properties properties = loadProperties("config.properties");

        // Suppress specific PDFBox warnings
        suppressPdfBoxWarnings();

        File outputFolder = new File(selectedFolder, "output");
        if (!outputFolder.exists()) {
            outputFolder.mkdir();
        }

        Collection<File> files = FileUtils.listFiles(selectedFolder, new String[]{"pdf", "doc", "docx", "png", "jpg", "jpeg"}, true);
        List<FileConversionRecord> records = new ArrayList<>();

        // Read TESSDATA_PREFIX from properties file
        String tessdataPath = properties.getProperty("tessdata.prefix");
        System.setProperty("TESSDATA_PREFIX", tessdataPath);
        updateTextArea(progressTextArea, "TESSDATA_PREFIX set to: " + System.getProperty("TESSDATA_PREFIX"));

        // Set selected language
        selectedLanguage = englishRadioButton.isSelected() ? "eng" : "tam";

        int totalFiles = files.size();
        int processedFiles = 0;

        for (File file : files) {
            if (isCancelled) {
                updateTextArea(progressTextArea, "Process cancelled by user.");
                break;
            }
            try {
                String conversionType = "";
                StringBuilder extractedText = new StringBuilder();
                File outputFile = new File(outputFolder, file.getName() + ".txt");
                if (file.getName().endsWith(".pdf")) {
                    conversionType = "PDF to Text";
                    extractedText.append(convertPdfToText(file, outputFile, tessdataPath));
                } else if (file.getName().endsWith(".doc")) {
                    conversionType = "DOC to Text";
                    extractedText.append(convertDocToText(file, outputFile));
                } else if (file.getName().endsWith(".docx")) {
                    conversionType = "DOCX to Text";
                    extractedText.append(convertDocxToText(file, outputFile));
                } else if (file.getName().endsWith(".png") || file.getName().endsWith(".jpg") || file.getName().endsWith(".jpeg")) {
                    conversionType = "Image to Text";
                    extractedText.append(convertImageToText(file, outputFile, tessdataPath));
                }

                // Detect language of extracted text
                String language = detectLanguage(extractedText.toString());
                updateTextArea(progressTextArea, "File: " + file.getName() + " - Language: " + language);

                // Display extracted text on the fly
                updateTextArea(extractedTextArea, extractedText.toString());

                processedFiles++;
                int progress = (int) (((double) processedFiles / totalFiles) * 100);
                updateProgressBar(progress);
                updateTextArea(progressTextArea, "Processed " + processedFiles + " of " + totalFiles + " files. " + (totalFiles - processedFiles) + " files left.");
                records.add(new FileConversionRecord(file.getAbsolutePath(), outputFile.getAbsolutePath(), conversionType, "Success"));
            } catch (Exception e) {
                e.printStackTrace();
                records.add(new FileConversionRecord(file.getAbsolutePath(), "", "", "Failed"));
            }
        }

        // Save records to an Excel file
        saveRecordsToExcel(records, outputFolder);

        updateTextArea(progressTextArea, "Processing complete. Records saved to " + new File(outputFolder, "conversion_records.xlsx").getAbsolutePath());

        selectFolderButton.setEnabled(true);
        cancelButton.setEnabled(false);
        restartButton.setEnabled(true);
    }

    private static Properties loadProperties(String propertiesFilePath) {
        Properties properties = new Properties();
        try (InputStream input = new FileInputStream(propertiesFilePath)) {
            properties.load(input);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return properties;
    }

    private static void suppressPdfBoxWarnings() {
        // Suppress specific PDFBox warnings by setting log levels
        Logger.getLogger("org.apache.pdfbox.pdmodel.graphics.color.PDICCBased").setLevel(Level.SEVERE);
        Logger.getLogger("org.apache.pdfbox.pdfparser.COSParser").setLevel(Level.SEVERE);
    }

    private static String convertPdfToText(File inputFile, File outputFile, String tessdataPath) throws IOException {
        PDDocument document = PDDocument.load(inputFile);
        PDFRenderer pdfRenderer = new PDFRenderer(document);
        ITesseract tesseract = new Tesseract();
        tesseract.setDatapath(tessdataPath);
        tesseract.setLanguage(selectedLanguage); // Set selected language

        StringBuilder text = new StringBuilder();
        for (int page = 0; page < document.getNumberOfPages(); ++page) {
            BufferedImage image = pdfRenderer.renderImageWithDPI(page, 300, ImageType.RGB);
            try {
                String result = tesseract.doOCR(image);
                text.append(result).append("\n");
            } catch (TesseractException e) {
                e.printStackTrace();
            }
        }

        document.close();
        Files.write(outputFile.toPath(), text.toString().getBytes());
        return text.toString();
    }

    private static String convertDocToText(File inputFile, File outputFile) throws IOException {
        try (FileInputStream fis = new FileInputStream(inputFile); HWPFDocument document = new HWPFDocument(fis); WordExtractor extractor = new WordExtractor(document)) {
            String text = extractor.getText();
            Files.write(outputFile.toPath(), text.getBytes());
            return text;
        }
    }

    private static String convertDocxToText(File inputFile, File outputFile) throws IOException {
        try (FileInputStream fis = new FileInputStream(inputFile); XWPFDocument document = new XWPFDocument(fis); XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            String text = extractor.getText();
            Files.write(outputFile.toPath(), text.getBytes());
            return text;
        }
    }

    private static String convertImageToText(File inputFile, File outputFile, String tessdataPath) throws IOException {
        ITesseract tesseract = new Tesseract();
        tesseract.setDatapath(tessdataPath);
        tesseract.setLanguage(selectedLanguage); // Set selected language
        BufferedImage image = ImageIO.read(inputFile);
        try {
            String result = tesseract.doOCR(image);
            Files.write(outputFile.toPath(), result.getBytes());
            return result;
        } catch (TesseractException e) {
            e.printStackTrace();
            return "";
        }
    }

    private static void saveRecordsToExcel(List<FileConversionRecord> records, File outputFolder) {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Conversion Records");

        // Create header row
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Source File Path");
        headerRow.createCell(1).setCellValue("Destination File Path");
        headerRow.createCell(2).setCellValue("Conversion Type");
        headerRow.createCell(3).setCellValue("Status");

        // Create data rows
        int rowNum = 1;
        for (FileConversionRecord record : records) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(record.getSourceFilePath());
            row.createCell(1).setCellValue(record.getDestinationFilePath());
            row.createCell(2).setCellValue(record.getConversionType());
            row.createCell(3).setCellValue(record.getStatus());
        }

        // Save the Excel file
        try (FileOutputStream fileOut = new FileOutputStream(new File(outputFolder, "conversion_records.xlsx"))) {
            workbook.write(fileOut);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void updateProgressBar(int value) {
        SwingUtilities.invokeLater(() -> progressBar.setValue(value));
    }

    private static void updateTextArea(JTextArea textArea, String message) {
        SwingUtilities.invokeLater(() -> {
            textArea.append(message + "\n");
            textArea.setCaretPosition(textArea.getDocument().getLength());
        });
    }

    private static void restartApplication() {
        progressBar.setValue(0);
        progressTextArea.setText("");
        extractedTextArea.setText("");
        restartButton.setEnabled(false);
        selectFolderButton.setEnabled(true);
        cancelButton.setEnabled(false);
    }

    private static String detectLanguage(String text) {
        try {
            Detector detector = DetectorFactory.create();
            detector.append(text);
            return detector.detect();
        } catch (LangDetectException e) {
            e.printStackTrace();
            return "Unknown";
        }
    }
}

class FileConversionRecord {

    private String sourceFilePath;
    private String destinationFilePath;
    private String conversionType;
    private String status;

    public FileConversionRecord(String sourceFilePath, String destinationFilePath, String conversionType, String status) {
        this.sourceFilePath = sourceFilePath;
        this.destinationFilePath = destinationFilePath;
        this.conversionType = conversionType;
        this.status = status;
    }

    public String getSourceFilePath() {
        return sourceFilePath;
    }

    public String getDestinationFilePath() {
        return destinationFilePath;
    }

    public String getConversionType() {
        return conversionType;
    }

    public String getStatus() {
        return status;
    }
}
