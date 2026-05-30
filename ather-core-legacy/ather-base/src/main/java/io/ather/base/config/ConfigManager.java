/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.ather.base.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.inject.Inject;
import io.airlift.log.Logger;
import io.airlift.units.DataSize;

import io.ather.base.atherException;
import io.ather.base.client.duckdb.DuckDBConfig;
import io.ather.base.client.duckdb.DuckDBConnectorConfig;

import static com.google.common.base.MoreObjects.toStringHelper;
import static io.ather.base.client.duckdb.DuckDBConfig.DUCKDB_CACHE_TASK_RETRY_DELAY;
import static io.ather.base.client.duckdb.DuckDBConfig.DUCKDB_HOME_DIRECTORY;
import static io.ather.base.client.duckdb.DuckDBConfig.DUCKDB_MAX_CACHE_QUERY_TIMEOUT;
import static io.ather.base.client.duckdb.DuckDBConfig.DUCKDB_MAX_CONCURRENT_TASKS;
import static io.ather.base.client.duckdb.DuckDBConfig.DUCKDB_MEMORY_LIMIT;
import static io.ather.base.client.duckdb.DuckDBConfig.DUCKDB_TEMP_DIRECTORY;
import static io.ather.base.client.duckdb.DuckDBConnectorConfig.DUCKDB_CONNECTOR_INIT_SQL_PATH;
import static io.ather.base.client.duckdb.DuckDBConnectorConfig.DUCKDB_CONNECTOR_SESSION_SQL_PATH;
import static io.ather.base.client.duckdb.FileUtil.ARCHIVED;
import static io.ather.base.metadata.StandardErrorCode.NOT_FOUND;
import static io.airlift.configuration.ConfigurationLoader.loadPropertiesFrom;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;



public class ConfigManager
{
    private static final Logger LOG = Logger.get(ConfigManager.class);
    private Optional<atherConfig> atherConfig;
    private Optional<DuckDBConfig> duckDBConfig;
    private Optional<DuckDBConnectorConfig> duckDBConnectorConfig;

    private final Map<String, String> configs = new HashMap<>();
    // All configs set by user and config files. It's used to sync with config file.
    private Properties setConfigs = new Properties();
    private final String configFile = System.getProperty("config");
    private final Set<String> requiredReload = new HashSet<>();
    private final Set<String> staticConfigs = new HashSet<>();

    @Inject
    public ConfigManager(
            atherConfig atherConfig,
            DuckDBConfig duckDBConfig,
            DuckDBConnectorConfig duckDBConnectorConfig)
    {
        this.atherConfig = Optional.of(atherConfig);
        this.duckDBConfig = Optional.of(duckDBConfig);
        this.duckDBConnectorConfig = Optional.of(duckDBConnectorConfig);

        initConfig(
                atherConfig,
                duckDBConfig,
                duckDBConnectorConfig);

        try {
            setConfigs.putAll(loadPropertiesFrom(configFile));
        }
        catch (IOException e) {
            throw new atherException(NOT_FOUND, "Config file not found");
        }
    }

    private void initConfig(
            atherConfig atherConfig,
            DuckDBConfig duckDBConfig,
            DuckDBConnectorConfig duckDBConnectorConfig)
    {
        initConfig(atherConfig.ather_DIRECTORY, atherConfig.getatherMDLDirectory().getPath(), false, true);
        initConfig(atherConfig.ather_DATASOURCE_TYPE, Optional.ofNullable(atherConfig.getDataSourceType()).map(Enum::name).orElse(null), true, false);
        initConfig(atherConfig.ather_ENABLE_DYNAMIC_FIELDS, Boolean.toString(atherConfig.getEnableDynamicFields()), false, false);
        initConfig(DUCKDB_MEMORY_LIMIT, duckDBConfig.getMemoryLimit().toString(), true, false);
        initConfig(DUCKDB_HOME_DIRECTORY, duckDBConfig.getHomeDirectory(), true, false);
        initConfig(DUCKDB_TEMP_DIRECTORY, duckDBConfig.getTempDirectory(), true, false);
        initConfig(DUCKDB_MAX_CONCURRENT_TASKS, Integer.toString(duckDBConfig.getMaxConcurrentTasks()), false, true);
        initConfig(DUCKDB_MAX_CACHE_QUERY_TIMEOUT, Long.toString(duckDBConfig.getMaxCacheQueryTimeout()), false, true);
        initConfig(DUCKDB_CACHE_TASK_RETRY_DELAY, Long.toString(duckDBConfig.getCacheTaskRetryDelay()), false, true);
        initConfig(DUCKDB_CONNECTOR_INIT_SQL_PATH, duckDBConnectorConfig.getInitSQLPath(), false, false);
        initConfig(DUCKDB_CONNECTOR_SESSION_SQL_PATH, duckDBConnectorConfig.getSessionSQLPath(), false, false);
    }

    private void initConfig(String key, String value, boolean requiredReload, boolean isStatic)
    {
        configs.put(key, value);

        if (requiredReload) {
            this.requiredReload.add(key);
        }

        if (isStatic) {
            staticConfigs.add(key);
        }
    }

    public <T> T getConfig(Class<T> config)
    {
        if (config == atherConfig.class) {
            return (T) atherConfig.orElseGet(() -> {
                atherConfig result = getatherConfig();
                atherConfig = Optional.of(result);
                return result;
            });
        }
        if (config == DuckDBConfig.class) {
            return (T) duckDBConfig.orElseGet(() -> {
                DuckDBConfig result = getDuckDBConfig();
                duckDBConfig = Optional.of(result);
                return result;
            });
        }
        if (config == DuckDBConnectorConfig.class) {
            return (T) duckDBConnectorConfig.orElseGet(() -> {
                DuckDBConnectorConfig result = getDuckDBConnectorConfig();
                duckDBConnectorConfig = Optional.of(result);
                return result;
            });
        }
        throw new RuntimeException("Unknown config class: " + config.getName());
    }

    private atherConfig getatherConfig()
    {
        atherConfig result = new atherConfig();
        Optional.ofNullable(configs.get(atherConfig.ather_DIRECTORY))
                .ifPresent(directory -> result.setatherMDLDirectory(Paths.get(directory).toFile()));
        result.setDataSourceType(atherConfig.DataSourceType.valueOf(configs.get(atherConfig.ather_DATASOURCE_TYPE).toUpperCase(Locale.ROOT)));
        result.setEnableDynamicFields(Boolean.parseBoolean(configs.get(atherConfig.ather_ENABLE_DYNAMIC_FIELDS)));
        return result;
    }

    private DuckDBConfig getDuckDBConfig()
    {
        DuckDBConfig result = new DuckDBConfig();
        result.setMemoryLimit(DataSize.valueOf(configs.get(DUCKDB_MEMORY_LIMIT)));
        result.setHomeDirectory(configs.get(DUCKDB_HOME_DIRECTORY));
        result.setTempDirectory(configs.get(DUCKDB_TEMP_DIRECTORY));
        result.setMaxConcurrentTasks(Integer.parseInt(configs.get(DUCKDB_MAX_CONCURRENT_TASKS)));
        result.setMaxCacheQueryTimeout(Integer.parseInt(configs.get(DUCKDB_MAX_CACHE_QUERY_TIMEOUT)));
        result.setCacheTaskRetryDelay(Integer.parseInt(configs.get(DUCKDB_CACHE_TASK_RETRY_DELAY)));
        return result;
    }

    private DuckDBConnectorConfig getDuckDBConnectorConfig()
    {
        DuckDBConnectorConfig result = new DuckDBConnectorConfig();
        result.setInitSQLPath(configs.get(DUCKDB_CONNECTOR_INIT_SQL_PATH));
        result.setSessionSQLPath(configs.get(DUCKDB_CONNECTOR_SESSION_SQL_PATH));
        return result;
    }

    public synchronized boolean setConfigs(List<ConfigEntry> configEntries, boolean reset)
    {
        if (reset) {
            reset();
        }

        Map<String, String> update = configEntries.stream()
                .map(entry -> Map.entry(entry.getName(), entry.getValue()))
                .collect(toMap(Map.Entry::getKey, entry -> entry.getValue().trim()));
        update.forEach(this::setConfigInternal);
        boolean needReload = false;
        for (ConfigEntry configEntry : configEntries) {
            needReload |= setConfigInternal(configEntry.getName(), configEntry.getValue());
        }
        resetCache();
        syncFile(update);
        return needReload;
    }

    private boolean setConfigInternal(String key, String value)
    {
        // ignore all static config changes
        if (staticConfigs.contains(key)) {
            return false;
        }

        // Only allow set the config that already exists.
        if (configs.containsKey(key)) {
            configs.put(key, value);
        }
        else {
            throw new atherException(NOT_FOUND, "Config not found: " + key);
        }

        return requiredReload.contains(key);
    }

    private void resetCache()
    {
        atherConfig = Optional.empty();
        duckDBConfig = Optional.empty();
        duckDBConnectorConfig = Optional.empty();
    }

    private void reset()
    {
        configs.clear();
        setConfigs.clear();
        initConfig(
                new atherConfig(),
                new DuckDBConfig(),
                new DuckDBConnectorConfig());
    }

    private void syncFile(Map<String, String> updated)
    {
        try {
            archiveConfigs();
            setConfigs.putAll(updated);
            setConfigs.store(Files.newBufferedWriter(Paths.get(configFile)), "sync with file");
            LOG.info("Syncing config file: " + configFile);
        }
        catch (IOException e) {
            throw new atherException(NOT_FOUND, format("Config file %s not found", configFile), e);
        }
    }

    private void archiveConfigs()
            throws IOException
    {
        Path home = Paths.get(configFile).toFile().getParentFile().toPath();
        File archived = home.resolve(ARCHIVED).toFile();
        if (!archived.exists()) {
            if (!archived.mkdir()) {
                throw new IOException("Cannot create archive folder");
            }
        }
        File archivedFile = Paths.get(configFile).toFile();
        Files.copy(archivedFile.toPath(),
                archived.toPath().resolve(archivedFile.getName() + "." + LocalDateTime.now().format(DateTimeFormatter.ofPattern("uuuuMMddHHmmssnnnn"))));
        LOG.info("Archiving config file: " + archived);
    }

    public List<ConfigEntry> getConfigs()
    {
        return configs.entrySet().stream().map(entry -> new ConfigEntry(entry.getKey(), entry.getValue())).collect(toList());
    }

    public ConfigEntry getConfig(String key)
    {
        if (configs.containsKey(key)) {
            return new ConfigEntry(key, configs.get(key));
        }
        throw new atherException(NOT_FOUND, "Config not found: " + key);
    }

    public static class ConfigEntry
    {
        public static ConfigEntry configEntry(String name, String value)
        {
            return new ConfigEntry(name, value);
        }

        private final String name;
        private final String value;

        @JsonCreator
        public ConfigEntry(
                @JsonProperty("name") String name,
                @JsonProperty("value") String value)
        {
            this.name = name;
            this.value = value == null || value.isEmpty() ? null : value;
        }

        @JsonProperty
        public String getName()
        {
            return name;
        }

        @JsonProperty
        public String getValue()
        {
            return value;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ConfigEntry that = (ConfigEntry) o;
            return Objects.equals(name, that.name) && Objects.equals(value, that.value);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(name, value);
        }

        @Override
        public String toString()
        {
            return toStringHelper(this)
                    .add("name", name)
                    .add("value", value)
                    .toString();
        }
    }
}