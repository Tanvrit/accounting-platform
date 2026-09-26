import { IconArrow } from "./icons";
import styles from "./Cta.module.css";

const MAILTO =
  "mailto:hello@tanvrit.com?subject=Accounting%20early%20access";

export default function Cta() {
  return (
    <section className="section" id="early-access">
      <div className="wrap">
        <div className={styles.panel}>
          <h2 className={styles.title}>Bring your books into balance.</h2>
          <p className={styles.sub}>
            Tanvrit Accounting is in early access. Tell us about your business
            and we&apos;ll get you onboarded.
          </p>
          <a className="btn btn-primary" href={MAILTO}>
            Request early access
            <IconArrow size={16} />
          </a>
          <p className={styles.micro}>hello@tanvrit.com</p>
        </div>
      </div>
    </section>
  );
}
