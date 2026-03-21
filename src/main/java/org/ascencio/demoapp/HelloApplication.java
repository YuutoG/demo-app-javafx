package org.ascencio.demoapp;

import atlantafx.base.theme.PrimerDark;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.*;
import java.util.Objects;
import java.util.Optional;

public class HelloApplication extends Application {

    private static final String DB_URL = "jdbc:sqlite:" + System.getProperty("user.dir") + System.getProperty("file.separator") + "mi_contabilidad.db";

    @Override
    public void start(Stage stage) throws IOException {
        inicializarBaseDeDatos();
        Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
        Empresa empresaActual = cargarEmpresa();

        // 3. Lógica de "Primer Uso"
        if (empresaActual == null) {
            Optional<Empresa> resultado = mostrarDialogoRegistro();

            if (resultado.isPresent()) {
                guardarEmpresa(resultado.get());
                empresaActual = resultado.get();
            } else {
                System.out.println("Configuración cancelada. Cerrando aplicación.");
                Platform.exit();
                return;
            }
        }

        // --- INICIO DEL FLUJO NORMAL (Vista Principal) ---
        FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("hello-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 900, 600);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("style.css")).toExternalForm());
        stage.setTitle("Sistema Contable - Portable");
        stage.setScene(scene);
        stage.setMaximized(true);
        stage.show();
    }

    // ==========================================
    // LÓGICA DE INTERFAZ (UI)
    // ==========================================
    private Optional<Empresa> mostrarDialogoRegistro() {
        Dialog<Empresa> dialog = new Dialog<>();
        dialog.setTitle("Configuración Inicial");
        dialog.setHeaderText("Registre los datos de su empresa para comenzar.");

        ButtonType btnGuardarType = new ButtonType("Guardar y Comenzar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnGuardarType, ButtonType.CANCEL);

        TextField txtNombre = new TextField();
        txtNombre.setPromptText("Ej. Consultores S.A.");
        TextField txtDireccion = new TextField();
        TextField txtTelefono = new TextField();
        TextField txtEmail = new TextField();
        TextField txtNit = new TextField();
        txtNit.setPromptText("0000-000000-000-0");
        TextField txtResponsable = new TextField();

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        grid.add(new Label("Nombre (Requerido):"), 0, 0);
        grid.add(txtNombre, 1, 0);
        grid.add(new Label("NIT (Requerido):"), 0, 1);
        grid.add(txtNit, 1, 1);
        grid.add(new Label("Dirección:"), 0, 2);
        grid.add(txtDireccion, 1, 2);
        grid.add(new Label("Teléfono:"), 0, 3);
        grid.add(txtTelefono, 1, 3);
        grid.add(new Label("Email:"), 0, 4);
        grid.add(txtEmail, 1, 4);
        grid.add(new Label("Responsable:"), 0, 5);
        grid.add(txtResponsable, 1, 5);

        dialog.getDialogPane().setContent(grid);

        javafx.scene.Node btnGuardar = dialog.getDialogPane().lookupButton(btnGuardarType);
        btnGuardar.setDisable(true);
        txtNombre.textProperty().addListener((observable, oldValue, newValue) -> {
            btnGuardar.setDisable(newValue.trim().isEmpty() || txtNit.getText().trim().isEmpty());
        });
        txtNit.textProperty().addListener((observable, oldValue, newValue) -> {
            btnGuardar.setDisable(newValue.trim().isEmpty() || txtNombre.getText().trim().isEmpty());
        });

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == btnGuardarType) {
                return new Empresa(
                        0,
                        txtNombre.getText(),
                        txtDireccion.getText(),
                        txtTelefono.getText(),
                        txtEmail.getText(),
                        txtNit.getText(),
                        txtResponsable.getText()
                );
            }
            return null;
        });

        return dialog.showAndWait();
    }

    // ==========================================
    // LÓGICA DE BASE DE DATOS (JDBC)
    // ==========================================
    private void inicializarBaseDeDatos() {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {

            // ¡LA CLAVE ESTÁ AQUÍ! Ejecutamos cada instrucción por separado.
            stmt.execute("PRAGMA foreign_keys = ON;");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS empresas (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    nombre TEXT NOT NULL,
                    direccion TEXT,
                    telefono TEXT,
                    email TEXT,
                    nit TEXT,
                    nombre_responsable TEXT
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS catalogo_cuentas (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        empresa_id INTEGER NOT NULL,
                        codigo TEXT NOT NULL,
                        nombre TEXT NOT NULL,
                        tipo TEXT CHECK(tipo IN ('ACTIVO', 'PASIVO', 'PATRIMONIO', 'INGRESO', 'EGRESO', 'CUENTA DE ORDEN Y DE CONTROL DEUDORAS', 'CUENTA DE ORDEN Y DE CONTROL ACREEDORAS')),
                        FOREIGN KEY (empresa_id) REFERENCES empresas(id) ON DELETE CASCADE
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS movimientos (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    empresa_id INTEGER NOT NULL,
                    cuenta_id INTEGER NOT NULL,
                    fecha DATE NOT NULL,
                    concepto TEXT,
                    debe DECIMAL(12,2) DEFAULT 0,
                    haber DECIMAL(12,2) DEFAULT 0,
                    num_partida INTEGER NOT NULL,
                    FOREIGN KEY (empresa_id) REFERENCES empresas(id) ON DELETE CASCADE,
                    FOREIGN KEY (cuenta_id) REFERENCES catalogo_cuentas(id)
                )
            """);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private Empresa cargarEmpresa() {
        String sql = "SELECT * FROM empresas LIMIT 1";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return new Empresa(
                        rs.getInt("id"),
                        rs.getString("nombre"),
                        rs.getString("direccion"),
                        rs.getString("telefono"),
                        rs.getString("email"),
                        rs.getString("nit"),
                        rs.getString("nombre_responsable")
                );
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void guardarEmpresa(Empresa emp) {
        String sql = "INSERT INTO empresas (nombre, direccion, telefono, email, nit, nombre_responsable) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, emp.nombre());
            pstmt.setString(2, emp.direccion());
            pstmt.setString(3, emp.telefono());
            pstmt.setString(4, emp.email());
            pstmt.setString(5, emp.nit());
            pstmt.setString(6, emp.responsable());
            pstmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
