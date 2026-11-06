package com.zlth.ivd.server.controller;

import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.inventory.service.InventoryService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inventory")
public class TracePdfController {
    private final InventoryService inventory;

    public TracePdfController(InventoryService inventory) {
        this.inventory = inventory;
    }

    @GetMapping(value = "/trace/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> export(@RequestParam String udiDi, @RequestParam(required = false) String lotNo)
            throws IOException {
        var result = inventory.traceUdi(udiDi, lotNo);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"udi-trace.pdf\"")
                .contentType(MediaType.APPLICATION_PDF).body(render(result));
    }

    private byte[] render(Map<String, Object> result) throws IOException {
        List<?> batches = (List<?>) result.get("batches");
        List<?> timeline = (List<?>) result.get("timeline");
        if (batches.size() + timeline.size() > 1000)
            throw new BizException(40001, "追溯范围过大，请按具体批号缩小范围后再导出");
        try (var document = new PDDocument();
             var input = new ClassPathResource("fonts/NotoSansSC-Regular.ttf").getInputStream();
             var output = new ByteArrayOutputStream()) {
            var font = PDType0Font.load(document, input, true);
            try (var writer = new TraceWriter(document, font)) {
                writer.line("仅展示已保存的业务证据，不包含患者关联信息。");
                writer.line("批次与五码（" + batches.size() + " 条）");
                for (Object item : batches) {
                    var row = (Map<?, ?>) item;
                    writer.line(fields(row, "reagentName", "batchNo", "warehouseName", "stock", "unit", "status"));
                    writer.line("UDI-DI / GTIN / 医保编码 / 注册证 / 院内编码：");
                    writer.line(fields(row, "udiDi", "gtin", "insuranceCode", "registrationCertNo", "hisCode"));
                }
                writer.line("业务流水（" + timeline.size() + " 条）");
                for (Object item : timeline) {
                    var row = (Map<?, ?>) item;
                    writer.line(fields(row, "timestamp", "node", "source", "sourceId", "detail"));
                }
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private static String fields(Map<?, ?> row, String... keys) {
        return java.util.Arrays.stream(keys).map(key -> Objects.toString(row.get(key), "-"))
                .collect(java.util.stream.Collectors.joining(" / "));
    }

    private static final class TraceWriter implements AutoCloseable {
        private final PDDocument document;
        private final PDType0Font font;
        private PDPageContentStream stream;
        private float y;
        private int pageNumber;

        TraceWriter(PDDocument document, PDType0Font font) {
            this.document = document;
            this.font = font;
        }

        void line(String raw) throws IOException {
            var line = new StringBuilder();
            float width = 0;
            for (int cp : raw.codePoints().toArray()) {
                String glyph = Character.isISOControl(cp) ? " " : Character.toString(cp);
                float advance;
                try { advance = font.getStringWidth(glyph) * 0.011f; }
                catch (IllegalArgumentException unsupported) {
                    glyph = "?";
                    advance = font.getStringWidth(glyph) * 0.011f;
                }
                if (width + advance > 510 && !line.isEmpty()) {
                    emit(line.toString());
                    line.setLength(0);
                    width = 0;
                }
                line.append(glyph);
                width += advance;
            }
            if (!line.isEmpty()) emit(line.toString());
        }

        private void emit(String text) throws IOException {
            if (stream == null || y < 44) {
                close();
                var page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                stream = new PDPageContentStream(document, page);
                y = 800;
                write("UDI 追溯报告  /  第 " + (++pageNumber) + " 页", 17);
                y -= 12;
            }
            write(text, 11);
        }

        private void write(String text, int size) throws IOException {
            stream.beginText();
            stream.setFont(font, size);
            stream.newLineAtOffset(42, y);
            stream.showText(text);
            stream.endText();
            y -= size + 6;
        }

        @Override public void close() throws IOException {
            if (stream != null) {
                stream.close();
                stream = null;
            }
        }
    }
}
