/** Joins CSS class names, skipping empty ones. */
export function cx(...classes: readonly (string | false | null | undefined)[]): string {
  return classes
    .filter((name): name is string => typeof name === 'string' && name !== '')
    .join(' ');
}
