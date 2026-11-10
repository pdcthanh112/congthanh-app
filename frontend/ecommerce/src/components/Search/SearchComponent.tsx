'use client';

import React, { useEffect, useRef, useState } from 'react';
import { Search as SearchIcon, ArrowDropDownOutlined } from '@mui/icons-material';
import { useTranslations } from 'next-intl';
import { Card } from '@mui/material';
import { useMutation, useQuery } from '@tanstack/react-query';
import { CATEGORY_KEY } from '@/utils/constants/queryKey';
import { getAllCategoryJson } from '@/api/categoryApi';
import { Category } from '@/models/types';
import { Input } from '@/components/ui';
import { useAuthenticated } from '@/hooks/auth/useAuthenticated';
import { addSearchHistoryItem, getSearchRecommend, removeSearchHistoryItem } from '@/api/searchApi';
import useDebounce from '@/hooks/useDebounce';
import { Tag } from 'antd';
import { Clock, Loader2, Search, TrendingUp, X } from 'lucide-react';
import { cn } from '@/lib/utils';

const searchHistory = [
  { id: 1, value: 'abchasf ạldfs adsjlj' },
  { id: 2, value: 'jljalsdf lajslfj alsjdf' },
  { id: 3, value: 'lajlsjf' },
  { id: 4, value: 'aslfj ajsljf' },
  { id: 5, value: 'asljfls lasjlfjo iasfjll' },
];

const searchTrending = [
  'lajlfjasl ajfls alsjfl',
  'asjljas asjlsf',
  'aloijasdl aljlfdjl akjflda sdjdis',
  'ljlkjajldsjfjdslj',
];

export default function SearchComponent() {
  const [showModal, setShowModal] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const [completion, setCompletion] = useState('');
  const [showCompletion, setShowCompletion] = useState(false);
  const [selectedIndex, setSelectedIndex] = useState(-1);
  const modalRef = useRef<HTMLDivElement | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const listRef = useRef(null);

  const { user } = useAuthenticated();
  const t = useTranslations();

  const debouncedSearchTerm = useDebounce(searchTerm, 300);

  const { data: categoryData } = useQuery({
    queryKey: [CATEGORY_KEY],
    queryFn: async () => await getAllCategoryJson().then((response) => response.data),
  });

  // const {data: searchHistory} = useQuery({queryKey: ['search-history'], queryFn: async () => await getSearchHistory()})

  // const {data: searchTrending} = useQuery({queryKey: ['search-trending'], queryFn: async () => await getSearchTrending()})

  const { mutate: addSearchHistory } = useMutation({
    mutationKey: ['search-history'],
    mutationFn: async (keyword: string) => await addSearchHistoryItem(user?.accountId as string, keyword),
  });
  const { mutate: removeSearchItem } = useMutation({
    mutationKey: ['search-history'],
    mutationFn: async (id: number) => await removeSearchHistoryItem(id),
  });

  const { data: recommend = [], isLoading } = useQuery({
    queryKey: ['search-recommend', debouncedSearchTerm],
    queryFn: async () => {
      if (!debouncedSearchTerm) return [];
      const response = await getSearchRecommend(debouncedSearchTerm);
      if (!response) return [];
      return response.sort((a: string, b: string) => {
        const aStartsWith = a.toLowerCase().startsWith(debouncedSearchTerm.toLowerCase());
        const bStartsWith = b.toLowerCase().startsWith(debouncedSearchTerm.toLowerCase());

        if (aStartsWith && !bStartsWith) return -1;
        if (!aStartsWith && bStartsWith) return 1;
        return 0;
      });
    },
    enabled: !!debouncedSearchTerm,
  });

  useEffect(() => {
    if (debouncedSearchTerm.trim()) {
      setShowModal(true);
      setSelectedIndex(-1);
    } else {
      setShowModal(false);
      setCompletion('');
      setShowCompletion(false);
    }
  }, [debouncedSearchTerm]);

  useEffect(() => {
    if (recommend.length > 0 && searchTerm.trim()) {
      const firstMatch = recommend.find((s) => s.toLowerCase().startsWith(searchTerm.toLowerCase()));

      if (firstMatch && searchTerm.length > 0) {
        setCompletion(firstMatch.slice(searchTerm.length));
        setShowCompletion(true);
      } else {
        setCompletion('');
        setShowCompletion(false);
      }
    } else {
      setCompletion('');
      setShowCompletion(false);
    }
  }, [recommend, searchTerm]);

  const handleSearch = (term: string) => {
    if (!term.trim()) return;

    addSearchHistory(term);

    console.log('Searching for:', term);
    setSearchTerm(term);
    setShowModal(false);
  };

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (modalRef.current && !modalRef.current.contains(event.target as Node)) {
        setShowModal(false);
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  useEffect(() => {
    if (selectedIndex >= 0 && listRef.current) {
      const selectedElement = listRef.current.children[selectedIndex] as HTMLElement;
      selectedElement?.scrollIntoView({ block: 'nearest' });
    }
  }, [selectedIndex]);

  const handleInputChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const newValue = event.target.value;
    setSearchTerm(newValue);

    setShowCompletion(false);
    setCompletion('');
  };

  const acceptCompletion = () => {
    if (completion && showCompletion) {
      const newQuery = searchTerm + completion;
      setSearchTerm(newQuery);
      setCompletion('');
      setShowCompletion(false);
      setShowModal(false);
    }
  };

  const handleKeyDown = (event: React.KeyboardEvent) => {
    const eventKey = event.key;
    if ((eventKey === 'Tab' || eventKey === 'ArrowRight') && showCompletion && completion) {
      event.preventDefault();
      acceptCompletion();
      return;
    }

    if (!showModal || recommend.length === 0) return;

    switch (eventKey) {
      case 'ArrowDown':
        event.preventDefault();
        setSelectedIndex((prev) => (prev < recommend.length - 1 ? prev + 1 : 0));
        break;
      case 'ArrowUp':
        event.preventDefault();
        setSelectedIndex((prev) => (prev > 0 ? prev - 1 : recommend.length - 1));
        break;
      case 'Enter':
        event.preventDefault();
        if (selectedIndex >= 0) {
          setSearchTerm(recommend[selectedIndex]);
          handleSearch(recommend[selectedIndex]);
        } else if (showCompletion && completion) {
          acceptCompletion();
        }
        break;
      case 'Escape':
        setShowModal(false);
        setSelectedIndex(-1);
        setShowCompletion(false);
        setCompletion('');
        break;
    }
  };

  const handleClear = () => {
    setSearchTerm('');
    setCompletion('');
    setShowCompletion(false);
    setShowModal(false);
    inputRef.current?.focus();
  };

  return (
    <div className="hidden sm:flex items-center h-10 rounded-md grow relative" ref={modalRef}>
      <span className="bg-gray-300 h-[2.5rem] w-20 rounded-l-md flex justify-center items-center relative group">
        <span className="">
          {t('common.all')}
          <ArrowDropDownOutlined />
        </span>

        <Card className="text-[#a4a4a4] text-sm hidden absolute transform translate-x-[36%] translate-y-[56%] py-2 w-[15rem] group-hover:block group-hover:z-50 max-h-96 group-hover:overflow-y-scroll">
          {categoryData?.map((item: Category) => (
            <div key={item.id} className="leading-6 px-2 hover:bg-gray-100">
              {item.name}
            </div>
          ))}
        </Card>
      </span>
      <div className="relative w-full h-full bg-white">
        {showCompletion && completion && (
          <div className="absolute inset-0 pl-1 py-3 text-lg pointer-events-none flex items-center">
            <span className="invisible">{searchTerm}</span>
            <span className="text-gray-400 bg-gray-100 px-1 rounded">{completion}</span>
          </div>
        )}
        <Input
          ref={inputRef}
          className="p-2 h-full w-full grow shrink focus:outline-hidden pl-4 rounded-none"
          onFocus={() => setShowModal(true)}
          value={searchTerm}
          onChange={handleInputChange}
          onKeyDown={handleKeyDown}
        />

        <div className="absolute inset-y-0 right-0 pr-4 flex items-center gap-2">
          {isLoading && <Loader2 className="h-5 w-5 text-blue-500 animate-spin" />}
          {searchTerm && !isLoading && (
            <button onClick={handleClear} className="p-1 hover:bg-gray-100 rounded-full transition-colors">
              <X className="h-4 w-4 text-gray-400" />
            </button>
          )}
        </div>
      </div>
      {showCompletion && completion && (
        <div className="absolute top-full left-0 mt-1 text-xs text-gray-500 bg-white px-2 py-1 rounded shadow-sm border">
          Press Tab or → to complete
        </div>
      )}

      {showModal && (
        <div className="absolute top-full left-0 w-full min-h-64 mt-2 bg-white rounded-lg shadow-lg border border-gray-200 z-50">
          {searchTerm ? (
            <div ref={listRef} className="p-4">
              {recommend.map((item, index) => (
                <div key={item} className={cn(selectedIndex === index && 'bg-blue-50 text-blue-700')}>
                  <span className="truncate">
                    <span className="font-medium text-blue-600">{item.slice(0, searchTerm.length)}</span>
                    {item.slice(searchTerm.length)}
                  </span>
                </div>
              ))}
              {!isLoading && recommend.length === 0 && searchTerm.trim() && (
                <div className="p-8 text-center text-gray-500">
                  <Search className="h-8 w-8 mx-auto mb-2 text-gray-300" />
                  <p className="text-sm">No suggestions found for &quot;{searchTerm}&quot;</p>
                </div>
              )}

              {recommend.length > 0 && (
                <div className="px-4 py-2 bg-gray-50 text-xs text-gray-500 border-t border-gray-100">
                  Use ↑↓ to navigate, Tab/→ to complete, Enter to select, Esc to close
                </div>
              )}
            </div>
          ) : (
            <React.Fragment>
              <div className="p-4 border-t border-gray-100">
                <div className="flex items-center gap-2 text-sm text-gray-500 mb-2">
                  <TrendingUp className="w-4 h-4" />
                  <span>Trending Searches</span>
                </div>
                {searchTrending.length > 0 &&
                  searchTrending.map((term, id) => (
                    <Tag
                      key={id}
                      icon="#"
                      className="py-1 px-2 hover:bg-gray-100 cursor-pointer rounded"
                      onClick={() => handleSearch(term)}
                    >
                      {term}
                    </Tag>
                  ))}
              </div>

              <div className="p-4">
                <div className="flex items-center gap-2 text-sm text-gray-500 mb-2">
                  <Clock className="w-4 h-4" />
                  <span>Recent Searches</span>
                </div>
                {searchHistory.length > 0 &&
                  searchHistory.map((term) => (
                    <div
                      key={term.id}
                      className="flex items-center justify-between py-2 px-2 hover:bg-gray-100 cursor-pointer rounded"
                      onClick={() => handleSearch(term.value)}
                    >
                      <span>{term.value}</span>
                      <X
                        className="w-4 h-4 text-gray-400 hover:text-gray-600"
                        onClick={() => removeSearchItem(term.id)}
                      />
                    </div>
                  ))}
              </div>
            </React.Fragment>
          )}
        </div>
      )}

      <SearchIcon
        className="w-14! h-10! p-1 bg-yellow-400 hover:bg-yellow-500 rounded-r-md hover:cursor-pointer"
        onClick={() => handleSearch(searchTerm)}
      />
    </div>
  );
}
