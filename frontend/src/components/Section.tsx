import { type ReactNode, useId, useState } from 'react';
import { it } from '../i18n/it';
import styles from './Section.module.css';

interface SectionProps {
  readonly title: string;
  readonly info: string;
  readonly lesson: string;
  readonly defaultOpen?: boolean;
  readonly children: ReactNode;
}

/** A collapsible sidebar section with an "i" button that explains it and names the lesson. */
export function Section({ title, info, lesson, defaultOpen = true, children }: SectionProps) {
  const [open, setOpen] = useState(defaultOpen);
  const [infoOpen, setInfoOpen] = useState(false);
  const contentId = useId();
  const infoId = useId();
  const headingId = useId();

  return (
    <section className={styles.section} aria-labelledby={headingId}>
      <header className={styles.header}>
        <h2 id={headingId} className={styles.title}>
          <button
            type="button"
            className={styles.toggle}
            aria-expanded={open}
            aria-controls={contentId}
            onClick={() => {
              setOpen(!open);
            }}
          >
            <span aria-hidden="true" className={styles.chevron}>
              {open ? '▾' : '▸'}
            </span>
            {title}
          </button>
        </h2>
        <button
          type="button"
          className={styles.info}
          aria-label={it.common.info(title)}
          aria-expanded={infoOpen}
          aria-controls={infoId}
          onClick={() => {
            setInfoOpen(!infoOpen);
          }}
        >
          i
        </button>
      </header>
      {infoOpen && (
        <p id={infoId} className={styles.infoText}>
          {info} <strong>{it.common.lesson}:</strong> {lesson}.
        </p>
      )}
      <div id={contentId} hidden={!open} className={styles.content}>
        {children}
      </div>
    </section>
  );
}
