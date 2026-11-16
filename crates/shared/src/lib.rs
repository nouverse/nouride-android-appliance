//! What every other crate shares: the build's names and numbers, Nouride's `/health` answer, the states the app
//! moves through, versions, and a small file of preferences. No crate imports a sibling; they meet here.

pub mod build;
pub mod health;
pub mod prefs;
pub mod state;
pub mod version;

pub use build::Build;
pub use health::Health;
pub use state::{AppStep, NourideStep, Phase, Reply, Where};
