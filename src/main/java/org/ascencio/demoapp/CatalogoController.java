package org.ascencio.demoapp;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import java.sql.*;

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

    private static final String DB_URL = "jdbc:sqlite:mi_contabilidad.db";

    // Lista reactiva para la tabla
    private final ObservableList<Cuenta> listaCuentas = FXCollections.observableArrayList();

    // Asumiremos la empresa 1 para la demo (la que creaste en el onboarding)
    private final int EMPRESA_ACTUAL_ID = 1;

    @FXML
    public void initialize() {
        // 1. Llenar el ComboBox con el Enum TipoCuenta
        cmbTipo.getItems().setAll(TipoCuenta.values());

        // 2. Configurar las columnas de la tabla para leer los Java Records
        colCodigo.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().codigo()));
        colNombre.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().nombre()));
        colTipo.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().tipo().name()));

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
            pstmt.setString(4, tipo.name()); // Guardamos como ACTIVO, PASIVO, etc.

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
                        TipoCuenta.valueOf(rs.getString("tipo")) // Convertir String a Enum
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
            javafx.scene.Scene scene = new javafx.scene.Scene(fxmlLoader.load(), 900, 600);
            javafx.stage.Stage stage = (javafx.stage.Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
            stage.setScene(scene);
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
    }
}