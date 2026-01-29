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

import java.util.List;

// TODO: eigene Klasse für DB-Zugriffe als Singleton //ERLEDIGT

public class HelloController {

    @FXML private ComboBox<String> cbContinent;
    @FXML private RadioButton rbAsc;
    @FXML private RadioButton rbDesc;
    @FXML private BarChart<String, Number> barChart;
    @FXML private CategoryAxis xAxis;

    private final ToggleGroup tgSort = new ToggleGroup();

    // Schutz gegen “alte Tasks überschreiben neue Auswahl”
    private int requestId = 0;

    // Singleton DB-Service
    private final DbService db = DbService.getInstance();

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

    private void loadContinentsAsync() {
        Task<List<String>> task = new Task<>() {
            @Override protected List<String> call() throws Exception {
                return db.loadContinents();
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

    private void reloadChartAsync() {
        String continent = cbContinent.getValue();
        if (continent == null) return;

        boolean asc = tgSort.getSelectedToggle() == rbAsc;
        final int myRequest = ++requestId;

        Task<List<DbService.RegionAvg>> task = new Task<>() {
            @Override protected List<DbService.RegionAvg> call() throws Exception {
                return db.loadAvgLifeExpectancyByRegion(continent, asc);
            }
        };

        task.setOnSucceeded(e -> {
            if (myRequest != requestId) return;

            var rows = task.getValue();

            barChart.getData().clear();

            var categories = FXCollections.<String>observableArrayList();
            XYChart.Series<String, Number> series = new XYChart.Series<>();

            for (var r : rows) {
                categories.add(r.region);
                series.getData().add(new XYChart.Data<>(r.region, r.avgLifeExpectancy));
            }

            xAxis.setAutoRanging(true);
            xAxis.setCategories(categories);
            xAxis.setAutoRanging(false);

            barChart.getData().add(series);

            barChart.applyCss();
            barChart.layout();
        });

        task.setOnFailed(e -> task.getException().printStackTrace());

        new Thread(task, "db-chart").start();
    }
}
