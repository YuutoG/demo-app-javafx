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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.FileReader;
import javafx.stage.FileChooser;
import javafx.scene.Node;
import javafx.stage.Stage;

public class MovimientosController {

    @FXML private DatePicker dpFecha;
    @FXML private TextField txtConcepto;
    @FXML private TableColumn<FilaUI, String> colConcepto;
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
    @FXML private Button btnCargarDTE;

    private static final String DB_URL = "jdbc:sqlite:" + System.getProperty("user.dir") + System.getProperty("file.separator") + "mi_contabilidad.db";
    private final int EMPRESA_ACTUAL_ID = 1;

    // Record interno solo para la vista de la tabla
    public record FilaUI(Cuenta cuenta, String concepto, BigDecimal debe, BigDecimal haber) {}

    private final ObservableList<FilaUI> listaFilas = FXCollections.observableArrayList();
    private final ObservableList<Cuenta> catalogoCuentas = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        dpFecha.setValue(LocalDate.now());

        // 1. Configurar cómo se ven las Cuentas en el ComboBox
        cmbCuenta.setConverter(new StringConverter<>() {
            @Override
            public String toString(Cuenta c) {
                return c == null ? "" : c.codigo() + " - " + c.nombre();
            }

            @Override
            public Cuenta fromString(String s) {
                return null;
            }
        });

        // 2. Mapear las columnas al Record FilaUI
        colCodigo.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().cuenta().codigo()));
        colCuenta.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().cuenta().nombre()));
        colDebe.setCellValueFactory(cell -> new SimpleStringProperty("$ " + cell.getValue().debe().toString()));
        colHaber.setCellValueFactory(cell -> new SimpleStringProperty("$ " + cell.getValue().haber().toString()));
        colConcepto.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().concepto()));
        tablaMovimientos.setItems(listaFilas);
        cmbCuenta.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {;
            btnCargarDTE.setDisable(newValue == null);
        });

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

        String conceptoFila = txtConcepto.getText().trim();
        if (conceptoFila.isEmpty()) {
            mostrarAlerta("Debe ingresar un concepto para esta línea.");
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

            listaFilas.add(new FilaUI(cuentaSeleccionada, conceptoFila, debe, haber));

            // Limpiar campos de entrada
            cmbCuenta.getSelectionModel().clearSelection();
            txtConcepto.clear();
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

        lblTotalDebe.setText("$ " + sumaDebe);
        lblTotalHaber.setText("$ " + sumaHaber);

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
        if (dpFecha.getValue() == null) {
            mostrarAlerta("La fecha es obligatoria.");
            return;
        }

        int siguienteNumPartida = obtenerSiguienteNumPartida();
        List<Movimiento> movimientosPreparados = new ArrayList<>();

        // Traducimos nuestras FilasUI al Record real de Movimiento
        for (FilaUI fila : listaFilas) {
            movimientosPreparados.add(new Movimiento(
                    0, EMPRESA_ACTUAL_ID, fila.cuenta().id(),
                    dpFecha.getValue(), fila.concepto(),
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
    private void cargarDTE(javafx.event.ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Seleccionar DTE (JSON)");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Archivos JSON", "*.json"));

        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        File file = fileChooser.showOpenDialog(null);

        if (file == null) return;

        try (FileReader reader = new FileReader(file)) {
            // 1. Parsear el archivo como un Objeto JSON
            JsonObject dteJson = JsonParser.parseReader(reader).getAsJsonObject();

            // 2. Extraer el bloque de identificación
            JsonObject identificacion = dteJson.getAsJsonObject("identificacion");
            String numeroControl = identificacion.get("numeroControl").getAsString();
            String codigoGeneracion = identificacion.get("codigoGeneracion").getAsString();
            String fecEmi = identificacion.get("fecEmi").getAsString();

            // 3. Extraer el bloque de resumen
            JsonObject resumen = dteJson.getAsJsonObject("resumen");
            String totalPagar = resumen.get("totalPagar").getAsString();

            // 4. Mapear a la Interfaz Gráfica
            dpFecha.setValue(LocalDate.parse(fecEmi));
            // Concatenamos el número de control y código de generación como pediste
            txtConcepto.setText("DTE: " + numeroControl + " | " + codigoGeneracion);

            // Colocamos el monto en el Debe por defecto para que el usuario lo revise
            txtDebe.setText(totalPagar);
            txtHaber.setText("0.00");

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("DTE Cargado");
            alert.setHeaderText(null);
            alert.setContentText("Datos extraídos correctamente.\nPor favor, verifique si el monto pertenece al Debe o al Haber antes de 'Agregar Fila'.");
            alert.showAndWait();

        } catch (Exception e) {
            mostrarAlerta("Error al procesar el DTE: Asegúrese de que es un JSON válido del Ministerio de Hacienda.\nDetalle: " + e.getMessage());
            e.printStackTrace();
        }
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