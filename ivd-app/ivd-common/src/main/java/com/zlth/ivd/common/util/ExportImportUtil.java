package com.zlth.ivd.common.util;

import cn.hutool.core.io.IoUtil;
import cn.hutool.poi.excel.ExcelReader;
import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;
import cn.hutool.poi.excel.StyleSet;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Excel / CSV 导入导出工具类
 * <p>
 * 基于 Hutool + Apache POI，统一处理制造商、配送商、证照等实体的导入导出。
 *
 * @author IVD Team
 * @since 2026-07-09
 */
@Slf4j
public final class ExportImportUtil {

    private ExportImportUtil() {}

    // ──────────────────────── 导出 ────────────────────────

    /**
     * 导出为 Excel (.xlsx)
     *
     * @param response   HttpServletResponse（流式写出）
     * @param fileName  下载文件名（不含扩展名）
     * @param headers   表头列表
     * @param rows      数据行（每行为 Map，key 对应 headers）
     */
    public static void exportExcel(HttpServletResponse response, String fileName,
                                   List<String> headers, List<Map<String, Object>> rows) {
        try (ExcelWriter writer = ExcelUtil.getWriter(true)) {
            // 表头别名映射（header -> header）
            for (int i = 0; i < headers.size(); i++) {
                writer.addHeaderAlias(aliasKey(i), headers.get(i));
            }

            // 写入数据
            List<Map<String, Object>> data = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Map<String, Object> m = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    String key = headers.get(i);
                    m.put(aliasKey(i), row.getOrDefault(key, ""));
                }
                data.add(m);
            }

            writer.write(data, true);

            // 设置响应头
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Content-Disposition",
                    "attachment; filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8) + ".xlsx");

            writer.flush(response.getOutputStream(), true);
        } catch (IOException e) {
            log.error("导出 Excel 失败: {}", fileName, e);
            throw new RuntimeException("导出 Excel 失败", e);
        }
    }

    /**
     * 导出为 CSV
     */
    public static void exportCsv(HttpServletResponse response, String fileName,
                                 List<String> headers, List<Map<String, Object>> rows) {
        response.setContentType("text/csv; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        // BOM for Excel UTF-8 recognition
        response.setHeader("Content-Disposition",
                "attachment; filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8) + ".csv");

        try (OutputStream os = response.getOutputStream();
             BufferedOutputStream bos = new BufferedOutputStream(os)) {
            // UTF-8 BOM
            bos.write(0xEF);
            bos.write(0xBB);
            bos.write(0xBF);

            // 写表头
            bos.write(String.join(",", escapeCsv(headers)).getBytes(StandardCharsets.UTF_8));
            bos.write('\n');

            // 写数据行
            for (Map<String, Object> row : rows) {
                List<String> values = new ArrayList<>();
                for (String h : headers) {
                    Object v = row.getOrDefault(h, "");
                    values.add(v != null ? v.toString() : "");
                }
                bos.write(String.join(",", escapeCsv(values)).getBytes(StandardCharsets.UTF_8));
                bos.write('\n');
            }
            bos.flush();
        } catch (IOException e) {
            log.error("导出 CSV 失败: {}", fileName, e);
            throw new RuntimeException("导出 CSV 失败", e);
        }
    }

    // ──────────────────────── 导入 ────────────────────────

    /**
     * 从上传文件中读取数据行（支持 .xlsx / .xls / .csv）
     *
     * @param file      MultipartFile
     * @param headers   表头列表（用于识别列）
     * @return 每行为 header -> value 的 Map
     */
    public static List<Map<String, Object>> importFromFile(MultipartFile file, List<String> headers) {
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        String ext = filename.toLowerCase();
        if (ext.endsWith(".csv")) {
            return importCsv(file, headers);
        } else if (ext.endsWith(".xlsx") || ext.endsWith(".xls")) {
            return importExcel(file, headers);
        } else {
            throw new IllegalArgumentException("不支持的文件格式: " + filename + "（仅支持 .xlsx / .xls / .csv）");
        }
    }

    private static List<Map<String, Object>> importExcel(MultipartFile file, List<String> headers) {
        try (InputStream is = file.getInputStream();
             ExcelReader reader = ExcelUtil.getReader(is)) {
            List<Map<String, Object>> result = new ArrayList<>();
            List<List<Object>> allRows = reader.read();
            if (allRows.isEmpty()) return result;

            // 第一行是表头，跳过
            for (int i = 1; i < allRows.size(); i++) {
                List<Object> rowData = allRows.get(i);
                Map<String, Object> row = new LinkedHashMap<>();
                for (int j = 0; j < headers.size() && j < rowData.size(); j++) {
                    row.put(headers.get(j), rowData.get(j));
                }
                // 跳过全空行
                if (row.values().stream().allMatch(v -> v == null || v.toString().isBlank())) {
                    continue;
                }
                result.add(row);
            }
            return result;
        } catch (IOException e) {
            log.error("导入 Excel 失败", e);
            throw new RuntimeException("导入 Excel 失败: " + e.getMessage(), e);
        }
    }

    private static List<Map<String, Object>> importCsv(MultipartFile file, List<String> headers) {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            List<Map<String, Object>> result = new ArrayList<>();
            String line;
            boolean isFirst = true;
            while ((line = br.readLine()) != null) {
                // 跳过 BOM
                if (isFirst && !line.isEmpty() && line.charAt(0) == '﻿') {
                    line = line.substring(1);
                }
                if (isFirst) { isFirst = false; continue; } // skip header
                if (line.isBlank()) continue;

                String[] parts = parseCsvLine(line);
                Map<String, Object> row = new LinkedHashMap<>();
                for (int j = 0; j < headers.size() && j < parts.length; j++) {
                    String val = parts[j].trim();
                    if (val.startsWith("\"") && val.endsWith("\"")) {
                        val = val.substring(1, val.length() - 1);
                    }
                    row.put(headers.get(j), val);
                }
                if (row.values().stream().allMatch(v -> v == null || v.toString().isBlank())) {
                    continue;
                }
                result.add(row);
            }
            return result;
        } catch (IOException e) {
            log.error("导入 CSV 失败", e);
            throw new RuntimeException("导入 CSV 失败: " + e.getMessage(), e);
        }
    }

    // ──────────────────────── 内部工具 ────────────────────────

    /** 用于 Excel 别名映射的 key */
    static String aliasKey(int index) {
        return "col_" + index;
    }

    /** CSV 字段转义（包含逗号/引号/换行时加引号） */
    static List<String> escapeCsv(List<String> values) {
        List<String> result = new ArrayList<>();
        for (String v : values) {
            String s = v != null ? v : "";
            if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
                s = "\"" + s.replace("\"", "\"\"") + "\"";
            }
            result.add(s);
        }
        return result;
    }

    /** CSV 行解析（简单实现，处理引号包裹的字段） */
    static String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        sb.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    sb.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    fields.add(sb.toString());
                    sb.setLength(0);
                } else {
                    sb.append(c);
                }
            }
        }
        fields.add(sb.toString());
        return fields.toArray(new String[0]);
    }
}
