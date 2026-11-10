import { createContext, useCallback, useContext, useEffect, useState } from 'react';

interface AuthContextType {
  accessToken: string | null;
  saveAccessToken: (token: string) => void;
  clearAuth: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

interface AuthProviderProps {
  children: React.ReactNode;
}

export const AuthProvider = ({ children }: AuthProviderProps) => {
  const [accessToken, setAccessToken] = useState<string | null>(
    localStorage.getItem('access_token')
  );

  useEffect(() => {
    const storedToken = localStorage.getItem('access_token');
    if (storedToken) {
      setAccessToken(storedToken);
    }
  }, []);

  const saveAccessToken = useCallback((token: string | null) => {
    setAccessToken(token);
    if (token) {
      localStorage.setItem('access_token', token);
    } else {
      localStorage.removeItem('access_token'); 
    }
  }, []);

  const clearAuth = useCallback(() => {
    setAccessToken(null);
    localStorage.removeItem('access_token');
  }, []);

  return (
    <AuthContext.Provider value={{ accessToken, saveAccessToken, clearAuth }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = (): AuthContextType => {
  const context = useContext(AuthContext)
  
  if(!context) {
    throw new Error('Error')
  }

  return context
}
