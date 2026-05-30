import ather_core
from fastapi import Request
from starlette.datastructures import Headers

from app.model import QueryDTO
from app.model.data_source import DataSource

X_ather_FALLBACK_DISABLE = "x-ather-fallback_disable"
X_ather_VARIABLE_PREFIX = "x-ather-variable-"
X_ather_TIMEZONE = "x-ather-timezone"
X_ather_DB_STATEMENT_TIMEOUT = "x-ather-db-statement_timeout"
X_CACHE_HIT = "X-Cache-Hit"
X_CACHE_CREATE_AT = "X-Cache-Create-At"
X_CACHE_OVERRIDE = "X-Cache-Override"
X_CACHE_OVERRIDE_AT = "X-Cache-Override-At"
X_CORRELATION_ID = "X-Correlation-ID"


# Validate the dto by building the specific connection info from the data source
def verify_query_dto(data_source: DataSource, dto: QueryDTO):
    # Use data_source.get_connection_info to validate the connection_info
    # This will ensure the connection_info can be properly parsed for the specific data source
    data_source.get_connection_info(dto.connection_info, {})


def get_ather_headers(request: Request) -> Headers:
    return Headers(
        raw=list(
            filter(
                lambda t: _filter_headers(t[0].decode("latin-1")),
                request.headers.raw,
            )
        )
    )


def _filter_headers(header_string: str) -> bool:
    if header_string.startswith("x-ather-"):
        return True
    elif header_string.startswith("x-user-"):
        return True
    elif header_string.startswith("x-correlation-id"):
        return True
    elif header_string == "traceparent":
        return True
    elif header_string == "tracestate":
        return True
    elif header_string == "sentry-trace":
        return True
    return False


def is_backward_compatible(manifest_str: str) -> bool:
    try:
        return ather_core.is_backward_compatible(manifest_str)
    except Exception:
        return False
