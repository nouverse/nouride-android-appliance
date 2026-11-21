//! "Go back to Nouride Home?": the question on the dashboard's window before it closes (`link_policy::Back::Ask`), in
//! the words the phone asks it in. The one thing the app ever puts over the dashboard: a confirmation, as a modal may
//! be.

pub const TITLE: &str = "leave_dashboard_title";
pub const BODY: &str = "leave_dashboard_body";
/// Closes the dashboard's window and brings Nouride Home forward.
pub const GO_BACK: &str = "leave_dashboard";
/// Changes nothing.
pub const STAY: &str = "stay";
