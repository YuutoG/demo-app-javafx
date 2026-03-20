package org.ascencio.demoapp;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import com.lowagie.text.Element;
import com.lowagie.text.PageSize;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import java.awt.Color;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class ReportesController {

    private static final String DB_URL = "jdbc:sqlite:mi_contabilidad.db";
    private final int EMPRESA_ACTUAL_ID = 1;

    // ==========================================
    // EXPORTACIÓN DE CATÁLOGO
    // ==========================================

    @FXML
    private void exportarCatalogoCSV(ActionEvent event) {
        File file = mostrarFileChooser(event, "Catálogo_Cuentas.csv", "Archivo CSV", "*.csv");
        if (file == null) return;

        String sql = "SELECT codigo, nombre, tipo FROM catalogo_cuentas WHERE empresa_id = ? ORDER BY codigo";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql);
             FileWriter out = new FileWriter(file);
             CSVPrinter printer = new CSVPrinter(out, CSVFormat.EXCEL.withHeader("Código", "Nombre de Cuenta", "Clasificación"))) {

            pstmt.setInt(1, EMPRESA_ACTUAL_ID);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                printer.printRecord(rs.getString("codigo"), rs.getString("nombre"), rs.getString("tipo"));
            }
            mostrarAlertaExito("CSV exportado exitosamente en:\n" + file.getAbsolutePath());

        } catch (Exception e) {
            mostrarAlertaError("Error al exportar CSV: " + e.getMessage());
        }
    }

    @FXML
    private void exportarCatalogoPDF(ActionEvent event) {
        File file = mostrarFileChooser(event, "Catálogo_Cuentas.pdf", "Documento PDF", "*.pdf");
        if (file == null) return;

        String sql = "SELECT codigo, nombre, tipo FROM catalogo_cuentas WHERE empresa_id = ? ORDER BY codigo";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, EMPRESA_ACTUAL_ID);
            ResultSet rs = pstmt.executeQuery();

            Document document = new Document();
            PdfWriter.getInstance(document, new FileOutputStream(file));
            document.open();

            // Título
            Font tituloFont = new Font(Font.HELVETICA, 18, Font.BOLD);
            document.add(new Paragraph("Reporte de Catálogo de Cuentas", tituloFont));
            document.add(new Paragraph(" ")); // Espaciador

            // Tabla PDF de 3 columnas
            PdfPTable table = new PdfPTable(3);
            table.setWidthPercentage(100);
            table.addCell("Código");
            table.addCell("Nombre de Cuenta");
            table.addCell("Clasificación");

            while (rs.next()) {
                table.addCell(rs.getString("codigo"));
                table.addCell(rs.getString("nombre"));
                table.addCell(rs.getString("tipo"));
            }

            document.add(table);
            document.close();

            mostrarAlertaExito("PDF exportado exitosamente en:\n" + file.getAbsolutePath());

        } catch (Exception e) {
            mostrarAlertaError("Error al exportar PDF: " + e.getMessage());
        }
    }

    // ==========================================
    // EXPORTACIÓN DE MOVIMIENTOS (LIBRO DIARIO)
    // ==========================================

    @FXML
    private void exportarMovimientosCSV(ActionEvent event) {
        File file = mostrarFileChooser(event, "Libro_Diario.csv", "Archivo CSV", "*.csv");
        if (file == null) return;

        // Un JOIN clásico de bases de datos para traer el nombre de la cuenta junto con el movimiento
        String sql = """
            SELECT m.num_partida, m.fecha, c.codigo, c.nombre, m.concepto, m.debe, m.haber 
            FROM movimientos m 
            JOIN catalogo_cuentas c ON m.cuenta_id = c.id 
            WHERE m.empresa_id = ? 
            ORDER BY m.num_partida, m.id
        """;

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql);
             FileWriter out = new FileWriter(file);
             CSVPrinter printer = new CSVPrinter(out, CSVFormat.EXCEL.withHeader("Partida", "Fecha", "Código", "Cuenta", "Concepto", "Debe", "Haber"))) {

            pstmt.setInt(1, EMPRESA_ACTUAL_ID);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                printer.printRecord(
                        rs.getInt("num_partida"),
                        rs.getString("fecha"),
                        rs.getString("codigo"),
                        rs.getString("nombre"),
                        rs.getString("concepto"),
                        rs.getDouble("debe"),
                        rs.getDouble("haber")
                );
            }
            mostrarAlertaExito("Libro Diario CSV exportado exitosamente.");

        } catch (Exception e) {
            mostrarAlertaError("Error al exportar CSV: " + e.getMessage());
        }
    }

    @FXML
    private void exportarMovimientosPDF(ActionEvent event) {
        File file = mostrarFileChooser(event, "Libro_Diario.pdf", "Documento PDF", "*.pdf");
        if (file == null) return;

        // El mismo JOIN que usamos para el CSV
        String sql = """
            SELECT m.num_partida, m.fecha, c.codigo, c.nombre, m.concepto, m.debe, m.haber 
            FROM movimientos m 
            JOIN catalogo_cuentas c ON m.cuenta_id = c.id 
            WHERE m.empresa_id = ? 
            ORDER BY m.num_partida, m.id
        """;

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, EMPRESA_ACTUAL_ID);
            ResultSet rs = pstmt.executeQuery();

            // 1. Crear documento en formato Horizontal (Landscape)
            Document document = new Document(PageSize.A4.rotate());
            PdfWriter.getInstance(document, new FileOutputStream(file));
            document.open();

            // 2. Título del Reporte
            Font tituloFont = new Font(Font.HELVETICA, 18, Font.BOLD);
            document.add(new Paragraph("Libro Diario (Movimientos Contables)", tituloFont));
            document.add(new Paragraph(" ")); // Espaciador

            // 3. Crear tabla de 7 columnas
            PdfPTable table = new PdfPTable(7);
            table.setWidthPercentage(100);

            // Asignar proporciones a las columnas (Ej: Concepto es 4 veces más ancho que Partida)
            table.setWidths(new float[]{1.2f, 2f, 2f, 3.5f, 4f, 2f, 2f});

            // 4. Configurar y pintar los Encabezados
            String[] headers = {"Partida", "Fecha", "Código", "Cuenta", "Concepto", "Debe", "Haber"};
            Font headerFont = new Font(Font.HELVETICA, 12, Font.BOLD);

            for (String header : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(header, headerFont));
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setBackgroundColor(Color.LIGHT_GRAY);
                cell.setPadding(5);
                table.addCell(cell);
            }

            // 5. Llenar los datos
            while (rs.next()) {
                table.addCell(String.valueOf(rs.getInt("num_partida")));
                table.addCell(rs.getString("fecha"));
                table.addCell(rs.getString("codigo"));
                table.addCell(rs.getString("nombre"));
                table.addCell(rs.getString("concepto"));

                // Formatear el dinero a 2 decimales con el signo de dólar
                table.addCell(String.format("$ %.2f", rs.getDouble("debe")));
                table.addCell(String.format("$ %.2f", rs.getDouble("haber")));
            }

            document.add(table);
            document.close();

            mostrarAlertaExito("PDF del Libro Diario exportado exitosamente en:\n" + file.getAbsolutePath());

        } catch (Exception e) {
            mostrarAlertaError("Error al exportar PDF: " + e.getMessage());
        }
    }

    // ==========================================
    // MÉTODOS AUXILIARES
    // ==========================================

    private File mostrarFileChooser(ActionEvent event, String nombreSugerido, String filtroDesc, String filtroExt) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Guardar Reporte");
        fileChooser.setInitialFileName(nombreSugerido);
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(filtroDesc, filtroExt));

        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        return fileChooser.showSaveDialog(stage);
    }

    private void mostrarAlertaExito(String mensaje) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Operación Exitosa");
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }

    private void mostrarAlertaError(String mensaje) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }
    @FXML
    private void volverAlMenu(javafx.event.ActionEvent event) {
        try {
            javafx.fxml.FXMLLoader fxmlLoader = new javafx.fxml.FXMLLoader(getClass().getResource("hello-view.fxml"));
            javafx.scene.Scene scene = new javafx.scene.Scene(fxmlLoader.load(), 900, 600);
            javafx.stage.Stage stage = (javafx.stage.Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
            stage.setScene(scene);
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
    }
}