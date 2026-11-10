'use client';

import { useEffect, useState, useCallback } from 'react';
import { useSession } from 'next-auth/react';
import { useDispatch } from 'react-redux';
import { initialCart } from '@/redux/reducers/cartReducer';
import { useWishlistStore } from '@/store/wishlistStore';
import { fetchUserData } from '@/app/action/sync-data';

export function useSyncUserData() {
  const { data: session, status } = useSession();
  const dispatch = useDispatch();
  const [isSyncing, setIsSyncing] = useState(false);
  const [error, setError] = useState(null);

  const setWishlistItems = useWishlistStore((state) => state.setWishlist);

  const syncData = useCallback(async () => {
    if (status !== 'authenticated') return;

    setIsSyncing(true);
    setError(null);

    try {
      initialCart(session.user.accountId);

      setWishlistItems([]);
    } catch (error) {
      setError(error?.message || 'Failed to sync user data');
      console.error('Sync error:', error);
    } finally {
      setIsSyncing(false);
    }
  }, [status, dispatch, setWishlistItems]);

  // Sync automatically when session becomes authenticated
  useEffect(() => {
    if (status === 'authenticated') {
      syncData();
    }
  }, [status, syncData]);

  return { syncData, isSyncing, error };
}
