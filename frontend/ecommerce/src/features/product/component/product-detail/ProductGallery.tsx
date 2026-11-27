'use client';

import DefaultImage from '@/assets/images/default-image.jpg';
import { cn } from '@/lib/utils';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { useTranslations } from 'next-intl';
import Image from 'next/image';
import React, { useRef, useState } from 'react';

const THUMBS_PER_VIEW = 5;

interface ProductGalleryProps {
  images: string[];
  alt: string;
}

export default function ProductGallery({ images, alt }: ProductGalleryProps) {
  const t = useTranslations();
  const [current, setCurrent] = useState(images[0]);
  const [thumbStart, setThumbStart] = useState(0);
  const [shownImages, setShownImages] = useState(images);
  const imageRef = useRef<HTMLImageElement>(null);

  if (shownImages !== images) {
    setShownImages(images);
    setCurrent(images[0]);
    setThumbStart(0);
  }

  const handleZoom = (event: React.MouseEvent<HTMLDivElement>) => {
    const image = imageRef.current;
    if (!image) return;
    const rect = event.currentTarget.getBoundingClientRect();
    const { naturalHeight, naturalWidth } = image;
    const { offsetX, offsetY } = event.nativeEvent;
    image.style.width = `${naturalWidth}px`;
    image.style.height = `${naturalHeight}px`;
    image.style.maxWidth = 'unset';
    image.style.top = `${offsetY * (1 - naturalHeight / rect.height)}px`;
    image.style.left = `${offsetX * (1 - naturalWidth / rect.width)}px`;
  };

  const visibleThumbs = images.slice(thumbStart, thumbStart + THUMBS_PER_VIEW);

  return (
    <div className="w-full md:w-2/5 py-3">
      <div
        className="relative w-full pt-[100%] shadow-sm cursor-zoom-in overflow-hidden"
        onMouseLeave={() => imageRef.current?.removeAttribute('style')}
        onMouseMove={handleZoom}
      >
        <Image
          src={current || DefaultImage}
          alt={alt}
          width={600}
          height={600}
          priority
          ref={imageRef}
          className="absolute pointer-events-none top-0 left-0 h-full w-full bg-white object-cover"
        />
      </div>
      {images.length > 1 && (
        <div className="relative mt-4 grid grid-cols-5 gap-1">
          <button
            type="button"
            onClick={() => setThumbStart((s) => Math.max(0, s - 1))}
            disabled={thumbStart === 0}
            className="absolute left-0 top-1/2 z-10 h-9 w-5 -translate-y-1/2 bg-black/20 text-white disabled:opacity-30"
            aria-label={t('product.previous_images')}
          >
            <ChevronLeft className="w-5 h-5" aria-hidden />
          </button>
          <button
            type="button"
            onClick={() => setThumbStart((s) => Math.min(Math.max(0, images.length - THUMBS_PER_VIEW), s + 1))}
            disabled={thumbStart + THUMBS_PER_VIEW >= images.length}
            className="absolute right-0 top-1/2 z-10 h-9 w-5 -translate-y-1/2 bg-black/20 text-white disabled:opacity-30"
            aria-label={t('product.next_images')}
          >
            <ChevronRight className="w-5 h-5" aria-hidden />
          </button>
          {visibleThumbs.map((src, i) => (
            <button
              type="button"
              key={src}
              className={cn('relative aspect-square', src === current && 'ring-2 ring-red-400')}
              onClick={() => setCurrent(src)}
              aria-label={t('product.view_image', { index: thumbStart + i + 1 })}
              aria-pressed={src === current}
            >
              <Image src={src} alt="" fill sizes="80px" className="object-cover" />
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
