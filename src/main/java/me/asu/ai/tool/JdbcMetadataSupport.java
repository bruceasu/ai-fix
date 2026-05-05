package me.asu.ai.tool;

import java.sql.*;
import java.util.*;

public class JdbcMetadataSupport {

    public String getTableMetadata(String url, String user, String password, String tableName) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            DatabaseMetaData metaData = conn.getMetaData();
            
            // Get table info
            try (ResultSet tables = metaData.getTables(null, null, tableName, new String[]{"TABLE", "VIEW"})) {
                if (!tables.next()) {
                    return "Table not found: " + tableName;
                }
                sb.append("Table: ").append(tables.getString("TABLE_NAME")).append("\n");
                sb.append("Type: ").append(tables.getString("TABLE_TYPE")).append("\n");
                String remarks = tables.getString("REMARKS");
                if (remarks != null && !remarks.isBlank()) {
                    sb.append("Remarks: ").append(remarks).append("\n");
                }
            }

            sb.append("\nColumns:\n");
            sb.append(String.format("%-20s | %-15s | %-8s | %s\n", "Column", "Type", "Nullable", "Remarks"));
            sb.append("-".repeat(70)).append("\n");

            try (ResultSet columns = metaData.getColumns(null, null, tableName, null)) {
                while (columns.next()) {
                    String columnName = columns.getString("COLUMN_NAME");
                    String typeName = columns.getString("TYPE_NAME");
                    int columnSize = columns.getInt("COLUMN_SIZE");
                    String isNullable = columns.getString("IS_NULLABLE");
                    String colRemarks = columns.getString("REMARKS");

                    sb.append(String.format("%-20s | %-15s | %-8s | %s\n",
                            columnName,
                            typeName + "(" + columnSize + ")",
                            isNullable,
                            colRemarks != null ? colRemarks : ""));
                }
            }

            // Primary Keys
            sb.append("\nPrimary Keys:\n");
            try (ResultSet pks = metaData.getPrimaryKeys(null, null, tableName)) {
                while (pks.next()) {
                    sb.append("- ").append(pks.getString("COLUMN_NAME")).append("\n");
                }
            }

            // Indexes
            sb.append("\nIndexes:\n");
            try (ResultSet indexes = metaData.getIndexInfo(null, null, tableName, false, false)) {
                Set<String> seenIndexes = new HashSet<>();
                while (indexes.next()) {
                    String indexName = indexes.getString("INDEX_NAME");
                    if (indexName == null || seenIndexes.contains(indexName)) continue;
                    boolean nonUnique = indexes.getBoolean("NON_UNIQUE");
                    sb.append("- ").append(indexName).append(nonUnique ? " (Non-Unique)" : " (Unique)").append("\n");
                    seenIndexes.add(indexName);
                }
            }
        }
        return sb.toString();
    }
}
