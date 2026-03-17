module org.ascencio.demoapp {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires net.synedra.validatorfx;
    requires org.kordamp.ikonli.core;
    requires org.kordamp.ikonli.javafx;

    opens org.ascencio.demoapp to javafx.fxml;
    exports org.ascencio.demoapp;
}