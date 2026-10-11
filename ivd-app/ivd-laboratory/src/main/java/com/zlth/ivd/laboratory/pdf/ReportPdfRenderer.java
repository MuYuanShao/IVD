package com.zlth.ivd.laboratory.pdf;

import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.laboratory.service.ReportSnapshot;
import com.zlth.ivd.laboratory.vo.IntervalVO;
import java.awt.Color;
import java.io.*;
import java.util.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Component;

/** Embedded OFL font, bounded wrapping, repeated headers and explicit non-CA business signing. */
@Component
public class ReportPdfRenderer {
    private final java.util.concurrent.Semaphore slots=new java.util.concurrent.Semaphore(4);
    public byte[] render(ReportSnapshot snapshot,String status){
        if(!slots.tryAcquire())throw new BizException(40900,"报告生成繁忙，请稍后重试；未改变签发状态");
        try(var document=new PDDocument();var input=getClass().getResourceAsStream("/fonts/NotoSansSC-Regular.ttf");var output=new ByteArrayOutputStream()){
            if(input==null)throw new BizException(50000,"报告中文字体未随应用加载，拒绝生成报告");
            var font=PDType0Font.load(document,input,true);
            try(var writer=new Writer(document,font,snapshot,status)){
                writer.paragraph("患者："+snapshot.patient().name()+"    编号："+snapshot.patient().number()+"    性别："+Map.of("MALE","男","FEMALE","女","UNKNOWN","未知").getOrDefault(snapshot.patient().sex(),"未知"),11);
                writer.paragraph("出生日期："+Objects.toString(snapshot.patient().birthDate(),"未记录")+"    样本 ID："+snapshot.sampleId(),11);
                writer.paragraph("本报告仅呈现检验结果及人工复核提示，不自动生成诊断。",10);
                writer.tableHeader();
                for(var line:snapshot.lines()){
                    var r=line.context().result();
                    var details=new ArrayList<String>();details.add("设备："+Objects.toString(r.deviceName(),"无本地设备")+"；检测机构："+r.testingInstitutionName()+"；检测时间："+time(r.testedAt()));
                    details.add("方法学："+line.context().item().methodology()+"；参考区间有效期："+(r.referenceInterval()==null?"未匹配":r.referenceInterval().validFrom()+" - "+r.referenceInterval().validTo()));
                    details.add("参考提示："+r.referenceReason()+"；测量提示："+r.measurementStatus()+" / "+r.measurementReason());
                    if(line.evidence().isEmpty())details.add("批次 / 消耗证据：未关联，需人工确认。");
                    for(var e:line.evidence())details.add("批次："+e.batch().batchNo()+" [ID "+e.batch().batchId()+"]；上机 ID："+Objects.toString(e.batch().deviceLoadId(),"无")+"；消耗 ID："+(e.consumption()==null?"未关联":e.consumption().id()));
                    writer.block(List.of(r.itemCode(),r.itemName(),r.result(),r.unit(),r.referenceStatus()+" / "+reference(r.referenceInterval())),details);
                }
                writer.paragraph("审核说明："+Objects.toString(snapshot.workflowNote(),"无"),10);
                for(var stamp:snapshot.stamps())writer.paragraph(stamp.action()+"："+stamp.username()+" [用户 ID "+stamp.actorId()+"]  "+time(stamp.at()),10);
            }
            for(int i=0;i<document.getNumberOfPages();i++){
                try(var footer=new PDPageContentStream(document,document.getPage(i),PDPageContentStream.AppendMode.APPEND,true,true)){
                    footer.beginText();footer.setFont(font,9);footer.setNonStrokingColor(new Color(90,90,90));footer.newLineAtOffset(40,26);
                    footer.showText("第 "+(i+1)+" / "+document.getNumberOfPages()+" 页    "+("SIGNED".equals(status)?"业务签发 - 未使用 CA 数字签名":"草稿预览 - 未签发，不得作为正式报告"));footer.endText();
                }
            }
            document.save(output);return output.toByteArray();
        }catch(BizException error){throw error;}catch(Exception error){throw new BizException(50000,"报告字体、版式或 PDF 生成失败，报告未签发");}finally{slots.release();}
    }
    private static String reference(IntervalVO value){return value==null?"待人工处理":("NUMERIC".equals(value.resultType())?value.lowerBound().stripTrailingZeros().toPlainString()+" - "+value.upperBound().stripTrailingZeros().toPlainString()+" "+value.unit():value.normalText());}
    private static String time(java.time.LocalDateTime value){return value.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));}
    private static final class Writer implements AutoCloseable {
        private static final float LEFT=40,WIDTH=515,BOTTOM=58;
        private static final float[] COLUMNS={80,135,60,65,175};
        private final PDDocument document;
        private final PDType0Font font;
        private final ReportSnapshot snapshot;
        private final String status;
        private PDPageContentStream stream;
        private float y;
        private boolean table;
        Writer(PDDocument document,PDType0Font font,ReportSnapshot snapshot,String status)throws IOException{this.document=document;this.font=font;this.snapshot=snapshot;this.status=status;newPage();}
        private void newPage()throws IOException{
            if(stream!=null)stream.close();var page=new PDPage(PDRectangle.A4);document.addPage(page);stream=new PDPageContentStream(document,page);y=790;
            text("IVD 检验报告",LEFT,y,18,new Color(0,121,107));y-=26;
            text("SIGNED".equals(status)?"业务签发 / 未使用 CA 数字签名":"草稿预览 / 未签发",LEFT,y,11,new Color(150,80,0));y-=26;
            for(var line:wrap("报告编号："+snapshot.number()+"    版本："+snapshot.edition(),WIDTH,9)){text(line,LEFT,y,9,Color.DARK_GRAY);y-=14;}y-=8;
            if(table)tableHeader();
        }
        void paragraph(String value,float size)throws IOException{
            var lines=wrap(value,WIDTH,size);float height=lines.size()*16+5;
            if(height<620 && y-height<BOTTOM)newPage();
            for(var line:lines){if(y-16<BOTTOM)newPage();text(line,LEFT,y,size,Color.DARK_GRAY);y-=16;}y-=5;
        }
        void block(List<String> cells,List<String> details)throws IOException{
            int rows=1;for(int i=0;i<cells.size();i++)rows=Math.max(rows,wrap(cells.get(i),COLUMNS[i]-8,10).size());float height=rows*14+17;
            for(var detail:details)height+=wrap(detail,WIDTH,9).size()*16+5;
            if(height<620 && y-height<BOTTOM)newPage();row(cells);for(var detail:details)paragraph(detail,9);
        }
        void tableHeader()throws IOException{
            table=true;if(y-28<BOTTOM)newPage();
            stream.setNonStrokingColor(new Color(231,245,242));stream.addRect(LEFT,y-22,WIDTH,26);stream.fill();
            float x=LEFT;var headings=List.of("项目编码","检验项目","结果","单位","参考区间 / 提示");for(int i=0;i<headings.size();i++){text(headings.get(i),x+4,y-12,10,new Color(0,90,80));x+=COLUMNS[i];}y-=30;
        }
        void row(List<String> cells)throws IOException{
            var lines=new ArrayList<List<String>>();int count=1;
            for(int i=0;i<cells.size();i++){var wrapped=wrap(cells.get(i),COLUMNS[i]-8,10);lines.add(wrapped);count=Math.max(count,wrapped.size());}
            float height=count*14+10;
            if(height>650)throw new BizException(40001,"报告单行内容过长，无法安全排版");
            if(y-height<BOTTOM)newPage();float x=LEFT;
            for(int i=0;i<cells.size();i++){stream.setStrokingColor(new Color(220,229,228));stream.addRect(x,y-height,COLUMNS[i],height);stream.stroke();float lineY=y-13;for(var value:lines.get(i)){text(value,x+4,lineY,10,Color.DARK_GRAY);lineY-=14;}x+=COLUMNS[i];}y-=height+7;
        }
        private List<String> wrap(String value,float width,float size)throws IOException{
            String safe=Objects.toString(value,"").replaceAll("[\\p{Cntrl}]"," ");var output=new ArrayList<String>();var current=new StringBuilder();
            for(int cp:safe.codePoints().toArray()){
                var character=new String(Character.toChars(cp));
                // PDType0Font.hasGlyph takes encoded character codes (CID), not Unicode.
                try{font.encode(character);}catch(IllegalArgumentException missing){throw new BizException(40001,"报告包含当前嵌入字体不支持的字符，需核对后签发");}
                if(current.length()>0 && font.getStringWidth(current+character)/1000*size>width){output.add(current.toString());current.setLength(0);}current.append(character);
            }
            if(current.length()>0 || output.isEmpty())output.add(current.toString());return output;
        }
        private void text(String value,float x,float baseline,float size,Color color)throws IOException{stream.beginText();stream.setFont(font,size);stream.setNonStrokingColor(color);stream.newLineAtOffset(x,baseline);stream.showText(value);stream.endText();}
        public void close()throws IOException{if(stream!=null)stream.close();}
    }
}
