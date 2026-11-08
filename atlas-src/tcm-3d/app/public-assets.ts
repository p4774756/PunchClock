/** Keep atlas and geometry requests inside this build's deployment prefix. */
export function publicAssetUrl(path:string):string{
 return `${import.meta.env.BASE_URL}${path.replace(/^\/+/, '')}`;
}
