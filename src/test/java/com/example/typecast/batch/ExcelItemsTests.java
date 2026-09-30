package com.example.typecast.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ExcelItemsTests {

    @Test
    void readsHeaderColumnFromSecondRow() throws IOException {
        byte[] xlsx = workbook(sheet -> {
            Row h = sheet.createRow(0);
            h.createCell(0).setCellValue("번호");
            h.createCell(1).setCellValue("텍스트");
            sheet.createRow(1).createCell(1).setCellValue("첫 문장");
            sheet.createRow(3).createCell(1).setCellValue("셋째 행");
            sheet.createRow(4).createCell(1).setCellValue(2026);
        });
        assertThat(ExcelItems.read(xlsx)).containsExactly("첫 문장", "", "셋째 행", "2026");
    }

    @Test
    void readsColumnAFromFirstRowWithoutHeader() throws IOException {
        byte[] xlsx = workbook(sheet -> {
            sheet.createRow(0).createCell(0).setCellValue("안녕하세요.");
            sheet.createRow(1).createCell(0).setCellValue("반갑습니다.");
        });
        assertThat(ExcelItems.read(xlsx)).containsExactly("안녕하세요.", "반갑습니다.");
    }

    @Test
    void emptyTemplateHasNoItems() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ExcelItems.writeTemplate(out, 1000);
        assertThat(ExcelItems.read(out.toByteArray())).isEmpty();
    }

    @Test
    void rejectsNonExcel() {
        assertThatThrownBy(() -> ExcelItems.read("not excel".getBytes()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void detectsExcelByExtension() {
        assertThat(ExcelItems.isExcel("대본.XLSX")).isTrue();
        assertThat(ExcelItems.isExcel("old.xls")).isTrue();
        assertThat(ExcelItems.isExcel("lines.txt")).isFalse();
        assertThat(ExcelItems.isExcel(null)).isFalse();
    }

    private static byte[] workbook(java.util.function.Consumer<Sheet> fill) throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            fill.accept(wb.createSheet());
            wb.write(out);
            return out.toByteArray();
        }
    }
}
