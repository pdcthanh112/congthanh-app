import { useCallback, useEffect, useState } from 'react';

import { useSearchParams } from 'react-router-dom';

type UsePaginationOptions = {
  defaultPage?: number;
  defaultSize?: number;
};

type UsePaginationReturn = {
  page: number;
  size: number;
  setPage: (page: number) => void;
  setSize: (size: number) => void;
  nextPage: () => void;
  prevPage: () => void;
  reset: () => void;
  goToPage: (page: number) => void;
};

export const usePagination = (
  options?: UsePaginationOptions
): UsePaginationReturn => {
  const { defaultPage = 0, defaultSize = 10 } = options || {};

  const [searchParams, setSearchParams] = useSearchParams();

  const getPageFromUrl = useCallback(() => {
    const pageParam = searchParams.get('page');
    if (!pageParam) return defaultPage;
    const urlPage = parseInt(pageParam, 10);
    return isNaN(urlPage) || urlPage < 1 ? defaultPage : urlPage - 1;
  }, [searchParams, defaultPage]);

  const getSizeFromUrl = useCallback(() => {
    const sizeParam = searchParams.get('size');
    if (!sizeParam) return defaultSize;
    const parsed = parseInt(sizeParam, 10);
    return isNaN(parsed) || parsed <= 0 ? defaultSize : parsed;
  }, [searchParams, defaultSize]);

  const [page, setPageState] = useState<number>(getPageFromUrl);
  const [size, setSizeState] = useState<number>(getSizeFromUrl);

  useEffect(() => {
    setPageState(getPageFromUrl());
    setSizeState(getSizeFromUrl());
  }, [getPageFromUrl, getSizeFromUrl]);

  useEffect(() => {
    const params = new URLSearchParams(searchParams);

    const urlPage = page + 1;

    if (urlPage === 1) {
      params.delete('page');
    } else {
      params.set('page', urlPage.toString());
    }

    if (size === defaultSize) {
      params.delete('size');
    } else {
      params.set('size', size.toString());
    }

    const newSearch = params.toString();
    const currentSearch = searchParams.toString();

    if (newSearch !== currentSearch) {
      setSearchParams(params, { replace: true });
    }
  }, [page, size, defaultPage, defaultSize, searchParams, setSearchParams]);

  const setPage = useCallback((newPage: number) => {
    if (newPage < 0) return;
    setPageState(newPage);
  }, []);

  const setSize = useCallback((newSize: number) => {
    if (newSize <= 0) return;
    setSizeState(newSize);
    setPageState(0);
  }, []);

  const nextPage = useCallback(() => {
    setPageState((prev) => prev + 1);
  }, []);

  const prevPage = useCallback(() => {
    setPageState((prev) => (prev > 0 ? prev - 1 : 0));
  }, []);

  const resetPagination = useCallback(() => {
    setPageState(defaultPage);
    setSizeState(defaultSize);
  }, [defaultPage, defaultSize]);

  const goToPage = useCallback(
    (targetPage: number) => {
      setPage(targetPage);
    },
    [setPage]
  );

  return {
    page,
    size,
    setPage,
    setSize,
    nextPage,
    prevPage,
    reset: resetPagination,
    goToPage,
  };
};
