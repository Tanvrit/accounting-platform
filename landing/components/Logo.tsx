export function LogoMark({ size = 28 }: { size?: number }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 64 64"
      role="img"
      aria-label="Tanvrit Accounting"
    >
      <rect width="64" height="64" rx="14" fill="#101713" />
      <rect
        x="0.75"
        y="0.75"
        width="62.5"
        height="62.5"
        rx="13.25"
        fill="none"
        stroke="#ffffff"
        strokeOpacity="0.12"
        strokeWidth="1.5"
      />
      <g strokeLinecap="round" strokeLinejoin="round">
        <circle cx="32" cy="19.5" r="2.4" fill="#e2c376" />
        <path d="M15 23h34" stroke="#e2c376" strokeWidth="3" />
        <path d="M32 21.5V47" stroke="#e2c376" strokeWidth="3" />
        <path d="M23 51h18" stroke="#e2c376" strokeWidth="3" />
        <g stroke="#3ddc9a" strokeWidth="2.4" fill="none">
          <path d="M15 23l-4.5 10.5M15 23l4.5 10.5" />
          <path d="M8.5 33.5a6.5 6.5 0 0 0 13 0" />
          <path d="M49 23l-4.5 10.5M49 23l4.5 10.5" />
          <path d="M42.5 33.5a6.5 6.5 0 0 0 13 0" />
        </g>
      </g>
    </svg>
  );
}
