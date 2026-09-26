import { IconArrow, IconCheck } from "./icons";
import styles from "./Hero.module.css";

const MAILTO =
  "mailto:hello@tanvrit.com?subject=Accounting%20early%20access";

export default function Hero() {
  return (
    <section className={styles.hero} id="top">
      <div className={`wrap ${styles.inner}`}>
        <div className={styles.copy}>
          <span className={styles.badge}>
            <span className={styles.dot} aria-hidden="true" />
            Early access
          </span>
          <h1 className={styles.title}>
            Books that stay balanced.
            <br />
            <span className={styles.accent}>Compliance, built in.</span>
          </h1>
          <p className={styles.sub}>
            Offline-first accounting for Indian SMBs — full double-entry
            ledgers, GST and TDS centers, reports your CA will accept, and an
            audit trail that can prove itself. One codebase, on Android, iOS,
            desktop, and the web.
          </p>
          <div className={styles.actions}>
            <a className="btn btn-primary" href={MAILTO}>
              Request early access
              <IconArrow size={16} />
            </a>
            <a className="btn" href="#features">
              Explore the product
            </a>
          </div>
          <p className={styles.micro}>
            Offline-first · Multi-tenant · No card required
          </p>
        </div>

        {/* Stylized CSS window frame — not a screenshot. */}
        <div className={styles.frame} aria-hidden="true">
          <div className={styles.frameBar}>
            <span />
            <span />
            <span />
            <p className={styles.frameTitle}>Trial Balance · FY 2026–27</p>
          </div>
          <div className={styles.frameBody}>
            <div className={styles.row}>
              <span>Cash at bank</span>
              <span className={styles.money}>₹ 4,82,300</span>
            </div>
            <div className={styles.row}>
              <span>Revenue (this month)</span>
              <span className={styles.money}>₹ 1,16,540</span>
            </div>
            <div className={styles.row}>
              <span>GST liability</span>
              <span className={`${styles.money} ${styles.gold}`}>₹ 20,977</span>
            </div>
            <div className={styles.divider} />
            <div className={styles.balanceRow}>
              <span className={styles.balanceLabel}>
                Dr <span className={styles.eq}>=</span> Cr
              </span>
              <span className={styles.balanced}>
                <IconCheck size={14} /> Balanced
              </span>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
