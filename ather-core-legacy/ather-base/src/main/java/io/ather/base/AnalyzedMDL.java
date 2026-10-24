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

package io.ather.base;

import javax.annotation.Nullable;

import io.ather.base.sqlrewrite.atherDataLineage;

import static java.util.Objects.requireNonNull;



public class AnalyzedMDL
{
    private final atherMDL atherMDL;
    private final atherDataLineage atherDataLineage;
    private final String version;

    public AnalyzedMDL(atherMDL atherMDL, @Nullable String version)
    {
        this.atherMDL = requireNonNull(atherMDL);
        this.atherDataLineage = atherDataLineage.analyze(atherMDL);
        this.version = version;
    }

    public AnalyzedMDL(atherMDL atherMDL, atherDataLineage atherDataLineage, @Nullable String version)
    {
        this.atherMDL = requireNonNull(atherMDL);
        this.atherDataLineage = requireNonNull(atherDataLineage);
        this.version = version;
    }

    public atherMDL getatherMDL()
    {
        return atherMDL;
    }

    public atherDataLineage getatherDataLineage()
    {
        return atherDataLineage;
    }

    @Nullable
    public String getVersion()
    {
        return version;
    }
}