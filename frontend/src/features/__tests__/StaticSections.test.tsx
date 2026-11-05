import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { it as t } from '../../i18n/it';
import { ExercisesSection } from '../exercises/ExercisesSection';
import { FirstRunOverlay } from '../guide/FirstRunOverlay';
import { GuideSection } from '../guide/GuideSection';

describe('teaching sections', () => {
  it('explains how to read the chart and reopens the first-run guide', async () => {
    const onShowOverlay = vi.fn();
    render(<GuideSection onShowOverlay={onShowOverlay} />);

    expect(screen.getByText(t.guide.paragraphs[2])).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: t.guide.showOverlay }));
    expect(onShowOverlay).toHaveBeenCalled();
  });

  it('shows the "i" explanation with the lesson on demand', async () => {
    render(<GuideSection onShowOverlay={vi.fn()} />);

    expect(screen.queryByText(t.guide.info, { exact: false })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: t.common.info(t.guide.title) }));
    expect(screen.getByText(t.guide.info, { exact: false })).toBeInTheDocument();
    expect(screen.getByText(t.guide.lesson, { exact: false })).toBeInTheDocument();
  });

  it('collapses and expands a section', async () => {
    render(<ExercisesSection />);
    const toggle = screen.getByRole('button', { name: t.exercises.title });

    expect(toggle).toHaveAttribute('aria-expanded', 'false');
    await userEvent.click(toggle);
    expect(toggle).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getAllByRole('listitem')).toHaveLength(t.exercises.items.length);
  });

  it('closes the first-run overlay with the button or Escape', async () => {
    const onDismiss = vi.fn();
    render(<FirstRunOverlay onDismiss={onDismiss} />);

    expect(screen.getByRole('dialog', { name: t.firstRun.title })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: t.firstRun.dismiss })).toHaveFocus();
    await userEvent.keyboard('{Escape}');
    await userEvent.click(screen.getByRole('button', { name: t.firstRun.dismiss }));
    expect(onDismiss).toHaveBeenCalledTimes(2);
  });
});
