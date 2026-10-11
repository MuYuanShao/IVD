package com.zlth.ivd.laboratory.importing;
import java.util.*;
/** Stable real headers; aliases and positional guessing are deliberately not accepted. */
public enum ImportResource {
    PATIENTS(true,"number|患者编号|T,name|姓名|T,sex|性别|T,birthDate|出生日期|D"),
    SAMPLES(true,"patientNumber|患者编号|T,number|样本编号|T,specimenType|样本类型|T,collectedAt|采集时间|D"),
    RESULTS(true,"sampleNumber|样本编号|T,itemCode|项目编码|T,executionMode|检测方式|T,deviceCode|设备编码|T,testingInstitutionCode|检测机构编码|T,result|结果|V,unit|单位|T,testedAt|检测时间|D"),
    REPORTS(true,"reportNumber|报告编号|T,sampleNumber|样本编号|T,itemCode|项目编码|T,executionMode|检测方式|T,deviceCode|设备编码|T,testingInstitutionCode|检测机构编码|T,result|结果|V,unit|单位|T,testedAt|检测时间|D"),
    ITEMS(false,"code|项目编码|T,name|项目名称|T,methodology|方法学|T,resultType|结果类型|T,unit|单位|T,active|启用|V,deviceRequired|必须使用仪器|V"),
    INTERVALS(false,"itemCode|项目编码|T,deviceCode|设备编码|T,kind|区间类型|T,sex|适用性别|T,minAgeDays|最小年龄天数|V,maxAgeDays|最大年龄天数|V,lowerBound|下限|V,upperBound|上限|V,normalText|定性参考结果|T,validFrom|有效开始日期|D,validTo|有效结束日期|D,active|启用|V"),
    DEVICES(false,"code|设备编码|T,name|设备名称|T,model|型号|T,manufacturer|厂家|T,locationId|库房ID|T,departmentId|科室ID|T,active|启用|V,lastCalibrationDate|上次校准日期|D,nextCalibrationDate|下次校准日期|D"),
    CAPABILITIES(false,"deviceCode|设备编码|T,itemCode|项目编码|T,active|启用|V"),
    INSTITUTIONS(false,"code|机构编码|T,name|机构名称|T,type|机构类型|T,contactPerson|联系人|T,contactPhone|联系电话|T,address|地址|T,capabilities|项目能力说明|T,status|启用|V");
    public record Column(String key,String header,String type) { }
    private final boolean clinical;
    private final List<Column> columns;
    ImportResource(boolean clinical,String specification){this.clinical=clinical;this.columns=Arrays.stream(specification.split(",")).map(value->{var parts=value.split("\\|");return new Column(parts[0],parts[1],parts[2]);}).toList();}
    public boolean clinical(){return clinical;}
    public List<Column> columns(){return columns;}
}
