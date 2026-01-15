package org.example.jdbc_datenvisualisierung;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class HelloController {

    // --- FXML Controls ---
    @FXML private ComboBox<String> cbContinent;
    @FXML private VBox vbContinentRadios;
    @FXML private RadioButton rbAsc;
    @FXML private RadioButton rbDesc;
    @FXML private BarChart<String, Number> barChart;

    private final ToggleGroup tgContinents = new ToggleGroup();
    private final ToggleGroup tgSort = new ToggleGroup();

    // --- DB Daten (ANPASSEN) ---
    private static final String DB_URL  = "jdbc:postgresql://xserv:5432/world2";
    private static final String DB_USER = "reader";
    private static final String DB_PASS = "reader";

    @FXML
    public void initialize() {
        // Sort ToggleGroup
        rbAsc.setToggleGroup(tgSort);
        rbDesc.setToggleGroup(tgSort);
        rbAsc.setSelected(true);

        // Listener: ComboBox -> Chart neu laden
        cbContinent.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                selectContinentRadio(newV);
                reloadChartAsync();
            }
        });

        // Listener: Sortierung -> Chart neu laden
        tgSort.selectedToggleProperty().addListener((obs, oldT, newT) -> reloadChartAsync());

        // Listener: Kontinent RadioButtons -> ComboBox setzen
        tgContinents.selectedToggleProperty().addListener((obs, oldT, newT) -> {
            if (newT instanceof RadioButton rb) {
                String cont = rb.getText();
                if (!cont.equals(cbContinent.getValue())) cbContinent.setValue(cont);
            }
        });

        loadContinentsAsync();
    }

    // ------------------ DB LOAD: Continents ------------------
    private void loadContinentsAsync() {
        Task<List<String>> task = new Task<>() {
            @Override protected List<String> call() throws Exception {
                return loadContinents();
            }
        };

        task.setOnSucceeded(e -> {
            List<String> continents = task.getValue();
            cbContinent.setItems(FXCollections.observableArrayList(continents));
            buildContinentRadios(continents);

            if (!continents.isEmpty()) {
                cbContinent.setValue(continents.get(0));
            }
        });

        task.setOnFailed(e -> task.getException().printStackTrace());

        new Thread(task, "db-continents").start();
    }

    private List<String> loadContinents() throws SQLException {
        String sql = "SELECT DISTINCT continent FROM country ORDER BY continent";
        List<String> list = new ArrayList<>();

        try (Connection con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) list.add(rs.getString("continent"));
        }
        return list;
    }

    private void buildContinentRadios(List<String> continents) {
        vbContinentRadios.getChildren().clear();
        for (String c : continents) {
            RadioButton rb = new RadioButton(c);
            rb.setToggleGroup(tgContinents);
            vbContinentRadios.getChildren().add(rb);
        }
    }

    private void selectContinentRadio(String continent) {
        vbContinentRadios.getChildren().forEach(node -> {
            if (node instanceof RadioButton rb && rb.getText().equals(continent)) {
                rb.setSelected(true);
            }
        });
    }

    // ------------------ DB LOAD: Chart Data ------------------
    private void reloadChartAsync() {
        String continent = cbContinent.getValue();
        if (continent == null) return;

        boolean asc = tgSort.getSelectedToggle() == rbAsc;

        Task<List<RegionAvg>> task = new Task<>() {
            @Override protected List<RegionAvg> call() throws Exception {
                return loadAvgLifeExpectancyByRegion(continent, asc);
            }
        };

        task.setOnSucceeded(e -> {
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            for (RegionAvg r : task.getValue()) {
                series.getData().add(new XYChart.Data<>(r.region, r.avgLifeExpectancy));
            }
            barChart.getData().setAll(series);
        });

        task.setOnFailed(e -> task.getException().printStackTrace());

        new Thread(task, "db-chart").start();
    }

    private List<RegionAvg> loadAvgLifeExpectancyByRegion(String continent, boolean ascending) throws SQLException {
        String order = ascending ? "ASC" : "DESC";

        String sql = """
            SELECT region, AVG(lifeexpectancy) AS avg_le
            FROM country
            WHERE continent = ?
              AND lifeexpectancy IS NOT NULL
            GROUP BY region
            ORDER BY avg_le %s, region ASC
            """.formatted(order);

        List<RegionAvg> list = new ArrayList<>();

        try (Connection con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, continent);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String region = rs.getString("region");
                    double avg = rs.getDouble("avg_le");
                    list.add(new RegionAvg(region, avg));
                }
            }
        }
        return list;
    }

    private static class RegionAvg {
        final String region;
        final double avgLifeExpectancy;

        RegionAvg(String region, double avgLifeExpectancy) {
            this.region = region;
            this.avgLifeExpectancy = avgLifeExpectancy;
        }
    }
}
