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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import io.trino.sql.tree.DereferenceExpression;
import io.trino.sql.tree.Node;
import io.trino.sql.tree.QualifiedName;
import io.trino.sql.tree.Statement;
import io.trino.sql.tree.Table;
import io.trino.sql.tree.WithQuery;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.jgrapht.graph.GraphCycleProhibitedException;

import io.ather.base.atherMDL;
import io.ather.base.AnalyzedMDL;
import io.ather.base.CatalogSchemaTableName;
import io.ather.base.SessionContext;
import io.ather.base.Utils;
import io.ather.base.dto.Column;
import io.ather.base.dto.CumulativeMetric;
import io.ather.base.dto.Metric;
import io.ather.base.dto.Model;
import io.ather.base.dto.Relationable;
import io.ather.base.sqlrewrite.analyzer.Analysis;
import io.ather.base.sqlrewrite.analyzer.Scope;
import io.ather.base.sqlrewrite.analyzer.StatementAnalyzer;

import static com.google.common.base.Strings.nullToEmpty;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static io.ather.base.sqlrewrite.Utils.toCatalogSchemaTableName;
import static java.lang.String.format;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;



public class atherSqlRewrite
        implements atherRule
{
    public static final atherSqlRewrite ather_SQL_REWRITE = new atherSqlRewrite();

    private atherSqlRewrite() {}

    private static LinkedHashMap<String, Set<String>> getTableRequiredFields(atherDataLineage dataLineage, Analysis analysis)
    {
        List<QualifiedName> collectedColumns = analysis.getCollectedColumns().asMap().entrySet().stream()
                .map(e ->
                        e.getValue().stream()
                                .map(columnName -> QualifiedName.of(e.getKey().getSchemaTableName().getTableName(), columnName))
                                .collect(toImmutableList()))
                .flatMap(List::stream)
                .collect(toImmutableList());

        return dataLineage.getRequiredFields(collectedColumns);
    }

    @Override
    public Statement apply(Statement root, SessionContext sessionContext, AnalyzedMDL analyzedMDL)
    {
        Analysis analysis = new Analysis(root);
        StatementAnalyzer.analyze(analysis, root, sessionContext, analyzedMDL.getatherMDL());
        return apply(root, sessionContext, analysis, analyzedMDL);
    }

    @Override
    public Statement apply(Statement root, SessionContext sessionContext, Analysis analysis, AnalyzedMDL analyzedMDL)
    {
        atherMDL atherMDL = analyzedMDL.getatherMDL();
        Set<QueryDescriptor> allDescriptors;
        // TODO: Currently DynamicCalculatedField is a experimental feature, and buggy. After all issues are solved,
        //  we should always enable this setting.
        if (sessionContext.isEnableDynamicField()) {
            Set<CatalogSchemaTableName> visitedTables = analysis.getTables().stream().filter(table -> atherMDL.getView(table).isEmpty()).collect(toSet());
            LinkedHashMap<String, Set<String>> tableRequiredFields = getTableRequiredFields(analyzedMDL.getatherDataLineage(), analysis).entrySet().stream()
                    .filter(e -> atherMDL.getView(e.getKey()).isEmpty())
                    .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));

            // Some node be applied `count(*)` which won't be collected but its source is required.
            analysis.getRequiredSourceNodes().forEach(node -> {
                Scope scope = analysis.getScope(node);
                if (tryGetTableName(node).flatMap(name -> scope.getNamedQuery(name.toString())).isEmpty()) {
                    String tableName = analysis.getSourceNodeNames(node).map(QualifiedName::toString)
                            .orElseThrow(() -> new IllegalArgumentException(format("source node name not found: %s", node)));
                    if (!tableRequiredFields.containsKey(tableName)) {
                        Relationable relationable = atherMDL.getRelationable(tableName)
                                .orElseThrow(() -> new IllegalArgumentException(format("dataset not found: %s", tableName)));
                        tableRequiredFields.put(tableName, relationable.getColumns().stream().filter(column -> !column.isCalculated()).map(Column::getName).collect(toImmutableSet()));
                    }
                }
            });

            ImmutableList.Builder<QueryDescriptor> descriptorsBuilder = ImmutableList.builder();
            tableRequiredFields.forEach((name, value) -> {
                addDescriptor(name, value, atherMDL, descriptorsBuilder);
                visitedTables.remove(toCatalogSchemaTableName(sessionContext, QualifiedName.of(name)));
            });

            List<WithQuery> withQueries = new ArrayList<>();
            // add date spine if needed
            if (tableRequiredFields.keySet().stream()
                    .map(atherMDL::getCumulativeMetric)
                    .anyMatch(Optional::isPresent)) {
                withQueries.add(WithRewriter.getWithQuery(DateSpineInfo.get(atherMDL.getDateSpine())));
            }
            descriptorsBuilder.build().forEach(queryDescriptor -> withQueries.add(WithRewriter.getWithQuery(queryDescriptor)));

            // If a selected table lacks any required fields, create a dummy with query for it.
            visitedTables.stream().filter(table -> atherMDL.isObjectExist(table.getSchemaTableName().getTableName()))
                    .forEach(dummy -> withQueries.add(WithRewriter.getWithQuery(new DummyInfo(dummy.getSchemaTableName().getTableName()))));

            Node rewriteWith = new WithRewriter(withQueries).process(root);
            return (Statement) new Rewriter(atherMDL, analysis).process(rewriteWith);
        }
        else {
            Set<QueryDescriptor> modelDescriptors = analysis.getModels().stream().map(model -> RelationInfo.get(model, atherMDL)).collect(toSet());
            Set<QueryDescriptor> metricDescriptors = analysis.getMetrics().stream().map(metric -> RelationInfo.get(metric, atherMDL)).collect(toSet());
            Set<QueryDescriptor> cumulativeMetricDescriptors = analysis.getCumulativeMetrics().stream().map(metric -> CumulativeMetricInfo.get(metric, atherMDL)).collect(toSet());
            allDescriptors = ImmutableSet.<QueryDescriptor>builder()
                    .addAll(modelDescriptors)
                    .addAll(metricDescriptors)
                    .addAll(cumulativeMetricDescriptors)
                    .build();
            return apply(root, sessionContext, analysis, analyzedMDL, allDescriptors);
        }
    }

    private Optional<QualifiedName> tryGetTableName(Node node)
    {
        if (node instanceof Table) {
            return Optional.of(((Table) node).getName());
        }
        return Optional.empty();
    }

    private void addDescriptor(String name, Set<String> requiredFields, atherMDL atherMDL, ImmutableList.Builder<QueryDescriptor> descriptorsBuilder)
    {
        if (atherMDL.getModel(name).isPresent()) {
            Model model = atherMDL.getModel(name).get();
            descriptorsBuilder.add(RelationInfo.get(model, atherMDL, requiredFields));
        }
        else if (atherMDL.getMetric(name).isPresent()) {
            Metric metric = atherMDL.getMetric(name).get();
            descriptorsBuilder.add(RelationInfo.get(metric, atherMDL, requiredFields));
        }
        else if (atherMDL.getCumulativeMetric(name).isPresent()) {
            CumulativeMetric cumulativeMetric = atherMDL.getCumulativeMetric(name).get();
            descriptorsBuilder.add(CumulativeMetricInfo.get(cumulativeMetric, atherMDL));
        }
        // If the table is not found in mdl, it could be a remote table or a CTE.
    }

    private void addDescriptor(String name, atherMDL atherMDL, ImmutableList.Builder<QueryDescriptor> descriptorsBuilder)
    {
        if (atherMDL.getModel(name).isPresent()) {
            Model model = atherMDL.getModel(name).get();
            descriptorsBuilder.add(RelationInfo.get(model, atherMDL));
        }
        else if (atherMDL.getMetric(name).isPresent()) {
            Metric metric = atherMDL.getMetric(name).get();
            descriptorsBuilder.add(RelationInfo.get(metric, atherMDL));
        }
        else if (atherMDL.getCumulativeMetric(name).isPresent()) {
            CumulativeMetric cumulativeMetric = atherMDL.getCumulativeMetric(name).get();
            descriptorsBuilder.add(CumulativeMetricInfo.get(cumulativeMetric, atherMDL));
        }
        // If the table is not found in mdl, it could be a remote table or a CTE.
    }

    private Statement apply(
            Statement root,
            SessionContext sessionContext,
            Analysis analysis,
            AnalyzedMDL analyzedMDL,
            Set<QueryDescriptor> allDescriptors)
    {
        DirectedAcyclicGraph<String, Object> graph = new DirectedAcyclicGraph<>(Object.class);
        Set<QueryDescriptor> requiredQueryDescriptors = new HashSet<>();
        // add to graph
        allDescriptors.forEach(queryDescriptor -> addSqlDescriptorToGraph(queryDescriptor, graph, analyzedMDL, requiredQueryDescriptors, sessionContext));

        Map<String, QueryDescriptor> descriptorMap = new HashMap<>();
        allDescriptors.forEach(queryDescriptor -> descriptorMap.put(queryDescriptor.getName(), queryDescriptor));
        requiredQueryDescriptors.forEach(queryDescriptor -> descriptorMap.put(queryDescriptor.getName(), queryDescriptor));

        List<WithQuery> withQueries = new ArrayList<>();
        graph.iterator().forEachRemaining(objectName -> {
            QueryDescriptor queryDescriptor = descriptorMap.get(objectName);
            Utils.checkArgument(queryDescriptor != null, objectName + " not found in query descriptors");
            withQueries.add(WithRewriter.getWithQuery(queryDescriptor));
        });

        Node rewriteWith = new WithRewriter(withQueries).process(root);
        return (Statement) new Rewriter(analyzedMDL.getatherMDL(), analysis).process(rewriteWith);
    }

    private static void addSqlDescriptorToGraph(
            QueryDescriptor queryDescriptor,
            DirectedAcyclicGraph<String, Object> graph,
            AnalyzedMDL analyzedMDL,
            Set<QueryDescriptor> queryDescriptors,
            SessionContext sessionContext)
    {
        // add vertex
        graph.addVertex(queryDescriptor.getName());
        queryDescriptor.getRequiredObjects().forEach(graph::addVertex);

        //add edge
        try {
            queryDescriptor.getRequiredObjects().forEach(modelName ->
                    graph.addEdge(modelName, queryDescriptor.getName()));
        }
        catch (GraphCycleProhibitedException ex) {
            throw new IllegalArgumentException("found cycle in models", ex);
        }

        // add required models to graph
        for (String objectName : queryDescriptor.getRequiredObjects()) {
            QueryDescriptor descriptor = QueryDescriptor.of(objectName, analyzedMDL, sessionContext);
            queryDescriptors.add(descriptor);
            addSqlDescriptorToGraph(descriptor, graph, analyzedMDL, queryDescriptors, sessionContext);
        }
    }

    private static class Rewriter
            extends BaseRewriter<Void>
    {
        private final atherMDL atherMDL;
        private final Analysis analysis;

        Rewriter(atherMDL atherMDL, Analysis analysis)
        {
            this.analysis = analysis;
            this.atherMDL = atherMDL;
        }

        @Override
        protected Node visitTable(Table node, Void context)
        {
            Node result = node;
            if (analysis.getSourceNodeNames(node).isPresent()) {
                result = applyModelRule(node);
            }
            return result;
        }

        // remove catalog schema from expression if exist since all tables are in with cte
        @Override
        protected Node visitDereferenceExpression(DereferenceExpression dereferenceExpression, Void context)
        {
            QualifiedName qualifiedName = DereferenceExpression.getQualifiedName(dereferenceExpression);
            if (qualifiedName != null && !nullToEmpty(atherMDL.getCatalog()).isEmpty() && !nullToEmpty(atherMDL.getSchema()).isEmpty()) {
                if (qualifiedName.hasPrefix(QualifiedName.of(atherMDL.getCatalog(), atherMDL.getSchema()))) {
                    return DereferenceExpression.from(
                            QualifiedName.of(qualifiedName.getOriginalParts().subList(2, qualifiedName.getOriginalParts().size())));
                }
                if (qualifiedName.hasPrefix(QualifiedName.of(atherMDL.getSchema()))) {
                    return DereferenceExpression.from(
                            QualifiedName.of(qualifiedName.getOriginalParts().subList(1, qualifiedName.getOriginalParts().size())));
                }
            }
            return dereferenceExpression;
        }

        // the model is added in with query, and the catalog and schema should be removed
        private Node applyModelRule(Table table)
        {
            return new Table(QualifiedName.of(ImmutableList.of(Iterables.getLast(table.getName().getOriginalParts()))));
        }
    }
}