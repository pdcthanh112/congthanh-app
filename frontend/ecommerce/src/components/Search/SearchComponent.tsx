'use client';

import React, { useEffect, useId, useRef, useState } from 'react';
import { useSession } from 'next-auth/react';
import { useTranslations } from 'next-intl';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Clock, Loader2, Search, TrendingUp, X } from 'lucide-react';
import { getAllCategoryJson } from '@/api/categoryApi';
import { addSearchHistoryItem, getSearchHistory, getSearchRecommend, removeSearchHistoryItem } from '@/api/searchApi';
import { Input } from '@/components/ui';
import { useRouter } from '@/i18n/routing';
import useDebounce from '@/hooks/useDebounce';
import { cn } from '@/lib/utils';
import { CATEGORY_KEY, SEARCH_KEY } from '@/utils/constants/queryKey';
import { PATH } from '@/utils/constants/path';

// Chưa có API xu hướng tìm kiếm: giữ danh sách mẫu.
const searchTrending = ['lajlfjasl ajfls alsjfl', 'asjljas asjlsf', 'aloijasdl aljlfdjl akjflda sdjdis', 'ljlkjajldsjfjdslj'];

/** Ô tìm kiếm trên header: gợi ý (search-service), lịch sử (customer-service), bàn phím đầy đủ theo mẫu combobox. */
export default function SearchComponent({ className }: { className?: string }) {
  const t = useTranslations('search');
  const router = useRouter();
  const queryClient = useQueryClient();
  const { status } = useSession();
  const listboxId = useId();

  const [open, setOpen] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const [category, setCategory] = useState('');
  const [selectedIndex, setSelectedIndex] = useState(-1);
  const containerRef = useRef<HTMLDivElement | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const debounced = useDebounce(searchTerm, 300);

  const { data: categories } = useQuery({
    queryKey: [CATEGORY_KEY],
    queryFn: async () => (await getAllCategoryJson()).data,
  });

  const { data: history = [] } = useQuery({
    queryKey: [SEARCH_KEY, 'history'],
    queryFn: async () => (await getSearchHistory()).data,
    enabled: status === 'authenticated',
  });

  const { data: recommend = [], isFetching } = useQuery({
    queryKey: [SEARCH_KEY, 'recommend', debounced],
    queryFn: async () => {
      const lower = debounced.toLowerCase();
      const list = await getSearchRecommend(debounced);
      // Gợi ý bắt đầu bằng từ khoá lên trước.
      return [...list].sort((a, b) => Number(b.toLowerCase().startsWith(lower)) - Number(a.toLowerCase().startsWith(lower)));
    },
    enabled: debounced.trim().length > 0,
  });

  const { mutate: recordSearch } = useMutation({
    mutationFn: (keyword: string) => addSearchHistoryItem(keyword),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: [SEARCH_KEY, 'history'] }),
  });
  const { mutate: removeSearch } = useMutation({
    mutationFn: (keyword: string) => removeSearchHistoryItem(keyword),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: [SEARCH_KEY, 'history'] }),
  });

  const firstMatch = searchTerm ? recommend.find((s) => s.toLowerCase().startsWith(searchTerm.toLowerCase())) : undefined;
  const completion = firstMatch ? firstMatch.slice(searchTerm.length) : '';

  useEffect(() => setSelectedIndex(-1), [debounced]);

  useEffect(() => {
    const onClick = (event: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', onClick);
    return () => document.removeEventListener('mousedown', onClick);
  }, []);

  const handleSearch = (term: string) => {
    const keyword = term.trim();
    if (!keyword) return;
    if (status === 'authenticated') recordSearch(keyword);
    setSearchTerm(keyword);
    setOpen(false);
    router.push({ pathname: PATH.PRODUCT_PATH_URL.PRODUCT, query: category ? { keyword, category } : { keyword } });
  };

  const handleKeyDown = (event: React.KeyboardEvent) => {
    if ((event.key === 'Tab' || event.key === 'ArrowRight') && completion) {
      event.preventDefault();
      setSearchTerm(searchTerm + completion);
      return;
    }
    switch (event.key) {
      case 'ArrowDown':
        if (recommend.length === 0) return;
        event.preventDefault();
        setOpen(true);
        setSelectedIndex((prev) => (prev < recommend.length - 1 ? prev + 1 : 0));
        break;
      case 'ArrowUp':
        if (recommend.length === 0) return;
        event.preventDefault();
        setSelectedIndex((prev) => (prev > 0 ? prev - 1 : recommend.length - 1));
        break;
      case 'Enter':
        event.preventDefault();
        handleSearch(selectedIndex >= 0 ? recommend[selectedIndex] : searchTerm);
        break;
      case 'Escape':
        setOpen(false);
        setSelectedIndex(-1);
        break;
    }
  };

  const optionId = (i: number) => `${listboxId}-opt-${i}`;
  const showSuggestions = open && searchTerm.trim().length > 0;

  return (
    <div className={cn('flex items-center h-10 rounded-md grow relative', className)} ref={containerRef}>
      <label className="sr-only" htmlFor={`${listboxId}-category`}>
        {t('category')}
      </label>
      <select
        id={`${listboxId}-category`}
        value={category}
        onChange={(e) => setCategory(e.target.value)}
        className="hidden md:block bg-gray-300 h-10 max-w-36 rounded-l-md px-2 text-sm text-black"
      >
        <option value="">{t('all_categories')}</option>
        {categories?.map((c) => (
          <option key={c.id} value={c.slug}>
            {c.name}
          </option>
        ))}
      </select>
      <div className="relative w-full h-full bg-white rounded-l-md md:rounded-none">
        {completion && (
          <div className="absolute inset-0 pl-4 pointer-events-none flex items-center text-sm" aria-hidden>
            <span className="invisible whitespace-pre">{searchTerm}</span>
            <span className="text-gray-400 bg-gray-100 px-0.5 rounded">{completion}</span>
          </div>
        )}
        <Input
          ref={inputRef}
          role="combobox"
          aria-label={t('label')}
          aria-expanded={showSuggestions || (open && !searchTerm)}
          aria-controls={listboxId}
          aria-autocomplete="both"
          aria-activedescendant={selectedIndex >= 0 ? optionId(selectedIndex) : undefined}
          placeholder={t('placeholder')}
          className="h-full w-full pl-4 pr-16 rounded-none text-black border-0"
          onFocus={() => setOpen(true)}
          value={searchTerm}
          onChange={(e) => {
            setSearchTerm(e.target.value);
            setOpen(true);
          }}
          onKeyDown={handleKeyDown}
        />
        <div className="absolute inset-y-0 right-0 pr-3 flex items-center gap-2">
          {isFetching && <Loader2 className="h-5 w-5 text-blue-500 animate-spin" aria-hidden />}
          {searchTerm && !isFetching && (
            <button
              type="button"
              onClick={() => {
                setSearchTerm('');
                inputRef.current?.focus();
              }}
              className="p-1 hover:bg-gray-100 rounded-full"
              aria-label={t('clear')}
            >
              <X className="h-4 w-4 text-gray-400" aria-hidden />
            </button>
          )}
        </div>
      </div>
      <button
        type="button"
        onClick={() => handleSearch(searchTerm)}
        className="h-10 px-3 bg-yellow-400 hover:bg-yellow-500 rounded-r-md text-black"
        aria-label={t('submit')}
      >
        <Search className="h-5 w-5" aria-hidden />
      </button>

      {open && (
        <div className="absolute top-full left-0 w-full mt-2 bg-white text-black rounded-lg shadow-lg border border-gray-200 z-50">
          {showSuggestions ? (
            <>
              <ul id={listboxId} role="listbox" aria-label={t('label')} className="p-2 max-h-80 overflow-y-auto">
                {recommend.map((item, index) => (
                  <li
                    key={item}
                    id={optionId(index)}
                    role="option"
                    aria-selected={selectedIndex === index}
                    onMouseDown={(e) => e.preventDefault()}
                    onClick={() => handleSearch(item)}
                    className={cn('px-3 py-2 rounded cursor-pointer truncate hover:bg-gray-100', selectedIndex === index && 'bg-blue-50 text-blue-700')}
                  >
                    {item.toLowerCase().startsWith(searchTerm.toLowerCase()) ? (
                      <>
                        <span className="font-medium text-blue-600">{item.slice(0, searchTerm.length)}</span>
                        {item.slice(searchTerm.length)}
                      </>
                    ) : (
                      item
                    )}
                  </li>
                ))}
              </ul>
              {!isFetching && recommend.length === 0 && (
                <p className="p-6 text-center text-sm text-gray-500">{t('no_suggestion', { term: searchTerm })}</p>
              )}
              {recommend.length > 0 && (
                <p className="px-4 py-2 bg-gray-50 text-xs text-gray-500 border-t border-gray-100">{t('keyboard_hint')}</p>
              )}
            </>
          ) : (
            <div id={listboxId}>
              <div className="p-4">
                <h3 className="flex items-center gap-2 text-sm text-gray-500 mb-2">
                  <TrendingUp className="w-4 h-4" aria-hidden />
                  {t('trending')}
                </h3>
                <div className="flex flex-wrap gap-2">
                  {searchTrending.map((term) => (
                    <button
                      type="button"
                      key={term}
                      className="py-1 px-2 border border-gray-200 hover:bg-gray-100 rounded text-sm"
                      onClick={() => handleSearch(term)}
                    >
                      # {term}
                    </button>
                  ))}
                </div>
              </div>
              {history.length > 0 && (
                <div className="p-4 border-t border-gray-100">
                  <h3 className="flex items-center gap-2 text-sm text-gray-500 mb-2">
                    <Clock className="w-4 h-4" aria-hidden />
                    {t('recent')}
                  </h3>
                  <ul>
                    {history.map((entry) => (
                      <li key={entry.keyword} className="flex items-center justify-between rounded hover:bg-gray-100">
                        <button type="button" className="flex-1 text-left py-2 px-2" onClick={() => handleSearch(entry.keyword)}>
                          {entry.keyword}
                        </button>
                        <button
                          type="button"
                          className="p-2 text-gray-400 hover:text-gray-600"
                          aria-label={t('remove', { term: entry.keyword })}
                          onClick={() => removeSearch(entry.keyword)}
                        >
                          <X className="w-4 h-4" aria-hidden />
                        </button>
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
