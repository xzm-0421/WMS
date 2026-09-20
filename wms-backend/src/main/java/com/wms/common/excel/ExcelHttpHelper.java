package com.wms.common.excel;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Excel 文件 HTTP 下载响应头辅助。
 */
public final class ExcelHttpHelper {

    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private ExcelHttpHelper() {
    }

    /**
     * 写出 xlsx 附件流（UTF-8 文件名，兼容主流浏览器）。
     */
    public static void writeXlsx(HttpServletResponse response, String fileName,
                                 Consumer<OutputStream> writer) throws IOException {
        if (response == null || writer == null) {
            throw new IllegalArgumentException("response/writer 不能为空");
        }
        String safeName = (fileName == null || fileName.isBlank()) ? "export.xlsx" : fileName.trim();
        if (!safeName.toLowerCase().endsWith(".xlsx")) {
            safeName = safeName + ".xlsx";
        }
        String encoded = URLEncoder.encode(safeName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(XLSX_CONTENT_TYPE);
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded);
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        OutputStream out = response.getOutputStream();
        writer.accept(out);
        out.flush();
        response.flushBuffer();
    }
}
