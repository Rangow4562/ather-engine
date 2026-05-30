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

import java.util.List;

import io.trino.sql.SqlFormatter;
import io.trino.sql.parser.SqlParser;
import io.trino.sql.tree.Statement;

import io.ather.base.AnalyzedMDL;
import io.ather.base.SessionContext;

import static io.ather.base.sqlrewrite.atherSqlRewrite.ather_SQL_REWRITE;
import static io.ather.base.sqlrewrite.EnumRewrite.ENUM_REWRITE;
import static io.ather.base.sqlrewrite.MetricRollupRewrite.METRIC_ROLLUP_REWRITE;
import static io.ather.base.sqlrewrite.Utils.parseSql;



public class atherPlanner
{
    public static final List<atherRule> ALL_RULES = List.of(
            GenerateViewRewrite.GENERATE_VIEW_REWRITE,
            METRIC_ROLLUP_REWRITE,
            ather_SQL_REWRITE,
            ENUM_REWRITE);
    private static final SqlParser SQL_PARSER = new SqlParser();

    private atherPlanner() {}

    public static String rewrite(String sql, SessionContext sessionContext, AnalyzedMDL analyzedMDL)
    {
        return rewrite(sql, sessionContext, analyzedMDL, ALL_RULES);
    }

    public static String rewrite(String sql, SessionContext sessionContext, AnalyzedMDL analyzedMDL, List<atherRule> rules)
    {
        Statement statement = parseSql(sql);
        for (atherRule rule : rules) {
            // we will replace or rewrite sql node in sql rewrite, to avoid rewrite rules affect each other, format and parse sql before each rewrite
            statement = rule.apply(parseSql(SqlFormatter.formatSql(statement)), sessionContext, analyzedMDL);
        }
        return SqlFormatter.formatSql(statement);
    }
}