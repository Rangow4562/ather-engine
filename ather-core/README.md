# ather Core Module

The ather Core module serves as the semantic core of the ather engine. To understand the concept of the ather engine, refer to the [ather engine documentation](). 

This module is utilized by the API v3 of the ibis-server for SQL planning. Additionally, there is another module for Python bindings, [ather-core-py](../ather-core-py/), which is also used by the [ibis-server](../ibis-server/).

## How to Test / Build

- **Run Tests**:
  - Most unit test cases are located in `src/mdl/mod.rs`.
  - SQL end-to-end tests are executed using `sqllogictests`.

You can run all tests with the following command:
```
cargo test
```


## Coding Style

Please ensure your code is properly formatted before submitting a pull request. Use `rustfmt` for Rust files and `taplo` for formatting TOML files.

### Format with rustfmt

Run the following command to format Rust code:

```
cargo fmt
```

### Format TOML with taplo

Install `taplo-cli` and format TOML files with the following commands:

```
cargo install taplo-cli --locked
taplo fmt
```