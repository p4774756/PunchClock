import fs from "node:fs";
import path from "node:path";
import { PNG } from "pngjs";
import toIco from "to-ico";

const pngPath = process.argv[2];
const outPath = process.argv[3];
if (!pngPath || !outPath) {
  console.error("usage: node package-desktop-windows-icon.mjs <png> <ico>");
  process.exit(1);
}

const source = PNG.sync.read(fs.readFileSync(pngPath));
const sizes = [16, 32, 48, 64, 128, 256];
const pngs = sizes.map((size) => resizePng(source, size));
const ico = await toIco(pngs);
fs.mkdirSync(path.dirname(outPath), { recursive: true });
fs.writeFileSync(outPath, ico);

function resizePng(src, size) {
  const out = new PNG({ width: size, height: size });
  for (let y = 0; y < size; y++) {
    const sy = Math.min(src.height - 1, Math.floor((y + 0.5) * src.height / size));
    for (let x = 0; x < size; x++) {
      const sx = Math.min(src.width - 1, Math.floor((x + 0.5) * src.width / size));
      const si = (sy * src.width + sx) << 2;
      const di = (y * size + x) << 2;
      out.data[di] = src.data[si];
      out.data[di + 1] = src.data[si + 1];
      out.data[di + 2] = src.data[si + 2];
      out.data[di + 3] = src.data[si + 3];
    }
  }
  return PNG.sync.write(out);
}
