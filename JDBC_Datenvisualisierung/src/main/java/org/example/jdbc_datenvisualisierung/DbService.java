package org.example.jdbc_datenvisualisierung;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DbService {

    // --- Singleton ---
    private static final DbService INSTANCE = new DbService();
    public static DbService getInstance() { return INSTANCE; }
    private DbService() {}

    // --- DB Konfiguration ---
    private static final String DB_URL  = "jdbc:postgresql://xserv:5432/world2";
    private static final String DB_USER = "reader";
    private static final String DB_PASS = "reader";

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
    }

    // --- Queries ---
    public List<String> loadContinents() throws SQLException {
        String sql = "SELECT DISTINCT continent " +
                "FROM country " +
                "ORDER BY continent";
        List<String> list = new ArrayList<>();

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) list.add(rs.getString("continent"));
        }
        return list;
    }

    public List<RegionAvg> loadAvgLifeExpectancyByRegion(String continent, boolean ascending) throws SQLException {
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

        try (Connection con = getConnection();
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

    public static class RegionAvg {
        public final String region;
        public final double avgLifeExpectancy;

        public RegionAvg(String region, double avgLifeExpectancy) {
            this.region = region;
            this.avgLifeExpectancy = avgLifeExpectancy;
        }
    }
}
