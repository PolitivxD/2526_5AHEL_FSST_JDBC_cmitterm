module org.example.jdbc_datenvisualisierung {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;


    opens org.example.jdbc_datenvisualisierung to javafx.fxml;
    exports org.example.jdbc_datenvisualisierung;
}