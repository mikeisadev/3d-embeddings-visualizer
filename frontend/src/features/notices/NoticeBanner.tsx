import { it } from '../../i18n/it';
import { useUiStore } from '../../state/uiStore';
import styles from './NoticeBanner.module.css';

/** Tells the user why the points are reloading (the data changed in the database). */
export function NoticeBanner() {
  const notice = useUiStore((state) => state.notice);
  const setNotice = useUiStore((state) => state.setNotice);
  if (notice === null) {
    return null;
  }
  const text = notice === 'datasetChanged' ? it.notices.datasetChanged : it.notices.datasetReloaded;
  return (
    <div className={styles.banner} role="status">
      <span>{text}</span>
      <button
        type="button"
        className={styles.close}
        aria-label={it.notices.dismiss}
        onClick={() => {
          setNotice(null);
        }}
      >
        ×
      </button>
    </div>
  );
}
