package com.erp.reimbursement.service;

import com.erp.reimbursement.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

@Service
public class BillDetectionService {
    private static final Set<String> STRONG_BILL_TERMS = Set.of(
            "invoice", "tax invoice", "receipt", "gstin", "invoice no", "invoice number",
            "receipt no", "receipt number", "grand total", "subtotal", "hsn", "sac",
            "item description", "qty", "quantity", "rate"
    );
    private static final Set<String> SUPPORTING_BILL_TERMS = Set.of(
            "bill", "merchant", "vendor", "seller", "customer", "amount", "total",
            "date", "payment", "cash", "upi"
    );
    private static final Set<String> NON_BILL_TERMS = Set.of(
            "account statement", "bank statement", "statement of account", "transaction details",
            "opening balance", "closing balance", "total debit", "total credit", "debit", "credit",
            "account number", "customer id", "ifsc", "branch", "available balance"
    );

    private final ObjectMapper objectMapper;
    private final RestClient client;
    private final String apiKey;
    private final boolean enabled;

    public BillDetectionService(ObjectMapper objectMapper,
            @Value("${app.bill-ocr.api-url:https://api.ocr.space/parse/image}") String apiUrl,
            @Value("${app.bill-ocr.api-key:}") String apiKey,
            @Value("${app.bill-ocr.enabled:true}") boolean enabled) {
        this.objectMapper = objectMapper;
        this.client = RestClient.builder().baseUrl(apiUrl).build();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.enabled = enabled;
    }

    public void validateBill(MultipartFile file) {
        if (!enabled) return;
        if (file == null || file.isEmpty()) throw new ApiException("Bill document is required");
        if (apiKey.isBlank()) throw new ApiException("Bill verification is not configured. Please configure the Bill OCR API key before uploading a bill.");
        try {
            String normalized = extractText(file).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
            if (normalized.isBlank()) throw new ApiException("We could not read the uploaded document. Please upload a clear bill or invoice.");

            boolean bankStatement = NON_BILL_TERMS.stream().anyMatch(normalized::contains);
            long strong = STRONG_BILL_TERMS.stream().filter(normalized::contains).count();
            long supporting = SUPPORTING_BILL_TERMS.stream().filter(normalized::contains).count();
            boolean hasMoney = normalized.matches("(?s).*\\b(?:rs|inr|amount|total|grand total)\\b.*[0-9]+(?:[.,][0-9]{1,2})?.*");
            boolean hasDate = normalized.matches("(?s).*\\b(?:date|dated)\\b.*");

            boolean validBill = !bankStatement && (
                    strong >= 1 ||
                    strong + supporting >= 3 ||
                    (supporting >= 2 && hasMoney && hasDate)
            );

            if (!validBill) {
                throw new ApiException("The uploaded file could not be identified as a bill, invoice, or receipt. Please upload a clear bill document instead of a bank statement or account statement.");
            }
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException("Bill verification failed. Please upload a clear bill image/PDF and try again.");
        }
    }

    private String extractText(MultipartFile file) throws IOException {
        ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
            @Override public String getFilename() { return file.getOriginalFilename() == null ? "bill" : file.getOriginalFilename(); }
        };
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("apikey", apiKey);
        body.add("language", "eng");
        body.add("isOverlayRequired", "false");
        body.add("OCREngine", "2");
        body.add("file", resource);
        String response = client.post().contentType(MediaType.MULTIPART_FORM_DATA).body(body).retrieve().body(String.class);
        if (response == null || response.isBlank()) return "";
        JsonNode root = objectMapper.readTree(response);
        StringBuilder text = new StringBuilder();
        JsonNode parsed = root.path("ParsedResults");
        if (parsed.isArray()) for (JsonNode item : parsed) if (item.has("ParsedText")) text.append(' ').append(item.path("ParsedText").asText(""));
        return text.toString();
    }
}
