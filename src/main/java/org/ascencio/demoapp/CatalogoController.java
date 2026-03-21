package org.ascencio.demoapp;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CatalogoController {

    // Controles del FXML
    @FXML private TextField txtCodigo;
    @FXML private TextField txtNombre;
    @FXML private ComboBox<TipoCuenta> cmbTipo;
    @FXML private Button btnGuardar;

    @FXML private TableView<Cuenta> tablaCuentas;
    @FXML private TableColumn<Cuenta, String> colCodigo;
    @FXML private TableColumn<Cuenta, String> colNombre;
    @FXML private TableColumn<Cuenta, String> colTipo;

    private static final String DB_URL = "jdbc:sqlite:" + System.getProperty("user.dir") + System.getProperty("file.separator") + "mi_contabilidad.db";

    // Lista reactiva para la tabla
    private final ObservableList<Cuenta> listaCuentas = FXCollections.observableArrayList();

    // Asumiremos la empresa 1 para la demo (la que creaste en el onboarding)
    private final int EMPRESA_ACTUAL_ID = 1;

    @FXML
    public void initialize() {
        cmbTipo.getItems().setAll(TipoCuenta.values());
        // 1. Enseñar al ComboBox a mostrar el texto con espacios
        cmbTipo.setConverter(new javafx.util.StringConverter<TipoCuenta>() {
            @Override
            public String toString(TipoCuenta t) { return t == null ? "" : t.getTexto(); }
            @Override
            public TipoCuenta fromString(String s) { return null; }
        });

        // 2. Configurar las columnas de la tabla para leer los Java Records
        colCodigo.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().codigo()));
        colNombre.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().nombre()));
        colTipo.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().tipo().getTexto()));

        // 3. Vincular la lista a la tabla y cargar datos de SQLite
        tablaCuentas.setItems(listaCuentas);
        cargarDatosDesdeBD();
    }

    @FXML
    private void guardarCuenta() {
        // Validaciones básicas
        if (txtCodigo.getText().isBlank() || txtNombre.getText().isBlank() || cmbTipo.getValue() == null) {
            mostrarAlerta("Error", "Todos los campos son obligatorios.");
            return;
        }

        String codigo = txtCodigo.getText().trim();
        String nombre = txtNombre.getText().trim();
        TipoCuenta tipo = cmbTipo.getValue();

        // Lógica de inserción JDBC
        String sql = "INSERT INTO catalogo_cuentas (empresa_id, codigo, nombre, tipo) VALUES (?, ?, ?, ?)";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setInt(1, EMPRESA_ACTUAL_ID);
            pstmt.setString(2, codigo);
            pstmt.setString(3, nombre);
            pstmt.setString(4, tipo.getTexto());

            pstmt.executeUpdate();

            // Obtener el ID generado por SQLite para agregarlo a la tabla visual
            ResultSet rs = pstmt.getGeneratedKeys();
            if (rs.next()) {
                int nuevoId = rs.getInt(1);
                Cuenta nuevaCuenta = new Cuenta(nuevoId, EMPRESA_ACTUAL_ID, codigo, nombre, tipo);
                listaCuentas.add(nuevaCuenta); // Actualiza la UI al instante
            }

            limpiarFormulario();

        } catch (SQLException e) {
            mostrarAlerta("Error de Base de Datos", "No se pudo guardar la cuenta: " + e.getMessage());
        }
    }

    @FXML
    private void importarCatalogoExcel(javafx.event.ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Importar Catálogo de Cuentas");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Archivos Excel", "*.xlsx"));

        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        File file = fileChooser.showOpenDialog(null);

        if (file == null) return;

        // Lista temporal en memoria: No tocamos la base de datos hasta estar seguros
        List<Cuenta> cuentasAImportar = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0); // Leemos la primera pestaña del Excel
            int filasProcesadas = 0;

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // Saltar la fila 0 (Encabezados)

                // Si la primera celda está vacía, asumimos que terminó la tabla
                if (row.getCell(0) == null || row.getCell(0).getCellType() == CellType.BLANK) break;

                // Extraemos los datos leyendo la celda de forma segura
                String codigo = leerCelda(row.getCell(0));
                String nombre = leerCelda(row.getCell(1));

                // Normalizamos la clasificación a MAYÚSCULAS y quitamos espacios extra
                String clasificacionStr = leerCelda(row.getCell(2)).toUpperCase().trim();

                // Validación 1: Verificar si el Enum existe
                TipoCuenta tipo;
                try {
                    tipo = TipoCuenta.desdeTexto(clasificacionStr);
                } catch (IllegalArgumentException e) {
                    mostrarAlerta("Error de Validación",
                            "La clasificación '" + clasificacionStr + "' en la fila " + (row.getRowNum() + 1) +
                                    " no es válida.\n\nDebe ser exactamente: ACTIVO, PASIVO, PATRIMONIO, INGRESO o EGRESO.\nLa importación ha sido cancelada.");
                    return; // ¡Abortamos todo el proceso!
                }

                // Si pasa la validación, la agregamos a nuestra lista en memoria
                cuentasAImportar.add(new Cuenta(0, EMPRESA_ACTUAL_ID, codigo, nombre, tipo));
                filasProcesadas++;
            }

            if (cuentasAImportar.isEmpty()) {
                mostrarAlerta("Advertencia", "El archivo Excel parece estar vacío o no tiene datos válidos debajo de la fila de encabezados.");
                return;
            }

            // Si llegamos aquí, toda la memoria está validada. Guardamos de golpe.
            guardarCuentasBatch(cuentasAImportar);
            cargarDatosDesdeBD(); // Refrescamos la tabla visual

            Alert exito = new Alert(Alert.AlertType.INFORMATION);
            exito.setTitle("Importación Exitosa");
            exito.setHeaderText(null);
            exito.setContentText("Se importaron " + filasProcesadas + " cuentas correctamente al sistema.");
            exito.showAndWait();

        } catch (Exception e) {
            mostrarAlerta("Error de Lectura", "No se pudo leer el archivo Excel: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Método para insertar masivamente en SQLite garantizando "Todo o Nada"
    private void guardarCuentasBatch(List<Cuenta> cuentas) throws SQLException {
        String sql = "INSERT INTO catalogo_cuentas (empresa_id, codigo, nombre, tipo) VALUES (?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            conn.setAutoCommit(false); // Iniciamos Transacción

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                for (Cuenta cuenta : cuentas) {
                    pstmt.setInt(1, cuenta.empresaId());
                    pstmt.setString(2, cuenta.codigo());
                    pstmt.setString(3, cuenta.nombre());
                    pstmt.setString(4, cuenta.tipo().getTexto());
                    pstmt.addBatch(); // Encolamos
                }
                pstmt.executeBatch(); // Ejecutamos la cola
                conn.commit();        // Confirmamos y guardamos físicamente
            } catch (SQLException e) {
                conn.rollback();      // Si algo falla, deshacemos lo que llevábamos
                throw e;
            }
        }
    }

    // Método auxiliar porque Apache POI es muy estricto con los tipos de celda
    private String leerCelda(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            // Si el código en Excel es "1101", POI lo lee como 1101.0, esto lo convierte a texto "1101"
            case NUMERIC -> String.valueOf((int) cell.getNumericCellValue());
            default -> "";
        };
    }

    private void cargarDatosDesdeBD() {
        listaCuentas.clear();
        String sql = "SELECT * FROM catalogo_cuentas WHERE empresa_id = ?";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, EMPRESA_ACTUAL_ID);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                Cuenta cuenta = new Cuenta(
                        rs.getInt("id"),
                        rs.getInt("empresa_id"),
                        rs.getString("codigo"),
                        rs.getString("nombre"),
                        TipoCuenta.desdeTexto(rs.getString("tipo")) // Convertir String a Enum
                );
                listaCuentas.add(cuenta);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void limpiarFormulario() {
        txtCodigo.clear();
        txtNombre.clear();
        cmbTipo.getSelectionModel().clearSelection();
        txtCodigo.requestFocus();
    }

    private void mostrarAlerta(String titulo, String mensaje) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }

    @FXML
    private void volverAlMenu(javafx.event.ActionEvent event) {
        try {
            javafx.fxml.FXMLLoader fxmlLoader = new javafx.fxml.FXMLLoader(getClass().getResource("hello-view.fxml"));
            javafx.scene.Parent nuevoContenido = fxmlLoader.load();

            javafx.scene.Scene scene = ((javafx.scene.Node) event.getSource()).getScene();
            scene.setRoot(nuevoContenido);

        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
    }
}