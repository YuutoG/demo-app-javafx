package org.ascencio.demoapp;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class MovimientosController {

    @FXML private DatePicker dpFecha;
    @FXML private TextField txtConcepto;
    @FXML private ComboBox<Cuenta> cmbCuenta;
    @FXML private TextField txtDebe;
    @FXML private TextField txtHaber;
    @FXML private TableView<FilaUI> tablaMovimientos;
    @FXML private TableColumn<FilaUI, String> colCodigo;
    @FXML private TableColumn<FilaUI, String> colCuenta;
    @FXML private TableColumn<FilaUI, String> colDebe;
    @FXML private TableColumn<FilaUI, String> colHaber;
    @FXML private Label lblTotalDebe;
    @FXML private Label lblTotalHaber;
    @FXML private Label lblEstado;
    @FXML private Button btnGuardarPartida;

    private static final String DB_URL = "jdbc:sqlite:mi_contabilidad.db";
    private final int EMPRESA_ACTUAL_ID = 1;

    // Record interno solo para la vista de la tabla
    public record FilaUI(Cuenta cuenta, BigDecimal debe, BigDecimal haber) {}

    private ObservableList<FilaUI> listaFilas = FXCollections.observableArrayList();
    private ObservableList<Cuenta> catalogoCuentas = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        dpFecha.setValue(LocalDate.now());

        // 1. Configurar cómo se ven las Cuentas en el ComboBox
        cmbCuenta.setConverter(new StringConverter<Cuenta>() {
            @Override
            public String toString(Cuenta c) {
                return c == null ? "" : c.codigo() + " - " + c.nombre();
            }
            @Override
            public Cuenta fromString(String s) { return null; }
        });

        // 2. Mapear las columnas al Record FilaUI
        colCodigo.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().cuenta().codigo()));
        colCuenta.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().cuenta().nombre()));
        colDebe.setCellValueFactory(cell -> new SimpleStringProperty("$ " + cell.getValue().debe().toString()));
        colHaber.setCellValueFactory(cell -> new SimpleStringProperty("$ " + cell.getValue().haber().toString()));

        tablaMovimientos.setItems(listaFilas);

        cargarCatalogo();
    }

    private void cargarCatalogo() {
        String sql = "SELECT * FROM catalogo_cuentas WHERE empresa_id = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, EMPRESA_ACTUAL_ID);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                catalogoCuentas.add(new Cuenta(
                        rs.getInt("id"), rs.getInt("empresa_id"),
                        rs.getString("codigo"), rs.getString("nombre"),
                        TipoCuenta.valueOf(rs.getString("tipo"))
                ));
            }
            cmbCuenta.setItems(catalogoCuentas);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void agregarFila() {
        Cuenta cuentaSeleccionada = cmbCuenta.getValue();
        if (cuentaSeleccionada == null) {
            mostrarAlerta("Seleccione una cuenta contable.");
            return;
        }

        try {
            // Parseo seguro a BigDecimal
            String strDebe = txtDebe.getText().isBlank() ? "0" : txtDebe.getText();
            String strHaber = txtHaber.getText().isBlank() ? "0" : txtHaber.getText();

            BigDecimal debe = new BigDecimal(strDebe).setScale(2, RoundingMode.HALF_UP);
            BigDecimal haber = new BigDecimal(strHaber).setScale(2, RoundingMode.HALF_UP);

            if (debe.compareTo(BigDecimal.ZERO) == 0 && haber.compareTo(BigDecimal.ZERO) == 0) {
                mostrarAlerta("Debe ingresar un monto mayor a cero en Debe o Haber.");
                return;
            }

            listaFilas.add(new FilaUI(cuentaSeleccionada, debe, haber));

            // Limpiar campos de entrada
            cmbCuenta.getSelectionModel().clearSelection();
            txtDebe.clear();
            txtHaber.clear();

            recalcularTotales();

        } catch (NumberFormatException e) {
            mostrarAlerta("Monto inválido. Use solo números y punto decimal.");
        }
    }

    private void recalcularTotales() {
        BigDecimal sumaDebe = BigDecimal.ZERO;
        BigDecimal sumaHaber = BigDecimal.ZERO;

        for (FilaUI fila : listaFilas) {
            sumaDebe = sumaDebe.add(fila.debe());
            sumaHaber = sumaHaber.add(fila.haber());
        }

        lblTotalDebe.setText("$ " + sumaDebe.toString());
        lblTotalHaber.setText("$ " + sumaHaber.toString());

        // Validar Partida Doble
        boolean hayDatos = sumaDebe.compareTo(BigDecimal.ZERO) > 0;
        boolean estaCuadrado = sumaDebe.compareTo(sumaHaber) == 0;

        if (hayDatos && estaCuadrado) {
            lblEstado.setText("¡Cuadrado!");
            lblEstado.setStyle("-fx-text-fill: -color-success-emphasis;");
            btnGuardarPartida.setDisable(false);
        } else {
            lblEstado.setText(hayDatos ? "Descuadrado" : "Esperando datos...");
            lblEstado.setStyle("-fx-text-fill: -color-danger-emphasis;");
            btnGuardarPartida.setDisable(true);
        }
    }

    @FXML
    private void guardarPartidaBD() {
        if (dpFecha.getValue() == null || txtConcepto.getText().isBlank()) {
            mostrarAlerta("La fecha y el concepto general son obligatorios.");
            return;
        }

        int siguienteNumPartida = obtenerSiguienteNumPartida();
        List<Movimiento> movimientosPreparados = new ArrayList<>();

        // Traducimos nuestras FilasUI al Record real de Movimiento
        for (FilaUI fila : listaFilas) {
            movimientosPreparados.add(new Movimiento(
                    0, EMPRESA_ACTUAL_ID, fila.cuenta().id(),
                    dpFecha.getValue(), txtConcepto.getText().trim(),
                    fila.debe(), fila.haber(), siguienteNumPartida
            ));
        }

        // Ejecutar Transacción JDBC
        if (registrarPartidaDoble(movimientosPreparados)) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setHeaderText("¡Éxito!");
            alert.setContentText("La Partida #" + siguienteNumPartida + " se registró correctamente.");
            alert.showAndWait();

            listaFilas.clear();
            txtConcepto.clear();
            recalcularTotales();
        }
    }

    private int obtenerSiguienteNumPartida() {
        String sql = "SELECT MAX(num_partida) FROM movimientos WHERE empresa_id = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, EMPRESA_ACTUAL_ID);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next() && rs.getInt(1) > 0) {
                return rs.getInt(1) + 1;
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return 1; // Si no hay registros, iniciamos en 1
    }

    private boolean registrarPartidaDoble(List<Movimiento> partida) {
        String sql = "INSERT INTO movimientos (empresa_id, cuenta_id, fecha, concepto, debe, haber, num_partida) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            conn.setAutoCommit(false); // Iniciar transacción
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                for (Movimiento mov : partida) {
                    pstmt.setInt(1, mov.empresaId());
                    pstmt.setInt(2, mov.cuentaId());
                    pstmt.setString(3, mov.fecha().toString());
                    pstmt.setString(4, mov.concepto());
                    pstmt.setBigDecimal(5, mov.debe());
                    pstmt.setBigDecimal(6, mov.haber());
                    pstmt.setInt(7, mov.numPartida());
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
                conn.commit(); // Confirmar transacción
                return true;
            } catch (SQLException e) {
                conn.rollback();
                mostrarAlerta("Error al guardar: " + e.getMessage());
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return false;
    }

    private void mostrarAlerta(String mensaje) {
        new Alert(Alert.AlertType.ERROR, mensaje).showAndWait();
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