import { IconDevices, IconCloudOff, IconMoon, IconUsers } from "./icons";
import styles from "./Platforms.module.css";

const PLATFORMS = [
  "Android",
  "iOS",
  "macOS",
  "Windows",
  "Linux",
  "Web (Wasm)",
];

const POINTS = [
  {
    icon: IconDevices,
    title: "One codebase, every screen",
    body: "Compose Multiplatform on the Tanvrit SDK — the same app ships to Android, iOS, desktop (macOS, Windows, Linux) and the browser.",
  },
  {
    icon: IconCloudOff,
    title: "Offline-first",
    body: "Draft vouchers and read cached ledgers with no network at all. Work syncs when connectivity returns.",
  },
  {
    icon: IconUsers,
    title: "Multi-tenant by construction",
    body: "Per-app namespaced auth (X-App-ID) keeps roles, tokens and permissions from bleeding across businesses.",
  },
  {
    icon: IconMoon,
    title: "Three themes",
    body: "Dark, light and true black — one token system, set once per business in settings.",
  },
];

export default function Platforms() {
  return (
    <section className="section" id="platforms">
      <div className="wrap">
        <div className="section-head">
          <span className="eyebrow">Multiplatform</span>
          <h2 className="section-title">
            The counter, the pocket, and the desk
          </h2>
          <p className="section-sub">
            Your accountant closes the year on a laptop. Your staff records a
            payment from a phone on the shop floor. Same books, same balances.
          </p>
        </div>
        <div className={styles.layout}>
          <ul className={styles.chips}>
            {PLATFORMS.map((name) => (
              <li key={name} className={styles.chip}>
                {name}
              </li>
            ))}
          </ul>
          <ul className={styles.points}>
            {POINTS.map(({ icon: Icon, title, body }) => (
              <li key={title} className={styles.point}>
                <span className={styles.iconWrap}>
                  <Icon size={20} />
                </span>
                <div>
                  <h3 className={styles.pointTitle}>{title}</h3>
                  <p className={styles.pointBody}>{body}</p>
                </div>
              </li>
            ))}
          </ul>
        </div>
      </div>
    </section>
  );
}
