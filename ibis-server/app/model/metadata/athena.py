import re
from contextlib import closing

import pandas as pd
from loguru import logger

from app.model import AthenaConnectionInfo
from app.model.data_source import DataSource
from app.model.metadata.dto import (
    Column,
    Constraint,
    RustatherEngineColumnType,
    Table,
    TableProperties,
)
from app.model.metadata.metadata import Metadata

# Athena-specific type mapping
ATHENA_TYPE_MAPPING = {
    # String Types (ignore Binary and Spatial Types for now)
    "char": RustatherEngineColumnType.CHAR,
    "varchar": RustatherEngineColumnType.VARCHAR,
    "tinytext": RustatherEngineColumnType.TEXT,
    "text": RustatherEngineColumnType.TEXT,
    "mediumtext": RustatherEngineColumnType.TEXT,
    "longtext": RustatherEngineColumnType.TEXT,
    "enum": RustatherEngineColumnType.VARCHAR,
    "set": RustatherEngineColumnType.VARCHAR,
    # Integer Types
    "bit": RustatherEngineColumnType.TINYINT,
    "tinyint": RustatherEngineColumnType.TINYINT,
    "smallint": RustatherEngineColumnType.SMALLINT,
    "mediumint": RustatherEngineColumnType.INTEGER,
    "int": RustatherEngineColumnType.INTEGER,
    "integer": RustatherEngineColumnType.INTEGER,
    "bigint": RustatherEngineColumnType.BIGINT,
    # Boolean Types
    "bool": RustatherEngineColumnType.BOOL,
    "boolean": RustatherEngineColumnType.BOOL,
    # Decimal Types
    "float": RustatherEngineColumnType.FLOAT4,
    "double": RustatherEngineColumnType.DOUBLE,
    "decimal": RustatherEngineColumnType.DECIMAL,
    "numeric": RustatherEngineColumnType.NUMERIC,
    # Date/Time Types
    "date": RustatherEngineColumnType.DATE,
    "datetime": RustatherEngineColumnType.TIMESTAMP,
    "timestamp": RustatherEngineColumnType.TIMESTAMPTZ,
    # JSON Type
    "json": RustatherEngineColumnType.JSON,
}


class AthenaMetadata(Metadata):
    def __init__(self, connection_info: AthenaConnectionInfo):
        super().__init__(connection_info)
        self.connection = DataSource.athena.get_connection(connection_info)

    def get_table_list(self) -> list[Table]:
        schema_name = self.connection_info.schema_name.get_secret_value()

        sql = f"""
            SELECT 
                t.table_catalog,
                t.table_schema,
                t.table_name,
                c.column_name,
                c.ordinal_position,
                c.is_nullable,
                c.data_type
            FROM 
                information_schema.tables AS t 
            JOIN 
                information_schema.columns AS c 
                ON t.table_catalog = c.table_catalog
                AND t.table_schema = c.table_schema
                AND t.table_name = c.table_name
            WHERE t.table_schema = '{schema_name}'
            ORDER BY t.table_name
            """

        # We need to use raw_sql here because using the sql method causes Ibis to *create  view* first,
        # which does not work with information_schema queries.
        with closing(self.connection.raw_sql(sql)) as cursor:
            response = pd.DataFrame(
                cursor.fetchall(), columns=[col[0] for col in cursor.description]
            ).to_dict(orient="records")

        def get_column(row) -> Column:
            return Column(
                name=row["column_name"],
                type=self._transform_column_type(row["data_type"]),
                notNull=row["is_nullable"].lower() == "no",
                description="",  # Athena doesn't provide column descriptions in information_schema
                properties=None,
            )

        def get_table(row) -> Table:
            return Table(
                name=self._format_athena_compact_table_name(
                    row["table_schema"], row["table_name"]
                ),
                description="",  # Athena doesn't provide table descriptions in information_schema
                columns=[],
                properties=TableProperties(
                    schema=row["table_schema"],
                    catalog=row["table_catalog"],
                    table=row["table_name"],
                ),
                primaryKey="",
            )

        unique_tables = {}

        for column_metadata in response:
            # generate unique table name
            table_name = self._format_athena_compact_table_name(
                column_metadata["table_schema"], column_metadata["table_name"]
            )
            # init table if not exists
            if table_name not in unique_tables:
                unique_tables[table_name] = get_table(column_metadata)

            current_table = unique_tables[table_name]
            # add column to table
            current_table.columns.append(get_column(column_metadata))

        return list(unique_tables.values())

    def get_constraints(self) -> list[Constraint]:
        # Athena doesn't support foreign key constraints
        return []

    def get_version(self) -> str:
        return "AWS Athena - Follow AWS service versioning"

    def _format_athena_compact_table_name(self, schema: str, table: str) -> str:
        return f"{schema}.{table}"

    def _transform_column_type(self, data_type: str) -> RustatherEngineColumnType:
        """Transform Athena data type to RustatherEngineColumnType.

        Args:
            data_type: The Athena data type string

        Returns:
            The corresponding RustatherEngineColumnType
        """
        # Remove parameter specifications like VARCHAR(255) -> VARCHAR
        normalized_type = re.sub(r"\(.*\)", "", data_type).strip().lower()

        # Use the module-level mapping table
        mapped_type = ATHENA_TYPE_MAPPING.get(
            normalized_type, RustatherEngineColumnType.UNKNOWN
        )

        if mapped_type == RustatherEngineColumnType.UNKNOWN:
            logger.warning(f"Unknown Athena data type: {data_type}")

        return mapped_type
