package com.localserviceconnect.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BookingStatusSchemaMigration implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BookingStatusSchemaMigration.class);

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public BookingStatusSchemaMigration(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            if (!"H2".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())) {
                return;
            }
        }

        String dataType = jdbcTemplate.query(
                "SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS " +
                        "WHERE TABLE_SCHEMA = 'public' AND TABLE_NAME = 'bookings' AND COLUMN_NAME = 'status'",
                resultSet -> resultSet.next() ? resultSet.getString("DATA_TYPE") : null
        );
        if ("ENUM".equalsIgnoreCase(dataType)) {
            jdbcTemplate.execute("ALTER TABLE bookings ALTER COLUMN status SET DATA TYPE VARCHAR(20)");
            log.info("Migrated bookings.status from H2 ENUM to VARCHAR");
        }
    }
}
