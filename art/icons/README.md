# Icon sources

Original artwork for this project, drawn from scratch. **These are ours** — unlike the rest of the
icon set, which comes from third-party packs (see [`ATTRIBUTION.md`](../../ATTRIBUTION.md)). They
carry the project's own licence.

They are drawn *of the same subjects* as the icons they replace — a bolt for connecting, a doorway
for disconnecting, a drive for the quota — because subjects are ideas and ideas are not anyone's
property. The drawings themselves are deliberately not the same: different proportions, different
composition, the doorway mirrored and the arrow reversed. A close copy would have been a derivative
work and would have solved nothing.

## Regenerating the bitmaps

The app loads `.webp` under `app/src/main/res/drawable-nodpi/`. After editing an SVG here:

```bash
rsvg-convert -w 96 -h 96 art/icons/ic_chat_connect.svg -o /tmp/i.png
cwebp -lossless /tmp/i.png -o app/src/main/res/drawable-nodpi/ic_chat_connect.webp
```

## Drawing notes

Learned the hard way, on the first attempt at the bolt:

- **At 32px, less is more.** Internal shading, folds and gradients turn to mud at display size. The
  Win9x icons that read well are bold and flat: one fill, one hard black outline, one drop shadow,
  and at most a single flat highlight.
- **Light comes from the top-left.** Top faces lightest, front faces mid, right faces darkest.
- **Give solids a side wall.** The first drive platter was a painted ellipse and read as flat; the
  wall underneath is what makes it an object.
- **Check at 32px, not at 96.** Everything looks fine at 96. Render small and judge there —
  `magick montage` beside the icon being replaced is the honest test.
