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

package io.ather.base.sqlrewrite;

import java.util.Optional;
import java.util.Set;

import io.trino.sql.tree.Query;

import io.ather.base.atherMDL;
import io.ather.base.AnalyzedMDL;
import io.ather.base.SessionContext;
import io.ather.base.dto.CumulativeMetric;
import io.ather.base.dto.Metric;
import io.ather.base.dto.Model;
import io.ather.base.dto.View;



public interface QueryDescriptor
{
    String getName();

    Set<String> getRequiredObjects();

    Query getQuery();

    static QueryDescriptor of(String name, AnalyzedMDL analyzedMDL, SessionContext sessionContext)
    {
        atherMDL mdl = analyzedMDL.getatherMDL();
        Optional<Model> model = mdl.getModel(name);
        if (model.isPresent()) {
            return RelationInfo.get(model.get(), mdl);
        }
        Optional<Metric> metric = mdl.getMetric(name);
        if (metric.isPresent()) {
            return RelationInfo.get(metric.get(), mdl);
        }
        Optional<CumulativeMetric> cumulativeMetric = mdl.getCumulativeMetric(name);
        if (cumulativeMetric.isPresent()) {
            return CumulativeMetricInfo.get(cumulativeMetric.get(), mdl);
        }
        Optional<View> view = mdl.getView(name);
        if (view.isPresent()) {
            return ViewInfo.get(view.get(), analyzedMDL, sessionContext);
        }
        if (name.equals(DateSpineInfo.NAME)) {
            return DateSpineInfo.get(mdl.getDateSpine());
        }
        throw new IllegalArgumentException(name + " not found in ather mdl");
    }
}