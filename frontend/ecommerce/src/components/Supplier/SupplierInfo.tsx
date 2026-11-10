'use client'
import React, { useState } from 'react';
import { PaginationParams, Product, Supplier } from '@/models/types';
import { Avatar, Icon } from '@mui/material';
import Image from 'next/image';
import { Button } from '@/components/ui/button';
import { Add, ForumOutlined } from '@mui/icons-material';
import { useTranslations } from 'next-intl';
import { useQuery } from '@tanstack/react-query';
import { PRODUCT_KEY } from '@/utils/constants/queryKey';
import { getProductFromSupplier } from '@/api/supplierApi';
import { Tabs, type TabsProps } from 'antd';

type SupplierInfoPropsType = {
  supplier: Supplier;
};

const SupplierInfo = ({ supplier }: SupplierInfoPropsType) => {
  const t = useTranslations();

  const [pagination, setPagination] = useState<PaginationParams>({ page: 1, limit: 10, totalPage: 0 });

  const { data: listProduct, isLoading: isLoadingProduct } = useQuery({
    queryKey: [PRODUCT_KEY, pagination],
    queryFn: async () =>
      await getProductFromSupplier(supplier.id, 0, 15).then((response) => {
        setPagination({ ...pagination, totalPage: response.data.totalPage });
        return response.data.responseList;
      }),
  });


  const items: TabsProps['items'] = [
    {
      key: '1',
      label: 'Tab 1',
      children: 'Content of Tab Pane 1',
    },
    {
      key: '2',
      label: 'Tab 2',
      children: 'Content of Tab Pane 2',
    },
    {
      key: '3',
      label: 'Tab 3',
      children: 'Content of Tab Pane 3',
    },
  ];

  return (
    <React.Fragment>
      <div className="bg-white flex justify-center py-5">
        <div className="w-[80%] flex">
          <div className="w-1/3 relative">
            <Image
              src={supplier.background}
              alt={'Store background'}
              width={350}
              height={100}
              className="relative opacity-90"
            />
            <div className="absolute top-0 left-0 w-full h-full bg-opacity-50 z-10 p-4 text-white">
              <div className="flex">
                <Avatar src={supplier.avatar} style={{ width: '5rem', height: '5rem' }} />
                <div className=" ml-2">
                  <h3 className="font-medium text-lg">{supplier.name}</h3>
                  <span>asljlajslasfjl</span>
                </div>
              </div>

              <div className="flex">
                <Button className=" border-solid border-2 border-gray-300 flex items-center mr-3">
                  <Icon component={Add} />
                  <span>{t('common.follow')}</span>
                </Button>
                <Button className="border-solid border-2">
                  <Icon component={ForumOutlined} />
                  <span>{t('common.contact')}</span>
                </Button>
              </div>
            </div>
          </div>
          <div className="bg-yellow-300 w-2/3">asdf</div>
        </div>
      </div>
      <Tabs defaultActiveKey="2" items={items} />

      {/* <div>
        {listProduct.map((item: Product) => (
          <>{item.name}</>
        ))}
      </div> */}
    </React.Fragment>
  );
};

export default SupplierInfo;
