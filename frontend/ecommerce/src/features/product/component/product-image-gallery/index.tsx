import { useState, useEffect } from 'react';

import ImageWithFallback from './ImageWithFallback';

export interface IProductImageGalleryProps {
  listImages: string[];
}

export function ProductImageGallery({ listImages }: IProductImageGalleryProps) {
  useEffect(() => {
    setCurrentIndex(0);
  }, [listImages]);

  const NO_SLIDER_IMAGE = 3;

  const [currentIndex, setCurrentIndex] = useState<number>(0);

  let startSliderIndex = 0;

  const currentMax = Math.max(currentIndex - Math.floor(NO_SLIDER_IMAGE / 2), 0);

  const remainingImages = listImages.length - NO_SLIDER_IMAGE;
  if (currentMax < remainingImages) {
    // If there are enough images after the current index to fill the slider, start the slider at the current max
    startSliderIndex = currentMax;
  } else if (remainingImages >= 0) {
    // Otherwise, start the slider at the end of the list minus the maximum number of images to show
    startSliderIndex = remainingImages;
  }

  const visibleImages =
    listImages.length > 0
      ? listImages.slice(startSliderIndex, startSliderIndex + NO_SLIDER_IMAGE)
      : [''];

  const handleNextClick = () => {
    if (currentIndex < listImages.length - 1) {
      setCurrentIndex(currentIndex + 1);
    }
  };

  const handlePrevClick = () => {
    if (currentIndex > 0) {
      setCurrentIndex(currentIndex - 1);
    }
  };

  return (
    <>
      <div className="main-image">
        <ImageWithFallback
          alt="photo"
          src={listImages.length > 0 ? listImages[currentIndex] : ''}
        />
      </div>
      <div className="image-slider">
        <button disabled={currentIndex === 0} className="slider-button" onClick={handlePrevClick}>
          <i className="bi bi-chevron-left"></i>
        </button>

        <div className="list-images">
          {visibleImages.map((item, index) => (
            <div
              className={`wrapper ${listImages[currentIndex] === item ? 'active' : ''}`}
              key={`${item}`}
              onClick={() => {
                if (listImages.length > 0 && listImages[currentIndex] !== item) {
                  setCurrentIndex(startSliderIndex + index);
                }
              }}
            >
              <ImageWithFallback width={100} height={100} alt="photo" src={item} />
            </div>
          ))}
        </div>

        <button
          disabled={currentIndex === listImages.length - 1 || !listImages.length}
          className="slider-button"
          onClick={handleNextClick}
        >
          <i className="bi bi-chevron-right"></i>
        </button>
      </div>
    </>
  );
}
