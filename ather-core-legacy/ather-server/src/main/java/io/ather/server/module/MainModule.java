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

package io.ather.server.module;

import com.google.inject.Binder;
import com.google.inject.Scopes;
import io.airlift.configuration.AbstractConfigurationAwareModule;
import io.ather.base.config.BigQueryConfig;
import io.ather.base.config.ConfigManager;
import io.ather.base.config.DuckdbS3StyleStorageConfig;
import io.ather.base.config.PostgresConfig;
import io.ather.base.config.PostgresWireProtocolConfig;
import io.ather.base.config.SQLGlotConfig;
import io.ather.base.config.SnowflakeConfig;
import io.ather.base.sql.SqlConverter;
import io.ather.main.metadata.Metadata;
import io.ather.main.metadata.MetadataManager;
import io.ather.main.sql.SqlConverterManager;

import static io.airlift.configuration.ConfigBinder.configBinder;

public class MainModule
        extends AbstractConfigurationAwareModule

{
    @Override
    protected void setup(Binder binder)
    {
        // backwards compatibility
        configBinder(binder).bindConfig(BigQueryConfig.class);
        configBinder(binder).bindConfig(PostgresConfig.class);
        configBinder(binder).bindConfig(DuckdbS3StyleStorageConfig.class);
        configBinder(binder).bindConfig(PostgresWireProtocolConfig.class);
        configBinder(binder).bindConfig(SnowflakeConfig.class);
        configBinder(binder).bindConfig(SQLGlotConfig.class);

        binder.bind(Metadata.class).to(MetadataManager.class).in(Scopes.SINGLETON);
        binder.bind(SqlConverter.class).to(SqlConverterManager.class).in(Scopes.SINGLETON);
        binder.bind(ConfigManager.class).in(Scopes.SINGLETON);
    }
}
