import { Customer } from '@/models/types';

const checkLocalStorageSupport = () => {
  if (typeof window !== 'undefined' && typeof localStorage === 'undefined') {
    throw new Error('local storage does not support');
  }
  return true;
};

export const LocalStorageEventTarget = new EventTarget();

export const setAccessTokenToLocalStorage = (access_token: string) => {
  checkLocalStorageSupport();
  try {
    localStorage.setItem('access_token', access_token);
  } catch (error) {
    if (error === 'QuotaExceededError') {
      console.log('LocalStorage is full');
    }
  }
};

export const clearLocalStorage = () => {
  localStorage.removeItem('access_token');
  localStorage.removeItem('profile');
  const clearStorageEvent = new Event('clearLS');
  LocalStorageEventTarget.dispatchEvent(clearStorageEvent);
};
export const getAccessTokenFromLocalStorage = () => localStorage.getItem('access_token') || '';

export const getProfileFromLocalStorage = () => {
  const result = localStorage.getItem('profile');
  return result ? JSON.parse(result) : null;
};

export const setProfileToLocalStorage = (profile: Customer) => {
  localStorage.setItem('profile', JSON.stringify(profile));
};
