import fs from "node:fs";
import path from "node:path";
import toIco from "to-ico";

const pngPath = process.argv[2];
const outPath = process.argv[3];
if (!pngPath || !outPath) {
  console.error("usage: node package-desktop-windows-icon.mjs <png> <ico>");
  process.exit(1);
}
const png = fs.readFileSync(pngPath);
const ico = await toIco([png]);
fs.mkdirSync(path.dirname(outPath), { recursive: true });
fs.writeFileSync(outPath, ico);