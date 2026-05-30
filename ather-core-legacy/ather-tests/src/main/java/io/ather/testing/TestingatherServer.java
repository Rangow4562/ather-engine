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

import com.google.common.collect.ImmutableList;
import com.google.common.io.Closer;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import io.airlift.bootstrap.Bootstrap;
import io.airlift.bootstrap.LifeCycleManager;
import io.airlift.http.server.testing.TestingHttpServer;
import io.airlift.http.server.testing.TestingHttpServerModule;
import io.airlift.jaxrs.JaxrsModule;
import io.airlift.json.JsonModule;
import io.airlift.node.NodeModule;
import io.ather.main.atherModule;
import io.ather.main.connector.duckdb.DuckDBMetadata;
import io.ather.server.module.DuckDBConnectorModule;
import io.ather.server.module.MainModule;
import io.ather.server.module.WebModule;

import java.io.Closeable;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class TestingatherServer
        implements Closeable
{
    private static final String HTTP_SERVER_PORT = "http-server.http.port";
    private static final String NODE_ENVIRONMENT = "node.environment";
    private final Injector injector;
    private final Closer closer = Closer.create();

    public static Builder builder()
    {
        return new Builder();
    }

    private TestingatherServer(Map<String, String> requiredConfigs)
            throws IOException
    {
        Map<String, String> requiredConfigProps = new HashMap<>();
        requiredConfigProps.put(HTTP_SERVER_PORT, String.valueOf(randomPort()));
        requiredConfigProps.put(NODE_ENVIRONMENT, "test");

        requiredConfigProps.putAll(requiredConfigs);

        Bootstrap app = new Bootstrap(ImmutableList.<Module>of(
                new TestingHttpServerModule(),
                new NodeModule(),
                new JsonModule(),
                new JaxrsModule(),
                new MainModule(),
                new DuckDBConnectorModule(),
                new atherModule(),
                new WebModule()));

        injector = app
                .doNotInitializeLogging()
                .setRequiredConfigurationProperties(requiredConfigProps)
                .quiet()
                .initialize();

        closer.register(() -> injector.getInstance(Key.get(DuckDBMetadata.class)).close());
        closer.register(() -> injector.getInstance(LifeCycleManager.class).stop());
    }

    public URI getHttpServerBasedUrl()
    {
        return injector.getInstance(TestingHttpServer.class).getBaseUrl();
    }

    public <T> T getInstance(Key<T> key)
    {
        return injector.getInstance(key);
    }

    @Override
    public void close()
            throws IOException
    {
        closer.close();
    }

    private static int randomPort()
            throws IOException
    {
        // ServerSocket(0) results in availability of a free random port
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            return serverSocket.getLocalPort();
        }
    }

    public static class Builder
    {
        private Map<String, String> configs = new HashMap<>();

        public Builder setRequireConfig(String key, String value)
        {
            this.configs.put(key, value);
            return this;
        }

        public Builder setRequiredConfigs(Map<String, String> configs)
        {
            this.configs = configs;
            return this;
        }

        public TestingatherServer build()
        {
            try {
                Path config = Files.createTempDirectory("config").resolve("config.properties");
                System.setProperty("config", config.toString());
                Properties properties = new Properties();
                properties.putAll(configs);
                properties.store(Files.newBufferedWriter(config), "TestingatherServer");
                return new TestingatherServer(configs);
            }
            catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }
}
