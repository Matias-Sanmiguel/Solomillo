/* eslint-disable @next/next/no-img-element */
export function Crest({ src, size = 22 }: { src?: string; size?: number }) {
  if (!src) {
    return (
      <span
        className="inline-block rounded-full bg-panel2"
        style={{ width: size, height: size }}
      />
    );
  }
  return (
    <img
      src={src}
      alt=""
      width={size}
      height={size}
      className="inline-block rounded-sm object-contain align-middle"
      style={{ width: size, height: size }}
    />
  );
}
