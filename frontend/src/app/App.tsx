import { lazy, Suspense, useState } from 'react';
import { SelectedChunkSection } from '../features/chunk/SelectedChunkSection';
import { ColorsSection } from '../features/colors/ColorsSection';
import { CompareSection } from '../features/compare/CompareSection';
import { ExercisesSection } from '../features/exercises/ExercisesSection';
import { FirstRunOverlay } from '../features/guide/FirstRunOverlay';
import { GuideSection } from '../features/guide/GuideSection';
import { Header } from '../features/header/Header';
import { NoticeBanner } from '../features/notices/NoticeBanner';
import { ProjectionSection } from '../features/projection/ProjectionSection';
import { RagSearchSection } from '../features/search/RagSearchSection';
import { StatusSection } from '../features/status/StatusSection';
import { useDataWatchers } from '../hooks/useDataWatchers';
import { it } from '../i18n/it';
import { GUIDE_SEEN_KEY, readFlag, writeFlag } from '../lib/storage';
import { SceneErrorBoundary } from '../scene/SceneErrorBoundary';
import styles from './App.module.css';

/** three.js is loaded separately, so the sidebar is usable before the 3D view is ready. */
const SceneView = lazy(() => import('../scene/SceneView'));

/** The app shell: header, sidebar with the teaching sections, 3D view. */
export function App() {
  useDataWatchers();
  const [showGuide, setShowGuide] = useState(() => !readFlag(GUIDE_SEEN_KEY));

  const dismissGuide = () => {
    writeFlag(GUIDE_SEEN_KEY, true);
    setShowGuide(false);
  };

  return (
    <div className={styles.app}>
      <Header />
      <div className={styles.main}>
        <aside className={styles.sidebar} aria-label={it.app.sidebarLabel}>
          <StatusSection />
          <GuideSection
            onShowOverlay={() => {
              setShowGuide(true);
            }}
          />
          <ProjectionSection />
          <ColorsSection />
          <SelectedChunkSection />
          <CompareSection />
          <RagSearchSection />
          <ExercisesSection />
        </aside>
        <main className={styles.scene} aria-label={it.app.sceneLabel}>
          <NoticeBanner />
          <SceneErrorBoundary>
            <Suspense fallback={<p className={styles.sceneLoading}>{it.scene.loading}</p>}>
              <SceneView />
            </Suspense>
          </SceneErrorBoundary>
        </main>
      </div>
      {showGuide && <FirstRunOverlay onDismiss={dismissGuide} />}
    </div>
  );
}
