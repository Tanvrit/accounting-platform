import { IconRupee, IconCertificate, IconChain } from "./icons";
import styles from "./Compliance.module.css";

const PILLARS = [
  {
    icon: IconRupee,
    title: "GST Center",
    items: [
      "GSTR-1 and GSTR-3B, generated and validated",
      "e-Invoice (IRN) and e-Way Bill readiness",
      "Pre-filing health checklist before you submit",
    ],
  },
  {
    icon: IconCertificate,
    title: "TDS Center",
    items: [
      "26Q, 27Q and 24Q return preparation",
      "Challan linkage against deductions",
      "Form 16 / 16A certificates for deductees",
    ],
  },
  {
    icon: IconChain,
    title: "Audit trail",
    items: [
      "Immutable, hash-chained event log",
      "Filter by event, diff before and after",
      "Integrity verification on demand",
    ],
  },
];

export default function Compliance() {
  return (
    <section className="section" id="compliance">
      <div className="wrap">
        <div className="section-head">
          <span className="eyebrow">Compliance</span>
          <h2 className="section-title">
            Indian statutory work, first-class
          </h2>
          <p className="section-sub">
            GST and TDS aren&apos;t plugins or exports to another tool. They
            live where your entries do — and every change leaves a verifiable
            trail.
          </p>
        </div>
        <ul className={styles.grid}>
          {PILLARS.map(({ icon: Icon, title, items }) => (
            <li key={title} className={styles.pillar}>
              <div className={styles.pillarHead}>
                <span className={styles.iconWrap}>
                  <Icon size={20} />
                </span>
                <h3 className={styles.pillarTitle}>{title}</h3>
              </div>
              <ul className={styles.items}>
                {items.map((item) => (
                  <li key={item}>
                    <span className={styles.tick} aria-hidden="true" />
                    {item}
                  </li>
                ))}
              </ul>
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}
