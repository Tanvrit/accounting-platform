import type { Metadata, Viewport } from "next";
import "./globals.css";

export const metadata: Metadata = {
  metadataBase: new URL("https://accounting.tanvrit.com"),
  title: "Tanvrit Accounting — Offline-first accounting for Indian SMBs",
  description:
    "Full double-entry bookkeeping with live Dr=Cr balancing, GST (GSTR-1, GSTR-3B, e-invoice, e-way bill) and TDS centers, exportable reports, fiscal-period control, and a hash-chained audit trail — one codebase for Android, iOS, desktop, and web.",
  icons: { icon: "/favicon.svg" },
  openGraph: {
    title: "Tanvrit Accounting",
    description:
      "Offline-first accounting for Indian SMBs. Double-entry, GST, TDS, reports, and an audit trail that can prove itself — on every platform.",
    url: "https://accounting.tanvrit.com",
    siteName: "Tanvrit Accounting",
    type: "website",
  },
};

export const viewport: Viewport = {
  themeColor: "#0a0e0c",
  colorScheme: "dark",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
