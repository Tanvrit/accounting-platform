/* Hand-drawn stroke icons. 24x24 grid, stroke=currentColor, no icon packs. */

type IconProps = { size?: number; className?: string };

function Base({
  size = 20,
  className,
  children,
}: IconProps & { children: React.ReactNode }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.5"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      className={className}
    >
      {children}
    </svg>
  );
}

export const IconGauge = (p: IconProps) => (
  <Base {...p}>
    <path d="M4 14a8 8 0 1 1 16 0" />
    <path d="M12 14l4-4.5" />
    <path d="M4 18h16" strokeOpacity="0.4" />
  </Base>
);

export const IconTree = (p: IconProps) => (
  <Base {...p}>
    <rect x="9" y="3" width="6" height="4.5" rx="1.2" />
    <rect x="3.5" y="16" width="6" height="4.5" rx="1.2" />
    <rect x="14.5" y="16" width="6" height="4.5" rx="1.2" />
    <path d="M12 7.5v3.5M6.5 16v-3a2 2 0 0 1 2-2h7a2 2 0 0 1 2 2v3" />
  </Base>
);

export const IconLedger = (p: IconProps) => (
  <Base {...p}>
    <path d="M4 4.5A2 2 0 0 1 6 2.5h14v19H6a2 2 0 0 1-2-2v-15Z" />
    <path d="M4 19.5a2 2 0 0 1 2-2h14" />
    <path d="M8.5 8h7M8.5 12h4" />
  </Base>
);

export const IconReport = (p: IconProps) => (
  <Base {...p}>
    <path d="M4 20V4M4 20h16" />
    <path d="M9 16v-5M13.5 16V7M18 16v-3" />
  </Base>
);

export const IconLayers = (p: IconProps) => (
  <Base {...p}>
    <path d="M12 3l9 5-9 5-9-5 9-5Z" />
    <path d="M3 13l9 5 9-5" strokeOpacity="0.55" />
  </Base>
);

export const IconRecon = (p: IconProps) => (
  <Base {...p}>
    <rect x="2.5" y="5" width="19" height="14" rx="2.5" />
    <path d="M2.5 10h19" strokeOpacity="0.5" />
    <path d="M9 15.5l2 2 4-4" />
  </Base>
);

export const IconLock = (p: IconProps) => (
  <Base {...p}>
    <rect x="5" y="10.5" width="14" height="10" rx="2" />
    <path d="M8 10.5V7.5a4 4 0 0 1 8 0v3" />
    <path d="M12 14.5v2.5" />
  </Base>
);

export const IconChain = (p: IconProps) => (
  <Base {...p}>
    <rect x="7" y="3" width="10" height="6.5" rx="1.5" />
    <path d="M12 9.5v5M12 14.5l-4 3M12 14.5l4 3" />
    <rect x="2.5" y="17.5" width="8" height="4" rx="1" />
    <rect x="13.5" y="17.5" width="8" height="4" rx="1" />
  </Base>
);

export const IconRupee = (p: IconProps) => (
  <Base {...p}>
    <path d="M6 4h12M6 8.5h12" />
    <path d="M6 4c6 0 8 1.5 8 4.5S12 13 8.5 13H6l7 7" />
  </Base>
);

export const IconCertificate = (p: IconProps) => (
  <Base {...p}>
    <rect x="3" y="4.5" width="18" height="13" rx="2" />
    <path d="M7.5 9h6M7.5 12.5h9" strokeOpacity="0.6" />
    <circle cx="16.5" cy="13.5" r="2" />
    <path d="M15.5 15.5L15 20l1.5-1 1.5 1-.5-4.5" />
  </Base>
);

export const IconDevices = (p: IconProps) => (
  <Base {...p}>
    <rect x="2.5" y="4.5" width="13" height="9.5" rx="1.5" />
    <path d="M6.5 18.5h8M10.5 14v4.5" strokeOpacity="0.5" />
    <rect x="16.5" y="8.5" width="5" height="9.5" rx="1.2" />
  </Base>
);

export const IconCloudOff = (p: IconProps) => (
  <Base {...p}>
    <path d="M6 18h11a4 4 0 0 0 .9-7.9A5.5 5.5 0 0 0 7.2 8.6 4.5 4.5 0 0 0 6 18Z" strokeOpacity="0.55" />
    <path d="M4 4l16 16" />
  </Base>
);

export const IconMoon = (p: IconProps) => (
  <Base {...p}>
    <path d="M20 14.5A8.5 8.5 0 0 1 9.5 4 8.5 8.5 0 1 0 20 14.5Z" />
  </Base>
);

export const IconUsers = (p: IconProps) => (
  <Base {...p}>
    <circle cx="9" cy="8" r="3.2" />
    <path d="M3.5 19.5a5.5 5.5 0 0 1 11 0" />
    <path d="M15.5 5.2a3.2 3.2 0 0 1 0 5.7M17.8 14.3a5.5 5.5 0 0 1 2.7 5.2" strokeOpacity="0.55" />
  </Base>
);

export const IconArrow = (p: IconProps) => (
  <Base {...p}>
    <path d="M4 12h15M13 5.5L19.5 12 13 18.5" />
  </Base>
);

export const IconCheck = (p: IconProps) => (
  <Base {...p}>
    <path d="M4.5 12.5l5 5L19.5 7" />
  </Base>
);
