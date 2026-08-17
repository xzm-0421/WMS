package com.wms.barcode.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从二维码原始内容解析领料数量，兼容 JSON / 管道符 / 后缀等多种格式。
 */
final class BarcodeQuantityParser {

    /** 后缀数量：MAT*1.25 / MAT#0.5kg */
    private static final Pattern QTY_SUFFIX =
            Pattern.compile("^(.+?)[*#@xX](\\d+(?:\\.\\d+)?)\\s*(?:kg|g|t|吨|千克|公斤|pcs|pc|ea)?$",
                    Pattern.CASE_INSENSITIVE);
    /** 数量段（可带重量单位）：1.25 / 1.25kg */
    private static final Pattern QTY_TOKEN =
            Pattern.compile("^(\\d+(?:\\.\\d+)?)\\s*(?:kg|g|t|吨|千克|公斤|pcs|pc|ea)?$",
                    Pattern.CASE_INSENSITIVE);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private BarcodeQuantityParser() {
    }

    static QuantityExtract extract(String raw, boolean strictQtySegment) {
        if (!StringUtils.hasText(raw)) {
            return QuantityExtract.none();
        }
        String trimmed = raw.trim();

        QuantityExtract json = tryJson(trimmed, strictQtySegment);
        if (json.hasQuantity()) {
            return json;
        }

        if (trimmed.contains("|")) {
            QuantityExtract pipe = tryPipe(trimmed, strictQtySegment);
            if (pipe.hasQuantity()) {
                return pipe;
            }
        }

        QuantityExtract suffix = trySuffix(trimmed, strictQtySegment);
        if (suffix.hasQuantity()) {
            return suffix;
        }

        return QuantityExtract.none();
    }

    private static QuantityExtract tryJson(String raw, boolean strict) {
        if (!raw.startsWith("{") || !raw.endsWith("}")) {
            return QuantityExtract.none();
        }
        try {
            JsonNode node = MAPPER.readTree(raw);
            if (!node.isObject()) {
                return QuantityExtract.none();
            }
            String qtyText = firstText(node, "quantity", "qty", "QTY", "Quantity", "packQty", "packQuantity");
            if (!StringUtils.hasText(qtyText)) {
                return QuantityExtract.none();
            }
            return QuantityExtract.of(parseToken(qtyText, strict), "JSON");
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception e) {
            if (strict) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "二维码 JSON 格式无法解析", "BARCODE_PARSE_FAILED");
            }
            return QuantityExtract.none();
        }
    }

    private static QuantityExtract tryPipe(String raw, boolean strict) {
        String[] parts = raw.split("\\|");
        if (parts.length < 2) {
            return QuantityExtract.none();
        }
        if (parts.length >= 3) {
            String qtyToken = parts[parts.length - 1].trim();
            if (looksLikeQuantity(qtyToken)) {
                return QuantityExtract.of(parseToken(qtyToken, true), "PIPE");
            }
            if (strict) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "二维码数量段格式无效: " + qtyToken, "BARCODE_QTY_INVALID");
            }
        }
        if (parts.length == 2) {
            String second = parts[1].trim();
            if (looksLikeQuantity(second) && !looksLikeBatch(second)) {
                return QuantityExtract.of(parseToken(second, strict), "PIPE");
            }
        }
        return QuantityExtract.none();
    }

    private static QuantityExtract trySuffix(String raw, boolean strict) {
        Matcher matcher = QTY_SUFFIX.matcher(raw);
        if (!matcher.matches()) {
            return QuantityExtract.none();
        }
        return QuantityExtract.of(parseToken(matcher.group(2), strict), "SUFFIX");
    }

    static BigDecimal parseToken(String token, boolean strict) {
        if (!StringUtils.hasText(token)) {
            if (strict) {
                throw qtyInvalid(token);
            }
            return null;
        }
        try {
            String normalized = normalizeQtyToken(token.trim());
            BigDecimal qty = new BigDecimal(normalized);
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                if (strict) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST,
                            "二维码数量必须大于 0", "BARCODE_QTY_INVALID");
                }
                return null;
            }
            // 保留有效小数（kg 等），去掉多余尾零
            return qty.stripTrailingZeros().scale() < 0 ? qty.setScale(0) : qty.stripTrailingZeros();
        } catch (NumberFormatException | ArithmeticException e) {
            if (strict) {
                throw qtyInvalid(token);
            }
            return null;
        }
    }

    /** 去掉 kg/g 等单位后缀，保留数值（含小数） */
    private static String normalizeQtyToken(String token) {
        Matcher matcher = QTY_TOKEN.matcher(token.trim());
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return token.trim();
    }

    private static boolean looksLikeQuantity(String token) {
        if (!StringUtils.hasText(token)) {
            return false;
        }
        return QTY_TOKEN.matcher(token.trim()).matches();
    }

    private static boolean looksLikeBatch(String token) {
        return StringUtils.hasText(token) && token.trim().matches("(?i)^B\\d+$");
    }

    private static String firstText(JsonNode node, String... names) {
        for (String name : names) {
            if (node.has(name) && !node.get(name).isNull()) {
                String v = node.get(name).asText("").trim();
                if (StringUtils.hasText(v)) {
                    return v;
                }
            }
        }
        return "";
    }

    private static BusinessException qtyInvalid(String token) {
        return new BusinessException(ErrorCode.BAD_REQUEST,
                "二维码数量格式无效: " + (token == null ? "" : token),
                "BARCODE_QTY_INVALID");
    }

    record QuantityExtract(BigDecimal quantity, String source) {
        static QuantityExtract none() {
            return new QuantityExtract(null, null);
        }

        static QuantityExtract of(BigDecimal quantity, String source) {
            return new QuantityExtract(quantity, source);
        }

        boolean hasQuantity() {
            return quantity != null && quantity.compareTo(BigDecimal.ZERO) > 0;
        }
    }
}
