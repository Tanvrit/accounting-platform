import { LogoMark } from "./Logo";
import styles from "./Nav.module.css";

const MAILTO =
  "mailto:hello@tanvrit.com?subject=Accounting%20early%20access";

export default function Nav() {
  return (
    <header className={styles.nav}>
      <nav className={`wrap ${styles.inner}`} aria-label="Primary">
        <a href="#top" className={styles.brand}>
          <LogoMark size={26} />
          <span className={styles.brandName}>
            Tanvrit <em>Accounting</em>
          </span>
        </a>
        <div className={styles.links}>
          <a href="#features">Features</a>
          <a href="#compliance">Compliance</a>
          <a href="#platforms">Platforms</a>
          <a className={styles.cta} href={MAILTO}>
            Early access
          </a>
        </div>
      </nav>
    </header>
  );
}
