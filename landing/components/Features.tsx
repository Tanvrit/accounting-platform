import {
  IconGauge,
  IconTree,
  IconLedger,
  IconReport,
  IconLayers,
  IconRecon,
  IconLock,
  IconUsers,
} from "./icons";
import styles from "./Features.module.css";

const FEATURES = [
  {
    icon: IconGauge,
    title: "Dashboard KPIs",
    body: "Cash, revenue, expenses, net profit and live GST liability — auto-refreshed as vouchers post.",
  },
  {
    icon: IconTree,
    title: "Full chart of accounts",
    body: "A searchable hierarchical tree with type filters and in-place creation. Structured from day one.",
  },
  {
    icon: IconLedger,
    title: "Voucher entry that balances itself",
    body: "Payment, receipt, journal, contra, sale and purchase vouchers with a live Dr = Cr bar and offline drafts.",
  },
  {
    icon: IconReport,
    title: "Reports, exported",
    body: "Trial Balance, P&L, Balance Sheet, Cash Flow and ratio analysis — to PDF, Excel or CSV.",
  },
  {
    icon: IconLayers,
    title: "Budgets with a spine",
    body: "Per-account budget lines, budget-vs-actual variance, and an indicative forecast.",
  },
  {
    icon: IconRecon,
    title: "Bank reconciliation",
    body: "Import a CSV statement, auto-match against the ledger, resolve the exceptions, done.",
  },
  {
    icon: IconLock,
    title: "Fiscal period control",
    body: "Open, lock, and close periods cleanly; carry opening balances forward without ceremony.",
  },
  {
    icon: IconUsers,
    title: "Per-business settings",
    body: "GSTIN/TAN, voucher numbering prefixes, base currency — each business keeps its own books.",
  },
];

export default function Features() {
  return (
    <section className="section" id="features">
      <div className="wrap">
        <div className="section-head">
          <span className="eyebrow">The product</span>
          <h2 className="section-title">
            A real ledger, not a spreadsheet with a login
          </h2>
          <p className="section-sub">
            Every feature below is already shipped in the Tanvrit Accounting
            app — not a roadmap.
          </p>
        </div>
        <ul className={styles.grid}>
          {FEATURES.map(({ icon: Icon, title, body }) => (
            <li key={title} className={styles.card}>
              <span className={styles.iconWrap}>
                <Icon size={20} />
              </span>
              <h3 className={styles.cardTitle}>{title}</h3>
              <p className={styles.cardBody}>{body}</p>
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}
