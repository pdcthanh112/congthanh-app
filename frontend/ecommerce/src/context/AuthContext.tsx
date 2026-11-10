import { createContext, useState } from 'react';
import { Customer } from '@/models/types';
import { getAccessTokenFromLocalStorage, getProfileFromLocalStorage } from '@/utils/auth';

interface AuthContextInterface {
  isAuthenticated: boolean;
  setIsAuthenticated: React.Dispatch<React.SetStateAction<boolean>>;
  profile: Customer | null;
  setProfile: React.Dispatch<React.SetStateAction<Customer | null>>;
  //   extendedPurchase: ExtendedPurchase[]
  //   setExtendedPurchase: React.Dispatch<React.SetStateAction<ExtendedPurchase[]>>
  reset: () => void;
}

const initialAuthContext: AuthContextInterface = {
  isAuthenticated: Boolean(getAccessTokenFromLocalStorage()),
  setIsAuthenticated: () => null,
  profile: getProfileFromLocalStorage(),
  setProfile: () => null,
  //   extendedPurchase: [],
  //   setExtendedPurchase: () => null,
  reset: () => null,
};

export const AuthContext = createContext<AuthContextInterface>(initialAuthContext);

export const AuthContextProvider = ({ children }: { children: React.ReactNode }) => {
  const [isAuthenticated, setIsAuthenticated] = useState<boolean>(initialAuthContext.isAuthenticated);
  const [profile, setProfile] = useState<Customer | null>(initialAuthContext.profile);
  //   const [extendedPurchase, setExtendedPurchase] = useState<ExtendedPurchase[]>(initialAuthContext.extendedPurchase)

  const reset = () => {
    setIsAuthenticated(false);
    setProfile(null);
    // setExtendedPurchase([])
  };

  return (
    <AuthContext.Provider
      value={{
        isAuthenticated,
        setIsAuthenticated,
        profile,
        setProfile,
        //  extendedPurchase, setExtendedPurchase,
        reset,
      }}>
      {children}
    </AuthContext.Provider>
  );
};
