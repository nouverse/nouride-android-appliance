//! What a person sees for each state: the cat's pose, the card's colour, which sentence, and at most one button.
//! Android's `StatusLook`, `VersionLooks`, `UpdateNotices` and `Tabs` (here the sidebar and Connect), with their tests,
//! plus the menu bar's items, how long the windows live and the one question asked over the dashboard.
//! Pure, so a new state cannot ship without its words: the `match`es below do not compile until it has them. The
//! words themselves are keys into `packages/ui-kit/src/words.json`, and a test holds every key to that file.

pub mod backup;
pub mod connect;
pub mod leave;
pub mod notices;
pub mod sidebar;
pub mod status;
pub mod tray;
pub mod versions;
pub mod window;

pub use status::{Action, Look, Mascot, Tone, Words};
