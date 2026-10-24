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

package io.ather.testing;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Key;
import io.ather.base.client.duckdb.DuckDBConfig;
import io.ather.base.config.ConfigManager;
import io.ather.base.config.atherConfig;
import io.ather.base.dto.Manifest;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static io.ather.base.client.duckdb.DuckDBConfig.DUCKDB_CACHE_TASK_RETRY_DELAY;
import static io.ather.base.client.duckdb.DuckDBConfig.DUCKDB_MAX_CACHE_QUERY_TIMEOUT;
import static io.ather.base.client.duckdb.DuckDBConfig.DUCKDB_MAX_CONCURRENT_TASKS;
import static io.ather.base.client.duckdb.DuckDBConfig.DUCKDB_MEMORY_LIMIT;
import static io.ather.base.config.ConfigManager.ConfigEntry.configEntry;
import static io.ather.base.config.atherConfig.DataSourceType.DUCKDB;
import static io.ather.base.config.atherConfig.ather_DATASOURCE_TYPE;
import static io.ather.base.config.atherConfig.ather_DIRECTORY;
import static io.ather.testing.AbstractTestFramework.withDefaultCatalogSchema;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Every test will affect each other, so we need to run them in single thread.
 */
@Test(singleThreaded = true)
public class TestConfigResource
        extends RequireatherServer
{
    private Path mdlDir;

    @Override
    protected TestingatherServer createatherServer()
    {
        Manifest manifest = withDefaultCatalogSchema()
                .build();

        try {
            mdlDir = Files.createTempDirectory("athermdls");
            Path atherMDLFilePath = mdlDir.resolve("athermdl.json");
            Files.write(atherMDLFilePath, MANIFEST_JSON_CODEC.toJsonBytes(manifest));
        }
        catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        ImmutableMap.Builder<String, String> properties = ImmutableMap.<String, String>builder()
                .put(ather_DIRECTORY, mdlDir.toAbsolutePath().toString())
                .put(ather_DATASOURCE_TYPE, DUCKDB.name());

        return TestingatherServer.builder()
                .setRequiredConfigs(properties.build())
                .build();
    }

    @Test
    public void testGetConfigs()
    {
        assertThat(getConfigs().size()).isGreaterThan(2);
        assertThat(getConfig(ather_DIRECTORY)).isEqualTo(configEntry(ather_DIRECTORY, mdlDir.toAbsolutePath().toString()));
        assertThat(getConfig(ather_DATASOURCE_TYPE)).isEqualTo(configEntry(ather_DATASOURCE_TYPE, DUCKDB.name()));

        assertThatThrownBy(() -> getConfig("notfound"))
                .hasMessageFindingMatch(".*404 Not Found.*");
        assertThatThrownBy(() -> getConfig(null))
                .hasMessageFindingMatch(".*404 Not Found.*");
    }

    @Test
    public void testuUpdateConfigs()
    {
        patchConfig(List.of(configEntry(DUCKDB_MEMORY_LIMIT, "2GB")));
        assertThat(getConfig(ather_DATASOURCE_TYPE)).isEqualTo(configEntry(ather_DATASOURCE_TYPE, DUCKDB.name()));
        assertThat(getConfig(ather_DIRECTORY)).isEqualTo(configEntry(ather_DIRECTORY, mdlDir.toAbsolutePath().toString()));
        assertThat(getConfig(DUCKDB_MEMORY_LIMIT)).isEqualTo(configEntry(DUCKDB_MEMORY_LIMIT, "2GB"));

        resetConfig();
        DuckDBConfig duckDBConfig = new DuckDBConfig();
        atherConfig atherConfig = new atherConfig();
        assertThat(getConfig(ather_DATASOURCE_TYPE)).isEqualTo(configEntry(ather_DATASOURCE_TYPE, atherConfig.getDataSourceType().name()));
        assertThat(getConfig(ather_DIRECTORY)).isEqualTo(configEntry(ather_DIRECTORY, atherConfig.getatherMDLDirectory().getPath()));
        assertThat(getConfig(DUCKDB_MEMORY_LIMIT)).isEqualTo(configEntry(DUCKDB_MEMORY_LIMIT, duckDBConfig.getMemoryLimit().toString()));
    }

    @Test
    public void testDataSourceTypeCaseInsensitive()
    {
        patchConfig(List.of(configEntry(ather_DATASOURCE_TYPE, "DuCkDb")));
        assertThat(getConfig(ather_DATASOURCE_TYPE)).isEqualTo(configEntry(ather_DATASOURCE_TYPE, "DuCkDb"));
        atherConfig atherConfig = server().getInstance(Key.get(ConfigManager.class)).getConfig(atherConfig.class);
        assertThat(atherConfig.getDataSourceType()).isEqualTo(DUCKDB);
        patchConfig(List.of(configEntry(ather_DATASOURCE_TYPE, DUCKDB.name())));
        assertThat(getConfig(ather_DATASOURCE_TYPE)).isEqualTo(configEntry(ather_DATASOURCE_TYPE, DUCKDB.name()));
    }

    @Test
    public void testStaticConfigWontBeChanged()
    {
        patchConfig(List.of(
                configEntry(ather_DIRECTORY, "fake"),
                configEntry(DUCKDB_MAX_CONCURRENT_TASKS, "100"),
                configEntry(DUCKDB_MAX_CACHE_QUERY_TIMEOUT, "1000"),
                configEntry(DUCKDB_CACHE_TASK_RETRY_DELAY, "1000")));

        DuckDBConfig duckDBConfig = new DuckDBConfig();
        assertThat(getConfig(ather_DIRECTORY)).isEqualTo(configEntry(ather_DIRECTORY, mdlDir.toAbsolutePath().toString()));
        assertThat(getConfig(DUCKDB_MAX_CONCURRENT_TASKS)).isEqualTo(configEntry(DUCKDB_MAX_CONCURRENT_TASKS, String.valueOf(duckDBConfig.getMaxConcurrentTasks())));
        assertThat(getConfig(DUCKDB_MAX_CACHE_QUERY_TIMEOUT)).isEqualTo(configEntry(DUCKDB_MAX_CACHE_QUERY_TIMEOUT, String.valueOf(duckDBConfig.getMaxCacheQueryTimeout())));
        assertThat(getConfig(DUCKDB_CACHE_TASK_RETRY_DELAY)).isEqualTo(configEntry(DUCKDB_CACHE_TASK_RETRY_DELAY, String.valueOf(duckDBConfig.getCacheTaskRetryDelay())));
    }
}
