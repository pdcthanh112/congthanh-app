import type { RefObject } from 'react';
import useEventListener from './useEventListener';

export default function useClickOutside(ref: RefObject<HTMLElement | null>, cb: (e: MouseEvent) => void) {
  useEventListener(
    'click',
    (e) => {
      if (ref.current == null || ref.current.contains(e.target as Node)) return;
      cb(e);
    },
    typeof document !== 'undefined' ? document : null
  );
}
