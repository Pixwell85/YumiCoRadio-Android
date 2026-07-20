# Attribution

Everything in this app that was not written for it, and where it came from.

The **code** is GPLv3 (see [LICENSE](LICENSE)). The assets below are **not** — they
belong to their authors and are redistributed here under their own terms. If you
fork this project, these terms follow the files, not the GPL.

## Typeface

| Asset | Source | Licence |
|---|---|---|
| `app/src/main/res/font/w95fa.otf` | **W95FA** by Alina Sava, via [FontsArena](https://fontsarena.com) | SIL Open Font License 1.1 — [full text](licenses/W95FA-OFL.txt) |

## Visual idiom

| Asset | Source | Licence |
|---|---|---|
| Window frames, bevels, controls, metrics | [98.css](https://github.com/jdan/98.css) by Jordan Scales — reimplemented in Compose, not copied | MIT |

"Windows" is a trademark of Microsoft Corporation. This project is not affiliated
with or endorsed by Microsoft.

## Chat emotes

| Asset | Source | Licence |
|---|---|---|
| `app/src/main/assets/emotes/*` (122 files) | [JOI3's Free Pixel Art Emoji and Icon](https://joi3.itch.io/joi3s-free-pixel-art-emoji-icon) by JOI3, on itch.io | **CC BY 4.0** — attribution required. See the note below on a contradiction on the source page. |

JOI3 asks that their work not be used to train generative AI. That is a wish
rather than a term of CC BY 4.0, and this project honours it.

## Window and toolbar icons

`app/src/main/res/drawable-nodpi/ic_win_*.webp` and `ic_chat_*.webp` come from
the "Star OS 99" set used by the website (`img/ico/w2k/`). The website's Credits
window attributes its other sets — Memphis98, SE98, React95, Vintage-Icons,
win95-winxp_icons, Windows Icon Archive — but **not** this one.

### The set has been identified

It is **Win2kSVG** by *Blackcrack* ([blackysgate.de](https://www.blackysgate.de),
source at [opencode.net/Blackcrack/win2ksvg](https://www.opencode.net/Blackcrack/win2ksvg)),
a Plasma icon theme of 6219 SVGs. The website's `img/ico/w2k/` is a subset of it.

Its licensing is stated four different ways, which have to be read together:

- `COPYING` in the pack says **CC BY-NC-SA**.
- The author's own [KDE Store listing](https://store.kde.org/p/1955362/) says
  **Creative Commons Attribution ShareAlike** — the same licence *without* the
  NonCommercial clause. No version number is given there.
- `Readme.md` says something different again for his own work: *"the Icons what i
  have made [...] stay all under CC0 and is free usable for all of the World"* —
  while noting that *"if a couples Icons from others in there, stay these under
  the same Copyright like it was before."*
- **2907 of the 6219 files carry a CC0 1.0 declaration in their own RDF metadata.**

Three of those four are free licences. The NonCommercial clause appears in exactly
one place — a `COPYING` file that names no version and matches neither the store
listing he publishes through nor the metadata he embedded in the files.

The per-file metadata is not noise: it tracks the author's statement exactly.
Every icon this project takes carries CC0 — and the two that do **not** are
`Nullsoft_Winamp.svg` and `tcmd7_1.svg`, precisely the third-party application
branding he says retains its original copyright.

One icon was replaced — the Winamp logo, which was a trademark and had to go. The
rest were kept as they were, deliberately: see the note under the table.

| Used as | Source | Licence status |
|---|---|---|
| `ic_win_player` | `audio-radio.svg` | ✅ CC0 declared in-file. **Replaced** the Winamp logo. |
| `ic_win_schedule` | `calendar-02.svg` | ✅ CC0 declared in-file |
| `ic_win_history` | `preferences-system-time.svg` | ✅ CC0 declared in-file |
| `ic_win_settings` | `preferences-00.svg` | ✅ CC0 declared in-file |
| `ic_win_info` | `emblem-information.svg` | ✅ CC0 declared in-file |
| `ic_chat_nickname` | `nickname.svg` | ✅ CC0 declared in-file |
| `ic_chat_users` | `chat_users_on.svg` | ✅ CC0 declared in-file |
| `ic_chat_options` | `options.svg` | ✅ CC0 declared in-file |
| `ic_win_chat` | `YIRC_logo.svg` | ✅ The station's own |
| `ic_chat_connect` | **ours** — `art/icons/ic_chat_connect.svg` | ✅ Drawn for this project |
| `ic_chat_disconnect` | **ours** — `art/icons/ic_chat_disconnect.svg` | ✅ Drawn for this project |
| `ic_win_contact` | **ours** — `art/icons/ic_win_contact.svg` | ✅ Drawn for this project |
| `ic_chat_upload` | **ours** — `art/icons/ic_chat_upload.svg` | ✅ Drawn for this project |
| `ic_chat_quota` | **ours** — `art/icons/ic_chat_quota.svg` | ✅ Drawn for this project |
| `ic_win_about` | `help.svg` (the website's chat set) | ✅ CC0 declared in-file |

**Every icon is now accounted for.** Nine carry a CC0 declaration inside their own
file, five were drawn from scratch for this project, and one is the station's own
logo.

The five drawn ones — the bolt, the doorway, the envelope, the paperclip and the
pie — have their sources in [`art/icons/`](art/icons/), which also records what
was learned about drawing in this idiom. They are drawn of the *same subjects* as
the icons they replace, because a subject is an idea and ideas are not anyone's
property; the drawings are deliberately different in proportion and composition.
A close copy would have been a derivative work and would have achieved nothing.

Two were solved without drawing at all. The About icon is the question mark from
the website's own chat set, which turned out to carry a CC0 declaration. The
quota icon became a pie chart rather than a hard disk — a better fit anyway,
since a pie says "how much of your allowance is gone" where a disk only implies
storage.

The rest of this section is kept as the record of how the set was assessed before
that work, and of what was checked and rejected on the way.

- The concrete, identifiable problem was the **Winamp logo** — a live trademark
  with explicit policies against it at both Google Play and F-Droid. That one is
  fixed and stays fixed.
- The remaining question is whether Windows 2000-era desktop artwork is
  Microsoft-derived. It almost certainly is — but that applies to *every* icon
  theme in this genre without exception, including ones that have sat openly on
  GitHub and the KDE Store for years. Two independently-authored packs
  (Win2kSVG and ClassicOS-2000) were found to reproduce the same envelope and the
  same help book, which is what that shared origin looks like.
- No amount of pack-shopping removes it. Eleven packs were examined; none did
  better. The only real escape is original artwork drawn from scratch.

So this is a known, accepted risk rather than an oversight, and it is recorded
here rather than glossed over.

Alternatives were looked for and rejected. The Win9x look is a requirement of this project, not a
preference, and that turns out to rule out most of the field: the desktop-icon aesthetic of that
era *is* Microsoft's artwork, so packs that look right are generally extracted from it, closely
derived from it, or made of third-party application logos. Checked and set aside:

- **SE98** (`nestoris/Win98SE`) — GPL-2.0 with no "or later" statement, so incompatible with GPLv3;
  and its own README describes it as coming "from MicroSoft Memphis project" and being "a manual
  copy-paste fork" of Chicago95, so the licence does not cover much of what it contains.
- **ChiDoors** — CC BY-SA 4.0, which *would* work, but the pack is Win95-style renditions of modern
  application logos (Netflix, Steam, Ableton). Trademarks throughout.
- **Windows 95 +PLUS+** — CC BY 4.0, but acknowledged as based on Windows 95 artwork.
- **pixelarticons** (MIT) and **Kenney Pixel UI Pack** (CC0) — both legally spotless, neither is
  Win9x: monochrome outline icons and game-UI panels respectively.

**The `COPYING` NC clause is still the loose end**, though a thinner one than it
first looked. NonCommercial is not a free licence, is incompatible with GPLv3 and
is rejected by F-Droid, so if `COPYING` governed everything, none of these icons
could ship.

The reading taken here: the per-file CC0 waiver is the specific, deliberate and
irrevocable one, it matches the Readme, and it is corroborated by the store
listing dropping the NC clause entirely. `COPYING` looks like a stale file rather
than an overriding term.

Note that the store listing alone would not settle it: **CC BY-SA 4.0 is one-way
compatible with GPLv3, CC BY-SA 3.0 is not**, and the listing states no version.
The CC0 declarations are what actually carry this, and CC0 has no such versioning
problem. The store listing is corroboration, not the foundation.

Still worth a written line from Blackcrack — see
`docs/superpowers/outreach/blackcrack-win2ksvg-licence.md`.

## Station assets

`radio_logo.webp`, `default_cover.webp`, `ic_launcher_*`, `ic_notify_star.xml` —
Yumi Co. Radio's own. Not covered by the GPLv3 grant; please don't ship a fork
under the station's identity.

## Third-party marks

Last.fm, Bluesky and X logos appear only as links to the station's profiles and
belong to their owners.

---

## Open items before public release

These are recorded here rather than quietly shipped.

1. ~~The Winamp logo has to go, and six other icons carry no licence
   declaration.~~ **Closed.** The Winamp logo was replaced with `audio-radio.svg`.
   Of the six undeclared ones, five were redrawn from scratch and one was swapped
   for a CC0-declared file already in the website's chat set. Every icon in the
   app now traces to a declaration or to this project's own hand.

   For the record: substituting `yumicostaricon3.png` for the player icon would
   not have worked. It is the Windows 98 CD-and-note icon with the station's star
   laid over it, which trades a Nullsoft trademark for a derivative of Microsoft
   artwork. The star on its own (`yumistar.png`) is the station's and is fine.

2. **The pack's `COPYING` NC clause is still open.** See the reading above. It is
   the one remaining question, and a message to Blackcrack settles it.

3. **The emote page states two different things.** Its licence section reads:

   > You are NOT allowed to
   >     - Resell OR redistribute the asset to others
   >     - Edit and resell the asset to others
   >
   > This work is licensed under CC BY 4.0

   Those conflict. CC BY 4.0 grants exactly what the bullet above it withholds —
   "Share — copy and redistribute the material in any medium or format", for any
   purpose including commercially — and it forbids adding restrictions on top.

   The formal grant is the one that carries weight: CC BY 4.0 is a public,
   irrevocable licence, and the bullets read as boilerplate carried across JOI3's
   asset pages. It is also [FSF-recognised as a free
   licence](https://www.fsf.org/blogs/licensing/cc-by-4-0-and-cc-by-sa-4-0-added-to-our-list-of-free-licenses)
   and compatible with **GPLv3** specifically — which is this project's licence.
   On that reading the emotes may stay in a public repository and F-Droid is
   fine.

   Two things follow. **Attribution becomes mandatory**, whatever the page says
   about credit being optional — CC BY requires it, and this file provides it.
   And the contradiction is worth having JOI3 resolve in writing: a comment on
   the itch.io page asking them to confirm CC BY 4.0 governs costs nothing and
   removes the one remaining doubt.

Item 1 is resolved. Items 2 and 3 are both readings of contradictory licence
text, and both are worth confirming with their authors before publication.
