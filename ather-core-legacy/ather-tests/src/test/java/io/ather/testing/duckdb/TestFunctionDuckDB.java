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
import io.ather.base.dto.Manifest;
import io.ather.testing.AbstractFunctionTest;
import io.ather.testing.TestingatherServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.ather.base.config.atherConfig.DataSourceType.DUCKDB;
import static io.ather.base.config.atherConfig.ather_DATASOURCE_TYPE;
import static io.ather.base.config.atherConfig.ather_DIRECTORY;

public class TestFunctionDuckDB
        extends AbstractFunctionTest
{
    @Override
    protected TestingatherServer createatherServer()
    {
        Path mdlDir;

        try {
            mdlDir = Files.createTempDirectory("athermdls");
            Path atherMDLFilePath = mdlDir.resolve("athermdl.json");
            Files.write(atherMDLFilePath, MANIFEST_JSON_CODEC.toJsonBytes(Manifest.builder().setCatalog("ather").setSchema("test").build()));
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
}
