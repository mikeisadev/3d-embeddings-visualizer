import { isApiError } from '../api/ApiError';
import { it, type MessageWithHint } from '../i18n/it';

export interface DescribedError extends MessageWithHint {
  readonly code: string;
  readonly requestId: string | null;
  readonly detail: string | null;
}

const errorMessages: Readonly<Record<string, MessageWithHint>> = it.errors;
const warningMessages: Readonly<Record<string, MessageWithHint>> = it.warnings;

/** Italian message and hint for any error; unknown codes still show the code and request id. */
export function describeError(error: unknown): DescribedError {
  if (!isApiError(error)) {
    return {
      ...it.unknownError('CLIENT_ERROR'),
      code: 'CLIENT_ERROR',
      requestId: null,
      detail: null,
    };
  }
  const known = errorMessages[error.code];
  return {
    ...(known ?? it.unknownError(error.code)),
    code: error.code,
    requestId: error.requestId,
    detail: error.detail,
  };
}

/** Italian message and hint for a code found in a diagnostics check (error or warning). */
export function describeCode(code: string): MessageWithHint {
  return errorMessages[code] ?? warningMessages[code] ?? it.unknownError(code);
}
