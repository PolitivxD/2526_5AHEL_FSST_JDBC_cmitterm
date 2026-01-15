module org.example.jdbc_datenvisualisierung {
    requires javafx.controls;
    requires javafx.fxml;


    opens org.example.jdbc_datenvisualisierung to javafx.fxml;
    exports org.example.jdbc_datenvisualisierung;
}