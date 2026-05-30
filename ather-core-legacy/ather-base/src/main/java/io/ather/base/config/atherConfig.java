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
import java.nio.file.Paths;

import io.airlift.configuration.Config;

import jakarta.validation.constraints.NotNull;



public class atherConfig
{
    public static final String ather_DIRECTORY = "ather.directory";
    public static final String ather_DATASOURCE_TYPE = "ather.datasource.type";
    public static final String ather_ENABLE_DYNAMIC_FIELDS = "ather.experimental-enable-dynamic-fields";

    public enum DataSourceType
    {
        @Deprecated
        BIGQUERY,
        @Deprecated
        POSTGRES,
        DUCKDB,
        @Deprecated
        SNOWFLAKE
    }

    private File atherMDLDirectory = Paths.get("etc/mdl").toFile();
    private DataSourceType dataSourceType = DataSourceType.DUCKDB;
    private boolean enableDynamicFields = true;

    @NotNull
    public File getatherMDLDirectory()
    {
        return atherMDLDirectory;
    }

    @Config(ather_DIRECTORY)
    public atherConfig setatherMDLDirectory(File atherMDLDirectory)
    {
        this.atherMDLDirectory = atherMDLDirectory;
        return this;
    }

    public DataSourceType getDataSourceType()
    {
        return dataSourceType;
    }

    @Config(ather_DATASOURCE_TYPE)
    public atherConfig setDataSourceType(DataSourceType dataSourceType)
    {
        this.dataSourceType = dataSourceType;
        return this;
    }

    public boolean getEnableDynamicFields()
    {
        return enableDynamicFields;
    }

    @Config(ather_ENABLE_DYNAMIC_FIELDS)
    public atherConfig setEnableDynamicFields(boolean enableDynamicFields)
    {
        this.enableDynamicFields = enableDynamicFields;
        return this;
    }
}