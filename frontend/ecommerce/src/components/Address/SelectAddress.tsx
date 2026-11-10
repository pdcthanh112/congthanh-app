import React, { useState } from 'react';
import { useAppSelector } from '@/redux/store';
import { useQuery } from '@tanstack/react-query';
import { getAddressByCustomer } from '@/api/addressApi';
import { Modal, Radio, Button, message } from 'antd';
import TabCreateAddress from './TabCreateAddress';
import TabEditAddress from './TabEditAddress';
import { Icon } from '@mui/material';
import { Add } from '@mui/icons-material';
import { Address } from '@/models/types';
import { CreateAddressForm, UpdateAddressForm } from '@/models/form';
import { useCreateAddress, useUpdateAddress } from '@/hooks/address/addressHook';
import { SubmitHandler } from 'react-hook-form';
import { useTranslations } from 'next-intl';

type PropsType = {
  isOpen: boolean;
  handleOpen: (isOpen: boolean) => void;
  changeAddress: (address: Address) => void;
};

const SelectAddress = ({ isOpen, handleOpen, changeAddress }: PropsType) => {
  const currentUser = useAppSelector((state) => state.auth.currentUser);

  const [activeTab, setActiveTab] = useState<{ currentTab: string; address: Address | null}>({ currentTab: 'show', address: null });
  const [address, setAddress] = useState<Address>();

  const { mutate: createAddress } = useCreateAddress();
  const { mutate: updateAddress } = useUpdateAddress();

  const t = useTranslations('common');
  const { data: listAddress } = useQuery({
    queryKey: ['address'],
    queryFn: async () => await getAddressByCustomer(currentUser.userInfo.accountId).then((response) => response.data),
  });

  const handleChangeAddress = () => {
    if (address) {
      changeAddress(address);
      handleOpen(false);
    }
  };

  const handleCreate: SubmitHandler<CreateAddressForm> = (data) => {
    createAddress(data, {
      onSuccess() {
        message.success(t('create_successfully'));
      },
      onError() {
        message.error(t('create_failed'));
      },
    });
  };

  const handleUpdate: SubmitHandler<UpdateAddressForm> = (data) => {
    updateAddress(data, {
      onSuccess() {
        message.success(t('change_successfully'));
      },
      onError() {
        message.error(t('change_failed'));
      },
    });
  };

  const TabShowAddress = () => {
    return (
      <React.Fragment>
        <h3>Your address</h3>
        <Radio.Group defaultValue={listAddress?.find((address: Address) => address.isDefault === true)} onChange={(event) => setAddress(event.target.value)}>
          {listAddress?.map((item: Address) => (
            <Radio key={item.id} value={item} checked={item.isDefault} className="my-2 px-3 py-2 border-t-2">
              <span className="mr-1 text-lg">Cong Thanh</span>|<span className="ml-2">{item.phone}</span>
              <div className="flex justify-between">
                <p className="w-[25rem]">
                  {item.street}, {item.addressLine3}, {item.addressLine2}, {item.addressLine1}, {item.country}
                </p>
                <span className="text-blue-400 flex justify-end" onClick={() => setActiveTab({ currentTab: 'edit', address: item })}>
                  Edit
                </span>
              </div>
              {item.isDefault && <span className="bg-yellow-300 px-2 py-1 rounded-md">Default</span>}
            </Radio>
          ))}
        </Radio.Group>

        <Button onClick={() => setActiveTab({ currentTab: 'create', address: null })} className="bg-yellow-300">
          <Icon component={Add} />
          <span>Add new address</span>
        </Button>

        <div className="flex justify-end">
          <Button type="default" style={{ marginRight: '8px' }} danger onClick={() => handleOpen(false)}>
            Cancel
          </Button>
          <Button type="primary" danger onClick={() => handleChangeAddress()}>
            Confirm
          </Button>
        </div>
      </React.Fragment>
    );
  };

  return (
    <Modal open={isOpen} onCancel={() => handleOpen(false)} footer={null}>
      {activeTab.currentTab === 'show' && <TabShowAddress />}
      {activeTab.currentTab === 'create' && <TabCreateAddress handleCreate={handleCreate} onBack={() => setActiveTab({ currentTab: 'show', address: null })} />}
      {activeTab.currentTab === 'edit' && activeTab.address!== null && <TabEditAddress address={activeTab.address} onBack={() => setActiveTab({ currentTab: 'show', address: null })} handleUpdate={handleUpdate} />}
    </Modal>
  );
};

export default SelectAddress;
