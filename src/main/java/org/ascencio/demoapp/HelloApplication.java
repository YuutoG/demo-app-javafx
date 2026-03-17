package org.ascencio.demoapp;

import atlantafx.base.theme.PrimerDark;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class HelloApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
        FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("hello-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 320, 240);
        stage.setTitle("Hello!");
        stage.setScene(scene);
        stage.setMaximized(true);
        stage.show();

        probarSQLite();
    }

    private void probarSQLite() {
        String url = "jdbc:sqlite:basita.db";

        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {
            String sql = """
CREATE TABLE IF NOT EXISTS empresas (
id integer PRIMARY KEY,
nombre text NOT NULL
);""";
            stmt.execute(sql);
            System.out.println("¡Éxito! Base de datos SQLite creada y conectada correctamente.");

        } catch (Exception e) {
            System.out.println("Error al conectar con SQLite: " + e.getMessage());
        }
    }
}
