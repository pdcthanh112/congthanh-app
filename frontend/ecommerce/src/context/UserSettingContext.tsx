import { createContext, useContext, useState, useEffect, SetStateAction } from 'react';

interface UserSettingInterface {
  settings: {
    theme: 'dark' | 'light' | 'system';
    language: string;
  };
  setSettings: React.Dispatch<SetStateAction<UserSettingInterface['settings']>>;
  reset: () => void;
}

const initialUserSettingContext: UserSettingInterface = {
  settings: {
    theme: 'light',
    language: 'us - EN',
  },
  setSettings: () => {},
  reset: () => {},
};

const UserSettingContext = createContext<UserSettingInterface>(initialUserSettingContext);

export const UserSettingProvider = ({ children }: { children: React.ReactNode }) => {
  const [settings, setSettings] = useState(initialUserSettingContext.settings);

  useEffect(() => {
    const fetchSettings = async () => {
      const res = await fetch('/api/user/settings');
      const data = await res.json();
      setSettings(data);
    };
    fetchSettings();
  }, []);

  const reset = () => {
    setSettings({ ...settings, theme: 'light', language: 'en - US' });
  };

  return <UserSettingContext.Provider value={{ settings, setSettings, reset }}>{children}</UserSettingContext.Provider>;
};

export const useUserSettings = () => useContext(UserSettingContext);
