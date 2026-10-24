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

package io.ather.main.metadata;

import com.google.inject.Inject;
import io.ather.base.Column;
import io.ather.base.ConnectorRecordIterator;
import io.ather.base.Parameter;
import io.ather.base.config.ConfigManager;
import io.ather.base.config.atherConfig;
import io.ather.main.connector.duckdb.DuckDBMetadata;

import java.util.List;

import static java.util.Objects.requireNonNull;

public final class MetadataManager
        implements Metadata
{
    private final DuckDBMetadata duckDBMetadata;

    private atherConfig.DataSourceType dataSourceType;
    private Metadata delegate;

    @Inject
    public MetadataManager(
            ConfigManager configManager,
            DuckDBMetadata duckDBMetadata)
    {
        this.duckDBMetadata = requireNonNull(duckDBMetadata, "duckDBMetadata is null");
        this.dataSourceType = requireNonNull(configManager.getConfig(atherConfig.class).getDataSourceType(), "dataSourceType is null");
        changeDelegate(dataSourceType);
    }

    private synchronized void changeDelegate(atherConfig.DataSourceType dataSourceType)
    {
        switch (dataSourceType) {
            case DUCKDB:
                delegate = duckDBMetadata;
                break;
            default:
                throw new UnsupportedOperationException("Unsupported data source type: " + dataSourceType);
        }
    }

    @Override
    public void directDDL(String sql)
    {
        delegate.directDDL(sql);
    }

    @Override
    public ConnectorRecordIterator directQuery(String sql, List<Parameter> parameters)
    {
        return delegate.directQuery(sql, parameters);
    }

    @Override
    public List<Column> describeQuery(String sql, List<Parameter> parameters)
    {
        return delegate.describeQuery(sql, parameters);
    }

    @Override
    public void reload()
    {
        delegate.reload();
    }

    @Override
    public void close()
    {
        delegate.close();
    }
}
