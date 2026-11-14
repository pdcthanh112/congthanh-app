'use client';

import { getAllCategoryJson } from '@/api/categoryApi';
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui';
import { useAuthenticated } from '@/hooks/auth/useAuthenticated';
import { Link } from '@/i18n/routing';
import { login } from '@/lib/auth/actions';
import type { Category } from '@/models/types';
import { PATH } from '@/utils/constants/path';
import { CATEGORY_KEY } from '@/utils/constants/queryKey';
import { useQuery } from '@tanstack/react-query';
import { ArrowLeft, ChevronRight } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useState } from 'react';

type Tree = Extract<Category, { children: unknown }>;

export default function AppSidebar({ onNavigate }: { onNavigate?: () => void }) {
  const t = useTranslations();
  const { user } = useAuthenticated();
  const [selected, setSelected] = useState<Tree | null>(null);
  const { data: categories } = useQuery({
    queryKey: [CATEGORY_KEY],
    queryFn: async () => (await getAllCategoryJson()).data as Tree[],
  });

  return (
    <div className="w-80 max-w-[85vw] h-full bg-white overflow-y-auto relative">
      <div className="bg-green-500 flex px-6 py-3 items-center gap-3">
        <Avatar>
          {user?.image && <AvatarImage src={user.image} alt="" />}
          <AvatarFallback>{user?.name?.[0] ?? '?'}</AvatarFallback>
        </Avatar>
        {user ? (
          <span className="text-white font-semibold text-lg truncate">
            {t('common.hello')}, {user.name?.split(' ').pop()}
          </span>
        ) : (
          <button type="button" className="text-white font-semibold text-lg hover:underline" onClick={() => login()}>
            {t('auth.login')}
          </button>
        )}
      </div>

      {selected ? (
        <div>
          <button
            type="button"
            className="w-full flex items-center gap-2 px-4 py-2 hover:bg-gray-100 border-b border-gray-300"
            onClick={() => setSelected(null)}
          >
            <ArrowLeft size={18} aria-hidden />
            {t('common.back')}
          </button>
          <Link
            href={`/category/${selected.slug}`}
            onClick={onNavigate}
            className="block px-6 py-2 font-semibold hover:bg-gray-100"
          >
            {selected.name}
          </Link>
          <ul>
            {selected.children.map((child) => (
              <li key={child.id}>
                <Link
                  href={`/category/${selected.slug}/${child.slug}`}
                  onClick={onNavigate}
                  className="block px-6 py-2 hover:bg-gray-100 truncate"
                >
                  {child.name}
                </Link>
              </li>
            ))}
          </ul>
        </div>
      ) : (
        <>
          <div className="border-b border-gray-300">
            <h2 className="font-semibold text-lg px-6 pt-2">{t('common.Category')}</h2>
            <ul>
              {categories?.map((item) => (
                <li key={item.id}>
                  <button
                    type="button"
                    className="w-full hover:bg-gray-100 px-6 py-2 flex justify-between items-center text-left"
                    onClick={() => setSelected(item)}
                  >
                    <span className="truncate">{item.name}</span>
                    <ChevronRight size={16} aria-hidden />
                  </button>
                </li>
              ))}
            </ul>
          </div>
          <div>
            <h2 className="font-semibold text-lg px-6 pt-2">{t('sidebar.help_and_settings')}</h2>
            <Link
              href={PATH.MANAGE_PATH_URL.PROFILE}
              onClick={onNavigate}
              className="block hover:bg-gray-100 px-6 py-2"
            >
              {t('sidebar.your_account')}
            </Link>
            <span className="block px-6 py-2 text-gray-500">{t('sidebar.customer_service')}</span>
          </div>
        </>
      )}
    </div>
  );
}
