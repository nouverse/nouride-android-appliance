//! What a person sees for each state: the cat's pose, the card's colour, which sentence, and at most one button.
//! Android's `StatusLook`, `VersionLooks`, `UpdateNotices` and `Tabs`, with their tests, plus the menu bar's items.
//! Pure, so a new state cannot ship without its words: the `match`es below do not compile until it has them. The
//! words themselves are keys into `packages/ui-kit/src/words.json`, and a test holds every key to that file.

pub mod notices;
pub mod status;
pub mod tabs;
pub mod tray;
pub mod versions;

pub use status::{Action, Look, Mascot, Tone, Words};
