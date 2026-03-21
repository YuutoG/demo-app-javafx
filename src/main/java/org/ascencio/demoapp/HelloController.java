package org.ascencio.demoapp;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.scene.control.Button;
import org.kordamp.ikonli.javafx.FontIcon;

public class HelloController {

    @FXML
    private Label lblBienvenida;
    @FXML
    private Button btnTema;
    @FXML
    private FontIcon iconoTema;
    private static final String DB_URL = "jdbc:sqlite:" + System.getProperty("user.dir") + FileSystems.getDefault().getSeparator() + "mi_contabilidad.db";
    private static boolean esTemaOscuro = true;

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
        actualizarTextoBotonTema();
    }

    @FXML
    private void alternarTema(ActionEvent event) {
        if (esTemaOscuro) {
            // Cambiar a Claro
            Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
            esTemaOscuro = false;
        } else {
            // Cambiar a Oscuro
            Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
            esTemaOscuro = true;
        }
        actualizarTextoBotonTema();
    }

    private void actualizarTextoBotonTema() {
        if (btnTema != null && iconoTema != null) {
            if (esTemaOscuro) {
                btnTema.setText(" Cambiar a Tema Claro");
                iconoTema.setIconLiteral("mdi2w-weather-sunny"); // Tu ícono de sol
            } else {
                btnTema.setText(" Cambiar a Tema Oscuro");
                iconoTema.setIconLiteral("mdi2w-weather-night"); // Tu ícono de luna
            }
        }
    }

    @FXML
    private void abrirCatalogo(ActionEvent event) { navegar(event, "catalogo-view.fxml"); }

    @FXML
    private void abrirMovimientos(ActionEvent event) { navegar(event, "movimientos-view.fxml"); }

    @FXML
    private void abrirReportes(ActionEvent event) { navegar(event, "reportes-view.fxml"); }

    // Método centralizado optimizado
    private void navegar(ActionEvent event, String vista) {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource(vista));
            // Cargamos el nuevo diseño visual (VBox, SplitPane, etc.)
            javafx.scene.Parent nuevoContenido = fxmlLoader.load();

            // Obtenemos la escena actual de la ventana y le cambiamos el contenido
            Scene scene = ((Node) event.getSource()).getScene();
            scene.setRoot(nuevoContenido);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}