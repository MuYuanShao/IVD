package com.zlth.ivd.laboratory.importing;
import java.util.*;
public record ImportDocument(ImportResource resource,List<Row> rows) {
    public record Row(int row,Map<String,String> values) { }
}
