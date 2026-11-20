import type {Plugin} from 'vite';
import {ANIME_MODEL_DIGEST} from './app/anime-model-digest';

/** Inject modulepreload / fetch preloads after build knows hashed chunk names. */
export function atlasPreloadPlugin(base: string): Plugin {
  let threeChunk = '';
  let sceneChunk = '';
  const prefix = base.endsWith('/') ? base : `${base}/`;

  return {
    name: 'atlas-preload',
    apply: 'build',
    generateBundle(_options, bundle) {
      for (const item of Object.values(bundle)) {
        if (item.type !== 'chunk') continue;
        if (item.name === 'three' || item.fileName.includes('three-')) threeChunk = item.fileName;
        if (item.fileName.includes('character-scene')) sceneChunk = item.fileName;
      }
    },
    transformIndexHtml(html) {
      const links: string[] = [];
      const assetHref = (file: string) =>
        `${prefix}${file.startsWith('assets/') ? file : `assets/${file}`}`;
      if (threeChunk) {
        links.push(`<link rel="modulepreload" href="${assetHref(threeChunk)}" crossorigin>`);
      }
      if (sceneChunk) {
        links.push(`<link rel="modulepreload" href="${assetHref(sceneChunk)}" crossorigin>`);
      }
      links.push(
        `<link rel="preload" href="${prefix}models/anime-assistant.glb?v=${ANIME_MODEL_DIGEST}" as="fetch" type="model/gltf-binary" crossorigin>`,
      );
      if (!links.length) return html;
      return html.replace('</head>', ` ${links.join('\n ')}\n</head>`);
    },
  };
}