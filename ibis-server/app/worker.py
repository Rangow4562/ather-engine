from uvicorn.workers import UvicornWorker


class atherUvicornWorker(UvicornWorker):
    CONFIG_KWARGS = {"loop": "uvloop", "http": "httptools"}
