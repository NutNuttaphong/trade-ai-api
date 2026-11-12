package com.trade.stocks.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

/**
 * Selects between SQL/JPA (H2 or PostgreSQL) and MongoDB before auto-configuration evaluates,
 * preventing dual-repository bean conflicts and normalizing Railway connection URLs.
 */
public class DatabaseModeEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    public static final String PROPERTY_SOURCE_NAME = "databaseModeProperties";
    public static final String RESOLVED_MODE_PROPERTY = "app.db.resolved-mode";
    public static final String MODE_AUTO = "auto";
    public static final String MODE_MONGO = "mongo";
    public static final String MODE_JPA = "jpa";
    static final String DEFAULT_MONGO_DATABASE = "stockdb";

    static final List<String> JPA_EXCLUDES = List.of(
            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
            "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration",
            "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration"
    );

    static final List<String> MONGO_EXCLUDES = List.of(
            "org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration"
    );

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void postProcessEnvironment(
            ConfigurableEnvironment environment, SpringApplication application) {
        String mode = resolveMode(environment);
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(RESOLVED_MODE_PROPERTY, mode);

        if (MODE_MONGO.equals(mode)) {
            applyMongoProperties(environment, properties);
        } else {
            applyJpaProperties(environment, properties);
        }

        mergeExcludes(
                environment,
                properties,
                MODE_MONGO.equals(mode) ? JPA_EXCLUDES : MONGO_EXCLUDES
        );
        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, properties));
    }

    /**
     * Resolves active persistence mode from {@code app.db.type} / {@code APP_DB_TYPE},
     * falling back to Mongo when Mongo URL variables are present, otherwise JPA.
     */
    public static String resolveMode(Environment environment) {
        String configured = firstNonBlank(environment, "app.db.type", "APP_DB_TYPE");
        if (StringUtils.hasText(configured)) {
            String normalized = configured.trim().toLowerCase(Locale.ROOT);
            if ("mongo".equals(normalized) || "mongodb".equals(normalized)) {
                return MODE_MONGO;
            }
            if ("jpa".equals(normalized)
                    || "sql".equals(normalized)
                    || "postgres".equals(normalized)
                    || "postgresql".equals(normalized)
                    || "h2".equals(normalized)) {
                return MODE_JPA;
            }
        }

        String mongoUrl = firstNonBlank(
                environment,
                "MONGO_URL",
                "MONGO_PRIVATE_URL",
                "MONGODB_URI",
                "SPRING_DATA_MONGODB_URI",
                "spring.data.mongodb.uri"
        );
        if (StringUtils.hasText(mongoUrl)) {
            return MODE_MONGO;
        }
        return MODE_JPA;
    }

    static void applyMongoProperties(Environment environment, Map<String, Object> properties) {
        String rawUri = firstNonBlank(
                environment,
                "MONGO_URL",
                "MONGO_PRIVATE_URL",
                "MONGODB_URI",
                "SPRING_DATA_MONGODB_URI",
                "spring.data.mongodb.uri"
        );
        String explicitDatabase = firstNonBlank(environment, "MONGO_DATABASE", "MONGODB_DATABASE");

        if (StringUtils.hasText(rawUri)) {
            String uriDatabase = extractMongoDatabaseName(rawUri);
            String effectiveDatabase = StringUtils.hasText(explicitDatabase)
                    ? explicitDatabase.trim()
                    : (StringUtils.hasText(uriDatabase) ? uriDatabase : DEFAULT_MONGO_DATABASE);
            properties.put("spring.data.mongodb.uri", buildMongoUri(rawUri, effectiveDatabase));
            properties.put("spring.data.mongodb.database", effectiveDatabase);
        } else if (StringUtils.hasText(explicitDatabase)) {
            properties.put("spring.data.mongodb.database", explicitDatabase.trim());
        }

        properties.put("spring.data.jpa.repositories.enabled", "false");
        properties.put("spring.h2.console.enabled", "false");
    }

    static void applyJpaProperties(Environment environment, Map<String, Object> properties) {
        properties.put("spring.data.mongodb.repositories.enabled", "false");
        String databaseUrl = firstNonBlank(
                environment,
                "JDBC_DATABASE_URL",
                "DATABASE_URL",
                "DATABASE_PRIVATE_URL"
        );
        if (StringUtils.hasText(databaseUrl)) {
            applyPostgresDatabaseUrl(databaseUrl.trim(), environment, properties);
        }
    }

    /**
     * Normalizes Railway {@code MONGO_URL} values that omit a database path and/or {@code authSource=admin}.
     */
    public static String buildMongoUri(String rawUri, String defaultDatabase) {
        String trimmed = rawUri.trim();
        int schemeEnd = trimmed.indexOf("://");
        if (schemeEnd < 0) {
            return trimmed;
        }

        int queryIndex = trimmed.indexOf('?');
        String base = queryIndex >= 0 ? trimmed.substring(0, queryIndex) : trimmed;
        String query = queryIndex >= 0 ? trimmed.substring(queryIndex + 1) : "";

        int authorityStart = schemeEnd + 3;
        int pathStart = base.indexOf('/', authorityStart);
        String authority = pathStart >= 0
                ? base.substring(authorityStart, pathStart)
                : base.substring(authorityStart);
        String path = pathStart >= 0 ? base.substring(pathStart) : "";
        boolean hasExplicitDb = path.length() > 1;

        String database = StringUtils.hasText(defaultDatabase)
                ? defaultDatabase.trim()
                : DEFAULT_MONGO_DATABASE;
        String effectiveBase = hasExplicitDb
                ? base
                : (pathStart >= 0 ? base.substring(0, pathStart) : base) + "/" + database;

        boolean hasUserInfo = authority.contains("@");
        boolean hasAuthSource = query.toLowerCase(Locale.ROOT).contains("authsource=");
        // Railway's root user in MONGO_URL authenticates against the admin database.
        if (!hasExplicitDb && hasUserInfo && !hasAuthSource) {
            query = query.isEmpty() ? "authSource=admin" : query + "&authSource=admin";
        }

        return query.isEmpty() ? effectiveBase : effectiveBase + "?" + query;
    }

    static String extractMongoDatabaseName(String rawUri) {
        String trimmed = rawUri.trim();
        int schemeEnd = trimmed.indexOf("://");
        if (schemeEnd < 0) {
            return null;
        }
        int queryIndex = trimmed.indexOf('?');
        String base = queryIndex >= 0 ? trimmed.substring(0, queryIndex) : trimmed;
        int pathStart = base.indexOf('/', schemeEnd + 3);
        if (pathStart < 0 || pathStart + 1 >= base.length()) {
            return null;
        }
        String dbName = base.substring(pathStart + 1).trim();
        return dbName.isEmpty() ? null : dbName;
    }

    /**
     * Converts Railway PostgreSQL {@code postgres://} or {@code postgresql://} URLs into JDBC configuration.
     */
    public static void applyPostgresDatabaseUrl(
            String rawUrl, Environment environment, Map<String, Object> properties) {
        if (rawUrl.startsWith("jdbc:")) {
            properties.put("spring.datasource.url", rawUrl);
            if (rawUrl.startsWith("jdbc:postgresql:")) {
                applyPostgresDriverDefaults(properties);
            }
            return;
        }

        if (!rawUrl.startsWith("postgres://") && !rawUrl.startsWith("postgresql://")) {
            return;
        }

        URI uri = URI.create(rawUrl);
        String host = uri.getHost();
        if (!StringUtils.hasText(host)) {
            return;
        }

        StringBuilder jdbcUrl = new StringBuilder("jdbc:postgresql://").append(host);
        if (uri.getPort() > 0) {
            jdbcUrl.append(':').append(uri.getPort());
        }
        String path = uri.getRawPath();
        jdbcUrl.append(StringUtils.hasText(path) ? path : "/postgres");
        if (StringUtils.hasText(uri.getRawQuery())) {
            jdbcUrl.append('?').append(uri.getRawQuery());
        }

        properties.put("spring.datasource.url", jdbcUrl.toString());
        applyPostgresDriverDefaults(properties);

        String userInfo = uri.getRawUserInfo();
        if (StringUtils.hasText(userInfo)) {
            String[] parts = userInfo.split(":", 2);
            properties.put("spring.datasource.username", decodeUrlComponent(parts[0]));
            properties.put(
                    "spring.datasource.password",
                    parts.length > 1 ? decodeUrlComponent(parts[1]) : ""
            );
        } else {
            String pgUser = firstNonBlank(environment, "PGUSER");
            String pgPassword = environment != null ? environment.getProperty("PGPASSWORD") : null;
            if (StringUtils.hasText(pgUser)) {
                properties.put("spring.datasource.username", pgUser.trim());
            }
            if (pgPassword != null) {
                properties.put("spring.datasource.password", pgPassword);
            }
        }
    }

    private static void applyPostgresDriverDefaults(Map<String, Object> properties) {
        // Override both camelCase (used in application.properties) and kebab-case H2 settings.
        properties.put("spring.datasource.driverClassName", "org.postgresql.Driver");
        properties.put("spring.datasource.driver-class-name", "org.postgresql.Driver");
        properties.put("spring.jpa.database-platform", "org.hibernate.dialect.PostgreSQLDialect");
        properties.put("spring.h2.console.enabled", "false");
    }

    private static void mergeExcludes(
            Environment environment, Map<String, Object> properties, List<String> excludesToAdd) {
        Set<String> excludes = new LinkedHashSet<>();
        String existing = environment.getProperty("spring.autoconfigure.exclude");
        if (StringUtils.hasText(existing)) {
            for (String item : existing.split(",")) {
                if (StringUtils.hasText(item)) {
                    excludes.add(item.trim());
                }
            }
        }
        excludes.addAll(excludesToAdd);
        properties.put("spring.autoconfigure.exclude", String.join(",", excludes));
    }

    private static String firstNonBlank(Environment environment, String... keys) {
        if (environment == null) {
            return null;
        }
        for (String key : keys) {
            String value = environment.getProperty(key);
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static String decodeUrlComponent(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
