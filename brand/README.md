# The Polyglossa logo

A P drawn as a speech bubble, its stem the bubble's tail, in Kotlin Multiplatform's gradient, and the
Polyglossa wordmark. kmark draws them from [`kmark.toml`](kmark.toml): `kmark build brand/kmark.toml
--out <dir>` writes the kit again, under kmark's own names, which are copied here as the table names them.
The logos and icons are SVGs, with PNGs at four times their size. The avatars are 1024 px squares, and the social preview is a PNG only.

In the logos, Dark and Light name the colour of the lettering, not of the background: `logo-dark` has
black lettering, for light backgrounds, and `logo-light` has white lettering, for dark ones. The mono logos
are all in that one colour, P included, for places that print a single colour. The avatars have no
lettering, so there dark and light name the background.

| File                                  | What it shows                       | Where it goes                                     |
|---------------------------------------|-------------------------------------|---------------------------------------------------|
| `logo-dark.svg`, `.png`               | The gradient P, black lettering     | The README header and the site on a light theme   |
| `logo-light.svg`, `.png`              | The gradient P, white lettering     | The README header and the site on a dark theme    |
| `logo-icon.svg`, `.png`               | The gradient P alone                | The site's logo and favicon, and Dokka's header   |
| `logo-mono-dark.svg`, `.png`          | The P and lettering in black        | One-colour print on light backgrounds             |
| `logo-mono-light.svg`, `.png`         | The P and lettering in white        | One-colour print on dark backgrounds              |
| `logo-icon-mono-dark.svg`, `.png`     | The P alone in black                | One-colour print on light backgrounds             |
| `logo-icon-mono-light.svg`, `.png`    | The P alone in white                | One-colour print on dark backgrounds              |
| `avatar-dark.svg`, `.png`             | The gradient P on black             | Profile pictures                                  |
| `avatar-light.svg`, `.png`            | The gradient P on white             | Profile pictures                                  |
| `avatar-palette.svg`, `.png`          | A white P on the gradient           | Profile pictures                                  |
| `social-preview.png`                  | The logo on black, 1280 × 640       | GitHub's repository card                          |

The avatars keep the P inside the circle that sites crop profile pictures to. GitHub has no API for the
social preview: it is uploaded under the repository's Settings → General → Social preview.

The root `README.md` shows `logo-dark.svg` and `logo-light.svg` in a `<picture>`, the docs build copies the
SVGs into `docs/brand/`, and the Dokka convention plugin in `build-logic` gives `logo-icon.svg` to Dokka,
which uses that file name for its own logo.
