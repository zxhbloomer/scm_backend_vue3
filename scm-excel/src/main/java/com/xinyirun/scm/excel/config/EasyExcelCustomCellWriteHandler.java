package com.xinyirun.scm.excel.config;

import org.apache.commons.collections.CollectionUtils;
import org.apache.fesod.sheet.enums.CellDataTypeEnum;
import org.apache.fesod.sheet.metadata.Head;
import org.apache.fesod.sheet.metadata.data.CellData;
import org.apache.fesod.sheet.metadata.data.WriteCellData;
import org.apache.fesod.sheet.write.metadata.holder.WriteSheetHolder;
import org.apache.fesod.sheet.write.style.column.AbstractColumnWidthStyleStrategy;
import org.apache.poi.ss.usermodel.Cell;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @describe: EasyExcel 自适应列宽<br>
 * @date: 2021/10/26 12:49 <br>
 * @author: <a href="#">fxsen</a>
 */
public class EasyExcelCustomCellWriteHandler extends AbstractColumnWidthStyleStrategy {
  private Map<Integer, Map<Integer, Integer>> CACHE = new HashMap<>();

  @Override
  protected void setColumnWidth(
      WriteSheetHolder writeSheetHolder,
      List<WriteCellData<?>> cellDataList,
      Cell cell,
      Head head,
      Integer relativeRowIndex,
      Boolean isHead) {
    boolean needSetWidth = isHead || !CollectionUtils.isEmpty(cellDataList);
    if (needSetWidth) {
      Map<Integer, Integer> maxColumnWidthMap = CACHE.get(writeSheetHolder.getSheetNo());
      if (maxColumnWidthMap == null) {
        maxColumnWidthMap = new HashMap<>();
        CACHE.put(writeSheetHolder.getSheetNo(), maxColumnWidthMap);
      }

      Integer columnWidth = this.dataLength(cellDataList, cell, isHead);
      if (columnWidth >= 0) {
        if (columnWidth > 255) {
          columnWidth = 255;
        }
        Integer maxColumnWidth = maxColumnWidthMap.get(cell.getColumnIndex());
        if (maxColumnWidth == null || columnWidth > maxColumnWidth) {
          maxColumnWidthMap.put(cell.getColumnIndex(), columnWidth);
          writeSheetHolder.getSheet().setColumnWidth(cell.getColumnIndex(), columnWidth * 256);
        }
      }
    }
  }

  private Integer dataLength(List<WriteCellData<?>> cellDataList, Cell cell, Boolean isHead) {
    if (isHead) {
      return cell.getStringCellValue().getBytes().length;
    } else {
      CellData cellData = cellDataList.get(0);
      CellDataTypeEnum type = cellData.getType();
      if (type == null) {
        return -1;
      } else {
        switch (type) {
          case STRING:
            return cellData.getStringValue().getBytes().length+5;
          case BOOLEAN:
            return cellData.getBooleanValue().toString().getBytes().length;
          case NUMBER:
            return cellData.getNumberValue().toString().getBytes().length;
          default:
            return -1;
        }
      }
    }
  }
}