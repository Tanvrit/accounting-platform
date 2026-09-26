import { LogoMark } from "./Logo";
import styles from "./Footer.module.css";

export default function Footer() {
  return (
    <footer className={styles.footer}>
      <div className={`wrap ${styles.inner}`}>
        <div className={styles.brand}>
          <a href="#top" className={styles.brandLink}>
            <LogoMark size={24} />
            <span>
              Tanvrit <em>Accounting</em>
            </span>
          </a>
          <p className={styles.tag}>
            Offline-first accounting for Indian SMBs, from the Tanvrit
            platform.
          </p>
        </div>
        <nav className={styles.links} aria-label="Footer">
          <a href="https://tanvrit.com" rel="noopener">
            tanvrit.com
          </a>
          <a href="https://developers.tanvrit.com" rel="noopener">
            developers.tanvrit.com
          </a>
          <a href="mailto:hello@tanvrit.com?subject=Accounting%20early%20access">
            early access
          </a>
        </nav>
      </div>
      <div className={`wrap ${styles.legal}`}>
        <p>© 2026 Tanvrit · accounting.tanvrit.com</p>
      </div>
    </footer>
  );
}
