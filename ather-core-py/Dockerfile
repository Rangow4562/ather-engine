FROM python:3.11-bookworm AS builder

# Install system dependencies
RUN apt-get update && apt-get -y install \
    build-essential \
    curl \
    pkg-config \
    libssl-dev \
    libffi-dev \
    && rm -rf /var/lib/apt/lists/*

# Install Rust
RUN curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | bash -s -- -y
ENV PATH="/root/.cargo/bin:$PATH"

# Install poetry
RUN pip install poetry==1.8.3

# Set poetry environment variables
ENV POETRY_NO_INTERACTION=1 \
    POETRY_VIRTUALENVS_IN_PROJECT=1 \
    POETRY_VIRTUALENVS_CREATE=1

WORKDIR /build

# Copy all source code in the correct structure
COPY ./ather-core-base ./ather-core-base
COPY ./ather-core ./ather-core
COPY ./ather-core-py ./ather-core-py

# Build dependencies first
RUN cd ather-core-base && cargo build --release
RUN cd ather-core && cargo build --release

# Install dependencies and build ather-core-py
RUN cd ather-core-py && \
    poetry install --no-root && \
    poetry run maturin build --release

FROM python:3.11-slim-bookworm AS runtime

# Install runtime dependencies
RUN apt-get update && apt-get -y install \
    libssl-dev \
    libffi-dev \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /ather-core-py

# Copy the built wheel from builder stage
COPY --from=builder /build/ather-core-py/target/wheels/*.whl ./

# Copy the entire ather-core-py source code directory (including justfile) to root
COPY --from=builder /build/ather-core-py ./

# Install the built package
RUN pip install *.whl

# Set Python path
ENV PYTHONPATH=/ather-core-py

CMD ["python", "-c", "import ather_core_py; print('ather-core-py installed successfully')"]