import { Section } from '../../components/Section';
import { it } from '../../i18n/it';
import styles from './ExercisesSection.module.css';

/** Section 8, "Esercizi": short classroom activities. */
export function ExercisesSection() {
  return (
    <Section
      title={it.exercises.title}
      info={it.exercises.info}
      lesson={it.exercises.lesson}
      defaultOpen={false}
    >
      <ol className={styles.list}>
        {it.exercises.items.map((exercise) => (
          <li key={exercise.title}>
            <strong>{exercise.title}</strong>
            <p>{exercise.text}</p>
          </li>
        ))}
      </ol>
    </Section>
  );
}
