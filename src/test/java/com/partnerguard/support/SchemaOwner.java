package com.partnerguard.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Changes the test database as the schema owner, for state the application role can't write
 * (ADR-0015), such as a partner's status. Each call commits on its own connection, outside the
 * test's transaction: tests must undo what they change, in a finally block or {@code @AfterEach}.
 */
public final class SchemaOwner {

    private SchemaOwner() {
    }

    public static void setPartnerStatus(UUID partnerId, String status) {
        try (Connection connection = DriverManager.getConnection(PostgresIntegrationTest.POSTGRES.getJdbcUrl(),
                PostgresIntegrationTest.POSTGRES.getUsername(), PostgresIntegrationTest.POSTGRES.getPassword());
             PreparedStatement update = connection.prepareStatement("update partner set status = ? where id = ?")) {
            update.setString(1, status);
            update.setObject(2, partnerId);
            if (update.executeUpdate() != 1) {
                throw new IllegalStateException("No partner " + partnerId);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
