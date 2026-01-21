package org.example.jdbc_datenvisualisierung;

import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class HelloController {

    @FXML private ComboBox<String> cbContinent;
    @FXML private RadioButton rbAsc;
    @FXML private RadioButton rbDesc;
    @FXML private BarChart<String, Number> barChart;
    @FXML private CategoryAxis xAxis;

    private final ToggleGroup tgSort = new ToggleGroup();

    // Damit alte DB-Tasks (z.B. Afrika) nicht nachträglich neue Auswahl überschreiben:
    private int requestId = 0;

    // DB
    private static final String DB_URL  = "jdbc:postgresql://xserv:5432/world2";
    private static final String DB_USER = "reader";
    private static final String DB_PASS = "reader";

    @FXML
    public void initialize() {
        rbAsc.setToggleGroup(tgSort);
        rbDesc.setToggleGroup(tgSort);
        rbAsc.setSelected(true);

        cbContinent.valueProperty().addListener((obs, o, n) -> {
            if (n != null) reloadChartAsync();
        });

        tgSort.selectedToggleProperty().addListener((obs, o, n) -> reloadChartAsync());

        loadContinentsAsync();
    }

    // ------------------ Continents ------------------

    private void loadContinentsAsync() {
        Task<List<String>> task = new Task<>() {
            @Override protected List<String> call() throws Exception {
                return loadContinents();
            }
        };

        task.setOnSucceeded(e -> {
            var continents = task.getValue();
            cbContinent.setItems(FXCollections.observableArrayList(continents));
            if (!continents.isEmpty()) cbContinent.setValue(continents.get(0));
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

    // ------------------ Chart ------------------

    private void reloadChartAsync() {
        String continent = cbContinent.getValue();
        if (continent == null) return;

        boolean asc = tgSort.getSelectedToggle() == rbAsc;

        final int myRequest = ++requestId;

        Task<List<RegionAvg>> task = new Task<>() {
            @Override protected List<RegionAvg> call() throws Exception {
                return loadAvgLifeExpectancyByRegion(continent, asc);
            }
        };

        task.setOnSucceeded(e -> {
            // Wenn inzwischen schon eine neuere Auswahl gemacht wurde -> altes Ergebnis ignorieren
            if (myRequest != requestId) return;

            var rows = task.getValue();

            // Alles neu setzen
            barChart.getData().clear();

            var categories = FXCollections.<String>observableArrayList();
            XYChart.Series<String, Number> series = new XYChart.Series<>();

            for (var r : rows) {
                categories.add(r.region);
                series.getData().add(new XYChart.Data<>(r.region, r.avgLifeExpectancy));
            }

            // Achse resetten + neue Kategorien setzen
            xAxis.setAutoRanging(true);
            xAxis.setCategories(categories);
            xAxis.setAutoRanging(false);

            barChart.getData().add(series);

            // erzwingt visuelles Update
            barChart.applyCss();
            barChart.layout();
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
                    list.add(new RegionAvg(
                            rs.getString("region"),
                            rs.getDouble("avg_le")
                    ));
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
