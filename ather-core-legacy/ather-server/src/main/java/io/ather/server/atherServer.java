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

package io.ather.server;

import com.google.common.collect.ImmutableList;
import com.google.inject.Injector;
import com.google.inject.Module;
import io.airlift.http.server.HttpServerModule;
import io.airlift.jaxrs.JaxrsModule;
import io.airlift.json.JsonModule;
import io.airlift.node.NodeModule;
import io.ather.main.PreviewService;
import io.ather.main.atherModule;
import io.ather.main.server.Server;
import io.ather.server.module.DuckDBConnectorModule;
import io.ather.server.module.MainModule;
import io.ather.server.module.OpenTelemetryModule;
import io.ather.server.module.WebModule;

public class atherServer
        extends Server
{
    public static void main(String[] args)
    {
        new atherServer().start();
    }

    @Override
    protected Iterable<? extends Module> getAdditionalModules()
    {
        return ImmutableList.of(
                new NodeModule(),
                new HttpServerModule(),
                new JsonModule(),
                new JaxrsModule(),
                new OpenTelemetryModule(),
                new MainModule(),
                new DuckDBConnectorModule(),
                new atherModule(),
                new WebModule());
    }

    @Override
    protected void configure(Injector injector)
    {
        warmUp(injector);
    }

    private void warmUp(Injector injector)
    {
        injector.getInstance(PreviewService.class).warmUp();
    }
}
