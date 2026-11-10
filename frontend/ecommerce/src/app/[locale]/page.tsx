import { NextComponentType, NextPage, NextPageContext } from "next";
import { AppProps } from "next/app";
import { ReactElement, ReactNode } from "react";
import Layout from "./layout";

export type LayoutProps = {
  children: React.ReactNode;
};

export type NextPageWithLayout<P = {}, IP = P> = NextPage<P, IP> & {
  getLayout?: (page: ReactElement) => ReactNode;
};

export type AppPropsWithLayout = AppProps & {
  Component: NextComponentType<NextPageContext, any, any> & NextPageWithLayout;
};

export default function App({ Component, pageProps, router }: AppPropsWithLayout) {
  return (
    <Layout>
      <></>
    </Layout>
  );
}
