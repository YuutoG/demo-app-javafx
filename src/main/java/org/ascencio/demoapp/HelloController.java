package org.ascencio.demoapp;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class HelloController {

    @FXML
    private Label lblBienvenida;
    private static final String DB_URL = "jdbc:sqlite:mi_contabilidad.db";

    @FXML
    public void initialize() {
        // Busca el nombre de la empresa automáticamente cada vez que se carga el menú
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT nombre FROM empresas LIMIT 1")) {
            if (rs.next()) {
                lblBienvenida.setText("Bienvenido, " + rs.getString("nombre"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void abrirCatalogo(ActionEvent event) { navegar(event, "catalogo-view.fxml"); }

    @FXML
    private void abrirMovimientos(ActionEvent event) { navegar(event, "movimientos-view.fxml"); }

    @FXML
    private void abrirReportes(ActionEvent event) { navegar(event, "reportes-view.fxml"); }

    private void navegar(ActionEvent event, String vista) {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource(vista));
            Scene scene = new Scene(fxmlLoader.load(), 900, 600);
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(scene);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}