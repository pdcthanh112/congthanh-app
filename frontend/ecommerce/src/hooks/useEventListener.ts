import { useEffect, useRef } from 'react';

export default function useEventListener<K extends keyof DocumentEventMap>(
  eventType: K,
  callback: (e: DocumentEventMap[K]) => void,
  element: Window | Document | HTMLElement | null = typeof window !== 'undefined' ? window : null
) {
  const callbackRef = useRef(callback);

  useEffect(() => {
    callbackRef.current = callback;
  }, [callback]);

  useEffect(() => {
    if (element == null) return;
    const handler = (e: Event) => callbackRef.current(e as DocumentEventMap[K]);
    element.addEventListener(eventType, handler);

    return () => element.removeEventListener(eventType, handler);
  }, [eventType, element]);
}
