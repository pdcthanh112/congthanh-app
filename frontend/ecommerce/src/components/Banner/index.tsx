'use client';

import { useEffect, useRef, useState } from 'react';
import Image from 'next/image';
import { useTranslations } from 'next-intl';
import Banner1 from '@/assets/images/banner1.jpg';
import Banner2 from '@/assets/images/banner2.jpg';
import Banner3 from '@/assets/images/banner3.jpg';
import { cn } from '@/lib/utils';

const SLIDES = [Banner1, Banner2, Banner3];
const INTERVAL = 5000;

/** Carousel đơn giản bằng CSS scroll-snap, tự chuyển slide, dừng khi rê chuột. Thay cho Carousel của antd. */
export default function Banner() {
  const t = useTranslations('common');
  const trackRef = useRef<HTMLDivElement>(null);
  const [index, setIndex] = useState(0);
  const [paused, setPaused] = useState(false);

  const goTo = (i: number) => {
    const track = trackRef.current;
    if (!track) return;
    const next = (i + SLIDES.length) % SLIDES.length;
    track.scrollTo({ left: next * track.clientWidth, behavior: 'smooth' });
    setIndex(next);
  };

  useEffect(() => {
    if (paused) return;
    const id = setInterval(() => goTo(index + 1), INTERVAL);
    return () => clearInterval(id);
  });

  return (
    <section
      aria-roledescription="carousel"
      aria-label={t('banner')}
      className="relative w-full"
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
    >
      <div
        ref={trackRef}
        className="flex snap-x snap-mandatory overflow-x-auto scroll-smooth [scrollbar-width:none]"
        onScroll={(e) => {
          const el = e.currentTarget;
          setIndex(Math.round(el.scrollLeft / el.clientWidth));
        }}
      >
        {SLIDES.map((src, i) => (
          <div key={i} className="relative aspect-[16/5] w-full shrink-0 snap-start">
            <Image src={src} alt="" fill priority={i === 0} sizes="100vw" className="object-cover" />
          </div>
        ))}
      </div>
      <div className="absolute bottom-3 left-1/2 flex -translate-x-1/2 gap-2">
        {SLIDES.map((_, i) => (
          <button
            key={i}
            type="button"
            aria-label={t('go_to_slide', { index: i + 1 })}
            aria-current={i === index}
            className={cn('h-2.5 w-2.5 rounded-full bg-white/60', i === index && 'bg-white')}
            onClick={() => goTo(i)}
          />
        ))}
      </div>
    </section>
  );
}
