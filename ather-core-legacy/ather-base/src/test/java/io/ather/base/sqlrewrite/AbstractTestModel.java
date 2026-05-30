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

import com.google.common.collect.ImmutableList;
import org.intellij.lang.annotations.Language;
import org.testng.annotations.Test;

import io.ather.base.atherMDL;
import io.ather.base.atherTypes;
import io.ather.base.AnalyzedMDL;
import io.ather.base.SessionContext;
import io.ather.base.dto.Column;
import io.ather.base.dto.JoinType;
import io.ather.base.dto.Manifest;
import io.ather.base.dto.Model;
import io.ather.base.dto.Relationship;
import io.ather.base.dto.TableReference;

import static io.ather.base.sqlrewrite.atherSqlRewrite.ather_SQL_REWRITE;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;



public abstract class AbstractTestModel
        extends AbstractTestFramework
{
    protected Model customer;
    protected Model orders;
    protected Model lineitem;

    protected final List<Column> customerColumns;
    protected final List<Column> ordersColumns;
    protected final List<Column> lineitemColumns;
    protected final Relationship ordersCustomer;
    protected final Relationship ordersLineitem;

    public AbstractTestModel()
    {
        customerColumns = List.of(
                Column.column("custkey", atherTypes.INTEGER, null, true),
                Column.column("name", atherTypes.VARCHAR, null, true),
                Column.column("address", atherTypes.VARCHAR, null, true),
                Column.column("nationkey", atherTypes.INTEGER, null, true),
                Column.column("phone", atherTypes.VARCHAR, null, true),
                Column.column("acctbal", atherTypes.INTEGER, null, true),
                Column.column("mktsegment", atherTypes.VARCHAR, null, true),
                Column.column("comment", atherTypes.VARCHAR, null, true));
        ordersColumns = List.of(
                Column.column("orderkey", atherTypes.INTEGER, null, true),
                Column.column("custkey", atherTypes.INTEGER, null, true),
                Column.column("orderstatus", atherTypes.VARCHAR, null, true),
                Column.column("totalprice", atherTypes.INTEGER, null, true),
                Column.column("orderdate", atherTypes.DATE, null, true),
                Column.column("orderpriority", atherTypes.VARCHAR, null, true),
                Column.column("clerk", atherTypes.VARCHAR, null, true),
                Column.column("shippriority", atherTypes.INTEGER, null, true),
                Column.column("comment", atherTypes.VARCHAR, null, true),
                Column.column("lineitem", "Lineitem", "OrdersLineitem", true));
        lineitemColumns = List.of(
                Column.column("orderkey", atherTypes.INTEGER, null, true),
                Column.column("partkey", atherTypes.INTEGER, null, true),
                Column.column("suppkey", atherTypes.INTEGER, null, true),
                Column.column("linenumber", atherTypes.INTEGER, null, true),
                Column.column("quantity", atherTypes.INTEGER, null, true),
                Column.column("extendedprice", atherTypes.INTEGER, null, true),
                Column.column("discount", atherTypes.INTEGER, null, true),
                Column.column("tax", atherTypes.INTEGER, null, true),
                Column.column("returnflag", atherTypes.VARCHAR, null, true),
                Column.column("linestatus", atherTypes.VARCHAR, null, true),
                Column.column("shipdate", atherTypes.DATE, null, true),
                Column.column("commitdate", atherTypes.DATE, null, true),
                Column.column("receiptdate", atherTypes.DATE, null, true),
                Column.column("shipinstruct", atherTypes.VARCHAR, null, true),
                Column.column("shipmode", atherTypes.VARCHAR, null, true),
                Column.column("comment", atherTypes.VARCHAR, null, true),
                Column.column("orderkey_linenumber", atherTypes.VARCHAR, null, true, "concat(orderkey, '-', linenumber)"));
        ordersCustomer = Relationship.relationship("OrdersCustomer", List.of("Orders", "Customer"), JoinType.MANY_TO_ONE, "Orders.custkey = Customer.custkey");
        ordersLineitem = Relationship.relationship("OrdersLineitem", List.of("Orders", "Lineitem"), JoinType.ONE_TO_MANY, "Orders.orderkey = Lineitem.orderkey");
    }

    @Override
    protected void prepareData()
    {
        String orders = requireNonNull(getClass().getClassLoader().getResource("tiny-orders.parquet")).getPath();
        exec("create table orders as select * from '" + orders + "'");
        String customer = requireNonNull(getClass().getClassLoader().getResource("tiny-customer.parquet")).getPath();
        exec("create table customer as select * from '" + customer + "'");
        String lineitem = requireNonNull(getClass().getClassLoader().getResource("tiny-lineitem.parquet")).getPath();
        exec("create table lineitem as select * from '" + lineitem + "'");
    }

    @Test
    public void testToManyCalculated()
    {
        // TODO: add this to test case, currently this won't work
        // caluclatedColumn("col_3", BIGINT, "concat(address, sum(orders.lineitem.discount * orders.lineitem.extendedprice))");

        Model newCustomer = addColumnsToModel(
                customer,
                Column.column("orders", "Orders", "OrdersCustomer", true),
                Column.calculatedColumn("totalprice", atherTypes.BIGINT, "sum(orders.totalprice)"),
                Column.calculatedColumn("buy_item_count", atherTypes.BIGINT, "count(distinct orders.lineitem.orderkey_linenumber)"),
                Column.calculatedColumn("lineitem_totalprice", atherTypes.BIGINT, "sum(orders.lineitem.discount * orders.lineitem.extendedprice)"),
                Column.calculatedColumn("test_col", atherTypes.BIGINT, "sum(orders.lineitem.discount * nationkey)"));
        Manifest manifest = withDefaultCatalogSchema()
                .setModels(List.of(newCustomer, orders, lineitem))
                .setRelationships(List.of(ordersCustomer, ordersLineitem))
                .build();
        atherMDL mdl = atherMDL.fromManifest(manifest);

        assertQuery(mdl,
                "SELECT totalprice FROM Customer WHERE custkey = 370",
                "SELECT sum(totalprice) FROM customer c LEFT JOIN orders o ON c.custkey = o.custkey WHERE c.custkey = 370");
        assertQuery(mdl,
                "SELECT custkey, buy_item_count FROM Customer WHERE custkey = 370",
                "SELECT c.custkey, count(*) FROM customer c " +
                        "LEFT JOIN orders o ON c.custkey = o.custkey " +
                        "LEFT JOIN lineitem l ON o.orderkey = l.orderkey " +
                        "WHERE c.custkey = 370 " +
                        "GROUP BY 1");
        assertQuery(mdl,
                "SELECT custkey, lineitem_totalprice FROM Customer WHERE custkey = 370",
                "SELECT c.custkey, sum(l.extendedprice * l.discount) FROM customer c " +
                        "LEFT JOIN orders o ON c.custkey = o.custkey " +
                        "LEFT JOIN lineitem l ON o.orderkey = l.orderkey " +
                        "WHERE c.custkey = 370 " +
                        "GROUP BY 1");

        assertQuery(mdl,
                "SELECT custkey, test_col FROM Customer WHERE custkey = 370",
                "SELECT c.custkey, sum(l.discount * c.nationkey) FROM customer c " +
                        "LEFT JOIN orders o ON c.custkey = o.custkey " +
                        "LEFT JOIN lineitem l ON o.orderkey = l.orderkey " +
                        "WHERE c.custkey = 370 " +
                        "GROUP BY 1");
    }

    @Test
    public void testToOneCalculated()
    {
        Model newLineitem = addColumnsToModel(
                lineitem,
                Column.column("orders", "Orders", "OrdersLineitem", true),
                Column.calculatedColumn("col_1", atherTypes.BIGINT, "orders.totalprice + orders.totalprice"),
                Column.calculatedColumn("col_2", atherTypes.BIGINT, "concat(orders.orderkey, '#', orders.customer.custkey)"));
        Model newOrders = addColumnsToModel(
                orders,
                Column.column("customer", "Customer", "OrdersCustomer", true));
        Manifest manifest = withDefaultCatalogSchema()
                .setModels(List.of(customer, newOrders, newLineitem))
                .setRelationships(List.of(ordersCustomer, ordersLineitem))
                .build();
        atherMDL mdl = atherMDL.fromManifest(manifest);

        assertQuery(mdl,
                "SELECT col_1 FROM Lineitem WHERE orderkey = 44995",
                "SELECT (totalprice + totalprice) AS col_1\n" +
                        "FROM lineitem l\n" +
                        "LEFT JOIN orders o ON l.orderkey = o.orderkey\n" +
                        "WHERE l.orderkey = 44995");
        assertQuery(mdl,
                "SELECT col_1 FROM Lineitem WHERE orderkey = 44995",
                "SELECT (totalprice + totalprice) AS col_1\n" +
                        "FROM lineitem l\n" +
                        "LEFT JOIN orders o ON l.orderkey = o.orderkey\n" +
                        "WHERE l.orderkey = 44995");
        assertQuery(mdl,
                "SELECT col_2 FROM Lineitem WHERE orderkey = 44995",
                "SELECT concat(l.orderkey, '#', c.custkey) AS col_2\n" +
                        "FROM lineitem l\n" +
                        "LEFT JOIN orders o ON l.orderkey = o.orderkey\n" +
                        "LEFT JOIN customer c ON o.custkey = c.custkey\n" +
                        "WHERE l.orderkey = 44995");

        assertQuery(mdl, "SELECT count(*) FROM Lineitem", "SELECT count(*) FROM lineitem");
        assertQuery(mdl, "SELECT count(*) FROM Lineitem WHERE orderkey = 44995",
                "SELECT count(*) FROM lineitem WHERE orderkey = 44995");
        assertQuery(mdl, "SELECT count(*) FROM Lineitem l WHERE l.orderkey = 44995",
                "SELECT count(*) FROM lineitem l WHERE l.orderkey = 44995");

        assertQuery(mdl, "SELECT col_1 FROM Lineitem ORDER BY col_2", "SELECT (totalprice + totalprice) AS col_1\n" +
                "FROM lineitem l\n" +
                "LEFT JOIN orders o ON l.orderkey = o.orderkey\n" +
                "LEFT JOIN customer c ON o.custkey = c.custkey\n" +
                "ORDER BY concat(l.orderkey, '#', c.custkey)");
        assertQuery(mdl, "SELECT count(*) FROM Lineitem group by col_1, col_2 order by 1", "SELECT count(*)\n" +
                "FROM lineitem l\n" +
                "LEFT JOIN orders o ON l.orderkey = o.orderkey\n" +
                "LEFT JOIN customer c ON o.custkey = c.custkey\n" +
                "GROUP BY (totalprice + totalprice), concat(l.orderkey, '#', c.custkey)\n" +
                "ORDER BY 1");
        assertQuery(mdl, "SELECT rank() over (order by col_1) FROM Lineitem",
                "SELECT rank() OVER (ORDER BY (totalprice + totalprice))\n" +
                        "FROM lineitem l\n" +
                        "LEFT JOIN orders o ON l.orderkey = o.orderkey");
        assertQuery(mdl, "SELECT count(f1) FROM (SELECT lag(extendedprice) over (partition by col_2) as f1 FROM Lineitem)",
                "SELECT count(f1) FROM (SELECT lag(extendedprice) OVER (PARTITION BY concat(l.orderkey, '#', c.custkey)) as f1\n" +
                        "FROM lineitem l\n" +
                        "LEFT JOIN orders o ON l.orderkey = o.orderkey\n" +
                        "LEFT JOIN customer c ON o.custkey = c.custkey)");
    }

    @Test
    public void testModelWithCycle()
    {
        Model newCustomer = addColumnsToModel(
                customer,
                Column.column("orders", "Orders", "OrdersCustomer", true),
                Column.calculatedColumn("total_price", atherTypes.BIGINT, "sum(orders.totalprice)"));
        Model newOrders = addColumnsToModel(
                orders,
                Column.column("customer", "Customer", "OrdersCustomer", true),
                Column.calculatedColumn("customer_name", atherTypes.BIGINT, "customer.name"));
        Manifest manifest = withDefaultCatalogSchema()
                .setModels(List.of(newCustomer, newOrders))
                .setRelationships(List.of(ordersCustomer, ordersLineitem))
                .build();
        atherMDL mdl = atherMDL.fromManifest(manifest);

        assertThatCode(() -> query(rewrite("SELECT * FROM Orders", mdl, true)))
                .doesNotThrowAnyException();
        assertThatCode(() -> query(rewrite("SELECT customer_name FROM Orders WHERE orderkey = 44995", mdl, true)))
                .doesNotThrowAnyException();
        assertThatCode(() -> query(rewrite("SELECT total_price FROM Customer", mdl, true)))
                .doesNotThrowAnyException();
        assertThatCode(() -> query(rewrite("SELECT total_price FROM Customer c LEFT JOIN Orders o ON c.custkey = o.custkey", mdl, true)))
                .doesNotThrowAnyException();
        assertThatCode(() -> query(rewrite("SELECT o.custkey, total_price FROM Customer c LEFT JOIN Orders o ON c.custkey = o.custkey", mdl, true)))
                .doesNotThrowAnyException();
        assertThatCode(() -> query(rewrite("SELECT customer_name, total_price FROM Customer c LEFT JOIN Orders o ON c.custkey = o.custkey", mdl, true)))
                .hasMessageMatching("found cycle in .*");
    }

    @Test
    public void testModelOnModel()
    {
        Model newCustomer = addColumnsToModel(
                customer,
                Column.column("orders", "Orders", "OrdersCustomer", true),
                Column.calculatedColumn("totalprice", atherTypes.BIGINT, "sum(orders.totalprice)"));
        Model onCustomer = Model.onBaseObject(
                "OnCustomer",
                "Customer",
                ImmutableList.of(
                        Column.column("mom_custkey", "VARCHAR", null, true, "custkey"),
                        Column.column("mom_totalprice", "VARCHAR", null, true, "totalprice")),
                "mom_custkey");
        Manifest manifest = withDefaultCatalogSchema()
                .setModels(List.of(newCustomer, onCustomer, orders))
                .setRelationships(List.of(ordersCustomer))
                .build();
        atherMDL mdl = atherMDL.fromManifest(manifest);

        assertQuery(mdl, "SELECT mom_custkey, mom_totalprice FROM OnCustomer WHERE mom_custkey = 370",
                "SELECT c.custkey, sum(o.totalprice) FROM customer c\n" +
                        "LEFT JOIN orders o ON c.custkey = o.custkey\n" +
                        "WHERE c.custkey = 370\n" +
                        "GROUP BY 1");

        assertThatCode(() -> query(rewrite("SELECT 1 FROM OnCustomer", mdl, true)))
                .doesNotThrowAnyException();
    }

    @Test
    public void testCalculatedUseAnotherCalculated()
    {
        Model newCustomer = addColumnsToModel(
                customer,
                Column.column("orders", "Orders", "OrdersCustomer", true),
                Column.calculatedColumn("total_price", atherTypes.BIGINT, "sum(orders.totalprice)"));
        Model newOrders = addColumnsToModel(
                orders,
                Column.column("customer", "Customer", "OrdersCustomer", true),
                Column.column("lineitem", "Lineitem", "OrdersLineitem", true),
                Column.calculatedColumn("customer_name", atherTypes.BIGINT, "customer.name"),
                Column.calculatedColumn("extended_price", atherTypes.BIGINT, "sum(lineitem.extendedprice)"));
        Model newLineitem = addColumnsToModel(
                lineitem,
                Column.column("orders", "Orders", "OrdersLineitem", true),
                Column.calculatedColumn("test_column", atherTypes.BIGINT, "orders.customer.total_price + extendedprice"));
        Manifest manifest = withDefaultCatalogSchema()
                .setModels(List.of(newCustomer, newOrders, newLineitem))
                .setRelationships(List.of(ordersCustomer, ordersLineitem))
                .build();
        atherMDL mdl = atherMDL.fromManifest(manifest);

        assertThatCode(() -> query(rewrite("SELECT test_column FROM Lineitem", mdl, true)))
                .doesNotThrowAnyException();
    }

    @Test
    public void testSelectEmpty()
    {
        Model newCustomer = addColumnsToModel(
                customer,
                Column.column("orders", "Orders", "OrdersCustomer", true),
                Column.calculatedColumn("total_price", atherTypes.BIGINT, "sum(orders.totalprice)"));
        Model newOrders = addColumnsToModel(
                orders,
                Column.column("customer", "Customer", "OrdersCustomer", true),
                Column.column("lineitem", "Lineitem", "OrdersLineitem", true),
                Column.calculatedColumn("customer_name", atherTypes.BIGINT, "customer.name"),
                Column.calculatedColumn("extended_price", atherTypes.BIGINT, "sum(lineitem.extendedprice)"));
        Model newLineitem = addColumnsToModel(
                lineitem,
                Column.column("orders", "Orders", "OrdersLineitem", true),
                Column.calculatedColumn("test_column", atherTypes.BIGINT, "orders.customer.total_price + extendedprice"));
        Manifest manifest = withDefaultCatalogSchema()
                .setModels(List.of(newCustomer, newOrders, newLineitem))
                .setRelationships(List.of(ordersCustomer, ordersLineitem))
                .build();
        atherMDL mdl = atherMDL.fromManifest(manifest);

        assertThatCode(() -> query(rewrite("SELECT true \"_\" FROM Lineitem", mdl, true)))
                .doesNotThrowAnyException();

        assertThatCode(() -> query(rewrite("SELECT true \"_\" FROM Lineitem, Orders", mdl, true)))
                .doesNotThrowAnyException();

        assertThatCode(() -> query(rewrite("SELECT orderkey FROM Lineitem, Orders", mdl, true)))
                .doesNotThrowAnyException();
    }

    @Test
    public void testCustomCTE()
    {
        Manifest manifest = withDefaultCatalogSchema()
                .setModels(List.of(customer, orders, lineitem))
                .build();
        atherMDL mdl = atherMDL.fromManifest(manifest);
        assertThatCode(() -> query(rewrite("WITH cte AS (SELECT * FROM Orders) SELECT * FROM cte", mdl, true)))
                .doesNotThrowAnyException();
        assertThatCode(() -> query(rewrite("WITH cte AS (SELECT * FROM Orders) SELECT * FROM cte", mdl, false)))
                .doesNotThrowAnyException();

        assertThatCode(() -> query(rewrite("WITH cte AS (SELECT * FROM Orders), cte2 as (SELECT * FROM cte) SELECT * FROM cte2", mdl, true)))
                .doesNotThrowAnyException();
        assertThatCode(() -> query(rewrite("WITH cte AS (SELECT * FROM Orders), cte2 as (SELECT * FROM cte) SELECT * FROM cte2", mdl, false)))
                .doesNotThrowAnyException();
    }

    @Test
    public void testSelectNotFound()
    {
        Manifest manifest = withDefaultCatalogSchema()
                .setModels(List.of(customer))
                .setRelationships(List.of(ordersCustomer, ordersLineitem))
                .build();
        atherMDL mdl = atherMDL.fromManifest(manifest);
        assertThatThrownBy(() -> query(rewrite("SELECT * FROM notfound", mdl, true)))
                .hasMessageFindingMatch(".*notfound.*");
    }

    @Test
    public void testBuildModelFailed()
    {
        assertThatThrownBy(() -> buildFailedModel("select * from main.orders", "Orders", TableReference.tableReference("memory", "main", "orders")))
                .hasMessageContaining("either none or more than one of (refSql, baseObject, tableReference) are set");
        assertThatThrownBy(() -> buildFailedModel(null, "Orders", TableReference.tableReference("memory", "main", "orders")))
                .hasMessageContaining("either none or more than one of (refSql, baseObject, tableReference) are set");
        assertThatThrownBy(() -> buildFailedModel("select * from main.orders", null, TableReference.tableReference("memory", "main", "orders")))
                .hasMessageContaining("either none or more than one of (refSql, baseObject, tableReference) are set");
        assertThatThrownBy(() -> buildFailedModel("select * from main.orders", "Orders", null))
                .hasMessageContaining("either none or more than one of (refSql, baseObject, tableReference) are set");
        assertThatThrownBy(() -> buildFailedModel(null, null, null))
                .hasMessageContaining("either none or more than one of (refSql, baseObject, tableReference) are set");
    }

    private void buildFailedModel(String refSql, String baseObject, TableReference tableReference)
    {
        new Model("failed", refSql, baseObject, tableReference, null, null, false, null);
    }

    private void assertQuery(atherMDL mdl, @Language("SQL") String atherSql, @Language("SQL") String duckDBSql)
    {
        assertThat(query(rewrite(atherSql, mdl, true))).isEqualTo(query(duckDBSql));
        assertThat(query(rewrite(atherSql, mdl, false))).isEqualTo(query(duckDBSql));
    }

    private String rewrite(String sql, atherMDL atherMDL, boolean enableDynamicField)
    {
        SessionContext sessionContext = SessionContext.builder()
                .setCatalog("ather")
                .setSchema("test")
                .setEnableDynamic(enableDynamicField)
                .build();
        return atherPlanner.rewrite(sql, sessionContext, new AnalyzedMDL(atherMDL, null), List.of(ather_SQL_REWRITE));
    }
}