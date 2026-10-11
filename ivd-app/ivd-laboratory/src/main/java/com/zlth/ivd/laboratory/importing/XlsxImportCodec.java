package com.zlth.ivd.laboratory.importing;
import com.zlth.ivd.common.exception.BizException;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Component;
@Component
public class XlsxImportCodec {
    public byte[] template(ImportResource resource){
        try(var book=new XSSFWorkbook();var output=new ByteArrayOutputStream()){
            var sheet=book.createSheet(resource.name());var header=sheet.createRow(0);var style=book.createCellStyle();style.setDataFormat(book.createDataFormat().getFormat("@"));
            var heading=book.createCellStyle();heading.setFillForegroundColor(IndexedColors.TEAL.getIndex());heading.setFillPattern(FillPatternType.SOLID_FOREGROUND);var font=book.createFont();font.setColor(IndexedColors.WHITE.getIndex());font.setBold(true);heading.setFont(font);
            for(int i=0;i<resource.columns().size();i++){var cell=header.createCell(i,CellType.STRING);cell.setCellValue(resource.columns().get(i).header());cell.setCellStyle(heading);sheet.setDefaultColumnStyle(i,style);sheet.setColumnWidth(i,22*256);}
            sheet.createFreezePane(0,1);book.write(output);return output.toByteArray();
        }catch(IOException error){throw new BizException(50000,"导入模板生成失败");}
    }
    public ImportDocument read(ImportResource resource,byte[] bytes){
        if(bytes==null || bytes.length==0 || bytes.length>10*1024*1024)throw new BizException(40001,"工作簿必须为非空且不超过10MB的 xlsx 文件");
        inspectZip(bytes);
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(bytes))){
            if(book.getWorkbookType()!=XSSFWorkbookType.XLSX || book.getNumberOfSheets()!=1 || !book.getExternalLinksTable().isEmpty())throw invalid("仅接受单工作表、无宏和外部链接的 xlsx 模板");
            var sheet=book.getSheetAt(0);if(sheet.getNumMergedRegions()!=0 || book.getSheetVisibility(0)!=SheetVisibility.VISIBLE || sheet.getLastRowNum()>5000)throw invalid("工作表存在合并、隐藏或超过5000行");
            var expected=new HashMap<String,ImportResource.Column>();resource.columns().forEach(column->expected.put(column.header(),column));
            var header=sheet.getRow(0);if(header==null || header.getLastCellNum()!=expected.size())throw invalid("模板表头缺失或包含未知列");
            var mapping=new LinkedHashMap<Integer,ImportResource.Column>();var seen=new HashSet<String>();
            for(int i=0;i<header.getLastCellNum();i++){var cell=header.getCell(i);if(cell==null || cell.getCellType()!=CellType.STRING)throw invalid("模板表头必须为真实文本");var title=cell.getStringCellValue().trim();if(!seen.add(title) || !expected.containsKey(title))throw invalid("模板包含重复或未知表头");mapping.put(i,expected.get(title));}
            var rows=new ArrayList<ImportDocument.Row>();var formatter=new DataFormatter(Locale.ROOT);
            for(var row:sheet){
                for(var cell:row){if(cell.getCellType()==CellType.FORMULA)throw invalid("工作簿包含公式，已拒绝导入");if(cell.getCellType()==CellType.ERROR)throw invalid("工作簿包含错误单元格");if(cell.getColumnIndex()>=mapping.size() && !formatter.formatCellValue(cell).isBlank())throw invalid("模板数据包含未知列");}
                if(row.getRowNum()==0)continue;
                var values=new LinkedHashMap<String,String>();boolean nonempty=false;
                for(var entry:mapping.entrySet()){
                    var cell=row.getCell(entry.getKey());var column=entry.getValue();String value="";
                    if(cell!=null && cell.getCellType()!=CellType.BLANK){
                        if(column.type().equals("T") && cell.getCellType()!=CellType.STRING)throw invalid("编码及文本列必须保存为文本，不得使用数值或公式");
                        if(column.type().equals("D") && cell.getCellType()==CellType.NUMERIC && DateUtil.isCellDateFormatted(cell))value=cell.getLocalDateTimeCellValue().toString();else value=formatter.formatCellValue(cell).trim();
                    }
                    if(value.length()>5000)throw invalid("单元格内容超过安全长度限制");values.put(column.key(),value);nonempty|=!value.isBlank();
                }
                if(nonempty)rows.add(new ImportDocument.Row(row.getRowNum()+1,Collections.unmodifiableMap(values)));
            }
            if(rows.isEmpty())throw invalid("工作簿没有可预检的数据行");return new ImportDocument(resource,List.copyOf(rows));
        }catch(BizException error){throw error;}catch(Exception error){throw invalid("工作簿格式无效或超过安全解析限制");}
    }
    private void inspectZip(byte[] bytes){
        long total=0;int entries=0;byte[] buffer=new byte[8192];
        try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes))){
            while(zip.getNextEntry()!=null){if(++entries>1000)throw invalid("工作簿归档条目过多");int count;while((count=zip.read(buffer))!=-1){total+=count;if(total>64L*1024*1024)throw invalid("工作簿解压大小超过安全限制");}}
            if(entries==0)throw invalid("仅接受 xlsx ZIP 工作簿");
        }catch(BizException error){throw error;}catch(IOException error){throw invalid("工作簿归档无效");}
    }
    private BizException invalid(String message){return new BizException(40001,message);}
}
