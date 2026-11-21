package com.xinyirun.scm.common.convert.excel;


import org.apache.fesod.sheet.converters.Converter;
import org.apache.fesod.sheet.metadata.GlobalConfiguration;
import org.apache.fesod.sheet.metadata.data.WriteCellData;
import org.apache.fesod.sheet.metadata.property.ExcelContentProperty;

/**
 * @author Wang Qianfeng
 * @Description 验车状态转换
 * @date 2023/2/20 14:08
 */
public class ValidateVehicleConvertor implements Converter<String> {

    //导出数据到excel
    @Override
    public WriteCellData<?> convertToExcelData(String value, ExcelContentProperty contentProperty, GlobalConfiguration globalConfiguration) throws Exception {
        return new WriteCellData<>("1".equals(value) ? "验车通过" : "验车失败");
    }

}
