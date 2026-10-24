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

package io.ather.testing.duckdb;

import com.google.common.collect.ImmutableMap;
import io.ather.testing.TestingatherServer;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static io.ather.base.client.duckdb.DuckDBConnectorConfig.DUCKDB_CONNECTOR_INIT_SQL_PATH;
import static io.ather.base.client.duckdb.DuckDBConnectorConfig.DUCKDB_CONNECTOR_SESSION_SQL_PATH;
import static io.ather.base.config.atherConfig.DataSourceType.DUCKDB;
import static io.ather.base.config.atherConfig.ather_DATASOURCE_TYPE;
import static org.assertj.core.api.Assertions.assertThatCode;

public class TestStartWithDuckDB
{
    @Test
    public void testStart()
            throws Exception
    {
        Path mdlDir = Files.createTempDirectory("ather-mdl");
        String initSqlPath = mdlDir.resolve("init.sql").toAbsolutePath().toString();
        Files.writeString(Path.of(initSqlPath), "CREATE SCHEMA tpch;");
        String sessionSqlPath = mdlDir.resolve("session.sql").toAbsolutePath().toString();
        Files.writeString(Path.of(sessionSqlPath), "SET s3_region = 'us-east-2';");

        assertServerStart(ImmutableMap.<String, String>builder()
                .put(ather_DATASOURCE_TYPE, DUCKDB.name())
                .put(DUCKDB_CONNECTOR_INIT_SQL_PATH, initSqlPath)
                .put(DUCKDB_CONNECTOR_SESSION_SQL_PATH, sessionSqlPath)
                .build());
    }

    @Test
    public void testStartWithIllegalInitSql()
            throws Exception
    {
        Path mdlDir = Files.createTempDirectory("ather-mdl");
        String initSqlPath = mdlDir.resolve("init.sql").toAbsolutePath().toString();
        Files.writeString(Path.of(initSqlPath), "Illegal SQL");
        String sessionSqlPath = mdlDir.resolve("session.sql").toAbsolutePath().toString();
        Files.writeString(Path.of(sessionSqlPath), "Illegal SQL");

        assertServerStart(ImmutableMap.<String, String>builder()
                .put(ather_DATASOURCE_TYPE, DUCKDB.name())
                .put(DUCKDB_CONNECTOR_INIT_SQL_PATH, initSqlPath)
                .put(DUCKDB_CONNECTOR_SESSION_SQL_PATH, sessionSqlPath)
                .build());
    }

    private void assertServerStart(Map<String, String> props)
    {
        assertThatCode(() -> TestingatherServer.builder().setRequiredConfigs(props).build())
                .doesNotThrowAnyException();
    }
}
