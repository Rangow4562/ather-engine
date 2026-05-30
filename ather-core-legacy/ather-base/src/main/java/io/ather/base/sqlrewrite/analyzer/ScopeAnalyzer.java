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

package io.ather.base.sqlrewrite.analyzer;

import io.trino.sql.tree.AliasedRelation;
import io.trino.sql.tree.DefaultTraversalVisitor;
import io.trino.sql.tree.Node;
import io.trino.sql.tree.QualifiedName;
import io.trino.sql.tree.Table;
import io.trino.sql.tree.TableSubquery;

import io.ather.base.atherMDL;
import io.ather.base.CatalogSchemaTableName;
import io.ather.base.SessionContext;
import io.ather.base.sqlrewrite.Utils;



public class ScopeAnalyzer
{
    private ScopeAnalyzer() {}

    public static ScopeAnalysis analyze(atherMDL atherMDL, Node node, SessionContext sessionContext)
    {
        ScopeAnalysis analysis = new ScopeAnalysis();
        Visitor visitor = new Visitor(atherMDL, analysis, sessionContext);
        visitor.process(node, null);
        return analysis;
    }

    static class Visitor
            extends DefaultTraversalVisitor<Void>
    {
        private final atherMDL atherMDL;
        private final ScopeAnalysis analysis;
        private final SessionContext sessionContext;

        public Visitor(atherMDL atherMDL, ScopeAnalysis analysis, SessionContext sessionContext)
        {
            this.atherMDL = atherMDL;
            this.analysis = analysis;
            this.sessionContext = sessionContext;
        }

        @Override
        protected Void visitTable(Table node, Void context)
        {
            if (isBelongToather(node.getName())) {
                analysis.addUsedatherObject(node);
            }
            return null;
        }

        @Override
        protected Void visitTableSubquery(TableSubquery node, Void context)
        {
            return null;
        }

        @Override
        protected Void visitAliasedRelation(AliasedRelation node, Void context)
        {
            analysis.addAliasedNode(node.getRelation(), node.getAlias().getValue());
            return super.visitAliasedRelation(node, context);
        }

        private boolean isBelongToather(QualifiedName atherObjectName)
        {
            CatalogSchemaTableName catalogSchemaTableName = Utils.toCatalogSchemaTableName(sessionContext, atherObjectName);
            String tableName = catalogSchemaTableName.getSchemaTableName().getTableName();
            return catalogSchemaTableName.getCatalogName().equals(atherMDL.getCatalog())
                    && catalogSchemaTableName.getSchemaTableName().getSchemaName().equals(atherMDL.getSchema())
                    && (atherMDL.listModels().stream().anyMatch(model -> model.getName().equals(tableName))
                    || atherMDL.listMetrics().stream().anyMatch(metric -> metric.getName().equals(tableName)));
        }
    }
}