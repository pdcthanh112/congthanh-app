'use client';

import { Button, Input, Label } from '@/components/ui';
import { MessageCircle, RotateCcw, Send, X } from 'lucide-react';
import { useTranslations } from 'next-intl';
import React, { useEffect, useRef, useState } from 'react';
import ChatMessageItem from './ChatMessageItem';
import { useChat } from './hooks';

export default function ChatWidget() {
  const t = useTranslations('chat');
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState('');
  const { messages, pending, send, reset } = useChat();
  const listRef = useRef<HTMLUListElement>(null);

  useEffect(() => {
    listRef.current?.scrollTo({ top: listRef.current.scrollHeight });
  }, [messages]);

  const handleSubmit = (event: React.FormEvent) => {
    event.preventDefault();
    if (!draft.trim() || pending) return;
    void send(draft);
    setDraft('');
  };

  if (!open) {
    return (
      <Button
        type="button"
        onClick={() => setOpen(true)}
        className="fixed bottom-5 right-5 z-50 h-14 w-14 rounded-full bg-yellow-400 text-black shadow-lg hover:bg-yellow-500"
        aria-label={t('open')}
      >
        <MessageCircle className="h-6 w-6" aria-hidden />
      </Button>
    );
  }

  const lastId = messages.at(-1)?.id;

  return (
    <section
      role="dialog"
      aria-label={t('title')}
      className="fixed bottom-5 right-5 z-50 flex h-[min(600px,80vh)] w-[min(400px,calc(100vw-2.5rem))] flex-col rounded-lg border bg-white shadow-xl"
    >
      <header className="flex items-center justify-between border-b px-4 py-2">
        <h2 className="font-medium">{t('title')}</h2>
        <div className="flex gap-1">
          <Button
            type="button"
            variant="ghost"
            size="icon"
            onClick={() => void reset()}
            aria-label={t('new_chat')}
            title={t('new_chat')}
          >
            <RotateCcw className="h-4 w-4" aria-hidden />
          </Button>
          <Button type="button" variant="ghost" size="icon" onClick={() => setOpen(false)} aria-label={t('close')}>
            <X className="h-4 w-4" aria-hidden />
          </Button>
        </div>
      </header>

      <ul ref={listRef} className="flex flex-1 flex-col gap-3 overflow-y-auto px-4 py-3" aria-live="polite">
        {messages.length === 0 && <li className="rounded-lg bg-gray-100 px-3 py-2 text-sm">{t('greeting')}</li>}
        {messages.map((m) => (
          <ChatMessageItem key={m.id} message={m} streaming={pending && m.id === lastId} />
        ))}
      </ul>

      <form onSubmit={handleSubmit} className="flex gap-2 border-t p-3">
        <Label htmlFor="chat-input" className="sr-only">
          {t('placeholder')}
        </Label>
        <Input
          id="chat-input"
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          placeholder={t('placeholder')}
          maxLength={2000}
          autoComplete="off"
        />
        <Button type="submit" size="icon" disabled={pending || !draft.trim()} aria-label={t('send')}>
          <Send className="h-4 w-4" aria-hidden />
        </Button>
      </form>
      <p className="px-3 pb-2 text-[11px] text-gray-400">{t('disclaimer')}</p>
    </section>
  );
}
