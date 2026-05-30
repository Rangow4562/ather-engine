use std::{error::Error, fmt::Display};

#[derive(Debug, Clone)]
pub enum atherError {
    PermissionDenied(String),
}

impl Error for atherError {}

impl Display for atherError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            atherError::PermissionDenied(msg) => write!(f, "Permission Denied: {msg}"),
        }
    }
}
