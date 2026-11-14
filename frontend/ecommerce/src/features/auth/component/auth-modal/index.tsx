'use client';

import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import KeycloakSignIn from '@/features/auth/component/KeycloakSignIn';
import useAppModalStore from '@/store/useAppModal';
import { IMAGE } from '@/utils/constants/path';
import { useTranslations } from 'next-intl';
import Image from 'next/image';

const AuthModal = () => {
  const t = useTranslations('auth');
  const isOpen = useAppModalStore((state) => state.isOpenModalAuth);
  const close = useAppModalStore((state) => state.closeModalAuth);

  return (
    <Dialog open={isOpen} onOpenChange={(open) => !open && close()}>
      <DialogContent className="w-[calc(100%-2rem)] max-w-md max-h-[90vh] overflow-y-auto">
        <DialogHeader className="items-center">
          <Image src={IMAGE.appLogo} alt="CongThanhApp" width={160} height={80} />
          <DialogTitle className="sr-only">{t('login')}</DialogTitle>
          <DialogDescription>{t('login_intro')}</DialogDescription>
        </DialogHeader>
        <Tabs defaultValue="login" className="w-full">
          <TabsList className="grid w-full grid-cols-2">
            <TabsTrigger value="login">{t('login')}</TabsTrigger>
            <TabsTrigger value="register">{t('register')}</TabsTrigger>
          </TabsList>
          <TabsContent value="login" className="pt-4">
            <KeycloakSignIn mode="login" />
          </TabsContent>
          <TabsContent value="register" className="pt-4">
            <KeycloakSignIn mode="register" />
          </TabsContent>
        </Tabs>
      </DialogContent>
    </Dialog>
  );
};

export default AuthModal;
