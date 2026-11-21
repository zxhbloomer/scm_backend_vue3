//package com.xinyirun.scm.common.convert;
//
//
//import org.apache.fesod.sheet.converters.Converter;
//import org.apache.fesod.sheet.enums.CellDataTypeEnum;
//import org.apache.fesod.sheet.metadata.CellData;
//import org.apache.fesod.sheet.metadata.GlobalConfiguration;
//import org.apache.fesod.sheet.metadata.property.ExcelContentProperty;
//
//import java.time.LocalDateTime;
//import java.time.format.DateTimeFormatter;
//
//public class LocalDateTimeConverter implements Converter<LocalDateTime> {
//
//    @Override
//    public Class<LocalDateTime> supportJavaTypeKey() {
//        return LocalDateTime.class;
//    }
//
//    @Override
//    public CellDataTypeEnum supportExcelTypeKey() {
//        return CellDataTypeEnum.STRING;
//    }
//
//    @Override
//    public LocalDateTime convertToJavaData(CellData cellData, ExcelContentProperty contentProperty,
//                                           GlobalConfiguration globalConfiguration) {
//        return LocalDateTime.parse(cellData.getStringValue(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
//    }
//
//    @Override
//    public CellData<String> convertToExcelData(LocalDateTime value, ExcelContentProperty contentProperty,
//                                               GlobalConfiguration globalConfiguration) {
//        return new CellData<>(value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
//    }
//}
