# Anatomy GLB derivatives (CC BY-SA 4.0)

Compressed `.glb.gz` files in this directory are derived from **Z-Anatomy** `Startup.blend`
(https://github.com/Z-Anatomy/Models-of-human-anatomy, CC BY-SA 4.0).

They include simplified **skeleton** and **muscle** meshes only (no viscera, vessels, lymph, inner ear, or kidney).

Rebuild (optional, not run in CI):

1. Place `work/z-anatomy/Z-Anatomy/Startup.blend` (from upstream ZIP; do not commit ZIP).
2. `blender -b work/z-anatomy/Z-Anatomy/Startup.blend -P scripts/export-z-anatomy-blender.py`
3. `node scripts/compress-anatomy-models.mjs`

Upstream attribution lists both the current BodyParts3D credit and the sentence Z-Anatomy still requires:

- `"BodyParts3D, © The Database Center for Life Science licensed under CC Attribution 4.0 International"`
- `"BodyParts3D - The Database Center for Life Science - CC-BY-SA 2.1 Japan"`

See `public/ATTRIBUTION.md`.