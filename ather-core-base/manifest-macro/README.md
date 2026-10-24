# ather Core Manifest Macro
This is module to collect the generating macros for the manifest struct of ather MDL.
They are used to generate the manifest struct for different bindings.
Currently, we have the following bindings:
- Python
- Rust

## Example
```rust
use ather_core_manifest_macro::manifest;

manifest!(true); // Generate the manifest struct for Python binding 
manifest!(false); // Generate the manifest struct for Rust binding
```