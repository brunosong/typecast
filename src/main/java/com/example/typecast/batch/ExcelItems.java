package com.example.typecast.batch;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * 엑셀(.xlsx, .xls) 업로드 처리. 첫 번째 시트에서 한 행 = 한 항목으로 읽는다.
 * 1행에 "텍스트" 같은 제목 칸이 있으면 그 열을 2행부터 읽고, 없으면 A열을 1행부터 읽는다.
 */
public final class ExcelItems {

    private static final Set<String> TEXT_HEADERS = Set.of("텍스트", "문장", "대사", "내용", "스크립트", "text", "script");

    private ExcelItems() {
    }

    public static boolean isExcel(String filename) {
        if (filename == null) {
            return false;
        }
        String name = filename.toLowerCase(Locale.ROOT);
        return name.endsWith(".xlsx") || name.endsWith(".xls");
    }

    public static List<String> read(byte[] content) {
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            FormulaEvaluator eval = wb.getCreationHelper().createFormulaEvaluator();

            int col = 0;
            int firstRow = sheet.getFirstRowNum();
            Row header = sheet.getRow(firstRow);
            if (header != null) {
                for (Cell cell : header) {
                    String v = fmt.formatCellValue(cell, eval).strip().toLowerCase(Locale.ROOT);
                    if (TEXT_HEADERS.contains(v)) {
                        col = cell.getColumnIndex();
                        firstRow++;
                        break;
                    }
                }
            }

            List<String> items = new ArrayList<>();
            for (int r = firstRow; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                Cell cell = row == null ? null : row.getCell(col);
                items.add(cell == null ? "" : fmt.formatCellValue(cell, eval));
            }
            return items;
        } catch (EncryptedDocumentException e) {
            throw new IllegalArgumentException("암호가 걸린 엑셀 파일은 읽을 수 없습니다. 암호를 해제한 뒤 올려 주세요.");
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("엑셀 파일을 읽을 수 없습니다: " + e.getMessage());
        }
    }

    /** 사용자가 내려받아 채워 넣을 업로드 양식 */
    public static void writeTemplate(OutputStream out, int maxItems) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFFont bold = wb.createFont();
            bold.setBold(true);
            CellStyle headerStyle = wb.createCellStyle();
            headerStyle.setFont(bold);
            CellStyle wrap = wb.createCellStyle();
            wrap.setWrapText(true);

            Sheet sheet = wb.createSheet("대량변환");
            Cell h = sheet.createRow(0).createCell(0);
            h.setCellValue("텍스트");
            h.setCellStyle(headerStyle);
            sheet.setColumnWidth(0, 100 * 256);
            sheet.setDefaultColumnStyle(0, wrap);
            sheet.createFreezePane(0, 1);

            Sheet guide = wb.createSheet("안내");
            String[] lines = {
                    "사용 방법",
                    "1. '대량변환' 시트의 A열(텍스트)에 2행부터 한 칸에 한 문장씩 입력합니다.",
                    "2. 위에서부터 순서대로 0001, 0002, ... 파일로 만들어집니다. 빈 칸은 건너뜁니다.",
                    "3. 한 칸은 최대 2000자, 한 파일은 최대 " + maxItems + "행까지 가능합니다.",
                    "4. 첫 번째 시트만 읽습니다. 이 '안내' 시트는 지워도 됩니다.",
            };
            for (int i = 0; i < lines.length; i++) {
                guide.createRow(i).createCell(0).setCellValue(lines[i]);
            }
            guide.getRow(0).getCell(0).setCellStyle(headerStyle);
            guide.setColumnWidth(0, 90 * 256);

            wb.write(out);
        }
    }
}
