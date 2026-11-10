import { Card } from '@mui/material';
import { ShieldAlert } from 'lucide-react';
import React from 'react';
import AddCardForm from './AddCardForm';

export default function AddCardPage() {
  return (
    <Card className="w-2/5 mx-auto px-3 py-4 bg-white">
      <h3 className="font-medium text-xl">Thêm thẻ Tín dụng/Ghi nợ</h3>
      <div className="flex border border-green-300 bg-green-100 p-3 mt-3">
        <div>
          <ShieldAlert className="text-green-300" />
        </div>
        <div className="">
          <div>Thông tin thẻ của bạn được bảo vệ.</div>
          <div className="text-sm opacity-90">
            Chúng tôi hợp tác với các đơn vị cung cấp dịch vụ thanh toán uy tín để đảm bảo thông tin thẻ của bạn được an
            toàn và bảo mật tuyệt đối. Shopee sẽ không có quyền truy cập vào thông tin thẻ của bạn.
          </div>
        </div>
      </div>
      <div className="flex justify-between mt-3">
        <span>Cart detail</span>
        <div className="flex gap-1">
          <span className="bg-[url(https://banner2.cleanpng.com/20180425/yde/aveotr6ag.webp)] bg-cover bg-center w-6 h-6 block" />
          <span className="bg-[url(https://download.logo.wine/logo/Mastercard/Mastercard-Logo.wine.png)] bg-cover bg-center w-6 h-6 block" />
          <span className="bg-[url(https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTdQUwr3c4YsMwncNL4Hsx5X4wjEjSUWYBU4E728YnrAUk1_kM_DZi-OMtGaZFozW_TO2s&usqp=CAU)] bg-cover bg-center w-6 h-6 block" />
          <span className="bg-[url(https://cdn-icons-png.flaticon.com/512/349/349228.png)] bg-cover bg-center w-6 h-6 block" />
        </div>
      </div>
      <AddCardForm />
    </Card>
  );
}
