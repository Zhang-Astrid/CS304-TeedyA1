package com.sismics.docs.core.service;

import com.google.common.base.Strings;
import com.sismics.docs.core.constant.Constants;
import com.sismics.docs.core.dao.FileDao;
import com.sismics.docs.core.model.jpa.File;
import com.sismics.docs.core.util.DirectoryUtil;
import com.sismics.docs.core.util.EncryptionUtil;
import com.sismics.docs.core.util.FileUtil;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Translation service.
 */
public class TranslationService {
    private static final Logger log = LoggerFactory.getLogger(TranslationService.class);
    private static final String BAIDU_APP_ID = "20250521002362139";
    private static final String BAIDU_SECRET_KEY = "QeS7E6KwPzuyeu2V5LgM";
    private static final String BAIDU_TRANSLATE_URL = "https://fanyi-api.baidu.com/api/trans/vip/translate";

    /**
     * Translate a file.
     *
     * @param fileId File ID
     * @param targetLanguage Target language
     * @param userId User ID
     * @return Translated File object
     * @throws Exception if translation fails or file not found/invalid
     */
    public File translateFile(String fileId, String targetLanguage, String userId) throws Exception {
        // Get the file
        FileDao fileDao = new FileDao();
        File file = fileDao.getFile(fileId);
        if (file == null) {
            throw new IllegalArgumentException("File not found");
        }

        // Check if the file is a PDF
        if (!file.getMimeType().equals("application/pdf")) {
            throw new IllegalArgumentException("Only PDF files can be translated");
        }

        // Get the file content
        Path storedFile = DirectoryUtil.getStorageDirectory().resolve(fileId);
        Path unencryptedFile = EncryptionUtil.decryptFile(storedFile, userId);

        // Extract text from PDF
        String text;
        try (PDDocument document = PDDocument.load(unencryptedFile.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            text = stripper.getText(document);
        }

        // 调用百度翻译API
        String translatedText = translateWithBaidu(text, targetLanguage);

        // Create a new file for the translation
        String translatedFileId = UUID.randomUUID().toString();
        Path translatedFile = DirectoryUtil.getStorageDirectory().resolve(translatedFileId);
        Files.write(translatedFile, translatedText.getBytes());

        // Create the file record
        File translatedFileRecord = new File();
        translatedFileRecord.setId(translatedFileId);
        translatedFileRecord.setName(file.getName() + " (" + targetLanguage + ")");
        translatedFileRecord.setMimeType(file.getMimeType());
        translatedFileRecord.setSize(Files.size(translatedFile));
        translatedFileRecord.setDocumentId(file.getDocumentId());
        translatedFileRecord.setUserId(userId);
        fileDao.create(translatedFileRecord, userId);

        return translatedFileRecord;
    }

    private String translateWithBaidu(String text, String targetLanguage) throws Exception {
        String salt = String.valueOf(System.currentTimeMillis());
        String sign = generateBaiduSign(text, salt);
        StringBuilder requestParams = new StringBuilder();
        requestParams.append("q=").append(URLEncoder.encode(text, StandardCharsets.UTF_8));
        requestParams.append("&from=auto");
        requestParams.append("&to=").append(getBaiduLanguageCode(targetLanguage));
        requestParams.append("&appid=").append(BAIDU_APP_ID);
        requestParams.append("&salt=").append(salt);
        requestParams.append("&sign=").append(sign);

        URL url = new URL(BAIDU_TRANSLATE_URL + "?" + requestParams.toString());
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        int responseCode = conn.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("Baidu Translate API error: " + responseCode);
        }

        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            JSONObject jsonResponse = new JSONObject(response.toString());
            if (jsonResponse.has("error_code")) {
                String errorCode = jsonResponse.getString("error_code");
                String errorMsg = jsonResponse.getString("error_msg");
                throw new IOException("Baidu Translate API error: " + errorCode + " - " + errorMsg);
            }
            JSONArray transResult = jsonResponse.getJSONArray("trans_result");
            if (transResult.length() > 0) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < transResult.length(); i++) {
                    sb.append(transResult.getJSONObject(i).getString("dst"));
                    if (i < transResult.length() - 1) {
                        sb.append("\n");
                    }
                }
                return sb.toString();
            }
            throw new IOException("No translation result found");
        }
    }

    private String generateBaiduSign(String text, String salt) throws Exception {
        String str = BAIDU_APP_ID + text + salt + BAIDU_SECRET_KEY;
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] bytes = md.digest(str.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String getBaiduLanguageCode(String language) {
        if (language == null) return "en";
        switch (language.toLowerCase()) {
            case "zh": return "zh";
            case "en": return "en";
            case "ja": return "jp";
            case "ko": return "kor";
            case "fr": return "fra";
            case "de": return "de";
            case "es": return "spa";
            case "ru": return "ru";
            case "eng": return "en";
            case "chi_sim": return "zh";
            case "chi_tra": return "cht";
            case "jpn": return "jp";
            case "kor": return "kor";
            case "fra": return "fra";
            case "deu": return "de";
            case "rus": return "ru";
            case "spa": return "spa";
            case "ita": return "it";
            case "por": return "pt";
            case "vie": return "vie";
            case "tur": return "tr";
            case "tha": return "th";
            case "ara": return "ara";
            default: return "en";
        }
    }
}