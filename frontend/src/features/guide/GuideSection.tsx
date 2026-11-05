import { Section } from '../../components/Section';
import ui from '../../components/ui.module.css';
import { it } from '../../i18n/it';

interface GuideSectionProps {
  readonly onShowOverlay: () => void;
}

/** Section 2, "Come leggere il grafico": what a point is and why the picture is a projection. */
export function GuideSection({ onShowOverlay }: GuideSectionProps) {
  return (
    <Section title={it.guide.title} info={it.guide.info} lesson={it.guide.lesson}>
      {it.guide.paragraphs.map((paragraph) => (
        <p key={paragraph}>{paragraph}</p>
      ))}
      <button type="button" className={ui.linkButton} onClick={onShowOverlay}>
        {it.guide.showOverlay}
      </button>
    </Section>
  );
}
